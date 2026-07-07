package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising comment lines and REM statements. */
class RemProgramTest extends BaseProgramTest {

  @Test
  void testEmptyLinesAndComments() {
    // Shebang first line and blank lines are ignored.
    runProgram(
        """
        #! shebang

        10 PRINT "OK"

        """,
        "OK\n");
  }

  @Test
  void testArbitraryCommentThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(
        com.davidconneely.bazlang.ReportException.class,
        () ->
            runProgram(
                """
            10 PRINT "OK"
            # arbitrary comment
            """));
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
