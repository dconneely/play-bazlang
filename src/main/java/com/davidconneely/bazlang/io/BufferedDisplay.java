package com.davidconneely.bazlang.io;

import java.util.Arrays;

abstract class BufferedDisplay implements Display {
  private static final int ROWS = 24;
  private static final int COLS = 32;
  private static final int PLOT_X_SIZE = COLS * 2;
  private static final int PLOT_Y_SIZE = ROWS * 2;

  // char[row][col]
  final char[][] buffer = new char[ROWS][COLS];

  // Cursor position (0-based)
  int currentRow = 0;
  int currentCol = 0;

  private static final char[] QUADRANTS = {
    ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'
  };

  protected BufferedDisplay() {
    clsBuffer();
  }

  protected void clsBuffer() {
    for (char[] row : buffer) {
      Arrays.fill(row, ' ');
    }
    currentRow = 0;
    currentCol = 0;
  }

  // Abstract methods for device I/O
  protected abstract void rawPrint(String text);

  protected abstract void rawLocate(int row, int col);

  protected abstract void rawScroll();

  protected abstract void rawCls();

  @Override
  public int currentRow() {
    return currentRow;
  }

  @Override
  public int currentCol() {
    return currentCol;
  }

  @Override
  public void cls() {
    clsBuffer();
    rawCls();
  }

  @Override
  public void locate(int row, int col) {
    currentRow = row;
    currentCol = col;
    rawLocate(row, col);
  }

  @Override
  public void print(String text) {
    for (char c : text.toCharArray()) {
      if (c < 32 || c == 127) continue;
      if (currentRow >= 0 && currentRow < ROWS && currentCol >= 0 && currentCol < COLS) {
        buffer[currentRow][currentCol] = c;
      }
      rawPrint(String.valueOf(c));
      currentCol++;
    }
  }

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    currentRow++;
    currentCol = 0;
    rawPrint("\n");
  }

  @Override
  public void scroll() {
    // Shift buffer up
    for (int r = 0; r < ROWS - 1; r++) {
      System.arraycopy(buffer[r + 1], 0, buffer[r], 0, COLS);
    }
    Arrays.fill(buffer[ROWS - 1], ' ');
    rawScroll();
  }

  private int getQuadState(char c) {
    for (int i = 0; i < QUADRANTS.length; i++) {
      if (QUADRANTS[i] == c) return i;
    }
    return 0;
  }

  @Override
  public void plot(int x, int y) {
    updateBlock(x, y, true);
  }

  @Override
  public void unplot(int x, int y) {
    updateBlock(x, y, false);
  }

  private void updateBlock(int x, int y, boolean set) {
    x = Math.abs(x);
    y = Math.abs(y);
    if (x >= PLOT_X_SIZE || y >= PLOT_Y_SIZE) {
      throw new IllegalArgumentException("Integer out of range");
    }

    int col = x / 2;
    int row = (ROWS - 1) - (y / 2);

    int subX = x % 2;
    int subY = y % 2;

    int mask = 0;
    if (subX == 0 && subY == 1) mask = 1; // UL
    else if (subX == 1 && subY == 1) mask = 2; // UR
    else if (subX == 0 && subY == 0) mask = 4; // LL
    else if (subX == 1 && subY == 0) mask = 8; // LR

    char current = buffer[row][col];
    int state = getQuadState(current);

    int newState = set ? (state | mask) : (state & ~mask);
    char newChar = QUADRANTS[newState];

    buffer[row][col] = newChar;

    rawLocate(row, col);
    rawPrint(String.valueOf(newChar));

    currentRow = row;
    currentCol = col + 1;
  }
}
