package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

class DataReadProgramTest extends BaseProgramTest {

  @Test
  void testDataReadBasic() {
    EvalState state =
        runProgram(
            """
                10 DATA 10, "HELLO", 20, BIN 1010
                20 READ A, B$, C, D
                """);
    assertEquals(10.0, state.numVar("A"));
    assertEquals("HELLO", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    assertEquals(20.0, state.numVar("C"));
    assertEquals(10.0, state.numVar("D"));
  }

  @Test
  void testDataReadExpressions() {
    EvalState state =
        runProgram(
            """
                10 DATA X + 5, A$ + "WORLD"
                20 LET X = 10
                30 LET A$ = "HELLO "
                40 READ Y, B$
                """);
    assertEquals(15.0, state.numVar("Y"));
    assertEquals(
        "HELLO WORLD", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
  }

  @Test
  void testDataReadOutOfData() {
    ReportException ex =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
                    10 DATA 42
                    20 READ A, B
                    """));
    assertEquals(ReportCode.OUT_OF_DATA, ex.reportCode());
  }

  @Test
  void testDataReadRestore() {
    EvalState state =
        runProgram(
            """
                10 DATA 1, 2
                20 DATA 3, 4
                30 READ A, B
                40 RESTORE 20
                50 READ C, D
                60 RESTORE
                70 READ E
                """);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(2.0, state.numVar("B"));
    assertEquals(3.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
  }

  @Test
  void testDataReadSpectrumExample() {
    String output =
        runProgramCapture(
            """
                10 LET A$="ABC"
                20 DATA A$, "DEF"
                30 READ X$, Y$
                40 PRINT X$, Y$
                """);
    assertEquals("ABC             DEF\n", output);
  }

  @Test
  void testDataReadTypeMismatch() {
    ReportException ex1 =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
                    10 DATA "HELLO"
                    20 READ A
                    """));
    assertEquals(ReportCode.NONSENSE_IN_BASIC, ex1.reportCode());

    ReportException ex2 =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
                    10 DATA 42
                    20 READ A$
                    """));
    assertEquals(ReportCode.NONSENSE_IN_BASIC, ex2.reportCode());
  }
}
