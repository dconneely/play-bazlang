package com.davidconneely.bazlang.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BufferedDisplayTest {

  // Minimal concrete implementation to test BufferedDisplay logic
  static class TestBufferedDisplay extends BufferedDisplay {
    @Override
    protected void rawPrint(String text) {}

    @Override
    protected void rawLocate(int row, int col) {}

    @Override
    protected void rawScroll() {}

    @Override
    protected void rawCls() {}

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
    TestBufferedDisplay display = new TestBufferedDisplay();

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
    TestBufferedDisplay display = new TestBufferedDisplay();

    // Target: Row 10, Col 10 -> x=20/21, y= (23-10)*2 + [0|1] = 26/27
    // Let's stick to 0,0 for simplicity (Row 23, Col 0)

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
  void testBounds() {
    TestBufferedDisplay display = new TestBufferedDisplay();

    assertThrows(IllegalArgumentException.class, () -> display.plot(-64, 0));
    assertThrows(IllegalArgumentException.class, () -> display.plot(64, 0));
    assertThrows(IllegalArgumentException.class, () -> display.plot(0, -48));
    assertThrows(IllegalArgumentException.class, () -> display.plot(0, 48));
  }
}
