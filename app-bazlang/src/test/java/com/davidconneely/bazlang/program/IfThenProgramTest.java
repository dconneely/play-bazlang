package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising IF-THEN conditional branching. */
class IfThenProgramTest extends BaseProgramTest {

  @Test
  void testIfStatement() {
    runProgram(
        """
        10 LET a = 1
        20 IF a = 1 THEN PRINT "Y"
        30 IF a = 0 THEN PRINT "N"
        """,
        "Y\n");
  }

  @Test
  void testIfThenConsumesRestOfLine() {
    runProgram("10 IF 0 = 1 THEN PRINT 1 : PRINT 2", "");

    runProgram("10 IF 1 = 1 THEN PRINT 1 : PRINT 2", "1\n2\n");
  }
}
