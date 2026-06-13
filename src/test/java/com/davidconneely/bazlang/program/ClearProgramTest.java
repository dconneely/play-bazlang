package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

class ClearProgramTest extends BaseProgramTest {

  @Test
  void testPointerOptimizationClear() {
    String source =
        """
        10 LET A = 10
        20 CLEAR
        30 LET A = 20
        40 PRINT A
        """;
    runProgram(source, "20\n");
  }
}
