package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising PLOT and UNPLOT screen graphics statements. */
class PlotUnplotProgramTest extends BaseProgramTest {
  @Test
  void testPlotSetsPixel() {
    var screen = runWithScreen("10 PLOT 0, 0");
    assertEquals(1, screen.point(0, 0));
    assertEquals(0, screen.point(1, 0));
    assertEquals(0, screen.point(0, 1));
  }

  @Test
  void testPlotOverXorsPixel() {
    var screen =
        runWithScreen(
            """
            10 PLOT 0, 0
            20 PLOT OVER 1; 0, 0
            """);
    assertEquals(0, screen.point(0, 0));
  }

  @Test
  void testPlotSetsQuadrantCharacter() {
    // Row 24, Col 0 is the bottom-left text cell in 25x80 display.
    // PLOT 0, 0 sets the bottom-left pixel of that cell, which is U+2596 (▖).
    var screen = runWithScreen("10 PLOT 0, 0");
    assertEquals(0x2596, screen.getScreenCodepoint(24, 0));
  }

  @Test
  void testPlotTopRight() {
    // PLOT 1, 1 sets the top-right pixel of the bottom-left cell (row 24, col 0)
    // which corresponds to U+259D (▝).
    var screen = runWithScreen("10 PLOT 1, 1");
    assertEquals(0x259D, screen.getScreenCodepoint(24, 0));
  }

  @Test
  void testPlotUpdatesCursor() {
    var state =
        runProgram(
            """
            10 PLOT 10, 20
            20 LET PX = PLOTX
            30 LET PY = PLOTY
            """);
    assertEquals(10.0, state.numVar("PX"));
    assertEquals(20.0, state.numVar("PY"));
  }

  @Test
  void testDrawLine() {
    var screen =
        runWithScreen(
            """
            10 PLOT 0, 0
            20 DRAW 3, 0
            """);
    assertEquals(1, screen.point(0, 0));
    assertEquals(1, screen.point(1, 0));
    assertEquals(1, screen.point(2, 0));
    assertEquals(1, screen.point(3, 0));
  }

  @Test
  void testDrawUpdatesCursor() {
    var state =
        runProgram(
            """
            10 PLOT 2, 2
            20 DRAW 4, 6
            30 LET PX = PLOTX
            40 LET PY = PLOTY
            """);
    assertEquals(6.0, state.numVar("PX"));
    assertEquals(8.0, state.numVar("PY"));
  }

  @Test
  void testPlotClsClears() {
    var screen =
        runWithScreen(
            """
            10 PLOT 0, 0
            20 CLS
            """);
    assertEquals(0, screen.point(0, 0));
  }

  @Test
  void testUscreenAfterPlot() {
    runProgram(
        """
        10 PLOT 0, 0
        20 PRINT USCREEN$(24, 0)
        """,
        "▖\n");
  }
}
