package com.davidconneely.repl.jline;

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
public class RobustLineReaderImpl extends LineReaderImpl {

  private final Clipboard clipboard = Clipboard.getDefault();
  private boolean mouseTrackingFixed = false;
  private final ResettableDisplay resettableDisplay;

  public RobustLineReaderImpl(Terminal terminal, String appName) throws IOException {
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
    boolean ret = buf.move(-1) == -1;
    copySelectionToClipboard();
    return ret;
  }

  boolean shiftForwardChar() {
    if (regionActive == RegionType.NONE) {
      regionMark = buf.cursor();
      regionActive = RegionType.CHAR;
    }
    boolean ret = buf.move(1) == 1;
    copySelectionToClipboard();
    return ret;
  }

  private boolean deleteSelectionIfNeeded() {
    if (regionActive != RegionType.NONE && regionMark != buf.cursor()) {
      int start = Math.min(regionMark, buf.cursor());
      int end = Math.max(regionMark, buf.cursor());

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
    boolean ret = super.beginPaste();
    regionActive = RegionType.NONE;
    return ret;
  }

  @Override
  public boolean yank() {
    deleteSelectionIfNeeded();
    boolean ret = super.yank();
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
      int start = Math.min(regionMark, buf.cursor());
      int end = Math.max(regionMark, buf.cursor());
      String selected = buf.substring(start, end);
      clipboard.copy(selected);
    }
  }

  private java.util.function.IntConsumer inputHeightListener;

  public void setInputHeightListener(java.util.function.IntConsumer listener) {
    this.inputHeightListener = listener;
  }

  public void forceRedrawFromCursor() {
    display.reset();
    resettableDisplay.resetCursorPos();
  }

  public org.jline.utils.Display getDisplay() {
    return display;
  }

  @Override
  public boolean redisplay() {
    int termWidth = terminal.getWidth();
    if (termWidth > 0 && inputHeightListener != null) {
      org.jline.utils.AttributedStringBuilder sbAll =
          new org.jline.utils.AttributedStringBuilder().tabs(getTabWidth());
      sbAll.append(prompt);
      sbAll.append(new org.jline.utils.AttributedString(buf.toString()));
      java.util.List<org.jline.utils.AttributedString> allLines =
          sbAll.columnSplitLength(termWidth, false, display.delayLineWrap());
      int inputHeight = Math.max(1, allLines.size());
      inputHeightListener.accept(inputHeight);
    }
    return super.redisplay();
  }

  @Override
  public String readLine(
      String prompt,
      String rightPrompt,
      org.jline.reader.MaskingCallback maskingCallback,
      String buffer) {
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
    org.jline.terminal.MouseEvent event = readMouseEvent();

    org.jline.utils.AttributedStringBuilder sbAll =
        new org.jline.utils.AttributedStringBuilder().tabs(getTabWidth());
    sbAll.append(prompt);
    sbAll.append(new org.jline.utils.AttributedString(buf.toString()));
    java.util.List<org.jline.utils.AttributedString> allLines =
        sbAll.columnSplitLength(size.getColumns(), false, display.delayLineWrap());

    int clickedLine = event.getY() - (terminal.getRows() - allLines.size());

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

      int promptCharLen = prompt.length();
      newPos = charOffset - promptCharLen;

      if (newPos < 0) {
        newPos = 0;
      }
      if (newPos > buf.length()) {
        newPos = buf.length();
      }
    }

    if (event.getType() == org.jline.terminal.MouseEvent.Type.Pressed) {
      buf.cursor(newPos);
      regionMark = newPos;
      regionActive = RegionType.CHAR;
    } else if (event.getType() == org.jline.terminal.MouseEvent.Type.Dragged) {
      buf.cursor(newPos);
      copySelectionToClipboard();
    } else if (event.getType() == org.jline.terminal.MouseEvent.Type.Released) {
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
