package com.davidconneely.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CellBufferTest {

  @Test
  void testCoordinates() {
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.plot(0, 0);
    assertEquals('▖', buf.getCell(23, 0)); // ▖ lower-left
    buf.plot(63, 47);
    assertEquals('▝', buf.getCell(0, 31)); // ▝ upper-right
  }

  @Test
  void testQuadrantLogic() {
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    assertEquals(' ', buf.getCell(23, 0));
    buf.plot(0, 1);
    assertEquals('▘', buf.getCell(23, 0)); // ▘ upper-left
    buf.plot(1, 1);
    assertEquals('▀', buf.getCell(23, 0)); // ▀ upper half
    buf.plot(0, 0);
    assertEquals('▛', buf.getCell(23, 0)); // ▛ upper+lower-left
    buf.plot(1, 0);
    assertEquals('█', buf.getCell(23, 0)); // █ full
  }

  @Test
  void testBoundsIgnored() {
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.plot(-64, 0);
    buf.plot(64, 0);
    buf.plot(0, -48);
    buf.plot(0, 48);
    assertEquals(' ', buf.getCell(23, 0));
  }

  @Test
  void testPoint() {
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
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
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
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
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.setCell(5, 10, 'T');
    buf.plot(0, 0); // cell (23, 0) = ▖
    assertEquals('T', buf.getCell(5, 10));
    assertEquals('▖', buf.getCell(23, 0));
    buf.resize(30, 40);
    assertEquals(30, buf.rows());
    assertEquals(40, buf.cols());
    assertEquals('T', buf.getCell(5, 10)); // preserved
    assertEquals('▖', buf.getCell(23, 0)); // cell content preserved at same position
    assertEquals(' ', buf.getCell(29, 0)); // new rows are empty
  }

  @Test
  void testDynamicPlotBounds() {
    final var buf = new CellBuffer(10, 20, QuadrantMode.INSTANCE);
    assertEquals(40, buf.pixelWidth()); // 20 * 2
    assertEquals(20, buf.pixelHeight()); // 10 * 2
    buf.plot(39, 19); // max valid coords: top-right
    assertEquals('▝', buf.getCell(0, 19)); // ▝
    buf.plot(40, 0); // just past width - ignored
    buf.plot(0, 20); // just past height - ignored
    assertEquals(' ', buf.getCell(9, 0));
  }

  @Test
  void testScrollUp() {
    final var buf = new CellBuffer(3, 4, QuadrantMode.INSTANCE);
    buf.setCell(0, 0, 'A');
    buf.setCell(1, 0, 'B');
    buf.setCell(2, 0, 'C');
    buf.scrollUp();
    assertEquals('B', buf.getCell(0, 0));
    assertEquals('C', buf.getCell(1, 0));
    assertEquals(' ', buf.getCell(2, 0));
  }

  @Test
  void testResizeShrink() {
    final var buf = new CellBuffer(24, 32, QuadrantMode.INSTANCE);
    buf.setCell(20, 30, 'X');
    buf.resize(10, 10);
    assertEquals(10, buf.rows());
    assertEquals(10, buf.cols());
    // Since 20, 30 is out of bounds, it was clipped.
  }

  @Test
  void testScrollUpSingleRow() {
    final var buf = new CellBuffer(1, 4, QuadrantMode.INSTANCE);
    buf.setCell(0, 0, 'A');
    buf.scrollUp();
    assertEquals(' ', buf.getCell(0, 0));
  }

  @Test
  void testPlotAndPointAllModes() {
    PixelMode[] modes = {
      CellMode.INSTANCE,
      HalfCellMode.INSTANCE,
      QuadrantMode.INSTANCE,
      SextantMode.INSTANCE,
      BrailleMode.INSTANCE
    };
    for (PixelMode mode : modes) {
      final var buf = new CellBuffer(10, 10, mode);
      assertEquals(0, buf.point(0, 0));
      buf.plot(0, 0);
      assertEquals(1, buf.point(0, 0));
    }
  }

  @Test
  void testTransparentAttributeRoundTrip() {
    final var buf = new CellBuffer(10, 10, QuadrantMode.INSTANCE);
    // Plot with specific foreground, background and style
    buf.plot(0, 0, 2, 4, CellAttributes.STYLE_BOLD);
    long attr1 = buf.getAttr(9, 0);
    assertEquals(2, CellBuffer.unpackFgColour(attr1));
    assertEquals(4, CellBuffer.unpackBgColour(attr1));
    assertEquals(CellAttributes.STYLE_BOLD, CellBuffer.unpackStyle(attr1));

    // Plot over the same cell (at adjacent pixel) with attribute preservation
    buf.plot(1, 0);
    long attr2 = buf.getAttr(9, 0);
    assertEquals(2, CellBuffer.unpackFgColour(attr2));
    assertEquals(4, CellBuffer.unpackBgColour(attr2));
    assertEquals(CellAttributes.STYLE_BOLD, CellBuffer.unpackStyle(attr2));
  }
}
