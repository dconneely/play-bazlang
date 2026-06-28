package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising substring slicing, slice constraints, and bounds. */
class StringSlicingProgramTest extends BaseProgramTest {

  @Test
  void testArrayElementSliceAssignment() {
    runProgram(
        """
        10 DIM a$(2, 10)
        15 LET a$(1)="HELLO"
        20 LET a$(1, 2 TO 4)="A"
        30 PRINT a$(1)
        """,
        "HA  O     \n");
  }

  @Test
  void testCharArraySliceAssignmentOutOfBounds() {
    // Slice assignment with out-of-bounds end - throws error
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 DIM A$(5)
        20 LET A$="12345"
        30 LET A$(3 TO 10)="ABCDEFGH"
        40 PRINT A$"""));
  }

  @Test
  void testCharArraySliceRead() {
    // Read a slice from a character array element
    runProgram(
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "HELLO WRLD"
        30 PRINT A$(1, 1 TO 5)
        """,
        "HELLO\n");
  }

  @Test
  void testCharArraySliceWithOpenEnd() {
    // Slice with open end (TO end of string)
    runProgram(
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "ABCDEFGHIJ"
        30 PRINT A$(1, 6 TO )
        """,
        "FGHIJ\n");
  }

  @Test
  void testCharArraySliceWithOpenStart() {
    // Slice with open start (from beginning) - uses explicit "1 TO 3"
    runProgram(
        """
        10 DIM A$(2, 10)
        20 LET A$(1) = "ABCDEFGHIJ"
        30 PRINT A$(1, 1 TO 3)
        """,
        "ABC\n");
  }

  @Test
  void testDynamicSliceAssignmentPadding() {
    runProgram(
        """
        10 LET b$="HELLO WORLD"
        20 LET b$(2 TO 4)="A"
        30 PRINT b$
        """,
        "HA  O WORLD\n");
  }

  @Test
  void testDynamicSliceAssignmentTruncation() {
    runProgram(
        """
        10 LET c$="HELLO WORLD"
        20 LET c$(2 TO 4)="123456789"
        30 PRINT c$
        """,
        "H123O WORLD\n");
  }

  @Test
  void testSingleCharacterStringSlice() {
    // Single character accessed as slice
    runProgram(
        """
        10 LET A$="X"
        20 PRINT A$(1 TO 1)""",
        "X\n");
  }

  @Test
  void testSliceWithBothNull() {
    runProgram(
        """
        10 LET a$="123456789"
        20 PRINT a$( TO )
        """,
        "123456789\n");
  }

  @Test
  void testSliceWithNullEnd() {
    runProgram(
        """
        10 LET a$="123456789"
        20 PRINT a$(5 TO )
        """,
        "56789\n");
  }

  @Test
  void testSliceWithNullStart() {
    runProgram(
        """
        10 LET a$="123456789"
        20 PRINT a$( TO 5)
        """,
        "12345\n");
  }

  @Test
  void testSlicingConstraints() {
    // Correct: slice on last index of character array
    assertDoesNotThrow(
        () ->
            runProgram(
                """
        10 DIM A$(5, 10)
        20 PRINT A$(1, 2 TO 5)
        """));

    // Correct: slice as sole index of string variable
    assertDoesNotThrow(
        () ->
            runProgram(
                """
        10 LET A$="HELLO"
        20 PRINT A$(2 TO 4)
        """));

    // Incorrect: slice NOT on last index of character array
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 DIM A$(5, 10)
        20 PRINT A$(1 TO 2, 5)
        """));

    // Incorrect: slice NOT as sole index of string variable
    // (This is incorrect because variable string only takes 1 index or 1 slice)
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 LET A$="HELLO"
        20 PRINT A$(1, 2 TO 4)
        """));
  }

  @Test
  void testStrVarSlicing() {
    runProgram(
        """
        10 LET A$="BAZLANG"
        20 PRINT A$(4 TO 6)
        """,
        "LAN\n");
  }

  @Test
  void testStringSliceAssignmentWithConcatenation() {
    // Assign concatenated string to slice
    runProgram(
        """
        10 LET A$ = "XXXXXXXXXXXX"
        20 LET A$(3 TO 9) = "HI" + " " + "THERE"
        30 PRINT A$
        """,
        "XXHI THERXXX\n");
  }

  @Test
  void testStringSliceOutOfBounds() {
    // Slice end beyond string length - throws error
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 LET A$="HI"
        20 PRINT A$(1 TO 10)
        """));
  }

  @Test
  void testStringSliceReversedIndices() {
    // Start > End should return empty or error
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 LET A$="HELLO"
        20 PRINT A$(4 TO 2)
        """));
  }

  @Test
  void testScrollingMessage() {
    runProgram(
        """
        10 LET A$="HELLO WORLD "
        20 LET A$=A$(2 TO ) + A$(1)
        30 PRINT A$
        40 LET A$=A$(2 TO ) + A$(1)
        50 PRINT A$
        """,
        "ELLO WORLD H\nLLO WORLD HE\n");
  }

  @Test
  void testFlyweightStringArrayModification() {
    runProgram(
        """
        10 DIM A$(1, 5)
        20 LET A$(1) = "ABCDE"
        30 LET A$(1, 2 TO 4) = "XYZ"
        40 PRINT A$(1)
        """,
        "AXYZE\n");
  }
}
