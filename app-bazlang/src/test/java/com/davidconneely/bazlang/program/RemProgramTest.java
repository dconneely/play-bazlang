package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising comment lines and REM statements. */
class RemProgramTest extends BaseProgramTest {

  @Test
  void testEmptyLinesAndComments() {
    // Documented: blank lines and # comments are ignored.
    runProgram(
        """
        # comment

        10 PRINT "OK"

        # endLabel
        """,
        "OK\n");
  }

  @Test
  void testRemConsumesRestOfLine() {
    runProgram(
        """
        10 REM PRINT 1 : PRINT 2
        """,
        "");
  }

  @Test
  void testRemStatement() {
    // REM should be ignored
    runProgram(
        """
        10 REM This is a comment
        20 PRINT "OK"
        """,
        "OK\n");
  }
}
