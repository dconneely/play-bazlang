package com.davidconneely.repl.jline;

import com.davidconneely.repl.BreakException;
import com.davidconneely.repl.TerminalEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.IntConsumer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.NonBlockingReader;

public class JLineTerminalEngine implements TerminalEngine {
  private final Terminal terminal;
  private final NonBlockingReader reader;
  private final RobustLineReaderImpl lineReader;
  private final Attributes savedAttributes;
  private Runnable onInterrupt;
  private Runnable onResize;

  public JLineTerminalEngine() throws IOException {
    this.terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build();
    this.savedAttributes = terminal.enterRawMode();
    Attributes attr = terminal.getAttributes();
    attr.setLocalFlag(Attributes.LocalFlag.ISIG, true);
    terminal.setAttributes(attr);
    terminal.puts(Capability.enter_ca_mode);
    terminal.puts(Capability.clear_screen);
    terminal.puts(Capability.cursor_invisible);
    terminal.flush();

    this.reader = terminal.reader();
    this.lineReader = new RobustLineReaderImpl(terminal, "BazLang");
    this.lineReader.option(LineReader.Option.MOUSE, true);

    terminal.handle(
        Terminal.Signal.INT,
        _ -> {
          if (onInterrupt != null) {
            onInterrupt.run();
          }
        });
    terminal.handle(
        Terminal.Signal.WINCH,
        _ -> {
          if (onResize != null) {
            onResize.run();
          }
        });
  }

  @Override
  public int getRows() {
    return terminal.getRows();
  }

  @Override
  public int getColumns() {
    return terminal.getColumns();
  }

  @Override
  public PrintWriter writer() {
    return terminal.writer();
  }

  @Override
  public int readKey(long timeoutMs) throws IOException {
    return reader.read(timeoutMs);
  }

  @Override
  public String readLine(String prompt, String prefill) {
    try {
      return lineReader.readLine(prompt, null, prefill);
    } catch (UserInterruptException e) {
      throw new BreakException();
    } catch (EndOfFileException e) {
      return null;
    }
  }

  @Override
  public void setInputHeightListener(IntConsumer listener) {
    lineReader.setInputHeightListener(listener::accept);
  }

  @Override
  public void forceRedrawFromCursor() {
    lineReader.forceRedrawFromCursor();
  }

  @Override
  public void onInterrupt(Runnable handler) {
    this.onInterrupt = handler;
  }

  @Override
  public void onResize(Runnable handler) {
    this.onResize = handler;
  }

  @Override
  public void close() {
    try {
      terminal.puts(Capability.cursor_normal);
      terminal.puts(Capability.exit_ca_mode);
      terminal.flush();
      terminal.setAttributes(savedAttributes);
      terminal.close();
    } catch (IOException e) {
      // Ignore
    }
  }
}
