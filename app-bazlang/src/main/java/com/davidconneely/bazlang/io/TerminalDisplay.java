package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.cell.CellAttributes;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.CellBufferRenderer;
import com.davidconneely.cell.PixelMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.repl.Display;
import com.davidconneely.repl.TerminalEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.reader.LineReader;

/**
 * Terminal-based Display implementation with dynamic screen regions:
 *
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │      Application Display Area           │  ← scrollable output
 * │ status                                  │  ← status (when input visible)
 * │ ❯ input here                            │  ← input area (REPL: ❯, INPUT: bold #/$)
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * <p>Content rows are written as raw UTF-8 (bypassing JLine's ACS character substitution) so that
 * Unicode box-drawing characters, block elements, sextants, and braille render correctly on modern
 * UTF-8 terminals. Line input uses JLine's {@link LineReader} for proper line-editing behaviour.
 */
@SuppressWarnings("PMD.TooManyFields")
public class TerminalDisplay implements BazLangDisplay {
  private boolean printingSystemPrompt = false;
  private int currentInputHeight = 1;

  private int currentReservedRows() {
    return inputVisible ? (3 + currentInputHeight) : 0;
  }

  private final TerminalEngine engine;

  // Display buffer (UTF-32 codepoint grid for display area)
  private CellBuffer cellBuffer;
  private final CellBufferRenderer renderer = new CellBufferRenderer();

  // Input state
  private boolean inputVisible = false;

  private enum InputContext {
    REPL,
    INPUT_NUMERIC,
    INPUT_STRING
  }

  private InputContext currentInputMode = InputContext.REPL;
  private String statusText = "";
  private String prefillText = null; // Pre-fill for next readln()

  // Pending render: print() marks dirty; flush()/println()/cls() drive render()
  private boolean dirty = false;

  // Rate-limiting: frame-boundary renders (println, inkey, plot) cap at ~50fps (~20ms).
  // flush()-driven renders use a longer threshold so that game drawing loops (which call flush()
  // after every PRINT with a semicolon) do not trigger partial mid-frame renders.
  private static final long FRAME_RENDER_INTERVAL_MS = 20L;
  private static final long FLUSH_RENDER_INTERVAL_MS = 100L;
  private long lastRenderTimeMs = 0L;
  private boolean fastMode = false;

  // Colour state
  private int activeInk = -1; // Terminal default
  private int activePaper = -1; // Terminal default
  private int activeBright = 0;
  private int activeFlash = 0;
  private int activeInverse = 0;
  private int activeOver = 0;
  private static final int[] ZX_TO_RGB = {
    0x000000, 0x0000D7, 0xD70000, 0xD700D7, 0x00D700, 0x00D7D7, 0xD7D700, 0xD7D7D7
  };
  private static final int[] ZX_TO_RGB_BRIGHT = {
    0x000000, 0x0000FF, 0xFF0000, 0xFF00FF, 0x00FF00, 0x00FFFF, 0xFFFF00, 0xFFFFFF
  };

  // Cursor tracking (logical position in display area)
  private int cursorRow = 0;
  private int cursorCol = 0;

  // Resize flag: set by WINCH signal handler on JLine's signal thread; handled by main thread in
  // render()
  private final AtomicBoolean resizePending = new AtomicBoolean(false);

