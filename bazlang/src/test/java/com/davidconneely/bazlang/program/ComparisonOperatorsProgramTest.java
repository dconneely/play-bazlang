package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.EvalState;
import org.junit.jupiter.api.Test;

/** Tests exercising comparison operators (=, <>, <, >, <=, >=) on numbers and strings. */
class ComparisonOperatorsProgramTest extends BaseProgramTest {

  @Test
  void testNumericComparisons() {
    String source =
        """
            10 LET A = (5 = 5)
            20 LET B = (5 <> 3)
            30 LET C = (3 < 5)
            40 LET D = (5 > 3)
            50 LET E = (3 <= 3)
            60 LET F = (5 >= 5)
            70 LET G = (3 > 5)
            """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(1.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(1.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(1.0, state.numVar("F"));
    assertEquals(0.0, state.numVar("G"));
  }

  @Test
  void testStringComparisons() {
    String source =
        """
            10 LET A = ("ABC" = "ABC")
            20 LET B = ("ABC" <> "DEF")
            30 LET C = ("ABC" < "DEF")
            40 LET D = ("DEF" > "ABC")
            50 LET E = ("ABC" <= "ABC")
            60 LET F = ("ABC" >= "ABC")
            """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(1.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(1.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(1.0, state.numVar("F"));
  }
}
