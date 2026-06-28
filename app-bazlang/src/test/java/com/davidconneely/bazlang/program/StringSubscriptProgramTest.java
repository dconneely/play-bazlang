package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising string variable subscript character indexing and string array elements. */
class StringSubscriptProgramTest extends BaseProgramTest {

  @Test
  void testAssignMultiDimArrayWithoutSubscriptThrows() {
    String source =
        """
            10 DIM a$(5,10)
            20 LET a$="HI"
            """;
    ReportException e = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.SUBSCRIPT_WRONG, e.reportCode());
  }

  @Test
  void testCharArrayCharacterAccess() {
    String source =
        """
            10 DIM a$(2, 10)
            15 LET a$(1) = "ABCDEFGHIJ"
            20 PRINT a$(1, 3)
            """;
    runProgram(source, "C\n");
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
    runProgram(source, "AXCDEFGHIJ\n");
  }

  @Test
  void testCharArrayCharacterIndexOutOfBounds() {
    // Character index beyond element width
    assertThrows(
        ReportException.class,
        () -> runProgram("10 DIM A$(3, 5)\n20 LET A$(1)=\"HELLO\"\n30 PRINT A$(1, 10)"));
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
  void testCharArrayWholeAssignment() {
    String source =
        """
            10 DIM a$(10)
            20 LET a$="123456789012345"
            30 PRINT a$
            """;
    runProgram(source, "1234567890\n");
  }

  @Test
  void testEmptyStringOperations() {
    // Empty string LEN
    runProgram("10 LET A$=\"\"\n20 PRINT LEN(A$)", "0\n");
  }

  @Test
  void testReadMultiDimArrayWithoutSubscriptThrows() {
    String source =
        """
            10 DIM a$(2,5)
            20 LET a$(1)="HELLO"
            30 LET a$(2)="THERE"
            40 PRINT a$
            """;
    ReportException e = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.SUBSCRIPT_WRONG, e.reportCode());
  }

  @Test
  void testReferencingNDimensionalWithNMinus1Indices() {
    // 1D: DIM A$(10) -> A$ is 10 chars
    runProgram("10 DIM A$(10)\n20 LET A$=\"HELLO\"\n30 PRINT A$", "HELLO     \n");

    // 2D: DIM A$(5, 10) -> A$(3) is 10 chars
    runProgram("10 DIM A$(5, 10)\n20 LET A$(3)=\"WORLD\"\n30 PRINT A$(3)", "WORLD     \n");

    // 3D: DIM A$(2, 2, 5) -> A$(1, 2) is 5 chars
    runProgram("10 DIM A$(2, 2, 5)\n20 LET A$(1, 2)=\"HI\"\n30 PRINT A$(1, 2)", "HI   \n");

    // 4D: DIM A$(2, 2, 2, 5) -> A$(1, 2, 1) is 5 chars
    runProgram(
        "10 DIM A$(2, 2, 2, 5)\n20 LET A$(1, 2, 1)=\"FOUR\"\n30 PRINT A$(1, 2, 1)", "FOUR \n");
  }

  @Test
  void testStrVarCharacterAssignment() {
    String source =
        """
            10 LET a$ = "ABCDE"
            20 LET a$(3) = "X"
            30 PRINT a$
            """;
    runProgram(source, "ABXDE\n");
  }

  @Test
  void testStrVarCharacterIndexing() {
    runProgram("10 LET A$=\"HELLO\"\n20 PRINT A$(2)", "E\n");
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
    runProgram(source, "XELLO\n");
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
    runProgram(source, "XELLO\n");
  }

  @Test
  void testStringConcatenation() {
    runProgram("10 LET A$ = \"HELLO\" + \" \" + \"WORLD\"\n20 PRINT A$", "HELLO WORLD\n");
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
  void testSinclairZxBasicMonthsArray() {
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
    runProgram(source, "FEB\nFE\n");
  }

  @Test
  void testVariableStringBuilding() {
    runProgram(
        """
        10 LET A$=""
        20 LET A$=A$+"Z"
        30 LET A$=A$+"X"
        40 LET A$=A$+"8"
        50 LET A$=A$+"1"
        60 PRINT A$
        """,
        "ZX81\n");
  }

  @Test
  void testZxSpectrumAdventureMap3DArray() {
    runProgram(
        """
        10 DIM M$(2, 2, 5)
        20 LET M$(1, 1)="TREES"
        30 LET M$(1, 2)="WATER"
        40 LET M$(2, 1)="PATH "
        50 LET M$(2, 2)="CAVE "
        60 PRINT M$(1, 2)
        70 PRINT M$(2, 1, 1 TO 4)
        """,
        "WATER\nPATH\n");
  }

  @Test
  void testFlyweightStringAssignmentIsolation() {
    runProgram(
        """
        10 DIM A$(2, 5)
        20 LET A$(1) = "HELLO"
        30 LET A$(2) = "WORLD"
        40 LET T$ = A$(1)
        50 LET A$(1) = A$(2)
        60 PRINT T$;" ";A$(1)
        """,
        "HELLO WORLD\n");
  }

  @Test
  void testFlyweightStringMathZeroCopy() {
    runProgram(
        """
        10 DIM A$(2, 5)
        20 LET A$(1) = "APPLE"
        30 LET A$(2) = "ZOO"
        40 IF A$(1) < A$(2) THEN PRINT "OK"
        """,
        "OK\n");
  }
}
