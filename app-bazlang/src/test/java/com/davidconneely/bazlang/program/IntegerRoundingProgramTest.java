package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import org.junit.jupiter.api.Test;

/**
 * Every place a number is used as an integer rounds it the way the Sinclair ROM's {@code FP-TO-BC}
 * does ({@code INT(x + 0.5)}), rather than truncating - see {@link ExpressionEvaluator#toInt}.
 */
class IntegerRoundingProgramTest extends BaseProgramTest {

  @Test
  void integerConversionRoundsHalfUpAndSaturates() {
    assertEquals(2, ExpressionEvaluator.toInt(1.5));
    assertEquals(3, ExpressionEvaluator.toInt(2.5));
    assertEquals(1, ExpressionEvaluator.toInt(1.4));
    assertEquals(-1, ExpressionEvaluator.toInt(-1.5));
    assertEquals(-2, ExpressionEvaluator.toInt(-1.6));
    assertEquals(Integer.MAX_VALUE, ExpressionEvaluator.toInt(1e20));
    assertEquals(Integer.MIN_VALUE, ExpressionEvaluator.toInt(-1e20));
  }

  @Test
  void numericArraySubscriptsRound() {
    runProgram(
        """
        10 DIM a(3)
        20 LET a(1.6) = 7
        30 PRINT a(2); a(1.5)
        """,
        "77\n");
  }

  @Test
  void dimSizesRound() {
    runProgram(
        """
        10 DIM a(2.5)
        20 LET a(3) = 1
        30 PRINT a(3)
        """,
        "1\n");
  }

  @Test
  void stringSubscriptsAndSlicesRound() {
    runProgram(
        """
        10 LET s$ = "ABCDE"
        20 PRINT s$(1.5); s$(1.6 TO 3.5)
        """,
        "BBCD\n");
  }

  @Test
  void chrRounds() {
    runProgram("10 PRINT CHR$ 64.5\n", "A\n");
  }

  @Test
  void printAtRounds() {
    final var screen = runWithScreen("10 PRINT AT 0.6, 1.5; \"X\"\n");
    assertEquals('X', screen.getScreenCodepoint(1, 2));
  }

  @Test
  void aHugeGoToTargetIsOutOfRangeRatherThanWrapping() {
    // 4294967326 = 2^32 + 30: a wrapping (int) cast would land on line 30.
    final var e =
        assertThrows(
            ReportException.class,
            () -> runProgramCapture("10 GO TO 4294967326\n30 PRINT \"WRAPPED\"\n"));
    assertEquals(ReportCode.INTEGER_OUT_OF_RANGE, e.reportCode());
  }
}
