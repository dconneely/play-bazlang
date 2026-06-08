package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising IF-THEN conditional branching. */
class IfThenProgramTest extends BaseProgramTest {

  @Test
  void testIfStatement() {
    runProgram(
        "10 LET a = 1\n20 IF a = 1 THEN PRINT \"Y\"\n30 IF a = 0 THEN PRINT \"N\"",
        "Y" + System.lineSeparator());
  }

  @Test
  void testIfThenConsumesRestOfLine() {
    String output = runProgramCapture("10 IF 0 = 1 THEN PRINT 1 : PRINT 2");
    assertEquals("", output);

    output = runProgramCapture("10 IF 1 = 1 THEN PRINT 1 : PRINT 2");
    assertEquals("1\n2\n", output.replace(System.lineSeparator(), "\n"));
  }
}
