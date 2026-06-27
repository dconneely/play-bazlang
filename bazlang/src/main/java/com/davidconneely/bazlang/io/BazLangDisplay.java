package com.davidconneely.bazlang.io;

import com.davidconneely.cell.PixelMode;
import com.davidconneely.repl.Display;
import com.davidconneely.repl.Shell;

public interface BazLangDisplay extends Display, Shell {
  /** Change the pixel mode for future PLOT/UNPLOT operations. Does not clear display content. */
  default void setPlotMode(PixelMode mode) {
    // no-op by default (e.g., for stream display)
  }

  /**
   * Forces an immediate render of any pending display changes, bypassing rate-limiting. Used by
   * PAUSE to ensure pending output is visible before sleeping.
   */
  default void forceFlush() {
    // no-op by default (e.g., for stream display where flush() is already immediate)
  }

  /**
   * Waits for a key press before closing. Called by the interpreter after a program ends (normally
   * or via STOP) so the user can see the final screen before the terminal is restored. No-op for
   * non-interactive displays (e.g. StreamDisplay).
   */
  default void waitForKey() {
    // no-op by default
  }
}
