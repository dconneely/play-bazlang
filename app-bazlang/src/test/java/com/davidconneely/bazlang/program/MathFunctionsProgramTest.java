package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising built-in math, trig, exponential, log, and random functions. */
class MathFunctionsProgramTest extends BaseProgramTest {

  @Test
  void testExpLogFuncs() {
    final var state =
        runProgram(
            """
        10 LET E = EXP(1)
        20 LET L = LN(E)
        """);
    assertEquals(Math.E, state.numVar("E"), 0.0001);
    assertEquals(1.0, state.numVar("L"), 0.0001);
  }

  @Test
  void testInverseTrigFuncs() {
    final var state =
        runProgram(
            """
        10 LET A = ASN(1)
        20 LET B = ACS(0)
        30 LET C = ATN(1)
        """);
    assertEquals(Math.PI / 2, state.numVar("A"), 0.0001);
    assertEquals(Math.PI / 2, state.numVar("B"), 0.0001);
    assertEquals(Math.PI / 4, state.numVar("C"), 0.0001);
  }

  @Test
  void testNullaryFuncs() {
    final var state =
        runProgram(
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
        """);
    assertEquals(Math.PI, state.numVar("P"), 0.0001);
    // RND returns value between 0 and 1
    final double r = state.numVar("R");
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
    final var state =
        runProgram(
            """
        10 LET A = ABS(-5)
        20 LET B = INT(3.14)
        30 LET C = SGN(-10)
        40 LET D = SQR(16)
        50 LET E = LEN("HELLO")
        60 LET F = VAL("123")
        70 LET G = CODE("A")
        """);
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
    final var state =
        runProgram(
            """
        10 LET S = SIN(0)
        20 LET C = COS(0)
        30 LET T = TAN(0)
        """);
    assertEquals(0.0, state.numVar("S"), 0.0001);
    assertEquals(1.0, state.numVar("C"), 0.0001);
    assertEquals(0.0, state.numVar("T"), 0.0001);
  }

  @Test
  void testColourFunc() {
    final var state =
        runProgram(
            """
        10 LET C1 = COLOUR(0, 0, 0)
        20 LET C2 = COLOUR(255, 255, 255)
        30 LET C3 = COLOUR(255, 128, 0)
        """);
    assertEquals(16_777_216.0, state.numVar("C1"));
    assertEquals(33_554_431.0, state.numVar("C2"));
    assertEquals(16_777_216.0 + 0xFF8000, state.numVar("C3"));
  }

  @Test
  void testColourFuncOutOfBounds() {
    final var ex1 =
        assertThrows(ReportException.class, () -> runProgram("10 LET C = COLOUR(-1, 0, 0)"));
    assertTrue(ex1.getMessage().contains("COLOUR components"));

    final var ex2 =
        assertThrows(ReportException.class, () -> runProgram("10 LET C = COLOUR(0, 256, 0)"));
    assertTrue(ex2.getMessage().contains("COLOUR components"));
  }

  @Test
  void testAttrFunctions() {
    final var state =
        runProgram(
            """
        10 PAPER 1: INK 7: PRINT "A"
        20 PAPER 2: INK 6: FLASH 1: BRIGHT 1: PRINT "B"
        30 LET A1 = ATTR(0, 0)
        40 LET A2 = ATTR(1, 0)
        50 LET X1 = XATTR(0, 0, 0)
        60 LET X2 = XATTR(0, 0, 1)
        70 LET X3 = XATTR(1, 0, 0)
        80 LET X4 = XATTR(1, 0, 1)
        90 LET X5 = XATTR(1, 0, 2)
        100 LET X6 = XATTR(1, 0, 3)
        """);
    // Line 10: PAPER 1, INK 7.
    // Spectrum ATTR: (0*128) + (0*64) + (1*8) + 7 = 15.
    assertEquals(15.0, state.numVar("A1"));

    // Line 20: PAPER 2, INK 6. Flash=1, Bright=1.
    // Spectrum ATTR: (1*128) + (1*64) + (2*8) + 6 = 128 + 64 + 16 + 6 = 214.
    assertEquals(214.0, state.numVar("A2"));

    // XATTR colors: return BazLang format.
    assertEquals(16_777_216.0 + 0xD7D7D7, state.numVar("X1"));
    assertEquals(16_777_216.0 + 0x0000D7, state.numVar("X2"));
    assertEquals(16_777_216.0 + 0xFFFF00, state.numVar("X3"));
    assertEquals(16_777_216.0 + 0xFF0000, state.numVar("X4"));

    // Line 20 flash (select 2) and bright (select 3)
    assertEquals(1.0, state.numVar("X5"));
    assertEquals(1.0, state.numVar("X6"));
  }

  @Test
  void testAttrFunctionsOutOfBounds() {
    var e1 = assertThrows(ReportException.class, () -> runProgram("10 LET A = ATTR(-1, 0)"));
    assertEquals(com.davidconneely.bazlang.ReportCode.INTEGER_OUT_OF_RANGE, e1.reportCode());

    var e2 = assertThrows(ReportException.class, () -> runProgram("10 LET A = ATTR(0, 80)"));
    assertEquals(com.davidconneely.bazlang.ReportCode.INTEGER_OUT_OF_RANGE, e2.reportCode());

    var e3 = assertThrows(ReportException.class, () -> runProgram("10 LET X = XATTR(0, 0, -1)"));
    assertEquals(com.davidconneely.bazlang.ReportCode.INTEGER_OUT_OF_RANGE, e3.reportCode());

    var e4 = assertThrows(ReportException.class, () -> runProgram("10 LET X = XATTR(0, 0, 9)"));
    assertEquals(com.davidconneely.bazlang.ReportCode.INTEGER_OUT_OF_RANGE, e4.reportCode());
  }
}
