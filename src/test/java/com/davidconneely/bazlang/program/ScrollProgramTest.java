package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests exercising display SCROLL statement. */
class ScrollProgramTest extends BaseProgramTest {

  @Test
  void testScroll() {
    String output = runProgramCapture("10 SCROLL");
    assertEquals(System.lineSeparator(), output);
  }
}
