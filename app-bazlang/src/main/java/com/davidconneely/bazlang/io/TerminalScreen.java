package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.cell.CellAttributes;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.CellBufferRenderer;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.repl.TerminalEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/** Terminal-based Screen implementation with dynamic screen regions. */
public class TerminalScreen extends AbstractCellBufferedScreen {
  private boolean printingSystemPrompt = false;
  private int currentInputHeight = 1;

  private int currentReservedRows() {
    return inputVisible ? (3 + currentInputHeight) : 0;
  }

  private final TerminalEngine engine;
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

  private static final long FRAME_RENDER_INTERVAL_MS = 20L;
  private static final long FLUSH_RENDER_INTERVAL_MS = 100L;
  private long lastRenderTimeMs = 0L;
  private boolean fastMode = false;

  // Resize flag: set by WINCH signal handler
  private final AtomicBoolean resizePending = new AtomicBoolean(false);

  // Break flag for Ctrl+C
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);

  // Track whether close() has been called
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public TerminalScreen(TerminalEngine engine) {
    super(createInitialBuffer(engine));
    this.engine = engine;

    this.engine.setInputHeightListener(this::adjustLayoutForInputHeight);
    this.engine.onInterrupt(() -> breakFlag.set(true));
    this.engine.onResize(() -> resizePending.set(true));

    // Ensure terminal is restored on JVM shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));

    render();
  }

  private static CellBuffer createInitialBuffer(TerminalEngine engine) {
    final int rawCols = engine.getColumns();
    final int cols = rawCols > 0 ? rawCols : 80;
    final int rawRows = engine.getRows();
    final int rows = rawRows > 0 ? rawRows : 25;
    return new CellBuffer(rows, cols, QuadrantMode.INSTANCE);
  }

  public void adjustLayoutForInputHeight(int newInputHeight) {
    if (this.currentInputHeight != newInputHeight) {
      this.currentInputHeight = newInputHeight;
      resizeBufferIfNeeded();
      render();
      engine.forceRedrawFromCursor();
    }
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
    final int newRows = Math.max(1, (rawRows > 0 ? rawRows : 25) - currentReservedRows());
    if (newRows != cellBuffer.rows() || newCols != cellBuffer.cols()) {
      cellBuffer.resize(newRows, newCols);
      cursorRow = Math.min(cursorRow, cellBuffer.rows() - 1);
      cursorCol = Math.min(cursorCol, cellBuffer.cols() - 1);
    }
  }

  private void render() {
    lastRenderTimeMs = System.currentTimeMillis();
    dirty = false;
    resizePending.set(false);
    resizeBufferIfNeeded();
    final int termWidth = engine.getColumns();
    final int termHeight = engine.getRows();
    final int rowsToRender = Math.min(cellBuffer.rows(), termHeight);
    final int colsToRender = Math.min(cellBuffer.cols(), termWidth);

    final var out = engine.writer();
    out.print("\033[?25l");
    renderer.renderContentRows(out, cellBuffer, rowsToRender, colsToRender);
    renderInputRows(out, rowsToRender, termWidth);

    out.flush();
  }

  private void renderInputRows(PrintWriter out, int rowsToRender, int termWidth) {
    if (inputVisible) {
      out.printf("\033[%d;1H", rowsToRender + 1);
      out.print("\033[90m" + "─".repeat(Math.max(0, termWidth)) + "\033[m");
      out.print("\033[K");

      for (int i = 0; i < currentInputHeight; i++) {
        out.printf("\033[%d;1H", rowsToRender + 2 + i);
        out.print("\033[K");
      }

      out.printf("\033[%d;1H", rowsToRender + 2 + currentInputHeight);
      out.print("\033[90m" + "─".repeat(Math.max(0, termWidth)) + "\033[m");
      out.print("\033[K");

      final String lineText = getStatusLine(termWidth);
      out.printf("\033[%d;1H", rowsToRender + 3 + currentInputHeight);
      out.print("\033[37m" + lineText + "\033[m");
      out.print("\033[K");

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

  private void renderIfDue() {
    renderIfDue(false);
  }

  @Override
  public void setFastMode(boolean fast) {
    this.fastMode = fast;
    if (!fast && dirty) {
      render();
    }
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
                  final int currentStyle = cellBuffer.getStyle(cursorRow, cursorCol);
                  final int cellStyle = getMappedStyle(currentStyle);
                  if (printingSystemPrompt) {
                    cellFg = CellAttributes.COLOUR_TYPE_INDEX | 4; // ANSI Blue
                    cellBg = CellAttributes.COLOUR_DEFAULT; // Default terminal background
                  }
                  cellBuffer.setCell(cursorRow, cursorCol, cp, cellFg, cellBg, cellStyle);
                  cursorCol++;
                }
              }
            });
    dirty = true;
  }

  @Override
  public void flush() {
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

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    cursorRow++;
    cursorCol = 0;
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

  @Override
  protected void afterPlot() {
    dirty = true;
    renderIfDue();
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
  public String readln(InputMode mode) {
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
  public String readReplInput() {
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

    render();
    engine.writer().print("\033[?25h");
    engine.writer().flush();

    engine.onResize(null);
    try {
      String fill = prefillText;
      prefillText = null;
      return engine.readLine(promptStr, fill);
    } finally {
      engine.onResize(() -> resizePending.set(true));
      engine.writer().print("\033[?25l");
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
    return breakFlag.compareAndSet(true, false);
  }

  @Override
  public String inkey() {
    renderIfDue(true);
    int lastCh = -1;
    try {
      int ch = engine.readKey(1L);
      if (ch < 0) {
        return "";
      }
      while (ch >= 0) {
        lastCh = ch;
        if (ch == 3) {
          breakFlag.set(true);
          return "";
        }
        ch = engine.readKey(1L);
      }
      return String.valueOf((char) lastCh);
    } catch (IOException e) {
      // Ignore
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
        if (ch == 3) {
          breakFlag.set(true);
          return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append((char) ch);
        if (ch == 27) {
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
      // Ignore
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    engine.close();
  }

  @Override
  public void setStatus(String status) {
    this.statusText = status;
  }

  @Override
  public void systemPrintln(String text) {
    printingSystemPrompt = true;
    try {
      println(text);
    } finally {
      printingSystemPrompt = false;
    }
  }
}
