package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising multi-statement lines using ':' as separator. */
class MultiStatementProgramTest extends BaseProgramTest {

  @Test
  void testMultiStatementLines() {
    String output = runProgramCapture("10 PRINT 1 : PRINT 2");
    assertEquals("1\n2\n", output);
  }
}
