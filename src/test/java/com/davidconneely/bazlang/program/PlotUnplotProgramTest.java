package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests exercising PLOT and UNPLOT display graphics statements. */
class PlotUnplotProgramTest extends BaseProgramTest {

  @Test
  void testPlotUnplot() {
    // PLOT and UNPLOT should use Unicode █
    String output = runProgramCapture("10 PLOT 0, 0\n20 PLOT OVER 1; 0, 0");
    assertTrue(output.contains("██"));
  }
}
