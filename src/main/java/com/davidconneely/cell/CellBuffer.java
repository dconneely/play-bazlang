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

  public CellBuffer(int rows, int cols, PixelMode mode) {
    this.mode = mode;
    this.rows = rows;
    this.cols = cols;
    this.codepoints = new int[rows * cols];
    this.attributes = new long[rows * cols];
    clear();
  }

  public void setMode(PixelMode mode) {
    this.mode = mode;
  }

  public PixelMode mode() {
    return mode;
  }

  public int rows() {
    return rows;
  }

  public int cols() {
    return cols;
  }

  public int pixelWidth() {
    return cols * mode.pixelsPerCellX();
  }

  public int pixelHeight() {
    return rows * mode.pixelsPerCellY();
  }

  // === Attribute packing ===
  // Layout in 64-bit long (guaranteed positive since top 3 bits are 0):
  // [0..25]  fgColor (26 bits: 2-bit type + 24-bit RGB/Index payload)
  // [26..51] bgColor (26 bits: 2-bit type + 24-bit RGB/Index payload)
  // [52..62] style   (11 bits)
  // [63]     unused  (1 bit sign, always 0)

  public static long packAttributes(int fgColor, int bgColor, int style) {
    return (fgColor & ATTR_MASK)
        | ((bgColor & ATTR_MASK) << 26)
        | (((long) style & STYLE_MASK) << 52);
  }

  public static int unpackFgColor(long attr) {
    return (int) (attr & ATTR_MASK);
  }

  public static int unpackBgColor(long attr) {
    return (int) ((attr >>> 26) & ATTR_MASK);
  }

  public static int unpackStyle(long attr) {
    return (int) ((attr >>> 52) & STYLE_MASK);
  }

  // === Per-cell accessors ===

  private int index(int row, int col) {
    return row * cols + col;
  }

  public int getCell(int row, int col) {
    return codepoints[index(row, col)];
  }

  public long getAttr(int row, int col) {
    return attributes[index(row, col)];
  }

  public int getFgColor(int row, int col) {
    return unpackFgColor(attributes[index(row, col)]);
  }

  public int getBgColor(int row, int col) {
    return unpackBgColor(attributes[index(row, col)]);
  }

  public int getStyle(int row, int col) {
    return unpackStyle(attributes[index(row, col)]);
  }

  public void setCell(int row, int col, int codepoint) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      codepoints[index(row, col)] = codepoint;
    }
  }

  public void setCell(int row, int col, int codepoint, int fgColor, int bgColor, int style) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      int idx = index(row, col);
      codepoints[idx] = codepoint;
      attributes[idx] = packAttributes(fgColor, bgColor, style);
    }
  }

  // === Bulk operations ===

  private void clearRange(
      int[] targetCodepoints, long[] targetAttributes, int fromCell, int cellCount) {
    int endCell = fromCell + cellCount;
    long defaultAttr =
        packAttributes(CellAttributes.COLOR_DEFAULT, CellAttributes.COLOR_DEFAULT, 0);
    for (int i = fromCell; i < endCell; i++) {
      targetCodepoints[i] = ' ';
      targetAttributes[i] = defaultAttr;
    }
  }

  public void clear() {
    clearRange(codepoints, attributes, 0, rows * cols);
  }

  public void scrollUp() {
    // Shift rows [1..rows-1] down by one row, then clear the last row.
    int rowStride = cols;
    System.arraycopy(codepoints, rowStride, codepoints, 0, (rows - 1) * rowStride);
    System.arraycopy(attributes, rowStride, attributes, 0, (rows - 1) * rowStride);
    // Clear the last row.
    clearRange(codepoints, attributes, (rows - 1) * cols, cols);
  }

  /**
   * Resize the buffer, anchoring existing content at the top-left corner. New cells are initialised
   * to space / default colours / no style.
   */
  public void resize(int newRows, int newCols) {
    if (newRows == rows && newCols == cols) {
      return;
    }
    int newBufSize = newRows * newCols;
    int[] newCodepoints = new int[newBufSize];
    long[] newAttributes = new long[newBufSize];
    // Initialise new buffer to default cell values.
    clearRange(newCodepoints, newAttributes, 0, newBufSize);

    // Copy existing content row by row, truncating or padding as needed.
    int copyRows = Math.min(rows, newRows);
    int copyCols = Math.min(cols, newCols);
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

  /** Returns whether the pixel at (x, y) maps to a cell within this buffer. */
  public boolean isPixelInBounds(int x, int y) {
    return Math.abs(x) < pixelWidth() && Math.abs(y) < pixelHeight();
  }

  /** Returns the cell row corresponding to pixel y-coordinate. */
  public int pixelToCellRow(int y) {
    return (rows - 1) - Math.abs(y) / mode.pixelsPerCellY();
  }

  /** Returns the cell column corresponding to pixel x-coordinate. */
  public int pixelToCellCol(int x) {
    return Math.abs(x) / mode.pixelsPerCellX();
  }

  public void plot(int x, int y) {
    updatePixel(x, y, true);
  }

  public void unplot(int x, int y) {
    updatePixel(x, y, false);
  }

  private void updatePixel(int x, int y, boolean set) {
    if (!isPixelInBounds(x, y)) {
      return;
    }
    int ppx = mode.pixelsPerCellX();
    int ppy = mode.pixelsPerCellY();
    int absX = Math.abs(x);
    int absY = Math.abs(y);
    int col = absX / ppx;
    int row = (rows - 1) - absY / ppy;
    int subX = absX % ppx;
    int subY = absY % ppy;
    int mask = mode.bitMask(subX, subY);
    int idx = index(row, col);
    int state = mode.decode(codepoints[idx]);
    codepoints[idx] = mode.encode(set ? (state | mask) : (state & ~mask));
  }
}
