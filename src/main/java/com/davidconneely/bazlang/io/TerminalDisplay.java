package com.davidconneely.bazlang.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public class TerminalDisplay extends BufferedDisplay {
  private final Terminal terminal;
  private final NonBlockingReader reader;
  private final StringBuilder typeAheadBuffer = new StringBuilder();
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);
  private LineReader cachedLineReader;

  public TerminalDisplay() {
    super();
    try {
      this.terminal =
          TerminalBuilder.builder()
              .system(true)
              .streams(System.in, System.out)
              .nativeSignals(true)
              .signalHandler(
                  sig -> {
                    if (sig == Terminal.Signal.INT) {
                      breakFlag.set(true);
                    }
                  })
              .build();

      if (isValidTerminal()) {
        enableRawMode();
      }
      this.reader = terminal.reader();
      Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialise terminal", e);
    }
  }

  private boolean isValidTerminal() {
    return terminal != null && !Terminal.TYPE_DUMB.equals(terminal.getType());
  }

  private void enableRawMode() {
    terminal.enterRawMode();
    Attributes attr = terminal.getAttributes();
    attr.setLocalFlag(LocalFlag.ISIG, true);
    terminal.setAttributes(attr);
    hideCursor();
  }

  private void hideCursor() {
    terminal.writer().print("\033[?25l");
    terminal.flush();
  }

  private void showCursor() {
    terminal.writer().print("\033[?25h");
    terminal.flush();
  }

  @Override
  protected void rawPrint(String text) {
    terminal.writer().print(text);
    terminal.flush();
  }

  @Override
  protected void rawLocate(int row, int col) {
    terminal.writer().print("\033[" + (row + 1) + ";" + (col + 1) + "H");
  }

  @Override
  protected void rawScroll() {
    int height = terminal.getHeight();
    if (height > 0) {
      // We rely on BufferedDisplay to have set currentRow correctly (ROWS-1)
      // We just need to ensure the cursor is at the bottom before printing newline
      rawLocate(height - 1, 0);
      terminal.writer().print('\n');
      terminal.flush();
    } else {
      rawPrint("\n");
    }
  }

  @Override
  protected void rawCls() {
    terminal.writer().print("\033[2J\033[H");
    terminal.flush();
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
  public String readln(String prompt) {
    int newlineIdx = typeAheadBuffer.indexOf("\r");
    if (newlineIdx == -1) newlineIdx = typeAheadBuffer.indexOf("\n");

    if (newlineIdx != -1) {
      String line = typeAheadBuffer.substring(0, newlineIdx);
      typeAheadBuffer.delete(0, newlineIdx + 1);

      if (prompt != null) print(prompt);
      println(line);

      return line;
    }

    String initial = typeAheadBuffer.toString();
    typeAheadBuffer.setLength(0);

    try {
      if (cachedLineReader == null) {
        cachedLineReader = LineReaderBuilder.builder().terminal(terminal).build();
      }
      showCursor();
      String line =
          cachedLineReader.readLine(
              prompt != null ? prompt : "", null, (MaskingCallback) null, initial);

      // Update buffer manually to match LineReader's output without double-printing
      updateBuffer(line);
      currentRow++;
      currentCol = 0;
      if (currentRow >= ROWS) {
        scrollBuffer();
        currentRow = ROWS - 1;
      }

      enableRawMode();
      return line;
    } catch (UserInterruptException e) {
      print("^C");
      println();
      enableRawMode();
      throw new Display.BreakException();
    } catch (EndOfFileException e) {
      return null;
    }
  }

  @Override
  public boolean pollForBreak() {
    return breakFlag.compareAndSet(true, false);
  }

  @Override
  public String inkey() {
    if (!typeAheadBuffer.isEmpty()) {
      char c = typeAheadBuffer.charAt(0);
      typeAheadBuffer.deleteCharAt(0);
      return String.valueOf(c);
    }
    try {
      int c = reader.read(1L);
      if (c >= 0) {
        return String.valueOf((char) c);
      }
    } catch (IOException e) {
      /* Ignore */
    }
    return "";
  }

  @Override
  public void scroll() {
    super.scroll();
    if (terminal.getHeight() > 0) {
      currentRow = terminal.getHeight() - 1;
      currentCol = 0;
    }
  }

  @Override
  public void close() {
    try {
      showCursor();
      terminal.close();
    } catch (IOException e) {
      /* Ignore */
    }
  }
}
