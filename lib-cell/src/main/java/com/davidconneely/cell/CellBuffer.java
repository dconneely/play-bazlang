package com.davidconneely.cell;

/**
 * A character cell buffer that supports both direct text writes and sub-character pixel graphics
 * via a pluggable {@link PixelMode}.
 *
 * <p>Cell coordinates are (row, col) anchored at the top-left. Pixel coordinates are (x, y) with
 * (0,0) at the bottom-left; x increases rightward, y increases upward. On resize, existing cell
 * content is preserved at its top-left position; the pixel origin moves with the new bottom-left
 * corner.
 *
 * <p>Internally the buffer uses a Structure-of-Arrays (SoA) layout: - {@code int[] codepoints}:
 * stores the UTF-32 codepoint for each cell. - {@code long[] attributes}: stores the fg, bg, and
 * style for each cell packed into a single long. - fg uses bits 0-25 - bg uses bits 26-51 - style
 * uses bits 52-63 This reduces memory footprint (12 bytes/cell vs 16 bytes/cell) and improves
 * rendering performance by fully saturating CPU cache prefetchers during sequential rendering.
 */
public final class CellBuffer {
  private static final long ATTR_MASK = 0x03FFFFFFL; // 26 bits
  private static final long STYLE_MASK = 0x7FFL; // 11 bits

  private PixelMode mode;
  private int[] codepoints;
  private long[] attributes;
  private int rows;
  private int cols;

  /**
   * Create a buffer of the given size, cleared to spaces with default colours and no style.
   *
   * @param rows number of character-cell rows.
   * @param cols number of character-cell columns.
   * @param mode the initial {@link PixelMode} used for pixel graphics addressing.
   */
  public CellBuffer(int rows, int cols, PixelMode mode) {
    this.mode = mode;
    this.rows = rows;
    this.cols = cols;
    this.codepoints = new int[rows * cols];
    this.attributes = new long[rows * cols];
    clear();
  }

  /**
   * Switch the {@link PixelMode} used to interpret sub-cell pixels. Existing cell content is left
   * as-is; only subsequent pixel graphics operations are affected.
   *
   * @param mode the new pixel mode.
   */
  public void setMode(PixelMode mode) {
    this.mode = mode;
  }

  /**
   * The current pixel mode.
   *
   * @return the {@link PixelMode} in effect.
   */
  public PixelMode mode() {
    return mode;
  }

  /**
   * Number of character-cell rows in the buffer.
   *
   * @return row count.
   */
  public int rows() {
    return rows;
  }

  /**
   * Number of character-cell columns in the buffer.
   *
   * @return column count.
   */
  public int cols() {
    return cols;
  }

  /**
   * Buffer width in pixels, per the current {@link #mode()}.
   *
   * @return {@code cols() * mode().pixelsPerCellX()}.
   */
  public int pixelWidth() {
    return cols * mode.pixelsPerCellX();
  }

  /**
   * Buffer height in pixels, per the current {@link #mode()}.
   *
   * @return {@code rows() * mode().pixelsPerCellY()}.
   */
  public int pixelHeight() {
    return rows * mode.pixelsPerCellY();
  }

  // === Attribute packing ===
  // Layout in 64-bit long (guaranteed positive since top 3 bits are 0):
  // [0..25]  fgColour (26 bits: 2-bit type + 24-bit RGB/Index payload)
  // [26..51] bgColour (26 bits: 2-bit type + 24-bit RGB/Index payload)
  // [52..62] style   (11 bits)
  // [63]     unused  (1 bit sign, always 0)

  /**
   * Pack a foreground colour, background colour, and style into the single {@code long} stored per
   * cell.
   *
   * @param fgColour foreground colour, as encoded by {@link CellAttributes}.
   * @param bgColour background colour, as encoded by {@link CellAttributes}.
   * @param style style bits.
   * @return the packed per-cell attribute value.
   */
  public static long packAttributes(int fgColour, int bgColour, int style) {
    return ((long) (fgColour & ATTR_MASK))
        | (((long) (bgColour & ATTR_MASK)) << 26)
        | (((long) style & STYLE_MASK) << 52);
  }

  /**
   * Extract the foreground colour from a packed attribute value.
   *
   * @param attr a value previously returned by {@link #packAttributes(int, int, int)}.
   * @return the foreground colour.
   */
  public static int unpackFgColour(long attr) {
    return (int) (attr & ATTR_MASK);
  }

  /**
   * Extract the background colour from a packed attribute value.
   *
   * @param attr a value previously returned by {@link #packAttributes(int, int, int)}.
   * @return the background colour.
   */
  public static int unpackBgColour(long attr) {
    return (int) ((attr >>> 26) & ATTR_MASK);
  }

