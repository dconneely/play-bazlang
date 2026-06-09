package com.davidconneely.repl.jline;

import org.jline.terminal.Terminal;
import org.jline.utils.Display;

/**
 * A {@link Display} subclass that adds {@link #resetCursorPos()}.
 *
 * <p>JLine's {@code Display} tracks where it believes the terminal cursor is via the {@code
 * protected int cursorPos} field. When code outside JLine (e.g. {@code TerminalDisplay}) moves the
 * cursor directly by writing raw ANSI escape sequences, JLine's tracker becomes stale. Calling
 * {@link Display#reset()} clears the line cache but does not reset {@code cursorPos}, so the next
 * {@code update()} call moves the cursor from the wrong starting position.
 *
 * <p>Because {@code cursorPos} is {@code protected}, a subclass can reset it directly without
 * reflection or accessibility overrides.
 */
class ResettableDisplay extends Display {

  ResettableDisplay(Terminal terminal, boolean fullscreen) {
    super(terminal, fullscreen);
  }

  /** Resets the cursor-position tracker to zero (top-left of the display area). */
  void resetCursorPos() {
    cursorPos = 0;
  }
}
