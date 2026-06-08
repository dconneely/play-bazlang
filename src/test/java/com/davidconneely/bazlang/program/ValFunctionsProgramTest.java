package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising built-in VAL and VAL$ expression evaluation functions. */
class ValFunctionsProgramTest extends BaseProgramTest {

  @Test
  void testValAndStrFunctions() {
    runProgram(
        "10 PRINT VAL(\"123.4\") + 1\n20 PRINT STR$(42)",
        "124.4" + System.lineSeparator() + "42" + System.lineSeparator());
  }

  @Test
  void testValEvaluatesExpression() {
    // ZX81: VAL evaluates a string as a numeric expression
    String output =
        runProgramCapture(
            """
        10 PRINT VAL "6-4"
        20 PRINT VAL "2*3+1"
        30 PRINT VAL (STR$ LEN "123456" + "-4")
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("2", lines[0]); // 6-4 = 2
    assertEquals("7", lines[1]); // 2*3+1 = 7
    assertEquals("2", lines[2]); // STR$ 6 + "-4" = "6-4" -> 6-4 = 2
  }

  @Test
  void testValRejectsPartialExpression() {
    // VAL("1 2") must throw — "1" is a complete expression but "2" is trailing garbage
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
                10 LET A = VAL("1 2")
                """));
  }

  @Test
  void testValRejectsTrailingGarbage() {
    // VAL("1+2JUNK") must throw, not silently return 3
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
                10 LET A = VAL("1+2JUNK")
                """));
  }

  @Test
  void testValString() {
    String sourceWithLine50 =
        """
        10 LET A$ = "Fruit punch"
        20 LET B$ = "A$"
        30 LET C$ = "(1 TO 5)"
        40 LET D$ = "B$ + C$"
        50 LET R$ = VAL$(D$)
        55 LET T$ = VAL$(R$)
        60 LET S$ = VAL$(""\"hello"" + "" world\""")
        """;
    EvalState state = runProgram(sourceWithLine50);
    assertEquals(
        "A$(1 TO 5)", ((EvalState.StrVar.Scalar) state.strVar("R$")).value().toJavaString());
    assertEquals("Fruit", ((EvalState.StrVar.Scalar) state.strVar("T$")).value().toJavaString());
    assertEquals(
        "hello world", ((EvalState.StrVar.Scalar) state.strVar("S$")).value().toJavaString());

    // Test syntax error in VAL$ (numeric expression inside VAL$)
    String invalidSource = "10 LET A$ = VAL$(\"123\")";
    assertThrows(ReportException.class, () -> runProgram(invalidSource));
  }
}
