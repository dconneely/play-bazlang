package com.davidconneely.repl.jline;

import java.io.IOException;
import java.util.function.IntConsumer;
import org.jline.keymap.KeyMap;
import org.jline.reader.MaskingCallback;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.MouseEvent;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

/**
 * A {@link LineReaderImpl} subclass that retries on transient read-binding nulls caused by JLine's
 * VMIN=0/VTIME=1 terminal settings.
 *
 * <p>JLine's {@code enterRawMode()} sets VMIN=0 and VTIME=1 (100 ms). When no keystroke arrives
 * within 100 ms, the OS {@code read(2)} call returns 0 bytes. Java's {@code FileInputStream.read()}
 * treats a 0-byte result as EOF and returns -1. The {@code NonBlockingInputStream} background
 * thread then propagates -1 upward: {@code readCharacter()} returns -1, {@code readBinding()}
 * returns {@code null}, and {@code LineReaderImpl.readLine()} throws {@code EndOfFileException} -
 * which in BazLang's executor retry loop manifests as a continuous "Syntax error in expression"
 * storm.
 *
 * <p>This override retries {@code doReadBinding()} whenever it returns {@code null} (transient
 * VTIME expiry). Genuine EOF - when the underlying reader is actually closed - propagates as {@code
 * EndOfFileException} via the {@code ClosedException} path in {@code BindingReader.readCharacter()}
 * and is not caught here, so real terminal-closed scenarios still terminate correctly.
 */
class RobustLineReaderImpl extends LineReaderImpl {

  private final Clipboard clipboard = Clipboard.getDefault();
  private boolean mouseTrackingFixed = false;
  private final ResettableDisplay resettableDisplay;

  @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
  RobustLineReaderImpl(Terminal terminal, String appName) throws IOException {
    super(terminal, appName);

    // Replace JLine's Display with our ResettableDisplay subclass so that forceRedrawFromCursor()
    // can zero cursorPos directly (a protected field), without needing reflection.
    resettableDisplay = new ResettableDisplay(terminal, true);
    display = resettableDisplay;

    getWidgets().put("shift-backward-char", this::shiftBackwardChar);
    getWidgets().put("shift-forward-char", this::shiftForwardChar);

    getKeyMaps()
        .get(EMACS)
        .bind(new org.jline.reader.Reference("shift-backward-char"), "\033[1;2D");
    getKeyMaps().get(EMACS).bind(new org.jline.reader.Reference("shift-forward-char"), "\033[1;2C");
  }

  boolean shiftBackwardChar() {
    if (regionActive == RegionType.NONE) {
      regionMark = buf.cursor();
      regionActive = RegionType.CHAR;
    }
    final boolean ret = buf.move(-1) == -1;
    copySelectionToClipboard();
    return ret;
  }

  boolean shiftForwardChar() {
    if (regionActive == RegionType.NONE) {
      regionMark = buf.cursor();
      regionActive = RegionType.CHAR;
    }
    final boolean ret = buf.move(1) == 1;
    copySelectionToClipboard();
    return ret;
  }

  private boolean deleteSelectionIfNeeded() {
    if (regionActive != RegionType.NONE && regionMark != buf.cursor()) {
      final int start = Math.min(regionMark, buf.cursor());
      final int end = Math.max(regionMark, buf.cursor());

      buf.cursor(end);
      buf.backspace(end - start);

      regionActive = RegionType.NONE;
      regionMark = buf.cursor();
      return true;
    }
    return false;
  }

  @Override
  public boolean beginPaste() {
    deleteSelectionIfNeeded();
    final boolean ret = super.beginPaste();
    regionActive = RegionType.NONE;
    return ret;
  }

  @Override
  public boolean yank() {
    deleteSelectionIfNeeded();
    final boolean ret = super.yank();
    regionActive = RegionType.NONE;
    return ret;
  }