  /**
   * Extract the style bits from a packed attribute value.
   *
   * @param attr a value previously returned by {@link #packAttributes(int, int, int)}.
   * @return the style bits.
   */
  public static int unpackStyle(long attr) {
    return (int) ((attr >>> 52) & STYLE_MASK);
  }

  // === Per-cell accessors ===

  private int index(int row, int col) {
    return row * cols + col;
  }

  /**
   * The codepoint currently displayed at a cell.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @return the cell's UTF-32 codepoint.
   */
  public int getCell(int row, int col) {
    return codepoints[index(row, col)];
  }

  /**
   * The raw packed attribute value at a cell.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @return the value as packed by {@link #packAttributes(int, int, int)}.
   */
  public long getAttr(int row, int col) {
    return attributes[index(row, col)];
  }

  /**
   * The foreground colour at a cell.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @return the foreground colour, as encoded by {@link CellAttributes}.
   */
  public int getFgColour(int row, int col) {
    return unpackFgColour(attributes[index(row, col)]);
  }

  /**
   * The background colour at a cell.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @return the background colour, as encoded by {@link CellAttributes}.
   */
  public int getBgColour(int row, int col) {
    return unpackBgColour(attributes[index(row, col)]);
  }

  /**
   * The style bits at a cell.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @return the style bits.
   */
  public int getStyle(int row, int col) {
    return unpackStyle(attributes[index(row, col)]);
  }

