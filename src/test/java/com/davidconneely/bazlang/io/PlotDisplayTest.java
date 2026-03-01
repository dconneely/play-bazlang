package com.davidconneely.bazlang.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlotDisplayTest {

  // Test implementation that maintains a fixed-size buffer for graphics testing
  static class TestPlotDisplay implements Display {
    private static final int ROWS = 24;
    private static final int COLS = 32;

    private static final char[] QUADRANTS = {
      ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'
    };

    private final char[][] buffer = new char[ROWS][COLS];
    private int cursorRow = 0;
    private int cursorCol = 0;

    TestPlotDisplay() {
      for (char[] row : buffer) {
        Arrays.fill(row, ' ');
      }
    }

    @Override
    public int currentRow() {
      return cursorRow;
    }

    @Override
    public int currentCol() {
      return cursorCol;
    }

    @Override
    public void cls() {
      for (char[] row : buffer) {
        Arrays.fill(row, ' ');
      }
      cursorRow = 0;
      cursorCol = 0;
    }

    @Override
    public void locate(int row, int col) {
      cursorRow = row;
      cursorCol = col;
    }

    private int getQuadState(char c) {
      for (int i = 0; i < QUADRANTS.length; i++) {
        if (QUADRANTS[i] == c) {
          return i;
        }
      }
      return 0;
    }

    @Override
    public void plot(int x, int y) {
      updatePixel(x, y, true);
    }

    @Override
    public void unplot(int x, int y) {
      updatePixel(x, y, false);
    }

    private void updatePixel(int x, int y, boolean set) {
      int pixelWidth = COLS * 2;
      int pixelHeight = ROWS * 2;

      int absX = Math.abs(x);
      int absY = Math.abs(y);
      if (absX >= pixelWidth || absY >= pixelHeight) {
        return;
      }

      int col = absX / 2;
      int row = (ROWS - 1) - (absY / 2);

      int subX = absX % 2;
      int subY = absY % 2;

      int mask = 0;
      if (subX == 0 && subY == 1) {
        mask = 1;
      } else if (subX == 1 && subY == 1) {
        mask = 2;
      } else if (subX == 0 && subY == 0) {
        mask = 4;
      } else if (subX == 1 && subY == 0) {
        mask = 8;
      }

      char current = buffer[row][col];
      int state = getQuadState(current);
      int newState = set ? (state | mask) : (state & ~mask);
      buffer[row][col] = QUADRANTS[newState];

      // Update cursor position
      cursorRow = row;
      cursorCol = col + 1;
    }

    @Override
    public void scroll() {}

    @Override
    public void print(String text) {
      if (text != null) {
        for (char c : text.toCharArray()) {
          if (cursorRow < ROWS && cursorCol < COLS) {
            buffer[cursorRow][cursorCol] = c;
            cursorCol++;
          }
        }
      }
    }

    @Override
    public void println(String text) {
      print(text);
      println();
    }

    @Override
    public void println() {
      cursorRow++;
      cursorCol = 0;
    }

    @Override
    public void lprint(String text) {}

    @Override
    public void lprintln(String text) {}

    @Override
    public void lprintln() {}

    @Override
    public String readln(String prompt) {
      return "";
    }

    @Override
    public void prefillInput(String text) {}

    @Override
    public boolean pollForBreak() {
      return false;
    }

    @Override
    public String inkey() {
      return "";
    }

    @Override
    public void close() {}

    // Helper to inspect buffer
    char getChar(int row, int col) {
      return buffer[row][col];
    }
  }

  @Test
  void testCoordinates() {
    TestPlotDisplay display = new TestPlotDisplay();

    // PLOT 0,0 -> Bottom-Left pixel of character at Row 23, Col 0
    display.plot(0, 0);
    // Quad map: 0,0 (subX=0, subY=0) is LL (mask 4).
    // Array index 4 is '▖' (\u2596)
    assertEquals('\u2596', display.getChar(23, 0));

    // PLOT 63,47 -> Top-Right pixel of character at Row 0, Col 31
    display.plot(63, 47);
    // Quad map: 63%2=1, 47%2=1 (subX=1, subY=1) is UR (mask 2).
    // Array index 2 is '▝' (\u259D)
    assertEquals('\u259D', display.getChar(0, 31));
  }

  @Test
  void testQuadrantLogic() {
    TestPlotDisplay display = new TestPlotDisplay();

    // Initial: Empty
    assertEquals(' ', display.getChar(23, 0));

    // PLOT 0,1 (Top-Left, mask 1) -> '▘' (\u2598)
    display.plot(0, 1);
    assertEquals('\u2598', display.getChar(23, 0));

    // PLOT 1,1 (Top-Right, mask 2) -> TL | TR = Upper Half '▀' (\u2580)
    display.plot(1, 1);
    assertEquals('\u2580', display.getChar(23, 0));

    // PLOT 0,0 (Bottom-Left, mask 4) -> Upper | LL = '▛' (\u259B)
    display.plot(0, 0);
    assertEquals('\u259B', display.getChar(23, 0));

    // PLOT 1,0 (Bottom-Right, mask 8) -> Full '█' (\u2588)
    display.plot(1, 0);
    assertEquals('\u2588', display.getChar(23, 0));

    // UNPLOT 0,1 (Remove TL) -> Full & ~1 = 14 = '▟' (\u259F)
    display.unplot(0, 1);
    assertEquals('\u259F', display.getChar(23, 0));
  }

  @Test
  void testBoundsIgnored() {
    TestPlotDisplay display = new TestPlotDisplay();

    // Out of bounds should be silently ignored (no exception)
    display.plot(-64, 0);
    display.plot(64, 0);
    display.plot(0, -48);
    display.plot(0, 48);
    // Verify buffer is unchanged
    assertEquals(' ', display.getChar(23, 0));
  }

  @Test
  void testPrintAndPlotCoexist() {
    TestPlotDisplay display = new TestPlotDisplay();

    // Print text at a location
    display.locate(10, 5);
    display.print("Hello");
    assertEquals('H', display.getChar(10, 5));
    assertEquals('o', display.getChar(10, 9));

    // Plot a pixel nearby - should not affect text
    display.plot(8, 26); // Near row 10 area
    // Text should still be there
    assertEquals('H', display.getChar(10, 5));
  }

  // Test implementation with resizable buffer
  static class ResizableTestDisplay implements Display {
    private static final char[] QUADRANTS = {
      ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'
    };

    private char[][] buffer;
    private int rows;
    private int cols;
    private int cursorRow = 0;
    private int cursorCol = 0;

    ResizableTestDisplay(int rows, int cols) {
      this.rows = rows;
      this.cols = cols;
      this.buffer = new char[rows][cols];
      for (char[] row : buffer) {
        Arrays.fill(row, ' ');
      }
    }

    void resize(int newRows, int newCols) {
      char[][] newBuffer = new char[newRows][newCols];
      for (char[] row : newBuffer) {
        Arrays.fill(row, ' ');
      }
      // Copy existing content
      int copyRows = Math.min(rows, newRows);
      int copyCols = Math.min(cols, newCols);
      for (int r = 0; r < copyRows; r++) {
        System.arraycopy(buffer[r], 0, newBuffer[r], 0, copyCols);
      }
      buffer = newBuffer;
      rows = newRows;
      cols = newCols;
      cursorRow = Math.min(cursorRow, rows - 1);
      cursorCol = Math.min(cursorCol, cols - 1);
    }

    @Override
    public int currentRow() {
      return cursorRow;
    }

    @Override
    public int currentCol() {
      return cursorCol;
    }

    @Override
    public void cls() {
      for (char[] row : buffer) {
        Arrays.fill(row, ' ');
      }
    }

    @Override
    public void locate(int row, int col) {
      cursorRow = Math.max(0, Math.min(row, rows - 1));
      cursorCol = Math.max(0, Math.min(col, cols - 1));
    }

    private int getQuadState(char c) {
      for (int i = 0; i < QUADRANTS.length; i++) {
        if (QUADRANTS[i] == c) {
          return i;
        }
      }
      return 0;
    }

    @Override
    public void plot(int x, int y) {
      updatePixel(x, y, true);
    }

    @Override
    public void unplot(int x, int y) {
      updatePixel(x, y, false);
    }

    private void updatePixel(int x, int y, boolean set) {
      int pixelWidth = cols * 2;
      int pixelHeight = rows * 2;
      int absX = Math.abs(x);
      int absY = Math.abs(y);
      if (absX >= pixelWidth || absY >= pixelHeight) {
        return;
      }
      int col = absX / 2;
      int row = (rows - 1) - (absY / 2);
      int subX = absX % 2;
      int subY = absY % 2;
      int mask = 0;
      if (subX == 0 && subY == 1) {
        mask = 1;
      } else if (subX == 1 && subY == 1) {
        mask = 2;
      } else if (subX == 0 && subY == 0) {
        mask = 4;
      } else if (subX == 1 && subY == 0) {
        mask = 8;
      }
      char current = buffer[row][col];
      int state = getQuadState(current);
      int newState = set ? (state | mask) : (state & ~mask);
      buffer[row][col] = QUADRANTS[newState];

      // Update cursor position
      cursorRow = row;
      cursorCol = col + 1;
    }

    @Override
    public void scroll() {}

    @Override
    public void print(String text) {
      if (text != null) {
        for (char c : text.toCharArray()) {
          if (cursorRow < rows && cursorCol < cols) {
            buffer[cursorRow][cursorCol] = c;
            cursorCol++;
          }
        }
      }
    }

    @Override
    public void println(String text) {
      print(text);
      println();
    }

    @Override
    public void println() {
      cursorRow++;
      cursorCol = 0;
    }

    @Override
    public void lprint(String text) {}

    @Override
    public void lprintln(String text) {}

    @Override
    public void lprintln() {}

    @Override
    public String readln(String prompt) {
      return "";
    }

    @Override
    public void prefillInput(String text) {}

    @Override
    public boolean pollForBreak() {
      return false;
    }

    @Override
    public String inkey() {
      return "";
    }

    @Override
    public void close() {}

    char getChar(int row, int col) {
      return buffer[row][col];
    }

    int getRows() {
      return rows;
    }

    int getCols() {
      return cols;
    }
  }

  @Test
  void testResizePreservesContent() {
    ResizableTestDisplay display = new ResizableTestDisplay(24, 32);

    // Add some content
    display.locate(5, 10);
    display.print("Test");
    display.plot(0, 0); // Bottom-left

    assertEquals('T', display.getChar(5, 10));
    assertEquals('\u2596', display.getChar(23, 0)); // ▖

    // Resize larger
    display.resize(30, 40);
    assertEquals(30, display.getRows());
    assertEquals(40, display.getCols());
    // Content should be preserved
    assertEquals('T', display.getChar(5, 10));
    // Note: plot position changes relative to new bottom
    // Original (23,0) content is still at row 23
    assertEquals('\u2596', display.getChar(23, 0));
  }

  @Test
  void testResizeSmallerClampsCursor() {
    ResizableTestDisplay display = new ResizableTestDisplay(24, 32);

    // Position cursor near edge
    display.locate(20, 28);
    assertEquals(20, display.currentRow());
    assertEquals(28, display.currentCol());

    // Resize smaller
    display.resize(15, 20);
    // Cursor should be clamped
    assertEquals(14, display.currentRow()); // clamped to rows-1
    assertEquals(19, display.currentCol()); // clamped to cols-1
  }

  @Test
  void testDynamicPlotBounds() {
    ResizableTestDisplay display = new ResizableTestDisplay(10, 20);

    // Pixel bounds are 2x character bounds
    // 10 rows * 2 = 20 pixel height, 20 cols * 2 = 40 pixel width

    // Plot at max valid coordinates
    display.plot(39, 19); // Top-right of 10x20 grid
    assertEquals('\u259D', display.getChar(0, 19)); // ▝ at top-right

    // Out of bounds should be ignored
    display.plot(40, 0); // Just past width
    display.plot(0, 20); // Just past height
    // No crash, no change to unrelated cells
    assertEquals(' ', display.getChar(9, 0));
  }
}
