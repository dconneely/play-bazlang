package com.davidconneely.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellAttributesTest {

  @Test
  void testRgb() {
    int colour = CellAttributes.rgb(0x123456);
    assertTrue(CellAttributes.isRgb(colour));
    assertFalse(CellAttributes.isDefault(colour));
    assertFalse(CellAttributes.isIndex(colour));
    assertEquals(0x123456, CellAttributes.valueOf(colour));
  }

  @Test
  void testIndex() {
    int colour = CellAttributes.index(0x42);
    assertTrue(CellAttributes.isIndex(colour));
    assertFalse(CellAttributes.isDefault(colour));
    assertFalse(CellAttributes.isRgb(colour));
    assertEquals(0x42, CellAttributes.valueOf(colour));
  }

  @Test
  void testDefault() {
    int colour = CellAttributes.COLOUR_DEFAULT;
    assertTrue(CellAttributes.isDefault(colour));
    assertFalse(CellAttributes.isIndex(colour));
    assertFalse(CellAttributes.isRgb(colour));
  }
}
