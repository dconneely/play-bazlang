package com.davidconneely.bazlang.io;

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
}
