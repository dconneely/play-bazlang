package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.exec.EvalState;
import org.junit.jupiter.api.Test;

class DefFnProgramTest extends BaseProgramTest {

  @Test
  void testDefFnBasic() {
    runProgram(
        """
        10 DEF FN AVERAGE(X, Y) = (X + Y) / 2
        20 PRINT FN AVERAGE(10, 20)
        """,
        "15\n");

    runProgram(
        """
        10 DEF FN CONCAT$(A$, B$) = A$ + B$
        20 PRINT FN CONCAT$("HELLO ", "WORLD")
        """,
        "HELLO WORLD\n");

    runProgram(
        """
        10 DEF FN CONSTANT() = 42
        20 PRINT FN CONSTANT()
        """,
        "42\n");
  }

  @Test
  void testDefFnErrors() {
    // 1. Definition-time type mismatch (numeric name with string expression)
    final var exDefType =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 DEF FN A() = "HELLO"
        """));
    assertEquals(ReportCode.NONSENSE_IN_BASIC, exDefType.reportCode());

    // 2. Definition-time duplicate parameter names
    final var exDefDup =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 DEF FN A(X, X) = X
        """));
    assertEquals(ReportCode.NONSENSE_IN_BASIC, exDefDup.reportCode());

    // 3. Call-time undefined function
    final var exCallUndefined =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 PRINT FN A()
        """));
    assertEquals(ReportCode.FN_WITHOUT_DEF, exCallUndefined.reportCode());

    // 4. Call-time incorrect parameter count
    final var exCount =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 DEF FN A(X) = X
        20 PRINT FN A(1, 2)
        """));
    assertEquals(ReportCode.PARAMETER_ERROR, exCount.reportCode());

    // 5. Call-time type mismatch in parameter (passing string for number)
    final var exType =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 DEF FN A(X) = X
        20 PRINT FN A("HELLO")
        """));
    assertEquals(ReportCode.PARAMETER_ERROR, exType.reportCode());
  }

  @Test
  void testDefFnShadowing() {
    final var state =
        runProgram(
            """
        10 DEF FN ADD(X) = X + Y
        15 DEF FN REPEAT$(A$) = A$ + A$
        20 DEF FN DOUBLE(X) = FN ADD(X) * 2
        25 LET X = 10
        30 LET Y = 5
        35 LET A$ = "GLOBAL"
        40 LET Z = FN ADD(1)
        45 LET B$ = FN REPEAT$("LOCAL")
        50 LET W = FN DOUBLE(2)
        """);
    // Z = 1 + Y = 1 + 5 = 6
    assertEquals(6.0, state.numVar("Z"));
    // Shadowed parameter X must restore to original value 10 after call
    assertEquals(10.0, state.numVar("X"));
    // B$ = "LOCAL" + "LOCAL" = "LOCALLOCAL"
    assertEquals(
        "LOCALLOCAL", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    // Shadowed string parameter A$ must restore to original value "GLOBAL"
    assertEquals("GLOBAL", ((EvalState.StrVar.Scalar) state.strVar("A$")).value().toJavaString());
    // W = FN DOUBLE(2) -> FN ADD(2) * 2 -> (2 + Y) * 2 -> 7 * 2 = 14
    assertEquals(14.0, state.numVar("W"));
  }

  @Test
  void testRecursiveDefFnOutOfMemory() {
    // Recursive DEF FN causes a stack overflow. On a Sinclair ZX Spectrum, this produces "4 Out of
    // memory". BazLang must catch StackOverflowError and surface it as OUT_OF_MEMORY rather than
    // letting the JVM crash the interpreter.
    final var e =
        assertThrows(
            ReportException.class,
            () ->
                runProgramCapture(
                    """
        10 DEF FN F(N) = FN F(N)
        20 PRINT FN F(1)
        """));
    assertEquals(ReportCode.OUT_OF_MEMORY, e.reportCode());
  }
}
