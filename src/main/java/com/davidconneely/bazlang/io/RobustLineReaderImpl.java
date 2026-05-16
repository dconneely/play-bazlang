package com.davidconneely.bazlang.io;

import java.io.IOException;
import org.jline.keymap.KeyMap;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;

/**
 * A {@link LineReaderImpl} subclass that retries on transient read-binding nulls caused by JLine's
 * VMIN=0/VTIME=1 terminal settings.
 *
 * <p>JLine's {@code enterRawMode()} sets VMIN=0 and VTIME=1 (100 ms). When no keystroke arrives
 * within 100 ms, the OS {@code read(2)} call returns 0 bytes. Java's {@code FileInputStream.read()}
 * treats a 0-byte result as EOF and returns -1. The {@code NonBlockingInputStream} background
 * thread then propagates -1 upward: {@code readCharacter()} returns -1, {@code readBinding()}
 * returns {@code null}, and {@code LineReaderImpl.readLine()} throws {@code EndOfFileException} —
 * which in BazLang's executor retry loop manifests as a continuous "Syntax error in expression"
 * storm.
 *
 * <p>This override retries {@code doReadBinding()} whenever it returns {@code null} (transient
 * VTIME expiry). Genuine EOF — when the underlying reader is actually closed — propagates as {@code
 * EndOfFileException} via the {@code ClosedException} path in {@code BindingReader.readCharacter()}
 * and is not caught here, so real terminal-closed scenarios still terminate correctly.
 */
class RobustLineReaderImpl extends LineReaderImpl {

  RobustLineReaderImpl(Terminal terminal, String appName) throws IOException {
    super(terminal, appName);
  }

  @Override
  protected <T> T doReadBinding(KeyMap<T> keys, KeyMap<T> local) {
    T result;
    do {
      result = super.doReadBinding(keys, local);
      // null == readCharacter() returned -1, meaning VTIME expired with no keystroke.
      // Retry until a real binding arrives or a genuine EOF exception propagates.
    } while (result == null);
    return result;
  }
}
