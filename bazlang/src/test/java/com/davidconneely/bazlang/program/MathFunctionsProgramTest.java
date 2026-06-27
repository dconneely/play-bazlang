package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.EvalState;
import org.junit.jupiter.api.Test;

/** Tests exercising built-in math, trig, exponential, log, and random functions. */
class MathFunctionsProgramTest extends BaseProgramTest {

  @Test
  void testExpLogFuncs() {
    String source =
        """
            10 LET E = EXP(1)
            20 LET L = LN(E)
            """;
    EvalState state = runProgram(source);
    assertEquals(Math.E, state.numVar("E"), 0.0001);
    assertEquals(1.0, state.numVar("L"), 0.0001);
  }

  @Test
  void testInverseTrigFuncs() {
    String source =
        """
            10 LET A = ASN(1)
            20 LET B = ACS(0)
            30 LET C = ATN(1)
            """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI / 2, state.numVar("A"), 0.0001);
    assertEquals(Math.PI / 2, state.numVar("B"), 0.0001);
    assertEquals(Math.PI / 4, state.numVar("C"), 0.0001);
  }

  @Test
  void testNullaryFuncs() {
    String source =
        """
            10 LET P = PI
            20 LET R = RND
            30 LET I$ = INKEY$
            40 LET F = FRAMES
            50 LET W1 = TEXTH
            60 LET W2 = TEXTW
            70 LET W3 = PLOTH
            80 LET W4 = PLOTW
            90 LET W5 = PLOTMODE
            100 LET W6 = TEXTX
            110 LET W7 = TEXTY
            120 LET W8 = PLOTX
            130 LET W9 = PLOTY
            """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI, state.numVar("P"), 0.0001);
    // RND returns value between 0 and 1
    double r = state.numVar("R");
    assertTrue(r >= 0.0 && r < 1.0);
    assertEquals("", ((EvalState.StrVar.Scalar) state.strVar("I$")).value().toJavaString());
    assertTrue(state.numVar("F") > 0);
    assertTrue(state.numVar("W1") > 0);
    assertTrue(state.numVar("W2") > 0);
    assertTrue(state.numVar("W3") > 0);
    assertTrue(state.numVar("W4") > 0);
    assertEquals(4.0, state.numVar("W5")); // default QuadrantMode = 4
    assertTrue(state.numVar("W6") >= 0);
    assertTrue(state.numVar("W7") >= 0);
    assertTrue(state.numVar("W8") >= 0);
    assertTrue(state.numVar("W9") >= 0);
  }

  @Test
  void testNumFuncs() {
    String source =
        """
            10 LET A = ABS(-5)
            20 LET B = INT(3.14)
            30 LET C = SGN(-10)
            40 LET D = SQR(16)
            50 LET E = LEN("HELLO")
            60 LET F = VAL("123")
            70 LET G = CODE("A")
            """;
    EvalState state = runProgram(source);
    assertEquals(5.0, state.numVar("A"));
    assertEquals(3.0, state.numVar("B"));
    assertEquals(-1.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(5.0, state.numVar("E"));
    assertEquals(123.0, state.numVar("F"));
    assertEquals(65.0, state.numVar("G"));
  }

  @Test
  void testTrigFuncs() {
    // Basic check they run and return somewhat sane values
    String source =
        """
            10 LET S = SIN(0)
            20 LET C = COS(0)
            30 LET T = TAN(0)
            """;
    EvalState state = runProgram(source);
    assertEquals(0.0, state.numVar("S"), 0.0001);
    assertEquals(1.0, state.numVar("C"), 0.0001);
    assertEquals(0.0, state.numVar("T"), 0.0001);
  }
}
