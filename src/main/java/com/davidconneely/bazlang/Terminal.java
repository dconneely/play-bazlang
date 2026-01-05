package com.davidconneely.bazlang;

import java.io.IOException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public class Terminal implements AutoCloseable {
  private final org.jline.terminal.Terminal terminal;
  private final NonBlockingReader reader;
  private final StringBuilder typeAheadBuffer = new StringBuilder();
  private LineReader cachedLineReader;
  private int currentRow = 0;
  private int currentCol = 0;
  private boolean interruptRequested = false;

  public Terminal() {
    this(true);
  }

  protected Terminal(boolean useSystem) {
    if (useSystem) {
      try {
        this.terminal =
            TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .streams(System.in, System.out)
                .build();
        if (isTerminal()) {
          terminal.enterRawMode();
        }
        this.reader = terminal.reader();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
      } catch (IOException e) {
        throw new RuntimeException("Failed to initialize terminal", e);
      }
    } else {
      this.terminal = null;
      this.reader = null;
    }
  }

  public boolean isTerminal() {
    return terminal != null && !org.jline.terminal.Terminal.TYPE_DUMB.equals(terminal.getType());
  }

  public int currentRow() {
    return currentRow;
  }

  public int currentCol() {
    return currentCol;
  }

  public void cls() {
    if (isTerminal()) {
      terminal.writer().print("\033[2J\033[H");
      terminal.flush();
    }
    currentRow = 0;
    currentCol = 0;
  }

  public void moveCursor(int row, int col) {
    if (isTerminal()) {
      terminal.writer().print("\033[" + (row + 1) + ";" + (col + 1) + "H");
      terminal.flush();
    }
    currentRow = row;
    currentCol = col;
  }

  public void plot(int x, int y) {
    moveCursor(y, x);
    if (isTerminal()) {
      terminal.writer().print("█");
      terminal.flush();
    } else {
      System.out.print("█");
    }
    currentCol = x + 1;
  }

  public void unplot(int x, int y) {
    moveCursor(y, x);
    if (isTerminal()) {
      terminal.writer().print(" ");
      terminal.flush();
    } else {
      System.out.print(" ");
    }
    currentCol = x + 1;
  }

  public void scroll() {
    if (isTerminal()) {
      int height = terminal.getHeight();
      if (height > 0) {
        moveCursor(height - 1, 0);
        terminal.writer().println();
        terminal.flush();
        // After scroll, we are still at the bottom row
        currentRow = height - 1;
        currentCol = 0;
        return;
      }
    }
    // Fallback
    System.out.println();
    currentRow++;
    currentCol = 0;
  }

  public void print(String text) {
    if (isTerminal()) {
      terminal.writer().print(text);
      terminal.flush();
    } else {
      System.out.print(text);
    }
    currentCol += text.length();
  }

  public void printInverse(String text) {
    if (isTerminal()) {
      terminal.writer().print("\033[7m" + text + "\033[27m");
      terminal.flush();
    } else {
      System.out.print(text);
    }
    currentCol += text.length();
  }

  public void println(String text) {
    if (isTerminal()) {
      terminal.writer().println(text);
      terminal.flush();
    } else {
      System.out.println(text);
    }
    currentRow++;
    currentCol = 0;
  }

  public void println() {
    if (isTerminal()) {
      terminal.writer().println();
      terminal.flush();
    } else {
      System.out.println();
    }
    currentRow++;
    currentCol = 0;
  }

  public void lprint(String text) {
    System.err.print(text);
  }

  public void lprintln(String text) {
    System.err.println(text);
  }

  public void lprintln() {
    System.err.println();
  }

  public String readln(String prompt) {
    int newlineIdx = typeAheadBuffer.indexOf("\r");
    if (newlineIdx == -1) newlineIdx = typeAheadBuffer.indexOf("\n");

    if (newlineIdx != -1) {
      String line = typeAheadBuffer.substring(0, newlineIdx);
      typeAheadBuffer.delete(0, newlineIdx + 1);

      if (prompt != null) print(prompt);
      println(line);

      currentRow++;
      currentCol = 0;
      return line;
    }

    String initial = typeAheadBuffer.toString();
    typeAheadBuffer.setLength(0);

    try {
      if (cachedLineReader == null) {
        cachedLineReader = LineReaderBuilder.builder().terminal(terminal).build();
      }
      String line =
          cachedLineReader.readLine(
              prompt != null ? prompt : "", null, (MaskingCallback) null, initial);
      currentRow++;
      currentCol = 0;
      return line;
    } catch (org.jline.reader.UserInterruptException | org.jline.reader.EndOfFileException e) {
      return null;
    }
  }

  public boolean checkInterrupt() {
    if (interruptRequested) {
      interruptRequested = false;
      return true;
    }
    if (!isTerminal()) {
      return false;
    }
    try {
      int c;
      while ((c = reader.read(1L)) != NonBlockingReader.READ_EXPIRED) {
        if (c == 27) { // ESC
          return true;
        } else if (c >= 0) {
          typeAheadBuffer.append((char) c);
        } else {
          break; // EOF
        }
      }
    } catch (IOException e) {
      /* Ignore */
    }
    return false;
  }

  public String inkey() {
    if (isTerminal()) {
      if (checkInterrupt()) {
        interruptRequested = true;
      }
      if (typeAheadBuffer.length() > 0) {
        char c = typeAheadBuffer.charAt(0);
        typeAheadBuffer.deleteCharAt(0);
        return String.valueOf(c);
      }
    } else {
      try {
        if (System.in.available() > 0) {
          int c = System.in.read();
          if (c >= 0) {
            return String.valueOf((char) c);
          }
        }
      } catch (IOException e) {
        /* Ignore */
      }
    }
    return "";
  }

  @Override
  public void close() {
    try {
      terminal.close();
    } catch (IOException e) {
      /* Ignore */
    }
  }
}
