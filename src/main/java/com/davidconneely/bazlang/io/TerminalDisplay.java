package com.davidconneely.bazlang.io;

import com.davidconneely.repl.BreakException;
import com.davidconneely.repl.Display;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.NonBlockingReader;

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
public class TerminalDisplay implements BazLangDisplay {
  /** Rows reserved for the status bar and input line. */
  private static final int RESERVED_ROWS = 2;

  private final Terminal terminal;
  private final NonBlockingReader reader;
  private final LineReader lineReader;
  private final Attributes savedAttributes;

  // Display buffer (UTF-32 codepoint grid for display area)
  private PixelBuffer pixelBuffer;

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

  // Rate-limiting: avoid re-rendering more frequently than ~60fps (~16ms)
  private long lastRenderTimeMs = 0L;

  // Cursor tracking (logical position in display area)
  private int cursorRow = 0;
  private int cursorCol = 0;

  // Break flag for Ctrl+C (set by INT signal handler on JLine's signal thread, read by main thread)
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);

  // Resize flag: set by WINCH signal handler on JLine's signal thread; handled by main thread in
  // render()
  private final AtomicBoolean resizePending = new AtomicBoolean(false);

  // Track whether close() has been called (for idempotent cleanup between main thread and shutdown
  // hook)
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public TerminalDisplay() throws IOException {
    this.terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build();
    this.savedAttributes = terminal.enterRawMode();
    terminal.puts(Capability.enter_ca_mode);
    terminal.puts(Capability.clear_screen);
    terminal.puts(Capability.cursor_invisible);
    terminal.flush();
    this.reader = terminal.reader();
    this.lineReader = new RobustLineReaderImpl(terminal, "BazLang");

    terminal.handle(Terminal.Signal.INT, _ -> breakFlag.set(true));
    terminal.handle(Terminal.Signal.WINCH, _ -> resizePending.set(true));

    // Ensure terminal is restored on JVM shutdown (e.g., if signal terminates the process)
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));

    initBuffer();
    render();
  }

  private void initBuffer() {
    int rows = Math.max(1, terminal.getRows() - RESERVED_ROWS);
    int cols = terminal.getColumns();
    pixelBuffer = new PixelBuffer(rows, cols, QuadrantMode.INSTANCE);
  }

  private void clearBuffer() {
    pixelBuffer.clear();
    cursorRow = 0;
    cursorCol = 0;
  }

  private void resizeBufferIfNeeded() {
    int newCols = terminal.getColumns();
    int newRows = Math.max(1, terminal.getRows() - RESERVED_ROWS);
    if (newRows != pixelBuffer.rows() || newCols != pixelBuffer.cols()) {
      pixelBuffer.resize(newRows, newCols);
      cursorRow = Math.min(cursorRow, pixelBuffer.rows() - 1);
      cursorCol = Math.min(cursorCol, pixelBuffer.cols() - 1);
    }
  }

  // === Layout and Rendering ===

  private void render() {
    lastRenderTimeMs = System.currentTimeMillis();
    dirty = false;
    resizePending.set(false);
    resizeBufferIfNeeded();
    int termWidth = terminal.getColumns();
    int termHeight = terminal.getRows();
    int rowsToRender = Math.min(pixelBuffer.rows(), termHeight);
    int colsToRender = Math.min(pixelBuffer.cols(), termWidth);

    // Write content rows as raw UTF-8, bypassing JLine's ACS substituteChar so that Unicode
    // box-drawing characters, block elements, sextants, and braille render correctly.
    PrintWriter out = terminal.writer();
    for (int r = 0; r < rowsToRender; r++) {
      out.printf("\033[%d;1H", r + 1);
      out.print(new String(pixelBuffer.rowCells(r), 0, colsToRender));
      out.print("\033[K");
    }

    // Status and input rows (always written to clear stale content)
    out.printf("\033[%d;1H", rowsToRender + 1);
    if (inputVisible) {
      out.print(statusText);
    }
    out.print("\033[K");

    out.printf("\033[%d;1H", rowsToRender + 2);
    out.print("\033[K");
    // When inputVisible, the cursor is now at the start of the input row — LineReader draws here.

    out.flush();
  }

  /**
   * Renders only if dirty and at least ~16ms have elapsed since the last render (~60fps cap). Used
   * by flush(), println(), plot(), unplot(), and scroll() to avoid flooding the terminal with
   * output when BASIC code calls these in tight loops (e.g. game main loops). inkey() also calls
   * this first so that the completed frame is visible before polling input.
   */
  private void renderIfDue() {
    if ((dirty || resizePending.get()) && System.currentTimeMillis() - lastRenderTimeMs >= 16L) {
      render();
    }
  }

  // === Display Interface Implementation ===

  @Override
  public int currentRow() {
    return cursorRow;
  }

  @Override
  public int currentCol() {
    return cursorCol;
  }

  @Override
  public void cls() {
    clearBuffer();
    render();
  }

  @Override
  public void locate(int row, int col) {
    cursorRow = Math.max(0, Math.min(row, pixelBuffer.rows() - 1));
    cursorCol = Math.max(0, Math.min(col, pixelBuffer.cols() - 1));
  }

  @Override
  public void print(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }

    text.codePoints()
        .forEach(
            cp -> {
              if (cp >= 32 && cp != 127) {
                if (cursorCol >= pixelBuffer.cols()) {
                  cursorRow++;
                  cursorCol = 0;
                  if (cursorRow >= pixelBuffer.rows()) {
                    scrollBuffer();
                    cursorRow = pixelBuffer.rows() - 1;
                  }
                }
                if (cursorRow < pixelBuffer.rows()) {
                  pixelBuffer.setCell(cursorRow, cursorCol, cp);
                  cursorCol++;
                }
              }
            });
    dirty = true; // Defer render to flush() or next println()
  }

  @Override
  public void flush() {
    // Intentionally not calling renderIfDue() here. In game loops, all PRINT statements use
    // trailing semicolons (no newline), so flush() is called after every PRINT. Rendering in
    // flush() causes a partial intermediate render mid-frame (after the rate-limit threshold
    // elapses), followed immediately by the complete render in forceFlush()/inkey(), producing
    // back-to-back PTY writes that can exceed the PTY buffer and stall the game loop.
    // Rendering is driven by println() for text output, inkey() for INKEY$-based games, and
    // forceFlush() for PAUSE-based games.
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
    // Scroll if we've gone past the buffer
    if (cursorRow >= pixelBuffer.rows()) {
      scrollBuffer();
      cursorRow = pixelBuffer.rows() - 1;
    }
    dirty = true;
    renderIfDue();
  }

  private void scrollBuffer() {
    pixelBuffer.scrollUp();
  }

  @Override
  public void scroll() {
    pixelBuffer.scrollUp();
    if (cursorRow > 0) {
      cursorRow--;
    }
    dirty = true;
    renderIfDue();
  }

  // === Graphics (PLOT/UNPLOT) ===

  @Override
  public void setPlotMode(PixelMode mode) {
    pixelBuffer.setMode(mode);
  }

  @Override
  public void plot(int x, int y) {
    pixelBuffer.plot(x, y);
    if (pixelBuffer.isPixelInBounds(x, y)) {
      cursorRow = pixelBuffer.pixelToCellRow(y);
      cursorCol = pixelBuffer.pixelToCellCol(x) + 1;
    }
    dirty = true;
    renderIfDue();
  }

  @Override
  public void unplot(int x, int y) {
    pixelBuffer.unplot(x, y);
    if (pixelBuffer.isPixelInBounds(x, y)) {
      cursorRow = pixelBuffer.pixelToCellRow(y);
      cursorCol = pixelBuffer.pixelToCellCol(x) + 1;
    }
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
      statusText = "READY";
    }
    return readln("");
  }

  @Override
  public String readln(String prompt) {
    inputVisible = true;
    if (prompt != null && !prompt.isEmpty()) {
      statusText = prompt.trim();
    }

    String promptStr =
        switch (currentInputMode) {
          case REPL -> "❯ ";
          case INPUT_NUMERIC -> "\033[1m# \033[m";
          case INPUT_STRING -> "\033[1m$ \033[m";
        };

    render(); // draws content + status row, clears input row, positions cursor at input row start
    terminal.puts(Capability.cursor_normal);
    terminal.writer().flush();

    // Yield WINCH handling to LineReader during input so our render() doesn't conflict.
    terminal.handle(Terminal.Signal.WINCH, Terminal.SignalHandler.SIG_DFL);
    try {
      String fill = prefillText;
      prefillText = null;
      return lineReader.readLine(promptStr, null, fill);
    } catch (UserInterruptException e) {
      throw new BreakException();
    } catch (EndOfFileException e) {
      return null;
    } finally {
      terminal.handle(Terminal.Signal.WINCH, _ -> resizePending.set(true));
      terminal.puts(Capability.cursor_invisible);
      terminal.writer().flush();
      inputVisible = false;
      statusText = "";
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
    renderIfDue(); // flush pending frame before polling for input (acts as frame boundary)
    try {
      int ch = reader.read(1L); // 1ms timeout read
      if (ch == 3) { // Ctrl+C
        breakFlag.set(true);
        return "";
      }
      if (ch >= 0) {
        return String.valueOf((char) ch);
      }
    } catch (IOException e) {
      // Ignore - no input available
    }
    return "";
  }

  @Override
  public void waitForKey() {
    forceFlush();
    // Write "Press any key" into the status row (directly below the display area)
    PrintWriter out = terminal.writer();
    out.printf("\033[%d;1H", pixelBuffer.rows() + 1);
    out.print("Press any key to exit.");
    out.print("\033[K");
    out.flush();
    try {
      while (reader.read(100L) < 0) {
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
    try {
      terminal.puts(Capability.cursor_normal);
      terminal.puts(Capability.exit_ca_mode);
      terminal.flush();
      terminal.setAttributes(savedAttributes);
      terminal.close();
    } catch (IOException e) {
      // Ignore - best effort cleanup
    }
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
    println(text);
  }
}