  /**
   * Set a cell's codepoint, leaving its existing attributes (colours/style) unchanged.
   * Out-of-bounds coordinates are silently ignored.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @param codepoint the UTF-32 codepoint to display.
   */
  public void setCell(int row, int col, int codepoint) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      codepoints[index(row, col)] = codepoint;
    }
  }

  /**
   * Set a cell's codepoint and attributes. Passing {@code -1} for {@code fgColour} or {@code
   * bgColour} preserves that cell's existing colour rather than overwriting it. Out-of-bounds
   * coordinates are silently ignored.
   *
   * @param row cell row, {@code [0, rows())}.
   * @param col cell column, {@code [0, cols())}.
   * @param codepoint the UTF-32 codepoint to display.
   * @param fgColour foreground colour, or {@code -1} to keep the cell's current one.
   * @param bgColour background colour, or {@code -1} to keep the cell's current one.
   * @param style style bits.
   */
  public void setCell(int row, int col, int codepoint, int fgColour, int bgColour, int style) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      final int idx = index(row, col);
      codepoints[idx] = codepoint;
      final long oldAttr = attributes[idx];
      final int finalFg = fgColour != -1 ? fgColour : unpackFgColour(oldAttr);
      final int finalBg = bgColour != -1 ? bgColour : unpackBgColour(oldAttr);
      attributes[idx] = packAttributes(finalFg, finalBg, style);
    }
  }

  // === Bulk operations ===

  private void clearRange(
      int[] targetCodepoints, long[] targetAttributes, int fromCell, int cellCount) {
    final int endCell = fromCell + cellCount;
    final long defaultAttr =
        packAttributes(CellAttributes.COLOUR_DEFAULT, CellAttributes.COLOUR_DEFAULT, 0);
    for (int i = fromCell; i < endCell; i++) {
      targetCodepoints[i] = ' ';
      targetAttributes[i] = defaultAttr;
    }
  }

  /** Reset every cell to a space, with default colours and no style. */
  public void clear() {
    clearRange(codepoints, attributes, 0, rows * cols);
  }

  /** Shift every row up by one, discarding row 0 and clearing the newly-exposed bottom row. */
  public void scrollUp() {
    // Shift rows [1...rows-1] down by one row, then clear the last row.
    final int rowStride = cols;
    System.arraycopy(codepoints, rowStride, codepoints, 0, (rows - 1) * rowStride);
    System.arraycopy(attributes, rowStride, attributes, 0, (rows - 1) * rowStride);
    // Clear the last row.
    clearRange(codepoints, attributes, (rows - 1) * cols, cols);
  }

  /**
   * Resize the buffer, anchoring existing content to the top-left corner. New cells are initialised
   * to space / default colours / no style.
   *
   * @param newRows the new row count.
   * @param newCols the new column count.
   */
  public void resize(int newRows, int newCols) {
    if (newRows == rows && newCols == cols) {
      return;
    }
    final int newBufSize = newRows * newCols;
    final int[] newCodepoints = new int[newBufSize];
    final long[] newAttributes = new long[newBufSize];
    // Initialise new buffer to default cell values.
    clearRange(newCodepoints, newAttributes, 0, newBufSize);

    // Copy existing content row by row, truncating or padding as needed.
    final int copyRows = Math.min(rows, newRows);
    final int copyCols = Math.min(cols, newCols);
    for (int r = 0; r < copyRows; r++) {
      System.arraycopy(codepoints, r * cols, newCodepoints, r * newCols, copyCols);
      System.arraycopy(attributes, r * cols, newAttributes, r * newCols, copyCols);
    }
    codepoints = newCodepoints;
    attributes = newAttributes;
    rows = newRows;
    cols = newCols;
  }

  // === Pixel graphics ===

  /**
   * Returns whether the pixel at (x, y) maps to a cell within this buffer. Uses Math.abs to make
   * the bounds check symmetric around the origin, which implements the documented
   * negative-coordinate mirroring behavior.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   * @return {@code true} if (x, y) addresses a pixel within the buffer.
   */
  public boolean isPixelInBounds(int x, int y) {
    return Math.abs(x) < pixelWidth() && Math.abs(y) < pixelHeight();
  }

  /**
   * Returns the cell row corresponding to pixel y-coordinate.
   *
   * @param y pixel y-coordinate.
   * @return the cell row containing that pixel.
   */
  public int pixelToCellRow(int y) {
    return (rows - 1) - Math.abs(y) / mode.pixelsPerCellY();
  }

  /**
   * Returns the cell column corresponding to pixel x-coordinate.
   *
   * @param x pixel x-coordinate.
   * @return the cell column containing that pixel.
   */
  public int pixelToCellCol(int x) {
    return Math.abs(x) / mode.pixelsPerCellX();
  }

  /**
   * Plots a pixel using existing cell attributes. Passes -1 for fg/bg colour and 0 for style, so
   * attributes are preserved rather than updated.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   */
  public void plot(int x, int y) {
    updatePixel(x, y, true, -1, -1, 0, false);
  }

  /**
   * Plots a pixel, setting the enclosing cell's colours and style.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   * @param fgColour foreground colour, or {@code -1} to keep the cell's current one.
   * @param bgColour background colour, or {@code -1} to keep the cell's current one.
   * @param style style bits.
   */
  public void plot(int x, int y, int fgColour, int bgColour, int style) {
    updatePixel(x, y, true, fgColour, bgColour, style, false);
  }

  /**
   * Plots or clears a pixel, setting the enclosing cell's colours and style.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   * @param fgColour foreground colour, or {@code -1} to keep the cell's current one.
   * @param bgColour background colour, or {@code -1} to keep the cell's current one.
   * @param style style bits.
   * @param set whether to set (as opposed to clear) the pixel; ignored if {@code over} is {@code
   *     true}.
   * @param over if {@code true}, XOR the pixel's current state instead of setting/clearing it.
   */
  public void plot(int x, int y, int fgColour, int bgColour, int style, boolean set, boolean over) {
    updatePixel(x, y, set, fgColour, bgColour, style, over);
  }

  /**
   * Reads whether a pixel is currently set.
   *
   * @param x pixel x-coordinate.
   * @param y pixel y-coordinate.
   * @return {@code 1} if the pixel is set, {@code 0} if clear or out of bounds.
   */
  public int point(int x, int y) {
    if (!isPixelInBounds(x, y)) {
      return 0;
    }
    final int ppx = mode.pixelsPerCellX();
    final int ppy = mode.pixelsPerCellY();
    final int absX = Math.abs(x);
    final int absY = Math.abs(y);
    final int col = absX / ppx;
    final int row = (rows - 1) - absY / ppy;
    final int subX = absX % ppx;
    final int subY = absY % ppy;
    final int mask = mode.bitMask(subX, subY);
    final int idx = index(row, col);
    final int state = mode.decode(codepoints[idx]);
    return (state & mask) != 0 ? 1 : 0;
  }

  private void updatePixel(
      int x, int y, boolean set, int fgColour, int bgColour, int style, boolean over) {
    if (!isPixelInBounds(x, y)) {
      return;
    }
    final int ppx = mode.pixelsPerCellX();
    final int ppy = mode.pixelsPerCellY();
    final int absX = Math.abs(x);
    final int absY = Math.abs(y);
    final int col = absX / ppx;
    final int row = (rows - 1) - absY / ppy;
    final int subX = absX % ppx;
    final int subY = absY % ppy;
    final int mask = mode.bitMask(subX, subY);
    final int idx = index(row, col);
    final int state = mode.decode(codepoints[idx]);
    int newState;
    if (over) {
      newState = state ^ mask;
    } else {
      newState = set ? (state | mask) : (state & ~mask);
    }
    codepoints[idx] = mode.encode(newState);
    if (fgColour != -1 || bgColour != -1) {
      final long oldAttr = attributes[idx];
      final int finalFg = fgColour != -1 ? fgColour : unpackFgColour(oldAttr);
      final int finalBg = bgColour != -1 ? bgColour : unpackBgColour(oldAttr);
      attributes[idx] = packAttributes(finalFg, finalBg, style);
    }
  }
}
