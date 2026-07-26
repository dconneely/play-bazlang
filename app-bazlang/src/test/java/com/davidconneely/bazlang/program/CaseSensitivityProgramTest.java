package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.exec.EvalState;
import org.junit.jupiter.api.Test;

/** Tests exercising case insensitivity rules for keywords and variables. */
class CaseSensitivityProgramTest extends BaseProgramTest {

  @Test
  void testCaseInsensitiveKeywords() {
    // Keywords should be case-insensitive
    final var state =
        runProgram(
            """
        10 let a = 1
        20 LET b = 2
        30 Let c = 3
        40 IF a = 1 then let d = 4
        50 if b = 2 THEN LET e = 5
        """);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(2.0, state.numVar("B"));
    assertEquals(3.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(5.0, state.numVar("E"));
  }

  @Test
  void testCaseInsensitiveStringVariables() {
    // String variable names should be case-insensitive
    final var state =
        runProgram(
            """
        10 LET name$ = "Hello"
        20 LET NAME$ = NAME$ + " World"
        """);
    assertEquals(
        "Hello World", ((EvalState.StrVar.Scalar) state.strVar("NAME$")).value().toJavaString());
  }

  @Test
  void testCaseInsensitiveVariables() {
    // Variable names should be case-insensitive
    final var state =
        runProgram(
            """
        10 LET myVar = 10
        20 LET MYVAR = MYVAR + 5
        30 LET MyVar = myvar * 2
        """);
    // All refer to same variable, stored as uppercase
    assertEquals(30.0, state.numVar("MYVAR"));
  }

  @Test
  void testCaseInsensitivity() {
    // Documented: Keywords are case-insensitive.
    runProgram(
        """
         10 let A = 1
         20 pRiNt A
         """,
        "1\n");
  }

  @Test
  void testCaseSensitiveStringValues() {
    // String VALUES should remain case-sensitive
    final var state =
        runProgram(
            """
        10 LET a$ = "Hello"
        20 LET b$ = "HELLO"
        30 LET eq = (a$ = b$)
        """);
    assertEquals("Hello", ((EvalState.StrVar.Scalar) state.strVar("A$")).value().toJavaString());
    assertEquals("HELLO", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    assertEquals(0.0, state.numVar("EQ")); // Not equal - case-sensitive
  }
}
