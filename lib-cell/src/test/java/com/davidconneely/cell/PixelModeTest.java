package com.davidconneely.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PixelModeTest {

  @Test
  void testBrailleMode() {
    final var mode = BrailleMode.INSTANCE;
    assertEquals(2, mode.pixelsPerCellX());
    assertEquals(4, mode.pixelsPerCellY());

    // Test encoding/decoding
    assertEquals(0x2800, mode.encode(0));
    assertEquals(0x28FF, mode.encode(0xFF));
    assertEquals(0, mode.decode(0x2800));
    assertEquals(0xFF, mode.decode(0x28FF));

    // Test bitmasks (dot positions in Unicode braille)
    assertEquals(1, mode.bitMask(0, 3)); // top-left (dot 1)
    assertEquals(8, mode.bitMask(1, 3)); // top-right (dot 4)
    assertEquals(128, mode.bitMask(1, 0)); // bottom-right (dot 8)
  }

  @Test
  void testCellMode() {
    final var mode = CellMode.INSTANCE;
    assertEquals(1, mode.pixelsPerCellX());
    assertEquals(1, mode.pixelsPerCellY());

    // Test encoding/decoding
    assertEquals(' ', mode.encode(0));
    assertEquals('█', mode.encode(1));
    assertEquals(0, mode.decode(' '));
    assertEquals(1, mode.decode('█'));

    // Test bitmasks
    assertEquals(1, mode.bitMask(0, 0));
  }

  @Test
  void testHalfCellMode() {
    final var mode = HalfCellMode.INSTANCE;
    assertEquals(1, mode.pixelsPerCellX());
    assertEquals(2, mode.pixelsPerCellY());

    // Test encoding/decoding
    assertEquals(' ', mode.encode(0));
    assertEquals('\u2580', mode.encode(1)); // Upper half block
    assertEquals('\u2584', mode.encode(2)); // Lower half block
    assertEquals('\u2588', mode.encode(3)); // Full block
    assertEquals(0, mode.decode(' '));
    assertEquals(1, mode.decode('\u2580'));
    assertEquals(2, mode.decode('\u2584'));
    assertEquals(3, mode.decode('\u2588'));

    // Test bitmasks (top/bottom)
    assertEquals(1, mode.bitMask(0, 1)); // top
    assertEquals(2, mode.bitMask(0, 0)); // bottom
  }

  @Test
  void testQuadrantMode() {
    final var mode = QuadrantMode.INSTANCE;
    assertEquals(2, mode.pixelsPerCellX());
    assertEquals(2, mode.pixelsPerCellY());

    // Test encoding/decoding
    assertEquals(' ', mode.encode(0));
    assertEquals('\u2596', mode.encode(4)); // Lower-left quadrant
    assertEquals('\u259D', mode.encode(2)); // Upper-right quadrant
    assertEquals(0, mode.decode(' '));
    assertEquals(4, mode.decode('\u2596'));
    assertEquals(2, mode.decode('\u259D'));

    // Test bitmasks
    assertEquals(4, mode.bitMask(0, 0)); // bottom-left
    assertEquals(8, mode.bitMask(1, 0)); // bottom-right
    assertEquals(1, mode.bitMask(0, 1)); // top-left
    assertEquals(2, mode.bitMask(1, 1)); // top-right
  }

  @Test
  void testSextantMode() {
    final var mode = SextantMode.INSTANCE;
    assertEquals(2, mode.pixelsPerCellX());
    assertEquals(3, mode.pixelsPerCellY());

    // Test encoding/decoding
    assertEquals(' ', mode.encode(0));
    assertEquals('\u2588', mode.encode(63)); // Full block
    assertEquals(0, mode.decode(' '));
    assertEquals(63, mode.decode('\u2588'));

    // Test bitmasks
    assertEquals(1, mode.bitMask(0, 2)); // top-left
    assertEquals(2, mode.bitMask(1, 2)); // top-right
    assertEquals(4, mode.bitMask(0, 1)); // mid-left
    assertEquals(8, mode.bitMask(1, 1)); // mid-right
    assertEquals(16, mode.bitMask(0, 0)); // bottom-left
    assertEquals(32, mode.bitMask(1, 0)); // bottom-right
  }
}
