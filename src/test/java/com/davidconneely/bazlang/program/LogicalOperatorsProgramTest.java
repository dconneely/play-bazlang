package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.EvalState;
import org.junit.jupiter.api.Test;

/** Tests exercising logical operators (AND, OR, NOT) on numbers and strings. */
class LogicalOperatorsProgramTest extends BaseProgramTest {

  @Test
  void testLogicalOperators() {
    String source =
        """
        10 LET A = 1 AND 1
        20 LET B = 1 AND 0
        30 LET C = 0 OR 1
        40 LET D = 0 OR 0
        50 LET E = NOT 0
        60 LET F = NOT 5
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(0.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(0.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(0.0, state.numVar("F"));
  }

  @Test
  void testZx81AndOperator() {
    // ZX81: A AND B = A if B ≠ 0, 0 if B = 0 (numeric)
    EvalState state =
        runProgram(
            """
        10 LET A = 5 AND 1
        20 LET B = 5 AND 0
        30 LET C = 0 AND 1
        40 LET D = 3.5 AND 2
        """);
    assertEquals(5.0, state.numVar("A")); // 5 AND 1 = 5
    assertEquals(0.0, state.numVar("B")); // 5 AND 0 = 0
    assertEquals(0.0, state.numVar("C")); // 0 AND 1 = 0
    assertEquals(3.5, state.numVar("D")); // 3.5 AND 2 = 3.5
  }

  @Test
  void testZx81AndOperatorWithStrings() {
    // ZX81: str AND n = str if n ≠ 0, "" if n = 0
    String output =
        runProgramCapture(
            """
        10 PRINT "A" AND 1
        20 PRINT "[" + ("A" AND 0) + "]"
        30 PRINT "HELLO" AND 5
        40 PRINT "[" + ("HELLO" AND 0) + "]"
        """);
    String[] lines = output.split(System.lineSeparator());
    assertEquals("A", lines[0]); // "A" AND 1 = "A"
    assertEquals("[]", lines[1]); // "A" AND 0 = "" (wrapped in brackets)
    assertEquals("HELLO", lines[2]); // "HELLO" AND 5 = "HELLO"
    assertEquals("[]", lines[3]); // "HELLO" AND 0 = "" (wrapped in brackets)
  }

  @Test
  void testZx81OrOperator() {
    // ZX81: A OR B = 1 if B ≠ 0, A if B = 0
    EvalState state =
        runProgram(
            """
        10 LET A = 5 OR 1
        20 LET B = 5 OR 0
        30 LET C = 0 OR 1
        40 LET D = 3.5 OR 0
        """);
    assertEquals(1.0, state.numVar("A")); // 5 OR 1 = 1
    assertEquals(5.0, state.numVar("B")); // 5 OR 0 = 5
    assertEquals(1.0, state.numVar("C")); // 0 OR 1 = 1
    assertEquals(3.5, state.numVar("D")); // 3.5 OR 0 = 3.5
  }
}
