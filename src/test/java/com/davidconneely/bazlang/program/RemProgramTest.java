package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising comment lines and REM statements. */
class RemProgramTest extends BaseProgramTest {

  @Test
  void testEmptyLinesAndComments() {
    // Documented: blank lines and # comments are ignored.
    String output =
        runProgramCapture(
            """
        # comment

        10 PRINT "OK"

        # endLabel
        """);
    assertEquals("OK" + System.lineSeparator(), output);
  }

  @Test
  void testRemConsumesRestOfLine() {
    String output = runProgramCapture("10 REM PRINT 1 : PRINT 2");
    assertEquals("", output);
  }

  @Test
  void testRemStatement() {
    // REM should be ignored
    runProgram("10 REM This is a comment\n20 PRINT \"OK\"", "OK" + System.lineSeparator());
  }
}
