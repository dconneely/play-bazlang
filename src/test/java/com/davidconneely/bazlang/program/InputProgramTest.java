package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests exercising the INPUT statement, array input, syntax retries, and errors. */
class InputProgramTest extends BaseProgramTest {

  @Test
  void testInputSyntaxErrorRetry() {
    // Test that syntax errors in numeric INPUT re-prompt with error message
    // First input "(1" is a syntax error (unbalanced parens), second input "42" is valid
    String output =
        runProgramCapture(
            """
        10 INPUT X
        20 PRINT X
        """,
            List.of("(1", "42"));

    assertTrue(output.contains("Syntax error in expression"));
    assertTrue(output.contains("42"));
  }

  @Test
  void testInputToArray() {
    // Test INPUT into an array element.
    EvalState state =
        runProgram(
            """
        10 DIM A(10)
        20 INPUT A(5)
        30 DIM B$(5, 10)
        40 INPUT B$(2)
        """,
            List.of("42", "HELLO"));

    assertEquals(42.0, state.numArray("A").data()[4]); // 1-based index 5 is data[4]
    String b2 = ((EvalState.StrVar.Array) state.strVar("B$")).elements()[1].toJavaString();
    assertTrue(b2.startsWith("HELLO"));
  }

  @Test
  void testInputUndefinedVariableEndsProgram() {
    // Undefined variable in INPUT should end program with error, not retry
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
            10 INPUT X
            """,
                List.of("NOTDEF")));
  }
}
