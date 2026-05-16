package com.davidconneely.bazlang.io;

import java.util.Arrays;

/**
 * A character cell buffer that supports both direct text writes and sub-character pixel graphics
 * via a pluggable {@link PixelMode}.
 *
 * <p>Cell coordinates are (row, col) anchored at the top-left. Pixel coordinates are (x, y) with
 * (0,0) at the bottom-left; x increases rightward, y increases upward. On resize, existing cell
 * content is preserved at its top-left position; the pixel origin moves with the new bottom-left
 * corner.
 */
final class PixelBuffer {
  private PixelMode mode;
  private int[][] cells;
  private int rows;
  private int cols;

  PixelBuffer(int rows, int cols, PixelMode mode) {
    this.mode = mode;
    this.rows = rows;
    this.cols = cols;
    this.cells = new int[rows][cols];
    clear();
  }

  void setMode(PixelMode mode) {
    this.mode = mode;
  }

  int rows() {
    return rows;
  }

  int cols() {
    return cols;
  }

  int pixelWidth() {
    return cols * mode.pixelsPerCellX();
  }

  int pixelHeight() {
    return rows * mode.pixelsPerCellY();
  }

  int getCell(int row, int col) {
    return cells[row][col];
  }

  void setCell(int row, int col, int codepoint) {
    if (row >= 0 && row < rows && col >= 0 && col < cols) {
      cells[row][col] = codepoint;
    }
  }

  int[] rowCells(int row) {
    return cells[row];
  }

  void clear() {
    for (int[] row : cells) {
      Arrays.fill(row, ' ');
    }
  }

  void scrollUp() {
    for (int r = 0; r < rows - 1; r++) {
      System.arraycopy(cells[r + 1], 0, cells[r], 0, cols);
    }
    Arrays.fill(cells[rows - 1], ' ');
  }

  /**
   * Resize the buffer, anchoring existing content at the top-left corner. New cells are initialised
   * to space.
   */
  void resize(int newRows, int newCols) {
    if (newRows == rows && newCols == cols) {
      return;
    }
    int[][] newCells = new int[newRows][newCols];
    for (int[] row : newCells) {
      Arrays.fill(row, ' ');
    }
    int copyRows = Math.min(rows, newRows);
    int copyCols = Math.min(cols, newCols);
    for (int r = 0; r < copyRows; r++) {
      System.arraycopy(cells[r], 0, newCells[r], 0, copyCols);
    }
    cells = newCells;
    rows = newRows;
    cols = newCols;
  }

  /** Returns whether the pixel at (x, y) maps to a cell within this buffer. */
  boolean isPixelInBounds(int x, int y) {
    return Math.abs(x) < pixelWidth() && Math.abs(y) < pixelHeight();
  }

  /** Returns the cell row corresponding to pixel y-coordinate. */
  int pixelToCellRow(int y) {
    return (rows - 1) - Math.abs(y) / mode.pixelsPerCellY();
  }

  /** Returns the cell column corresponding to pixel x-coordinate. */
  int pixelToCellCol(int x) {
    return Math.abs(x) / mode.pixelsPerCellX();
  }

  void plot(int x, int y) {
    updatePixel(x, y, true);
  }

  void unplot(int x, int y) {
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
    int state = mode.decode(cells[row][col]);
    cells[row][col] = mode.encode(set ? (state | mask) : (state & ~mask));
  }
}
