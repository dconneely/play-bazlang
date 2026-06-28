package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising GOTO statements, branching, and line label matching. */
class GotoProgramTest extends BaseProgramTest {

  @Test
  void testAddressBasedNavigationMissingLabel() {
    // Documented: Target label resolves to next highest.
    runProgram(
        """
            10 GOTO 15
            20 PRINT "SKIP"
            30 PRINT "TARGET"
            """,
        "SKIP\nTARGET\n");
  }

  @Test
  void testGoto() {
    runProgram(
        """
        10 GOTO 30
        20 PRINT "SKIP"
        30 PRINT "END\"""",
        "END\n");
  }

  @Test
  void testJumpBeyondLastLine() {
    runProgram("10 GOTO 20", "");
  }

  @Test
  void testLooseLineLabelMatching() {
    runProgram(
        """
        1 GOTO 11
        10 PRINT "Line label 10"
        20 PRINT "Line label 20"
        """,
        "Line label 20\n");
  }
}
