package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
