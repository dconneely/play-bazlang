package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising numeric arrays, index subscripts, and namespace separation. */
class NumericArrayProgramTest extends BaseProgramTest {

  @Test
  void testNumArrayBounds() {
    runProgram(
        """
        10 DIM a(5)
        20 LET a(1) = 1
        30 LET a(5) = 5
        40 PRINT a(1); a(5)
        """,
        "15\n");
  }

  @Test
  void testNumArrayOutOfBoundsHigh() {
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 DIM a(5)
        20 PRINT a(6)
        """));
  }

  @Test
  void testNumArrayOutOfBoundsLow() {
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 DIM a(5)
        20 PRINT a(0)
        """));
  }

  @Test
  void testNumNamespaceIndependence() {
    runProgram(
        """
        10 LET a=1
        20 DIM a(5)
        30 LET a(1)=42
        40 PRINT a;a(1)
        """,
        "142\n");
  }

  @Test
  void testNumericArraySliceNotAllowed() {
    // Slices not allowed on numeric arrays
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
        10 DIM A(5)
        20 PRINT A(1 TO 3)"""));
  }

  @Test
  void testNumArrayDefaultsToZero() {
    // After DIM, every element should be initialised to 0.
    runProgram(
        """
        10 DIM A(3)
        20 PRINT A(1); A(2); A(3)
        """,
        "000\n");
  }

  @Test
  void testTwoDimensionalArray() {
    // Set one cell and verify a neighbouring cell stays at 0.
    runProgram(
        """
        10 DIM A(3,4)
        20 LET A(2,3) = 99
        30 PRINT A(2,3); A(1,1)
        """,
        "990\n");
  }

  @Test
  void testTwoDimensionalArrayOutOfBounds() {
    // Accessing row 4 of a DIM A(3,4) should raise SUBSCRIPT_WRONG.
    final var ex =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
            10 DIM A(3,4)
            20 PRINT A(4,1)
            """));
    assertEquals(ReportCode.SUBSCRIPT_WRONG, ex.reportCode());
  }

  @Test
  void testReDimArray() {
    // Re-dimensioning an array resets all elements to 0.
    runProgram(
        """
        10 DIM A(3)
        20 LET A(2) = 42
        30 DIM A(5)
        40 PRINT A(2); A(5)
        """,
        "00\n");
  }

  @Test
  void testArrayInForLoop() {
    // Use a numeric array as an accumulator in a FOR loop.
    runProgram(
        """
        10 DIM S(3)
        20 FOR I = 1 TO 3
        30 LET S(I) = I * 2
        40 NEXT I
        50 PRINT S(1); S(2); S(3)
        """,
        "246\n");
  }

  @Test
  void testOutOfBoundsReportCode() {
    // Verify that an out-of-bounds access raises specifically SUBSCRIPT_WRONG.
    final var ex =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
            10 DIM A(5)
            20 PRINT A(6)
            """));
    assertEquals(ReportCode.SUBSCRIPT_WRONG, ex.reportCode());
  }
}
