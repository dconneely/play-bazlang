package com.davidconneely.bazlang.io;

import com.davidconneely.cell.PixelMode;

/**
 * The interpreter's abstraction over a terminal-like display: text output with a movable cursor, ZX
 * Spectrum-style colour/style attributes, and optional pixel graphics. Implementations range from a
 * real interactive terminal to a headless stream for tests. Every graphics-related method has a
 * no-op or harmless default so a minimal, text-only implementation needs to override only the first
 * dozen methods.
 */
public interface VirtualScreen extends AutoCloseable {
  /**
   * The cursor's current row.
   *
   * @return the row, 0-based from the top.
   */
  int currentRow();

  /**
   * The cursor's current column.
   *
   * @return the column, 0-based from the left.
   */
  int currentCol();

  /** Clear the screen and home the cursor. */
  void cls();

  /**
   * Move the cursor.
   *
   * @param row target row, 0-based from the top.
   * @param col target column, 0-based from the left.
   */
  void locate(int row, int col);

  /**
   * Screen width in text columns.
   *
   * @return the column count.
   */
  int printWidth();

  /**
   * Screen height in text rows.
   *
   * @return the row count.
   */
  int printHeight();

  /** Scroll the screen content up by one row, as if the cursor had printed past the last row. */
  void scroll();

  /**
   * Print text at the cursor without a trailing newline.
   *
   * @param text the text to print.
   */
  void print(String text);

  /**
   * Print text at the cursor followed by a newline.
   *
   * @param text the text to print.
   */
  void println(String text);

  /** Print a newline at the cursor. */
  void println();

  /** Flush any buffered output to the underlying display. */
  void flush();

  /** Release the screen's underlying resources and restore any terminal state it changed. */
  @Override
  void close();

  /**
   * Toggle fast mode, in which visual flourishes (e.g. per-character render delay) are skipped in
   * favour of throughput. No-op by default.
   *
   * @param fast whether fast mode should be active.
   */
  default void setFastMode(boolean fast) {}

  /**
   * Set the default foreground (ink) colour for subsequent output. No-op by default.
   *
   * @param colour the new ink colour.
   */
  default void setInk(int colour) {}

  /**
   * Set the default background (paper) colour for subsequent output. No-op by default.
   *
   * @param colour the new paper colour.
   */
  default void setPaper(int colour) {}

  /**
   * Set the default brightness for subsequent output. No-op by default.
   *
   * @param bright the new brightness value.
   */
  default void setBright(int bright) {}

  /**
   * Set the default flash (blink) setting for subsequent output. No-op by default.
   *
   * @param flash the new flash value.
   */
  default void setFlash(int flash) {}

  /**
   * Set the default inverse-video setting for subsequent output. No-op by default.
   *
   * @param inverse the new inverse value.
   */
  default void setInverse(int inverse) {}

  /**
   * Set the default overlay (XOR-plot) setting for subsequent pixel graphics. No-op by default.
   *
   * @param over the new over value.
   */
  default void setOver(int over) {}

  /**
   * Plot a pixel. No-op by default (a screen without pixel graphics support simply ignores it).
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   */
  default void plot(int x, int y) {}

  /**
   * Read whether a pixel is set. Always {@code 0} by default.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   * @return {@code 1} if the pixel is set, {@code 0} otherwise.
   */
  default int point(int x, int y) {
    return 0;
  }

  /**
   * Pixel graphics width.
   *
   * @return the width in pixels; {@code 160} by default.
   */
  default int plotWidth() {
    return 160;
  }

  /**
   * Pixel graphics height.
   *
   * @return the height in pixels; {@code 50} by default.
   */
  default int plotHeight() {
    return 50;
  }

  /**
   * The current pixel mode, as a {@code PLOTMODE} numeric code.
   *
   * @return the mode code; {@code 4} (QuadrantMode) by default.
   */
  default int plotMode() {
    return 4; // QuadrantMode by default
  }

  /**
   * Change the pixel mode for future PLOT/UNPLOT operations. Does not clear screen content.
   *
   * @param mode the new pixel mode.
   */
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

  /**
   * Returns the codepoint at (row, col) on the screen.
   *
   * @param row cell row, 0-based from the top.
   * @param col cell column, 0-based from the left.
   * @return the codepoint displayed there; a space by default.
   */
  default int getScreenCodepoint(int row, int col) {
    return 32;
  }

  /**
   * Returns the ZX Spectrum attribute byte at (row, col) on the screen.
   *
   * @param row cell row, 0-based from the top.
   * @param col cell column, 0-based from the left.
   * @return the packed attribute byte; a default-colours/no-style value by default.
   */
  default int getScreenAttributes(int row, int col) {
    return 56;
  }

  /**
   * Returns the extended attribute value at (row, col) for the given selector.
   *
   * @param row cell row, 0-based from the top.
   * @param col cell column, 0-based from the left.
   * @param select which extended attribute to read (0/1 select colour channels).
   * @return the selected attribute's value; {@code -1} (default colour) for selectors 0/1 and
   *     {@code 0} (style inactive) otherwise, by default.
   */
  default int getXAttributes(int row, int col, int select) {
    if (select == 0 || select == 1) {
      return -1; // Default colour
    }
    return 0; // Style inactive by default
  }

  /**
   * Set a status line/indicator outside the main screen area, if the implementation has one. No-op
   * by default.
   *
   * @param status the status text to display.
   */
  default void setStatus(String status) {}

  /**
   * Print a line to a diagnostic/system channel distinct from the program's own output, if the
   * implementation has one. No-op by default.
   *
   * @param text the text to print.
   */
  default void systemPrintln(String text) {}
}