  // Break flag for Ctrl+C (set by INT signal handler on JLine's signal thread, read by main thread)
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);

  // Track whether close() has been called (for idempotent clean-up between main thread and shutdown
  // hook)
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public TerminalDisplay(TerminalEngine engine) {
    this.engine = engine;

    this.engine.setInputHeightListener(this::adjustLayoutForInputHeight);
    this.engine.onInterrupt(() -> breakFlag.set(true));
    this.engine.onResize(() -> resizePending.set(true));

    // Ensure terminal is restored on JVM shutdown (e.g., if signal terminates the process)
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));

    initBuffer();
    render();
  }

  public void adjustLayoutForInputHeight(int newInputHeight) {
    if (this.currentInputHeight != newInputHeight) {
      this.currentInputHeight = newInputHeight;
      resizeBufferIfNeeded();
      render();
      engine.forceRedrawFromCursor();
    }
  }

  private void initBuffer() {
    final int rawCols = engine.getColumns();
    final int cols = rawCols > 0 ? rawCols : 80;
    final int rawRows = engine.getRows();
    final int rows = Math.max(1, (rawRows > 0 ? rawRows : 24) - currentReservedRows());
    cellBuffer = new CellBuffer(rows, cols, QuadrantMode.INSTANCE);
  }

  private void clearBuffer() {
    cellBuffer.clear();
    cursorRow = 0;
    cursorCol = 0;
  }

  private void resizeBufferIfNeeded() {
    final int rawCols = engine.getColumns();
    final int newCols = rawCols > 0 ? rawCols : 80;
    final int rawRows = engine.getRows();
    final int newRows = Math.max(1, (rawRows > 0 ? rawRows : 24) - currentReservedRows());
    if (newRows != cellBuffer.rows() || newCols != cellBuffer.cols()) {
      cellBuffer.resize(newRows, newCols);
      cursorRow = Math.min(cursorRow, cellBuffer.rows() - 1);
      cursorCol = Math.min(cursorCol, cellBuffer.cols() - 1);
    }
  }

  // === Layout and Rendering ===

  private void render() {
    lastRenderTimeMs = System.currentTimeMillis();
    dirty = false;
    resizePending.set(false);
    resizeBufferIfNeeded();
    final int termWidth = engine.getColumns();
    final int termHeight = engine.getRows();
    final int rowsToRender = Math.min(cellBuffer.rows(), termHeight);
    final int colsToRender = Math.min(cellBuffer.cols(), termWidth);

    // Ensure cursor is hidden during render
    final var out = engine.writer();
    out.print("\033[?25l");
    renderer.renderContentRows(out, cellBuffer, rowsToRender, colsToRender);
    renderInputRows(out, rowsToRender, termWidth);

    out.flush();
  }

  private void renderInputRows(PrintWriter out, int rowsToRender, int termWidth) {
    // Status and input rows (only written when input is visible to avoid overwriting or scrolling
    // during execution)
    if (inputVisible) {
      // 1. Line above input
      out.printf("\033[%d;1H", rowsToRender + 1);
      out.print("\033[90m" + "─".repeat(Math.max(0, termWidth)) + "\033[m");
      out.print("\033[K");

      // 2. Input rows (cleared, JLine will draw prompt and input here)
      for (int i = 0; i < currentInputHeight; i++) {
        out.printf("\033[%d;1H", rowsToRender + 2 + i);
        out.print("\033[K");
      }

      // 3. Line below input
      out.printf("\033[%d;1H", rowsToRender + 2 + currentInputHeight);
      out.print("\033[90m" + "─".repeat(Math.max(0, termWidth)) + "\033[m");
      out.print("\033[K");

      // 4. Status/report row (left-aligned status text, right-aligned "BazLang REPL")
      final String lineText = getStatusLine(termWidth);
      out.printf("\033[%d;1H", rowsToRender + 3 + currentInputHeight);
      out.print("\033[37m" + lineText + "\033[m");
      out.print("\033[K");

      // Position the cursor back to the input start row for JLine
      out.printf("\033[%d;1H", rowsToRender + 2);
    }
  }

  private String getStatusLine(int termWidth) {
    final String rightText = "BazLang REPL";
    final int rightLen = rightText.length();
    final int spacesNeeded = termWidth - statusText.length() - rightLen;
    String lineText;
    if (spacesNeeded > 0) {
      lineText = statusText + " ".repeat(spacesNeeded) + rightText;
    } else {
      final int availableForStatus = termWidth - rightLen - 1;
      lineText =
          availableForStatus > 0
              ? statusText.substring(0, availableForStatus) + " " + rightText
              : statusText;
    }
    return lineText;
  }

  private void renderIfDue(boolean bypassFastMode) {
    if (fastMode && !bypassFastMode) {
      return;
    }
    if ((dirty || resizePending.get())
        && System.currentTimeMillis() - lastRenderTimeMs >= FRAME_RENDER_INTERVAL_MS) {
      render();
    }
  }

  /**
   * Renders only if dirty and at least ~20ms have elapsed since the last render (~50fps cap). Used
   * by println(), plot(), scroll(), and inkey() as frame-boundary render points.
   */
  private void renderIfDue() {
    renderIfDue(false);
  }

  // === Display Interface Implementation ===

  @Override
  public void setFastMode(boolean fast) {
    this.fastMode = fast;
    if (!fast && dirty) {
      // SLOW: flush any pending frame immediately.
      render();
    }
  }

  @Override
  public int currentRow() {
    return cursorRow;
  }

  @Override
  public int currentCol() {
    return cursorCol;
  }

  @Override
  public int printWidth() {
    return cellBuffer.cols();
  }

  @Override
  public int printHeight() {
    return cellBuffer.rows();
  }

  @Override
  public int plotWidth() {
    return cellBuffer.pixelWidth();
  }

  @Override
  public int plotHeight() {
    return cellBuffer.pixelHeight();
  }

  @Override
  public void setInk(int colour) {
    this.activeInk = colour;
  }

  @Override
  public void setPaper(int colour) {
    this.activePaper = colour;
  }

  @Override
  public void setBright(int bright) {
    this.activeBright = bright;
  }

  @Override
  public void setFlash(int flash) {
    this.activeFlash = flash;
  }

  @Override
  public void setInverse(int inverse) {
    this.activeInverse = inverse;
  }

  @Override
  public void setOver(int over) {
    this.activeOver = over;
  }

  @Override
  public int plotMode() {
    return cellBuffer.mode().pixelsPerCellX() * cellBuffer.mode().pixelsPerCellY();
  }

  @Override
  public void cls() {
    clearBuffer();
    dirty = true;
    if (!fastMode) {
      render();
    }
  }

  @Override
  public void locate(int row, int col) {
    cursorRow = Math.clamp(row, 0, cellBuffer.rows() - 1);
    cursorCol = Math.clamp(col, 0, cellBuffer.cols() - 1);
  }

  @Override
  public void print(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }

    text.codePoints()
        .forEach(
            cp -> {
              if (cp == 10) { // Newline (LF)
                cursorRow++;
                cursorCol = 0;
                if (cursorRow >= cellBuffer.rows()) {
                  scrollBuffer();
                  cursorRow = cellBuffer.rows() - 1;
                }
              } else if (cp == 13) { // Carriage return (CR)
                cursorCol = 0;
              } else if (cp >= 32 && cp != 127) {
                if (cursorCol >= cellBuffer.cols()) {
                  cursorRow++;
                  cursorCol = 0;
                  if (cursorRow >= cellBuffer.rows()) {
                    scrollBuffer();
                    cursorRow = cellBuffer.rows() - 1;
                  }
                }
                if (cursorRow < cellBuffer.rows()) {
                  final int ink = activeInverse == 1 ? activePaper : activeInk;
                  final int paper = activeInverse == 1 ? activeInk : activePaper;
                  int cellFg = getMappedColour(ink, paper);
                  int cellBg = getMappedColour(paper, ink);
                  final int cellStyle = getMappedStyle();
                  if (printingSystemPrompt) {
                    cellFg = CellAttributes.COLOUR_TYPE_INDEX | 4; // ANSI Blue
                    cellBg = CellAttributes.COLOUR_DEFAULT; // Default terminal background
                  }
                  cellBuffer.setCell(cursorRow, cursorCol, cp, cellFg, cellBg, cellStyle);
                  cursorCol++;
                }
              }
            });
    dirty = true; // Defer render to flush() or next println()
  }

  @Override
  public void flush() {
    // Use a longer threshold than the frame-boundary renders: game drawing loops call flush()
    // after every PRINT with a semicolon, and we must not trigger partial mid-frame renders.
    // 100ms is large enough for drawing phases to complete, yet fast enough that pure
    // PRINT-and-GOTO output loops display visibly (10 renders/sec).
    if ((dirty || resizePending.get())
        && System.currentTimeMillis() - lastRenderTimeMs >= FLUSH_RENDER_INTERVAL_MS) {
      render();
    }
  }

  @Override
  public void forceFlush() {
    if (dirty) {
      render();
    }
  }

  private int getMappedStyle() {
    int style = 0;
    if (activeFlash == 1) {
      style |= CellAttributes.STYLE_BLINK;
    }
    if (activeBright == 1) {
      style |= CellAttributes.STYLE_BOLD;
    }
    return style;
  }

  private int getMappedColour(int colourCode, int opposingCode) {
    if (colourCode == 8) {
      // Transparent: return -1 to signal CellBuffer to preserve existing cell attributes
      return -1;
    }
    if (colourCode >= 256 && colourCode <= 511) {
      return CellAttributes.index(colourCode - 256);
    }
    if (colourCode >= 16_777_216 && colourCode <= 33_554_431) {
      return CellAttributes.rgb(colourCode - 16_777_216);
    }
    int zxColour = colourCode;
    if (colourCode == 9) {
      // Contrast: if opposing colour is dark (0,1,2,3), pick White (7).
      // If light (4,5,6,7), pick Black (0).
      zxColour = (opposingCode >= 0 && opposingCode <= 3) ? 7 : 0;
    }
    if (zxColour >= 0 && zxColour <= 7) {
      int rgb = activeBright == 1 ? ZX_TO_RGB_BRIGHT[zxColour] : ZX_TO_RGB[zxColour];
      return CellAttributes.rgb(rgb);
    }
    // Default fallback (including colourCode == -1) maps to terminal's own default colours
    return CellAttributes.COLOUR_DEFAULT;
  }

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    cursorRow++;
    cursorCol = 0;
    // Scroll if we've gone past the buffer
    if (cursorRow >= cellBuffer.rows()) {
      scrollBuffer();
      cursorRow = cellBuffer.rows() - 1;
    }
    dirty = true;
    renderIfDue();
  }

  private void scrollBuffer() {
    cellBuffer.scrollUp();
  }

  @Override
  public void scroll() {
    cellBuffer.scrollUp();
    if (cursorRow > 0) {
      cursorRow--;
    }
    dirty = true;
    renderIfDue();
  }

  // === Graphics (PLOT/UNPLOT) ===

  @Override
  public void setPlotMode(PixelMode mode) {
    cellBuffer.setMode(mode);
    dirty = true;
  }

  @Override
  public void plot(int x, int y) {
    final int cellFg = getMappedColour(activeInk, activePaper);
    final int cellBg = getMappedColour(activePaper, activeInk);
    cellBuffer.plot(x, y, cellFg, cellBg, getMappedStyle(), activeInverse != 1, activeOver == 1);
    if (cellBuffer.isPixelInBounds(x, y)) {
      cursorRow = cellBuffer.pixelToCellRow(y);
      cursorCol = cellBuffer.pixelToCellCol(x) + 1;
    }
    dirty = true;
    renderIfDue();
  }

  @Override
  public int point(int x, int y) {
    return cellBuffer.point(x, y);
  }

  @Override
  public int getScreenCodepoint(int row, int col) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      return 32;
    }
    return cellBuffer.getCell(row, col);
  }

  @Override
  public int getScreenAttributes(int row, int col) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      return 56;
    }
    int fg = cellBuffer.getFgColour(row, col);
    int bg = cellBuffer.getBgColour(row, col);
    int style = cellBuffer.getStyle(row, col);

    int flash = (style & CellAttributes.STYLE_BLINK) != 0 ? 1 : 0;
    int bright = (style & CellAttributes.STYLE_BOLD) != 0 ? 1 : 0;
    int ink = resolveZxColour(fg, true);
    int paper = resolveZxColour(bg, false);

    return (flash * 128) + (bright * 64) + (paper * 8) + ink;
  }

  private int resolveZxColour(int cellColour, boolean isInk) {
    if (CellAttributes.isRgb(cellColour)) {
      int rgb = CellAttributes.valueOf(cellColour);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      int bitR = r > 155 ? 1 : 0;
      int bitG = g > 155 ? 1 : 0;
      int bitB = b > 155 ? 1 : 0;
      return (bitG << 2) | (bitR << 1) | bitB;
    }
    if (CellAttributes.isIndex(cellColour)) {
      int idx = CellAttributes.valueOf(cellColour);
      if (idx >= 0 && idx <= 15) {
        final int[] mapping = {0, 2, 4, 6, 1, 3, 5, 7};
        return mapping[idx & 7];
      }
      if (idx >= 16 && idx <= 231) {
        int code = idx - 16;
        int rVal = code / 36;
        int gVal = (code % 36) / 6;
        int bVal = code % 6;
        int bitR = rVal >= 3 ? 1 : 0;
        int bitG = gVal >= 3 ? 1 : 0;
        int bitB = bVal >= 3 ? 1 : 0;
        return (bitG << 2) | (bitR << 1) | bitB;
      }
      if (idx >= 232 && idx <= 255) {
        return idx < 244 ? 0 : 7;
      }
    }
    return isInk ? 7 : 0;
  }

  @Override
  public int getXAttributes(int row, int col, int select) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      if (select == 0 || select == 1) {
        return -1;
      }
      return 0;
    }
    switch (select) {
      case 0:
        int fg = cellBuffer.getFgColour(row, col);
        if (CellAttributes.isDefault(fg)) {
          return -1;
        }
        if (CellAttributes.isIndex(fg)) {
          return 256 + CellAttributes.valueOf(fg);
        }
        return 16_777_216 + CellAttributes.valueOf(fg);
      case 1:
        int bg = cellBuffer.getBgColour(row, col);
        if (CellAttributes.isDefault(bg)) {
          return -1;
        }
        if (CellAttributes.isIndex(bg)) {
          return 256 + CellAttributes.valueOf(bg);
        }
        return 16_777_216 + CellAttributes.valueOf(bg);
      case 2:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_BLINK) != 0 ? 1 : 0;
      case 3:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_BOLD) != 0 ? 1 : 0;
      case 4:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_INVERSE) != 0 ? 1 : 0;
      case 5:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_ITALIC) != 0 ? 1 : 0;
      case 6:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_UNDERLINE) != 0 ? 1 : 0;
      case 7:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_STRIKETHROUGH) != 0 ? 1 : 0;
      case 8:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_FAINT) != 0 ? 1 : 0;
      default:
        return 0;
    }
  }

  @Override
  public void lprint(String text) {
    System.err.print(text);
  }

  @Override
  public void lprintln(String text) {
    System.err.println(text);
  }

  @Override
  public void lprintln() {
    System.err.println();
  }

  @Override
  public String readln(Display.InputMode mode) {
    currentInputMode =
        switch (mode) {
          case INPUT_NUMERIC -> InputContext.INPUT_NUMERIC;
          case INPUT_STRING -> InputContext.INPUT_STRING;
        };
    if (statusText.isEmpty()) {
      statusText =
          switch (mode) {
            case INPUT_NUMERIC -> "Please enter a number or expression";
            case INPUT_STRING -> "Please enter a text value";
          };
    }
    return readln("");
  }

  @Override
  public String readReplLine() {
    currentInputMode = InputContext.REPL;
    if (statusText.isEmpty()) {
      statusText = new ReportException(ReportCode.OK, 0, 1, "Ready").format();
    }
    return readln("");
  }

  @Override
  public String readln(String prompt) {
    if (!inputVisible) {
      inputVisible = true;
      currentInputHeight = 1;
      final int newRows = Math.max(1, engine.getRows() - currentReservedRows());
      if (cursorRow >= newRows) {
        final int scrollAmount = cursorRow - newRows + 1;
        for (int i = 0; i < scrollAmount; i++) {
          scrollBuffer();
        }
        cursorRow = newRows - 1;
      }
      resizeBufferIfNeeded();
    }
    if (prompt != null && !prompt.isEmpty()) {
      statusText = prompt.trim();
    }

    final String promptStr =
        switch (currentInputMode) {
          case REPL -> "\033[34m❯ \033[m";
          case INPUT_NUMERIC -> "\033[1m# \033[m";
          case INPUT_STRING -> "\033[1m$ \033[m";
        };

    render(); // draws content + status row, clears input row, positions cursor at input row start
    engine.writer().print("\033[?25h"); // cursor visible
    engine.writer().flush();

    // Yield WINCH handling to LineReader during input so our render() doesn't conflict.
    engine.onResize(null);
    try {
      String fill = prefillText;
      prefillText = null;
      return engine.readLine(promptStr, fill);
    } finally {
      engine.onResize(() -> resizePending.set(true));
      engine.writer().print("\033[?25l"); // cursor invisible
      engine.writer().flush();
      inputVisible = false;
      statusText = "";
      currentInputHeight = 1;
      render();
    }
  }

  @Override
  public void prefillInput(String text) {
    prefillText = text;
  }

  @Override
  public boolean pollForBreak() {
    // The INT signal handler sets breakFlag for Ctrl+C; no I/O read needed here.
    return breakFlag.compareAndSet(true, false);
  }

  @Override
  public String inkey() {
    renderIfDue(true); // flush pending frame before polling for input, even in FAST mode
    int lastCh = -1;
    try {
      int ch = engine.readKey(1L); // 1ms timeout read
      if (ch < 0) {
        return "";
      }
      while (ch >= 0) {
        lastCh = ch;
        if (ch == 3) { // Ctrl+C
          breakFlag.set(true);
          return "";
        }
        ch = engine.readKey(1L);
      }
      if (lastCh >= 0) {
        return String.valueOf((char) lastCh);
      }
    } catch (IOException e) {
      // Ignore - no input available
    }
    return "";
  }

  @Override
  public String uinkey() {
    renderIfDue(true);
    String lastKey = "";
    try {
      while (true) {
        int ch = engine.readKey(1L);
        if (ch < 0) {
          return lastKey;
        }
        if (ch == 3) { // Ctrl+C
          breakFlag.set(true);
          return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append((char) ch);
        if (ch == 27) { // ESC sequence
          int nextCh = engine.readKey(1L);
          if (nextCh >= 0) {
            sb.append((char) nextCh);
            if (nextCh == '[' || nextCh == 'O') {
              while (true) {
                int seqCh = engine.readKey(1L);
                if (seqCh < 0) {
                  break;
                }
                sb.append((char) seqCh);
                if (seqCh >= 0x40 && seqCh <= 0x7E) {
                  break;
                }
              }
            }
          }
        }
        lastKey = sb.toString();
      }
    } catch (IOException e) {
      return lastKey;
    }
  }

  @Override
  public void waitForKey() {
    if (!inputVisible) {
      inputVisible = true;
      currentInputHeight = 1;
      int newRows = Math.max(1, engine.getRows() - currentReservedRows());
      if (cursorRow >= newRows) {
        final int scrollAmount = cursorRow - newRows + 1;
        for (int i = 0; i < scrollAmount; i++) {
          scrollBuffer();
        }
        cursorRow = newRows - 1;
      }
      resizeBufferIfNeeded();
    }
    forceFlush();
    // Write "Press any key" into the status row in grey (directly below the display area, at row
    // rows + 3 + currentInputHeight)
    final var out = engine.writer();
    out.printf("\033[%d;1H", cellBuffer.rows() + 3 + currentInputHeight);
    out.print("\033[37mPress any key to exit.\033[m");
    out.print("\033[K");
    out.flush();
    try {
      while (engine.readKey(100L) < 0) {
        if (breakFlag.get()) {
          break;
        }
      }
    } catch (IOException e) {
      // Ignore - proceed to close
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return; // Already closed
    }
    engine.close();
  }

  @Override
  public void setStatus(String status) {
    this.statusText = status != null ? status : "";
    if (inputVisible) {
      render();
    }
  }

  @Override
  public void systemPrintln(String text) {
    if (cursorCol > 0) {
      println();
    }
    if (text != null && (text.startsWith("❯") || text.startsWith("\033[34m❯"))) {
      printingSystemPrompt = true;
      try {
        println(text);
      } finally {
        printingSystemPrompt = false;
      }
    } else {
      println(text);
    }
  }
}
