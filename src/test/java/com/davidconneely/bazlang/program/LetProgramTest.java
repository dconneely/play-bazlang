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
}
