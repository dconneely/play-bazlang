package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising variable assignments and the LET statement keyword mandatory rules. */
class LetProgramTest extends BaseProgramTest {

  @Test
  void testLetKeywordMandatory() {
    // ZX81: LET cannot be omitted
    assertThrows(ReportException.class, () -> runProgram("10 A = 5"));
  }

  @Test
  void testPointerOptimizationVariableReassignment() {
    String source =
        """
            10 LET A = 0
            20 FOR I = 1 TO 5
            30 LET A = A + I
            40 NEXT I
            50 PRINT A
            """;
    runProgram(source, "15\n");
  }
}
