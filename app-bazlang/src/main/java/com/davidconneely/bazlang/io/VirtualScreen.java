package com.davidconneely.bazlang.io;

import com.davidconneely.cell.PixelMode;

public interface VirtualScreen extends AutoCloseable {
  int currentRow();

  int currentCol();

  void cls();

  void locate(int row, int col);

  int printWidth();

  int printHeight();

  void scroll();

  void print(String text);

  void println(String text);

  void println();

  void flush();

  @Override
  void close();

  default void setFastMode(boolean fast) {}

  default void setInk(int colour) {}

  default void setPaper(int colour) {}

  default void setBright(int bright) {}

  default void setFlash(int flash) {}

  default void setInverse(int inverse) {}

  default void setOver(int over) {}

  default void plot(int x, int y) {}

  default int point(int x, int y) {
    return 0;
  }

  default int plotWidth() {
    return 160;
  }

  default int plotHeight() {
    return 50;
  }

  default int plotMode() {
    return 4; // QuadrantMode by default
  }

  /** Change the pixel mode for future PLOT/UNPLOT operations. Does not clear screen content. */
  default void setPlotMode(PixelMode mode) {}

  /**
   * Forces an immediate render of any pending screen changes, bypassing rate-limiting. Used by
   * PAUSE to ensure pending output is visible before sleeping.
   */
  default void forceFlush() {}

  /**
   * Waits for a key press before closing. Called by the interpreter after a program ends (normally
   * or via STOP) so the user can see the final screen before the terminal is restored. No-op for
   * non-interactive screen implementations (e.g. StreamScreen).
   */
  default void waitForKey() {}

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
      return -1; // Default colour
    }
    return 0; // Style inactive by default
  }

  default void setStatus(String status) {}

  default void systemPrintln(String text) {}
}
