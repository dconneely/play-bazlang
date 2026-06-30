package com.davidconneely.bazlang.io;

import com.davidconneely.cell.PixelMode;
import com.davidconneely.repl.Canvas;
import com.davidconneely.repl.Shell;

public interface BazLangScreen extends Canvas, Shell {
  /** Change the pixel mode for future PLOT/UNPLOT operations. Does not clear screen content. */
  default void setPlotMode(PixelMode mode) {
    // no-op by default (e.g., for stream screen)
  }

  /**
   * Forces an immediate render of any pending screen changes, bypassing rate-limiting. Used by
   * PAUSE to ensure pending output is visible before sleeping.
   */
  default void forceFlush() {
    // no-op by default (e.g., for stream screen where flush() is already immediate)
  }

  /**
   * Waits for a key press before closing. Called by the interpreter after a program ends (normally
   * or via STOP) so the user can see the final screen before the terminal is restored. No-op for
   * non-interactive screen implementations (e.g. StreamScreen).
   */
  default void waitForKey() {
    // no-op by default
  }

  /** Returns the codepoint at (row, col) on the screen. */
  default int getScreenCodepoint(int row, int col) {
    return 32;
  }

  /** Returns the ZX Spectrum attribute byte at (row, col) on the screen. */
  default int getScreenAttributes(int row, int col) {
    return 56;
  }

  /** Returns the extended attribute value at (row, col) for the given selector. */
  default int getXAttributes(int row, int col, int select) {
    if (select == 0 || select == 1) {
      return -1; // Default color
    }
    return 0; // Style inactive by default
  }
}
