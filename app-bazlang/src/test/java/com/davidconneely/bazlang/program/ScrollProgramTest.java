package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising screen SCROLL statement. */
class ScrollProgramTest extends BaseProgramTest {

  @Test
  void testScroll() {
    runProgram("10 SCROLL", "\n");
  }
}
