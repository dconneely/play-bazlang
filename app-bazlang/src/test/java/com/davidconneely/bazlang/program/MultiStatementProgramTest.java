package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising multi-statement lines using ':' as separator. */
class MultiStatementProgramTest extends BaseProgramTest {

  @Test
  void testMultiStatementLines() {
    runProgram("10 PRINT 1 : PRINT 2", "1\n2\n");
  }

  @Test
  void testMultiStatementSequentialExecution() {
    // Three statements on one line should execute in order.
    runProgram("10 PRINT 1 : PRINT 2 : PRINT 3", "1\n2\n3\n");
  }

  @Test
  void testMultiStatementWithIf() {
    // Spectrum semantics: when IF is true, all remaining statements on the line execute.
    runProgram("10 LET X = 5 : IF X > 3 THEN PRINT \"BIG\" : PRINT \"END\"", "BIG\nEND\n");
  }

  @Test
  void testMultiStatementIfFalseSkipsLine() {
    // When IF is false, the rest of the line is skipped; execution resumes at the next line.
    runProgram(
        """
        10 IF 0 THEN PRINT "A" : PRINT "B"
        20 PRINT "C"
        """,
        "C\n");
  }

  @Test
  void testMultiStatementGotoTerminatesRemainder() {
    // GOTO within a multi-statement line skips the remaining statements on that line.
    runProgram(
        """
        10 PRINT "A" : GOTO 30 : PRINT "B"
        20 PRINT "C"
        30 PRINT "D"
        """,
        "A\nD\n");
  }

  @Test
  void testMultiStatementLetAndPrint() {
    // Multiple LET statements followed by a PRINT that uses earlier values.
    runProgram("10 LET A = 1 : LET B = 2 : LET C = A + B : PRINT C", "3\n");
  }
}
