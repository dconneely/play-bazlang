package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising basic arithmetic operators, precedence, and division by zero. */
class ArithmeticProgramTest extends BaseProgramTest {

  @Test
  void testArithmetic() {
    String output = runProgramCapture("10 PRINT 1 + 2, 3 - 4, 5 * 6, 8 / 4, 2 ** 3");
    String[] results = output.trim().split("\\s+");
    assertArrayEquals(new String[] {"3", "-1", "30", "2", "8"}, results);
  }

  @Test
  void testDivisionByZero() {
    ReportException e = assertThrows(ReportException.class, () -> runProgram("10 PRINT 1 / 0"));
    assertEquals(ReportCode.NUMBER_TOO_BIG, e.reportCode());
  }

  @Test
  void testNumOps() {
    String source =
        """
        10 LET A = 1 + 2 * 3
        20 LET B = (1 + 2) * 3
        30 LET C = 2 ** 3
        40 LET D = 5 / 2
        """;
    EvalState state = runProgram(source);
    assertEquals(7.0, state.numVar("A"));
    assertEquals(9.0, state.numVar("B"));
    assertEquals(8.0, state.numVar("C"));
    assertEquals(2.5, state.numVar("D"));
  }

  @Test
  void testPowerPrecedence() {
    // -2**2 should be -4
    EvalState state = runProgram("10 LET A = -2**2\n20 LET B = (-2)**2");
    assertEquals(-4.0, state.numVar("A"));
    assertEquals(4.0, state.numVar("B"));
  }

  @Test
  void testUnaryMinusWithPower() {
    // Standard precedence: ** binds tighter than unary minus
    // So -2**2 = -(2**2) = -4 (not (-2)**2 = 4 as in some BASIC dialects)
    String source =
        """
        10 LET A = -2**2
        20 LET B = (-2)**2
        30 LET C = 0-2**2
        """;
    EvalState state = runProgram(source);
    assertEquals(-4.0, state.numVar("A")); // -(2**2) = -4
    assertEquals(4.0, state.numVar("B")); // (-2)**2 = 4
    assertEquals(-4.0, state.numVar("C")); // 0-(2**2) = -4
  }
}
