package com.davidconneely.bazlang.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
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
 */
public class TerminalDisplay implements Display {
  // Quadrant block characters for 2x2 pixel graphics
  private static final char[] QUADRANTS = {
    ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'
  };

  private final Terminal terminal;
  private final NonBlockingReader reader;
  private final Attributes savedAttributes;
  private final org.jline.utils.Display screenDisplay;

  // Display buffer (character grid for display area)
  private char[][] displayBuffer;
  private int bufferRows;
  private int bufferCols;

  // Input state
  private boolean inputVisible = false;
  private InputMode currentInputMode = InputMode.REPL;
  private final List<String> inputLines = new ArrayList<>();
  private String statusText = "";
  private int inputCursorPos = 0; // Cursor position within input line
  private String prefillText = null; // Type-ahead buffer

  // Command history (REPL mode only)
  private final List<String> history = new ArrayList<>();
  private int historyIndex = -1; // -1 = current input, 0+ = history entries
  private String savedCurrentInput = ""; // Save current input when browsing history

  // Cursor tracking (logical position in display area)
  private int cursorRow = 0;
  private int cursorCol = 0;

  // Break flag for Ctrl+C (set by signal handler or character detection)
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);

  // Track whether close() has been called (for idempotent cleanup)
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // Type-ahead buffer for keys read by checkForBreak() that weren't Ctrl+C
  private final StringBuilder typeAheadBuffer = new StringBuilder();

  public TerminalDisplay() throws IOException {
    this.terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build();
    this.savedAttributes = terminal.enterRawMode();
    terminal.puts(Capability.enter_ca_mode);
    terminal.puts(Capability.clear_screen);
    terminal.puts(Capability.cursor_invisible);
    terminal.flush();
    this.reader = terminal.reader();
    this.screenDisplay = new org.jline.utils.Display(terminal, true);

    terminal.handle(Terminal.Signal.INT, _ -> breakFlag.set(true));

    // Ensure terminal is restored on JVM shutdown (e.g., if signal terminates the process)
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));

    initBuffer();
    render();
  }

  private void initBuffer() {
    bufferRows = Math.max(1, terminal.getHeight() - 2);
    bufferCols = terminal.getWidth();
    displayBuffer = new char[bufferRows][bufferCols];
    clearBuffer();
  }

  private void clearBuffer() {
    for (char[] row : displayBuffer) {
      Arrays.fill(row, ' ');
    }
    cursorRow = 0;
    cursorCol = 0;
  }

  private void resizeBufferIfNeeded() {
    int newCols = terminal.getWidth();
    int newRows = Math.max(1, terminal.getHeight() - 4);

    if (newRows != bufferRows || newCols != bufferCols) {
      char[][] newBuffer = new char[newRows][newCols];
      for (char[] row : newBuffer) {
        Arrays.fill(row, ' ');
      }
      // Copy existing content, anchoring at top-left
      int copyRows = Math.min(bufferRows, newRows);
      int copyCols = Math.min(bufferCols, newCols);
      for (int r = 0; r < copyRows; r++) {
        System.arraycopy(displayBuffer[r], 0, newBuffer[r], 0, copyCols);
      }
      displayBuffer = newBuffer;
      bufferRows = newRows;
      bufferCols = newCols;
      // Clamp cursor to new bounds
      cursorRow = Math.min(cursorRow, bufferRows - 1);
      cursorCol = Math.min(cursorCol, bufferCols - 1);
    }
  }

  // === Layout and Rendering ===

  private void render() {
    resizeBufferIfNeeded();
    int termWidth = terminal.getWidth();
    int termHeight = terminal.getHeight();
    List<AttributedString> lines = new ArrayList<>(termHeight);

    int rowsToRender = Math.min(bufferRows, termHeight);
    for (int r = 0; r < rowsToRender; r++) {
      AttributedStringBuilder sb = new AttributedStringBuilder(termWidth);
      int colsToRender = Math.min(bufferCols, termWidth);
      for (int c = 0; c < colsToRender; c++) {
        sb.append(displayBuffer[r][c]);
      }
      lines.add(sb.toAttributedString());
    }

    int targetCursorPos = 0;
    if (inputVisible) {
      int inputHeight = Math.max(1, inputLines.size());

      // Status line
      lines.add(new AttributedString(statusText));

      // Input lines
      for (int row = 0; row < inputHeight; row++) {
        AttributedStringBuilder inputSb = new AttributedStringBuilder(termWidth);
        if (row == 0) {
          String prompt =
              switch (currentInputMode) {
                case REPL -> "❯ ";
                case INPUT_NUMERIC -> "# ";
                case INPUT_STRING -> "$ ";
              };
          if (currentInputMode != InputMode.REPL) {
            inputSb.style(AttributedStyle.BOLD);
            inputSb.append(prompt);
            inputSb.style(AttributedStyle.DEFAULT);
          } else {
            inputSb.append(prompt);
          }
        } else {
          inputSb.append("  ");
        }
        inputSb.append(inputLines.get(row));
        lines.add(inputSb.toAttributedString());
      }

      // Cursor: after display rows + status row, at the prompt offset + input cursor position
      // targetCursorPos uses Display's internal encoding: row * (termWidth + 1) + col
      targetCursorPos = (rowsToRender + 1) * (termWidth + 1) + (2 + inputCursorPos);
    }

    // Pad to full terminal height so screenDisplay clears any leftover content
    while (lines.size() < termHeight) {
      lines.add(AttributedString.EMPTY);
    }

    screenDisplay.resize(termHeight, termWidth);
    screenDisplay.update(lines, targetCursorPos);
    terminal.writer().flush();
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
    cursorRow = Math.max(0, Math.min(row, bufferRows - 1));
    cursorCol = Math.max(0, Math.min(col, bufferCols - 1));
  }

  @Override
  public void print(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }

    for (char c : text.toCharArray()) {
      if (c >= 32 && c != 127) {
        if (cursorCol >= bufferCols) {
          cursorRow++;
          cursorCol = 0;
          if (cursorRow >= bufferRows) {
            scrollBuffer();
            cursorRow = bufferRows - 1;
          }
        }
        if (cursorRow < bufferRows) {
          displayBuffer[cursorRow][cursorCol] = c;
          cursorCol++;
        }
      }
    }
    render();
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
    if (cursorRow >= bufferRows) {
      scrollBuffer();
      cursorRow = bufferRows - 1;
    }
    render();
  }

  private void scrollBuffer() {
    // Shift all rows up by one
    for (int r = 0; r < bufferRows - 1; r++) {
      System.arraycopy(displayBuffer[r + 1], 0, displayBuffer[r], 0, bufferCols);
    }
    // Clear the last row
    Arrays.fill(displayBuffer[bufferRows - 1], ' ');
  }

  @Override
  public void scroll() {
    scrollBuffer();
    if (cursorRow > 0) {
      cursorRow--;
    }
    render();
  }

  // === Graphics (PLOT/UNPLOT) ===

  private int getQuadState(char c) {
    for (int i = 0; i < QUADRANTS.length; i++) {
      if (QUADRANTS[i] == c) {
        return i;
      }
    }
    return 0;
  }

  @Override
  public void plot(int x, int y) {
    updatePixel(x, y, true);
  }

  @Override
  public void unplot(int x, int y) {
    updatePixel(x, y, false);
  }

  private void updatePixel(int x, int y, boolean set) {
    // Pixel coordinates: (0,0) is bottom-left of display area
    // Each character cell is 2x2 pixels
    int pixelWidth = bufferCols * 2;
    int pixelHeight = bufferRows * 2;

    int absX = Math.abs(x);
    int absY = Math.abs(y);
    if (absX >= pixelWidth || absY >= pixelHeight) {
      return; // Out of range - silently ignore
    }

    // Convert pixel to character cell
    int col = absX / 2;
    int row = (bufferRows - 1) - (absY / 2); // Y=0 is bottom

    // Determine which quadrant within the cell
    int subX = absX % 2;
    int subY = absY % 2;

    // Quadrant bits: UL=1, UR=2, LL=4, LR=8
    int mask;
    if (subX == 0 && subY == 1) {
      mask = 1; // Upper-left
    } else if (subX == 1 && subY == 1) {
      mask = 2; // Upper-right
    } else if (subX == 0) {
      mask = 4; // Lower-left
    } else {
      mask = 8; // Lower-right
    }

    char current = displayBuffer[row][col];
    int state = getQuadState(current);
    int newState = set ? (state | mask) : (state & ~mask);
    displayBuffer[row][col] = QUADRANTS[newState];

    // Update cursor position to the cell after the plotted one
    cursorRow = row;
    cursorCol = col + 1;

    render();
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
    currentInputMode = mode;
    // Only set default status if no status was explicitly set (e.g., report code)
    if (statusText.isEmpty()) {
      statusText =
          switch (mode) {
            case REPL -> "READY";
            case INPUT_NUMERIC -> "Please enter a number or expression";
            case INPUT_STRING -> "Please enter a text value";
          };
    }
    return readln("");
  }

  @Override
  public String readln(String prompt) {
    inputVisible = true;
    inputLines.clear();
    inputLines.add("");
    inputCursorPos = 0;
    historyIndex = -1;
    savedCurrentInput = "";

    // Handle prefill from previous syntax error
    if (prefillText != null) {
      inputLines.set(0, prefillText);
      inputCursorPos = prefillText.length();
      prefillText = null;
    }

    // If called with a non-empty prompt (e.g., "Syntax error?"), keep current mode but update
    // status
    if (prompt != null && !prompt.isEmpty()) {
      statusText = prompt.trim();
    }
    render(); // positions cursor via targetCursorPos
    terminal.puts(Capability.cursor_normal);
    terminal.writer().flush();

    try {
      StringBuilder line = new StringBuilder(inputLines.getFirst());
      while (true) {
        int ch = reader.read(); // blocking read
        if (ch < 0 || ch == '\n' || ch == '\r') {
          break;
        } else if (ch == 27) { // ESC - start of escape sequence
          handleEscapeSequence(line);
        } else if (ch == 127 || ch == 8) { // Backspace
          if (inputCursorPos > 0) {
            line.deleteCharAt(inputCursorPos - 1);
            inputCursorPos--;
            updateInputDisplay(line);
          }
        } else if (ch == 1) { // Ctrl+A - Home
          inputCursorPos = 0;
          updateInputDisplay(line);
        } else if (ch == 5) { // Ctrl+E - End
          inputCursorPos = line.length();
          updateInputDisplay(line);
        } else if (ch >= 32) {
          line.insert(inputCursorPos, (char) ch);
          inputCursorPos++;
          updateInputDisplay(line);
        }
      }

      String result = line.toString();
      // Add to history if non-empty and in REPL mode (avoiding duplicates)
      if (!result.isBlank()
          && currentInputMode == InputMode.REPL
          && (history.isEmpty() || !history.getLast().equals(result))) {
        history.add(result);
      }
      return result;
    } catch (IOException e) {
      return null;
    } finally {
      terminal.puts(Capability.cursor_invisible);
      terminal.writer().flush();
      inputVisible = false;
      statusText = ""; // Clear status so next readln gets fresh default
      render();
    }
  }

  private void handleEscapeSequence(StringBuilder line) throws IOException {
    int ch2 = reader.read(100L); // Short timeout for escape sequence
    if (ch2 != '[') {
      return; // Not a CSI sequence
    }
    int ch3 = reader.read(100L);
    switch (ch3) {
      case 'A' -> navigateHistory(line, 1); // Up arrow - go back in history
      case 'B' -> navigateHistory(line, -1); // Down arrow - go forward in history
      case 'C' -> { // Right arrow
        if (inputCursorPos < line.length()) {
          inputCursorPos++;
          updateInputDisplay(line);
        }
      }
      case 'D' -> { // Left arrow
        if (inputCursorPos > 0) {
          inputCursorPos--;
          updateInputDisplay(line);
        }
      }
      case 'H' -> { // Home
        inputCursorPos = 0;
        updateInputDisplay(line);
      }
      case 'F' -> { // End
        inputCursorPos = line.length();
        updateInputDisplay(line);
      }
      case '3' -> { // Delete key (ESC [ 3 ~)
        int ch4 = reader.read(100L);
        if (ch4 == '~' && inputCursorPos < line.length()) {
          line.deleteCharAt(inputCursorPos);
          updateInputDisplay(line);
        }
      }
      default -> {
        // Unknown sequence, ignore
      }
    }
  }

  private void navigateHistory(StringBuilder line, int direction) {
    if (currentInputMode != InputMode.REPL || history.isEmpty()) {
      return;
    }

    if (historyIndex == -1) {
      // Save current input before browsing history
      savedCurrentInput = line.toString();
    }

    int newIndex = historyIndex + direction;
    if (newIndex < -1) {
      newIndex = -1;
    } else if (newIndex >= history.size()) {
      newIndex = history.size() - 1;
    }

    if (newIndex != historyIndex) {
      historyIndex = newIndex;
      String newContent =
          historyIndex == -1 ? savedCurrentInput : history.get(history.size() - 1 - historyIndex);
      line.setLength(0);
      line.append(newContent);
      inputCursorPos = newContent.length();
      updateInputDisplay(line);
    }
  }

  private void updateInputDisplay(StringBuilder line) {
    inputLines.set(0, line.toString());
    render();
  }

  @Override
  public void prefillInput(String text) {
    prefillText = text;
  }

  @Override
  public boolean pollForBreak() {
    // Only check flag - I/O based detection happens in inkey() and readln()
    return breakFlag.compareAndSet(true, false);
  }

  @Override
  public void checkForBreak() {
    try {
      int ch = reader.read(1L); // 1ms timeout
      if (ch == 3) {
        breakFlag.set(true);
      } else if (ch >= 0) {
        typeAheadBuffer.append((char) ch);
      }
    } catch (IOException e) {
      // Ignore
    }
  }

  @Override
  public String inkey() {
    // First check type-ahead buffer
    if (!typeAheadBuffer.isEmpty()) {
      char c = typeAheadBuffer.charAt(0);
      typeAheadBuffer.deleteCharAt(0);
      return String.valueOf(c);
    }
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
}
