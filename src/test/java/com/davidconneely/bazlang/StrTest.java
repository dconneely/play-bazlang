package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrTest {

  private void runProgram(String source, String expectedOutput) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
    Interpreter interpreter = new Interpreter(state, executor);

    interpreter.execute(program);

    assertEquals(expectedOutput, display.getOutput());
  }

  private void runProgram(String source) {
    // Overloaded for cases where we don't check output immediately in runProgram
    // or when we expect an exception.
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
    Interpreter interpreter = new Interpreter(state, executor);

    interpreter.execute(program);
  }

  @Test
  void testCharArrayCharacterAssignment() {
    String source =
        """
        10 DIM a$(2, 10)
        20 LET a$(1) = "ABCDEFGHIJ"
        30 LET a$(1, 2) = "X"
        40 PRINT a$(1)
        """;
    runProgram(source, "AXCDEFGHIJ" + System.lineSeparator());
  }

  @Test
  void testStrVarCharacterAssignment() {
    String source =
        """
        10 LET a$ = "ABCDE"
        20 LET a$(3) = "X"
        30 PRINT a$
        """;
    runProgram(source, "ABXDE" + System.lineSeparator());
  }

  @Test
  void testCharArrayWholeAssignment() {
    String source =
        """
        10 DIM a$(10)
        20 LET a$="123456789012345"
        30 PRINT a$
        """;
    runProgram(source, "1234567890" + System.lineSeparator());
  }

  @Test
  void testDynamicSliceAssignmentPadding() {
    String source =
        """
        10 LET b$="HELLO WORLD"
        20 LET b$(2 TO 4)="A"
        30 PRINT b$
        """;
    runProgram(source, "HA  O WORLD" + System.lineSeparator());
  }

  @Test
  void testDynamicSliceAssignmentTruncation() {
    String source =
        """
        10 LET c$="HELLO WORLD"
        20 LET c$(2 TO 4)="123456789"
        30 PRINT c$
        """;
    runProgram(source, "H123O WORLD" + System.lineSeparator());
  }

  @Test
  void testArrayElementSliceAssignment() {
    String source =
        """
        10 DIM a$(2, 10)
        15 LET a$(1)="HELLO"
        20 LET a$(1, 2 TO 4)="A"
        30 PRINT a$(1)
        """;
    runProgram(source, "HA  O     " + System.lineSeparator());
  }

  @Test
  void testSliceWithNullStart() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$( TO 5)
        """;
    runProgram(source, "12345" + System.lineSeparator());
  }

  @Test
  void testSliceWithNullEnd() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$(5 TO )
        """;
    runProgram(source, "56789" + System.lineSeparator());
  }

  @Test
  void testSliceWithBothNull() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$( TO )
        """;
    runProgram(source, "123456789" + System.lineSeparator());
  }

  @Test
  void testNumNamespaceIndependence() {
    String source =
        """
        10 LET a=1
        20 DIM a(5)
        30 LET a(1)=42
        40 PRINT a;a(1)
        """;
    runProgram(source, "142" + System.lineSeparator());
  }

  @Test
  void testCharArrayCharacterAccess() {
    String source =
        """
        10 DIM a$(2, 10)
        20 LET a$(1) = "ABCDEFGHIJ"
        30 PRINT a$(1, 3)
        """;
    runProgram(source, "C" + System.lineSeparator());
  }

  @Test
  void testNumArrayBounds() {
    String source =
        """
        10 DIM a(5)
        20 LET a(1) = 1
        30 LET a(5) = 5
        40 PRINT a(1); a(5)
        """;
    runProgram(source, "15" + System.lineSeparator());
  }

  @Test
  void testNumArrayOutOfBoundsLow() {
    String source =
        """
        10 DIM a(5)
        20 PRINT a(0)
        """;
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  @Test
  void testNumArrayOutOfBoundsHigh() {
    String source =
        """
        10 DIM a(5)
        20 PRINT a(6)
        """;
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  @Test
  void testStrNamespaceConflict() {
    String source =
        """
        10 DIM a$(5,10)
        20 LET a$="HI"
        30 PRINT a$(1);a$(2)
        """;
    runProgram(source, "HI                  " + System.lineSeparator());
  }

  @Test
  void testReferencingNDimensionalWithNMinus1Indices() {
    // 1D: DIM A$(10) -> A$ is 10 chars
    runProgram(
        "10 DIM A$(10)\n20 LET A$=\"HELLO\"\n30 PRINT A$", "HELLO     " + System.lineSeparator());

    // 2D: DIM A$(5, 10) -> A$(3) is 10 chars
    runProgram(
        "10 DIM A$(5, 10)\n20 LET A$(3)=\"WORLD\"\n30 PRINT A$(3)",
        "WORLD     " + System.lineSeparator());

    // 3D: DIM A$(2, 2, 5) -> A$(1, 2) is 5 chars
    runProgram(
        "10 DIM A$(2, 2, 5)\n20 LET A$(1, 2)=\"HI\"\n30 PRINT A$(1, 2)",
        "HI   " + System.lineSeparator());
  }

  @Test
  void testStrVarCharacterIndexing() {
    runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(2)", "E" + System.lineSeparator());
  }

  @Test
  void testStrVarSlicing() {
    runProgram("10 LET A$=\"BAZLANG\"\n20 PRINT A$(4 TO 6)", "LAN" + System.lineSeparator());
  }

  @Test
  void testSlicingConstraints() {
    // Correct: slice on last index of character array
    assertDoesNotThrow(() -> runProgram("10 DIM A$(5, 10)\n20 PRINT A$(1, 2 TO 5)"));

    // Correct: slice as sole index of string variable
    assertDoesNotThrow(() -> runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(2 TO 4)"));

    // Incorrect: slice NOT on last index of character array
    assertThrows(
        ReportException.class, () -> runProgram("10 DIM A$(5, 10)\n20 PRINT A$(1 TO 2, 5)"));

    // Incorrect: slice NOT as sole index of string variable
    // (This is incorrect because variable string only takes 1 index or 1 slice)
    assertThrows(
        ReportException.class, () -> runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(1, 2 TO 4)"));
  }

  @Test
  void testTypeMismatchBinaryOperation() {
    String source = "10 PRINT 1 + \"1\"";
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  @Test
  void testTypeMismatchUnaryFunction() {
    String source = "10 PRINT LEN(1)";
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  private void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      org.junit.jupiter.api.Assertions.fail("Expected no exception, but caught: " + t);
    }
  }
}
