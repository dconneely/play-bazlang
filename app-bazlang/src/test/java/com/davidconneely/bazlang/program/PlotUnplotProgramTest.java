package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising PLOT and UNPLOT screen graphics statements. */
class PlotUnplotProgramTest extends BaseProgramTest {

  @Test
  void testPlotUnplot() {
    // A single PLOT sets the pixel.
    assertEquals(1, runWithScreen("10 PLOT 0, 0").point(0, 0));
    // PLOT OVER 1 XORs the pixel back off.
    assertEquals(
        0,
        runWithScreen(
                """
        10 PLOT 0, 0
        20 PLOT OVER 1; 0, 0""")
            .point(0, 0));
  }
}
