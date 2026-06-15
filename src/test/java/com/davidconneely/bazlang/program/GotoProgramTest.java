package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising GOTO statements, branching, and line label matching. */
class GotoProgramTest extends BaseProgramTest {

  @Test
  void testAddressBasedNavigationMissingLabel() {
    // Documented: Target label resolves to next highest.
    String output =
        runProgramCapture(
            """
                10 GOTO 15
                20 PRINT "SKIP"
                30 PRINT "TARGET"
                """);
    // GOTO 15 should jump to 20.
    assertEquals("SKIP\nTARGET\n", output);
  }

  @Test
  void testGoto() {
    runProgram("10 GOTO 30\n20 PRINT \"SKIP\"\n30 PRINT \"END\"", "END\n");
  }

  @Test
  void testJumpBeyondLastLine() {
    runProgram("10 GOTO 20", "");
  }

  @Test
  void testLooseLineLabelMatching() {
    runProgram(
        "1 GOTO 11\n10 PRINT \"Line label 10\"\n20 PRINT \"Line label 20\"", "Line label 20\n");
  }
}
