package com.davidconneely.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CellBufferTest {

  @Test
  void testCoordinates() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.plot(0, 0);
    assertEquals('\u2596', buf.getCell(23, 0)); // ▖ lower-left
    buf.plot(63, 47);
    assertEquals('\u259D', buf.getCell(0, 31)); // ▝ upper-right
  }

  @Test
  void testQuadrantLogic() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    assertEquals(' ', buf.getCell(23, 0));
    buf.plot(0, 1);
    assertEquals('\u2598', buf.getCell(23, 0)); // ▘ upper-left
    buf.plot(1, 1);
    assertEquals('\u2580', buf.getCell(23, 0)); // ▀ upper half
    buf.plot(0, 0);
    assertEquals('\u259B', buf.getCell(23, 0)); // ▛ upper+lower-left
    buf.plot(1, 0);
    assertEquals('\u2588', buf.getCell(23, 0)); // █ full
  }

  @Test
  void testBoundsIgnored() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.plot(-64, 0);
    buf.plot(64, 0);
    buf.plot(0, -48);
    buf.plot(0, 48);
    assertEquals(' ', buf.getCell(23, 0));
  }

  @Test
  void testPoint() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    assertEquals(0, buf.point(10, 10));
    buf.plot(10, 10, -1, -1, 0, true, false); // normal PLOT
    assertEquals(1, buf.point(10, 10));
    buf.plot(10, 10, -1, -1, 0, false, false); // PLOT INVERSE 1
    assertEquals(0, buf.point(10, 10));
    buf.plot(15, 15, -1, -1, 0, true, true); // PLOT OVER 1 (was 0, now 1)
    assertEquals(1, buf.point(15, 15));
    buf.plot(15, 15, -1, -1, 0, true, true); // PLOT OVER 1 (was 1, now 0)
    assertEquals(0, buf.point(15, 15));
  }

  @Test
  void testSetCellAndPlotCoexist() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.setCell(10, 5, 'H');
    buf.setCell(10, 6, 'e');
    buf.setCell(10, 7, 'l');
    buf.setCell(10, 8, 'l');
    buf.setCell(10, 9, 'o');
    buf.plot(8, 26); // cell (10, 4) - different cell from text
    assertEquals('H', buf.getCell(10, 5)); // text unaffected
  }

  @Test
  void testResizePreservesContent() {
    CellBuffer buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.setCell(5, 10, 'T');
    buf.plot(0, 0); // cell (23, 0) = ▖
    assertEquals('T', buf.getCell(5, 10));
    assertEquals('\u2596', buf.getCell(23, 0));
    buf.resize(30, 40);
    assertEquals(30, buf.rows());
    assertEquals(40, buf.cols());
    assertEquals('T', buf.getCell(5, 10)); // preserved
    assertEquals('\u2596', buf.getCell(23, 0)); // cell content preserved at same position
    assertEquals(' ', buf.getCell(29, 0)); // new rows are empty
  }

  @Test
  void testDynamicPlotBounds() {
    CellBuffer buf = new CellBuffer(10, 20, QuadrantMode.INSTANCE);
    assertEquals(40, buf.pixelWidth()); // 20 * 2
    assertEquals(20, buf.pixelHeight()); // 10 * 2
    buf.plot(39, 19); // max valid coords: top-right
    assertEquals('\u259D', buf.getCell(0, 19)); // ▝
    buf.plot(40, 0); // just past width - ignored
    buf.plot(0, 20); // just past height - ignored
    assertEquals(' ', buf.getCell(9, 0));
  }

  @Test
  void testScrollUp() {
    CellBuffer buf = new CellBuffer(3, 4, QuadrantMode.INSTANCE);
    buf.setCell(0, 0, 'A');
    buf.setCell(1, 0, 'B');
    buf.setCell(2, 0, 'C');
    buf.scrollUp();
    assertEquals('B', buf.getCell(0, 0));
    assertEquals('C', buf.getCell(1, 0));
    assertEquals(' ', buf.getCell(2, 0));
  }

  // Note: testResizeSmallerClampsCursor is not included here because cursor
  // clamping on resize is a TerminalDisplay concern, not a PixelBuffer concern.
}
