package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising multi-statement lines using ':' as separator. */
class MultiStatementProgramTest extends BaseProgramTest {

  @Test
  void testMultiStatementLines() {
    runProgram("10 PRINT 1 : PRINT 2", "1\n2\n");
  }
}
