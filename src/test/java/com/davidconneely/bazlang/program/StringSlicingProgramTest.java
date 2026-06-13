package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising substring slicing, slice constraints, and bounds. */
class StringSlicingProgramTest extends BaseProgramTest {

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
  void testCharArraySliceAssignmentOutOfBounds() {
    // Slice assignment with out-of-bounds end - throws error
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                "10 DIM A$(5)\n20 LET A$=\"12345\"\n30 LET A$(3 TO 10)=\"ABCDEFGH\"\n40 PRINT A$"));
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
  void testSingleCharacterStringSlice() {
    // Single character accessed as slice
    runProgram("10 LET A$=\"X\"\n20 PRINT A$(1 TO 1)", "X" + System.lineSeparator());
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
  void testSliceWithNullEnd() {
    String source =
        """
        10 LET a$="123456789"
        20 PRINT a$(5 TO )
        """;
    runProgram(source, "56789" + System.lineSeparator());
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
  void testStrVarSlicing() {
    runProgram("10 LET A$=\"BAZLANG\"\n20 PRINT A$(4 TO 6)", "LAN" + System.lineSeparator());
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
  void testStringSliceOutOfBounds() {
    // Slice end beyond string length - throws error
    assertThrows(ReportException.class, () -> runProgram("10 LET A$=\"HI\"\n20 PRINT A$(1 TO 10)"));
  }

  @Test
  void testStringSliceReversedIndices() {
    // Start > End should return empty or error
    assertThrows(
        ReportException.class, () -> runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(4 TO 2)"));
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
  void testFlyweightStringArrayModification() {
    String source =
        """
        10 DIM A$(1, 5)
        20 LET A$(1) = "ABCDE"
        30 LET A$(1, 2 TO 4) = "XYZ"
        40 PRINT A$(1)
        """;
    runProgram(source, "AXYZE\n");
  }
}
