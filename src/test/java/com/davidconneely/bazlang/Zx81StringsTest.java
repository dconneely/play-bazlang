package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Zx81StringsTest {
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  @BeforeEach
  public void setUpStreams() {
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  public void restoreStreams() {
    System.setOut(originalOut);
  }

  private void runProgram(String source) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();
    Interpreter interpreter = new Interpreter();
    interpreter.execute(program);
  }

  @Test
  void testStringArrayCharacterAssignment() {
    String source =
        """
        10 DIM a$(2, 10)
        20 LET a$(1) = "ABCDEFGHIJ"
        30 LET a$(1, 2) = "X"
        40 PRINT a$(1)
        """;
    runProgram(source);
    assertEquals("AXCDEFGHIJ" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testScalarStringCharacterAssignment() {
    String source =
        """
        10 LET a$ = "ABCDE"
        20 LET a$(3) = "X"
        30 PRINT a$
        """;
    runProgram(source);
    assertEquals("ABXDE" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testFixedLengthWholeAssignment() {
    String source =
        """
        10 DIM a$(10)
        20 LET a$="123456789012345"
        30 PRINT a$
        """;
    runProgram(source);
    assertEquals("1234567890" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testDynamicSliceAssignmentPadding() {
    String source =
        """
        10 LET b$="HELLO WORLD"
        20 LET b$(2 TO 4)="A"
        30 PRINT b$
        """;
    runProgram(source);
    assertEquals("HA  O WORLD" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testDynamicSliceAssignmentTruncation() {
    String source =
        """
        10 LET c$="HELLO WORLD"
        20 LET c$(2 TO 4)="123456789"
        30 PRINT c$
        """;
    runProgram(source);
    assertEquals("H123O WORLD" + System.lineSeparator(), outContent.toString());
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
    runProgram(source);
    assertEquals("HA  O     " + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testSliceWithNullStart() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$( TO 5)
        """;
    runProgram(source);
    assertEquals("12345" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testSliceWithNullEnd() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$(5 TO )
        """;
    runProgram(source);
    assertEquals("56789" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testSliceWithBothNull() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$( TO )
        """;
    runProgram(source);
    assertEquals("123456789" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testNumericNamespaceIndependence() {
    String source =
        """
        10 LET a=1
        20 DIM a(5)
        30 LET a(1)=42
        40 PRINT a;a(1)
        """;
    runProgram(source);
    assertEquals("142" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testStringArrayCharacterAccess() {
    String source =
        """
        10 DIM a$(2, 10)
        20 LET a$(1) = "ABCDEFGHIJ"
        30 PRINT a$(1, 3)
        """;
    runProgram(source);
    assertEquals("C" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testNumericArrayBounds() {
    String source =
        """
        10 DIM a(5)
        20 LET a(1) = 1
        30 LET a(5) = 5
        40 PRINT a(1); a(5)
        """;
    runProgram(source);
    assertEquals("15" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testNumericArrayOutOfBoundsLow() {
    String source =
        """
        10 DIM a(5)
        20 PRINT a(0)
        """;
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  @Test
  void testNumericArrayOutOfBoundsHigh() {
    String source =
        """
        10 DIM a(5)
        20 PRINT a(6)
        """;
    assertThrows(ReportException.class, () -> runProgram(source));
  }

  @Test
  void testStringNamespaceConflict() {
    String source =
        """
        10 DIM a$(5,10)
        20 LET a$="HI"
        30 PRINT a$(1);a$(2)
        """;
    runProgram(source);
    // HI followed by 8 spaces, then 10 spaces for the second element.
    assertEquals("HI                  " + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testReferencingNDimensionalWithNMinus1Indices() {
    // 1D: DIM A$(10) -> A$ is 10 chars
    runProgram("10 DIM A$(10)\n20 LET A$=\"HELLO\"\n30 PRINT A$");
    assertEquals("HELLO     " + System.lineSeparator(), outContent.toString());
    outContent.reset();

    // 2D: DIM A$(5, 10) -> A$(3) is 10 chars
    runProgram("10 DIM A$(5, 10)\n20 LET A$(3)=\"WORLD\"\n30 PRINT A$(3)");
    assertEquals("WORLD     " + System.lineSeparator(), outContent.toString());
    outContent.reset();

    // 3D: DIM A$(2, 2, 5) -> A$(1, 2) is 5 chars
    runProgram("10 DIM A$(2, 2, 5)\n20 LET A$(1, 2)=\"HI\"\n30 PRINT A$(1, 2)");
    assertEquals("HI   " + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testVariableLengthStringCharacterIndexing() {
    runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(2)");
    assertEquals("E" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testVariableLengthStringSlicing() {
    runProgram("10 LET A$=\"BAZLANG\"\n20 PRINT A$(4 TO 6)");
    assertEquals("LAN" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testSlicingConstraints() {
    // Correct: slice on last index of character array
    assertDoesNotThrow(() -> runProgram("10 DIM A$(5, 10)\n20 PRINT A$(1, 2 TO 5)"));

    // Correct: slice as sole index of variable string
    assertDoesNotThrow(() -> runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(2 TO 4)"));

    // Incorrect: slice NOT on last index of character array
    assertThrows(
        ReportException.class, () -> runProgram("10 DIM A$(5, 10)\n20 PRINT A$(1 TO 2, 5)"));

    // Incorrect: slice NOT as sole index of variable string
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