  @Override
  protected boolean selfInsert() {
    deleteSelectionIfNeeded();
    return super.selfInsert();
  }

  @Override
  protected boolean backwardDeleteChar() {
    return deleteSelectionIfNeeded() || super.backwardDeleteChar();
  }

  @Override
  protected boolean deleteChar() {
    return deleteSelectionIfNeeded() || super.deleteChar();
  }

  private void copySelectionToClipboard() {
    if (regionActive != RegionType.NONE && regionMark != buf.cursor()) {
      final int start = Math.min(regionMark, buf.cursor());
      final int end = Math.max(regionMark, buf.cursor());
      final String selected = buf.substring(start, end);
      clipboard.copy(selected);
    }
  }

  private IntConsumer inputHeightListener;

  void setInputHeightListener(IntConsumer listener) {
    this.inputHeightListener = listener;
  }

  void forceRedrawFromCursor() {
    display.reset();
    resettableDisplay.resetCursorPos();
  }

  @Override
  public boolean redisplay() {
    final int termWidth = terminal.getColumns();
    if (termWidth > 0 && inputHeightListener != null) {
      final var sbAll = new AttributedStringBuilder().tabs(getTabWidth());
      sbAll.append(prompt);
      sbAll.append(new AttributedString(buf.toString()));
      final var allLines = sbAll.columnSplitLength(termWidth, false, display.delayLineWrap());
      final int inputHeight = Math.max(1, allLines.size());
      inputHeightListener.accept(inputHeight);
    }
    return super.redisplay();
  }

  @Override
  public String readLine(
      String prompt, String rightPrompt, MaskingCallback maskingCallback, String buffer) {
    this.mouseTrackingFixed = false;
    return super.readLine(prompt, rightPrompt, maskingCallback, buffer);
  }

  @Override
  protected <T> T doReadBinding(KeyMap<T> keys, KeyMap<T> local) {
    if (!mouseTrackingFixed) {
      terminal.trackMouse(Terminal.MouseTracking.Button);
      mouseTrackingFixed = true;
    }
    T result;
    do {
      result = super.doReadBinding(keys, local);
      // null == readCharacter() returned -1, meaning VTIME expired with no keystroke.
      // Retry until a real binding arrives or a genuine EOF exception propagates.
    } while (result == null);
    return result;
  }

  @Override
  public boolean mouse() {
    final var event = readMouseEvent();

    final var sbAll = new AttributedStringBuilder().tabs(getTabWidth());
    sbAll.append(prompt);
    sbAll.append(new AttributedString(buf.toString()));
    final var allLines = sbAll.columnSplitLength(size.getColumns(), false, display.delayLineWrap());

    final int clickedLine = event.getY() - (terminal.getRows() - allLines.size());

    int newPos;
    if (clickedLine < 0) {
      newPos = 0;
    } else if (clickedLine >= allLines.size()) {
      newPos = buf.length();
    } else {
      int charOffset = 0;
      for (int i = 0; i < clickedLine; i++) {
        charOffset += allLines.get(i).length();
      }

      charOffset += event.getX();

      final int promptCharLen = prompt.length();
      newPos = charOffset - promptCharLen;

      if (newPos < 0) {
        newPos = 0;
      }
      if (newPos > buf.length()) {
        newPos = buf.length();
      }
    }

    if (event.getType() == MouseEvent.Type.Pressed) {
      buf.cursor(newPos);
      regionMark = newPos;
      regionActive = RegionType.CHAR;
    } else if (event.getType() == MouseEvent.Type.Dragged) {
      buf.cursor(newPos);
      copySelectionToClipboard();
    } else if (event.getType() == MouseEvent.Type.Released) {
      buf.cursor(newPos);
      copySelectionToClipboard();
      // Keep region active so user can see it, it will clear when they type.
      // But if they clicked without dragging, clear it.
      if (regionMark == buf.cursor()) {
        regionActive = RegionType.NONE;
      }
    }
    return true;
  }
}
