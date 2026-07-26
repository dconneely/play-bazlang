package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.exec.EvalState;
import org.junit.jupiter.api.Test;

/** Tests exercising the CIRCLE graphics statement. */
class CircleProgramTest extends BaseProgramTest {
  @Test
  void testCirclePlotsCardinalPoints() {
    var screen = runWithScreen("10 CIRCLE 20, 20, 5");
    // The four cardinal points of a radius-5 circle centred at (20, 20).
    assertEquals(1, screen.point(25, 20)); // east
    assertEquals(1, screen.point(15, 20)); // west
    assertEquals(1, screen.point(20, 25)); // north
    assertEquals(1, screen.point(20, 15)); // south
    // The centre is not part of the outline.
    assertEquals(0, screen.point(20, 20));
  }

  @Test
  void testCircleZeroRadiusPlotsCentre() {
    var screen = runWithScreen("10 CIRCLE 20, 20, 0");
    assertEquals(1, screen.point(20, 20));
  }

  @Test
  void testCircleStyleItemDoesNotLeak() {
    // A style item on CIRCLE applies only for the circle; a subsequent PLOT still draws.
    var screen =
        runWithScreen(
            """
            10 CIRCLE INK 2; 20, 20, 5
            20 PLOT 0, 0
            """);
    assertEquals(1, screen.point(25, 20)); // circle drawn
    assertEquals(1, screen.point(0, 0)); // subsequent plot drawn
  }

  @Test
  void testCircleSetsGraphicsCursorToCentre() {
    EvalState state = runProgram("10 CIRCLE 30, 25, 5");
    assertEquals(30, state.graphicsCursorX());
    assertEquals(25, state.graphicsCursorY());
  }
}
