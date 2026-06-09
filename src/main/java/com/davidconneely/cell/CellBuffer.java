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
 * <p>Internally the buffer uses a single flat {@code int[]} in AoS (array-of-structures) layout:
 * for cell (row, col) the four attributes are stored at consecutive indices {@code base}, {@code
 * base+1}, {@code base+2}, {@code base+3} where {@code base = (row * cols + col) * STRIDE}. All
 * four values for a cell are therefore in the same CPU cache line, which suits the rendering loop
 * that reads all four per cell.
 */
public final class CellBuffer {
  /** Number of int slots per cell: codepoint, fg colour, bg colour, style flags. */
  private static final int STRIDE = 4;

  private static final int OFF_CELL = 0;
  private static final int OFF_FG = 1;
  private static final int OFF_BG = 2;
  private static final int OFF_STYLE = 3;

  private PixelMode mode;
  private int[] buf;
  private int rows;
  private int cols;

  public CellBuffer(int rows, int cols, PixelMode mode) {
    this.mode = mode;
    this.rows = rows;
    this.cols = cols;
    this.buf = new int[rows * cols * STRIDE];
    clear();
  }

  public void setMode(PixelMode mode) {
    this.mode = mode;
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

  // === Per-cell accessors ===

  private int base(int row, int col) {
    return (row * cols + col) * STRIDE;
  }

  public int getCell(int row, int col) {
    return buf[base(row, col) + OFF_CELL];
  }

  public int getFgColor(int row, int col) {
    return buf[base(row, col) + OFF_FG];
  }

  public int getBgColor(int row, int col) {
    return buf[base(row, col) + OFF_BG];
  }

  public int getStyle(int row, int col) {
    return buf[base(row, col) + OFF_STYLE];
  }

  public void setCell(int row, int col, int codepoint) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      buf[base(row, col) + OFF_CELL] = codepoint;
    }
  }

  public void setCell(int row, int col, int codepoint, int fgColor, int bgColor, int style) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      int b = base(row, col);
      buf[b + OFF_CELL] = codepoint;
      buf[b + OFF_FG] = fgColor;
      buf[b + OFF_BG] = bgColor;
      buf[b + OFF_STYLE] = style;
    }
  }

  // === Bulk operations ===

  private void clearRange(int[] targetBuf, int fromCell, int cellCount) {
    int endCell = fromCell + cellCount;
    for (int i = fromCell; i < endCell; i++) {
      int b = i * STRIDE;
      targetBuf[b + OFF_CELL] = ' ';
      targetBuf[b + OFF_FG] = CellAttributes.COLOR_DEFAULT;
      targetBuf[b + OFF_BG] = CellAttributes.COLOR_DEFAULT;
      targetBuf[b + OFF_STYLE] = 0;
    }
  }

  public void clear() {
    clearRange(buf, 0, rows * cols);
  }

  public void scrollUp() {
    // Shift rows [1..rows-1] down by one row, then clear the last row.
    int rowStride = cols * STRIDE;
    System.arraycopy(buf, rowStride, buf, 0, (rows - 1) * rowStride);
    // Clear the last row.
    clearRange(buf, (rows - 1) * cols, cols);
  }

  /**
   * Resize the buffer, anchoring existing content at the top-left corner. New cells are initialised
   * to space / default colours / no style.
   */
  public void resize(int newRows, int newCols) {
    if (newRows == rows && newCols == cols) {
      return;
    }
    int newBufSize = newRows * newCols * STRIDE;
    int[] newBuf = new int[newBufSize];
    // Initialise new buffer to default cell values.
    clearRange(newBuf, 0, newRows * newCols);

    // Copy existing content row by row, truncating or padding as needed.
    int copyRows = Math.min(rows, newRows);
    int copyCols = Math.min(cols, newCols);
    int srcRowStride = cols * STRIDE;
    int dstRowStride = newCols * STRIDE;
    int copyLen = copyCols * STRIDE;
    for (int r = 0; r < copyRows; r++) {
      System.arraycopy(buf, r * srcRowStride, newBuf, r * dstRowStride, copyLen);
    }
    buf = newBuf;
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
    int b = base(row, col);
    int state = mode.decode(buf[b + OFF_CELL]);
    buf[b + OFF_CELL] = mode.encode(set ? (state | mask) : (state & ~mask));
  }
}
