package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private void runProgram(String source, String expectedOutput) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    interpreter.execute(program);

    assertEquals(expectedOutput, display.getOutput());
  }

  private void runProgram(String source) {
    // Overloaded for cases where we don't check output immediately in runProgram
    // or when we expect an exception.
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramManager executor = new ProgramManager(state, display);
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

  @Test
  void testMultiDimCharArraySliceAssignment() {
    // 2D character array: assign to slice of element
    String source =
        """
        10 DIM A$(3, 10)
        20 LET A$(2) = "ABCDEFGHIJ"
        30 LET A$(2, 3 TO 6) = "XY"
        40 PRINT A$(2)
        """;
    runProgram(source, "ABXY  GHIJ" + System.lineSeparator());
  }

  @Test
  void testCharArraySliceRead() {
    // Read a slice from a character array element
    String source =
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "HELLO WRLD"
        30 PRINT A$(1, 1 TO 5)
        """;
    runProgram(source, "HELLO" + System.lineSeparator());
  }

  @Test
  void testCharArraySliceWithOpenEnd() {
    // Slice with open end (TO end of string)
    String source =
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "ABCDEFGHIJ"
        30 PRINT A$(1, 6 TO )
        """;
    runProgram(source, "FGHIJ" + System.lineSeparator());
  }

  @Test
  void testCharArraySliceWithOpenStart() {
    // Slice with open start (from beginning) - uses explicit "1 TO 3"
    String source =
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "ABCDEFGHIJ"
        30 PRINT A$(1, 1 TO 3)
        """;
    runProgram(source, "ABC" + System.lineSeparator());
  }

  @Test
  void testStringConcatenation() {
    runProgram(
        "10 LET A$ = \"HELLO\" + \" \" + \"WORLD\"\n20 PRINT A$",
        "HELLO WORLD" + System.lineSeparator());
  }

  @Test
  void testStringSliceAssignmentWithConcatenation() {
    // Assign concatenated string to slice
    String source =
        """
        10 LET A$ = "XXXXXXXXXXXX"
        20 LET A$(3 TO 9) = "HI" + " " + "THERE"
        30 PRINT A$
        """;
    runProgram(source, "XXHI THERXXX" + System.lineSeparator());
  }

  @Test
  void testStringIndexOutOfBoundsHigh() {
    // Accessing beyond string length
    assertThrows(ReportException.class, () -> runProgram("10 LET A$=\"HI\"\n20 PRINT A$(5)"));
  }

  @Test
  void testStringIndexOutOfBoundsZero() {
    // Index 0 is invalid (1-based indexing)
    assertThrows(ReportException.class, () -> runProgram("10 LET A$=\"HI\"\n20 PRINT A$(0)"));
  }

  @Test
  void testStringSliceOutOfBounds() {
    // Slice end beyond string length - throws error
    assertThrows(ReportException.class, () -> runProgram("10 LET A$=\"HI\"\n20 PRINT A$(1 TO 10)"));
  }

  @Test
  void testEmptyStringOperations() {
    // Empty string LEN
    runProgram("10 LET A$=\"\"\n20 PRINT LEN(A$)", "0" + System.lineSeparator());
  }

  @Test
  void testSingleCharacterStringSlice() {
    // Single character accessed as slice
    runProgram("10 LET A$=\"X\"\n20 PRINT A$(1 TO 1)", "X" + System.lineSeparator());
  }

  @Test
  void testCharArrayIndexOutOfBoundsHigh() {
    // Accessing beyond array bounds
    assertThrows(ReportException.class, () -> runProgram("10 DIM A$(3, 5)\n20 PRINT A$(5)"));
  }

  @Test
  void testCharArrayIndexOutOfBoundsZero() {
    // Index 0 is invalid
    assertThrows(ReportException.class, () -> runProgram("10 DIM A$(3, 5)\n20 PRINT A$(0)"));
  }

  @Test
  void testCharArrayCharacterIndexOutOfBounds() {
    // Character index beyond element width
    assertThrows(
        ReportException.class,
        () -> runProgram("10 DIM A$(3, 5)\n20 LET A$(1)=\"HELLO\"\n30 PRINT A$(1, 10)"));
  }

  @Test
  void testStringAssignmentToIndex() {
    // Assign single character to string index
    String source =
        """
        10 LET A$="HELLO"
        20 LET A$(1)="X"
        30 PRINT A$
        """;
    runProgram(source, "XELLO" + System.lineSeparator());
  }

  @Test
  void testStringAssignmentToIndexMultiChar() {
    // Assign multi-char to single index - only first char used
    String source =
        """
        10 LET A$="HELLO"
        20 LET A$(1)="XYZ"
        30 PRINT A$
        """;
    runProgram(source, "XELLO" + System.lineSeparator());
  }

  @Test
  void testCharArraySliceAssignmentOutOfBounds() {
    // Slice assignment with out-of-bounds end - throws error
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                "10 DIM A$(5)\n20 LET A$=\"12345\"\n30 LET A$(3 TO 10)=\"ABCDEFGH\"\n40 PRINT A$"));
  }

  @Test
  void testStringSliceReversedIndices() {
    // Start > End should return empty or error
    assertThrows(
        ReportException.class, () -> runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(4 TO 2)"));
  }

  @Test
  void testNumericArraySliceNotAllowed() {
    // Slices not allowed on numeric arrays
    assertThrows(ReportException.class, () -> runProgram("10 DIM A(5)\n20 PRINT A(1 TO 3)"));
  }

  @Test
  void testZx81MonthsArray() {
    String source =
        """
        10 DIM M$(12, 3)
        20 LET M$(1)="JAN"
        30 LET M$(2)="FEBRUARY"
        40 LET M$(3)="MAR"
        50 PRINT M$(2)
        60 PRINT M$(2, 1 TO 2)
        """;
    // "FEBRUARY" is truncated to 3 characters: "FEB"
    // "FEB" sliced 1 TO 2 is "FE"
    runProgram(source, "FEB" + System.lineSeparator() + "FE" + System.lineSeparator());
  }

  @Test
  void testZxSpectrumAdventureMap3DArray() {
    String source =
        """
        10 DIM M$(2, 2, 5)
        20 LET M$(1, 1)="TREES"
        30 LET M$(1, 2)="WATER"
        40 LET M$(2, 1)="PATH "
        50 LET M$(2, 2)="CAVE "
        60 PRINT M$(1, 2)
        70 PRINT M$(2, 1, 1 TO 4)
        """;
    runProgram(source, "WATER" + System.lineSeparator() + "PATH" + System.lineSeparator());
  }

  @Test
  void testZxSpectrumScrollingMessage() {
    String source =
        """
        10 LET A$="HELLO WORLD "
        20 LET A$=A$(2 TO ) + A$(1)
        30 PRINT A$
        40 LET A$=A$(2 TO ) + A$(1)
        50 PRINT A$
        """;
    runProgram(
        source, "ELLO WORLD H" + System.lineSeparator() + "LLO WORLD HE" + System.lineSeparator());
  }

  @Test
  void testZx81VariableStringBuilding() {
    String source =
        """
        10 LET A$=""
        20 LET A$=A$+"Z"
        30 LET A$=A$+"X"
        40 LET A$=A$+"8"
        50 LET A$=A$+"1"
        60 PRINT A$
        """;
    runProgram(source, "ZX81" + System.lineSeparator());
  }
}
