package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising the screen SCROLL statement. */
class ScrollProgramTest extends BaseProgramTest {

  @Test
  void testScrollProducesNewline() {
    // A bare SCROLL should emit exactly one newline.
    runProgram("10 SCROLL", "\n");
  }

  @Test
  void testScrollAfterPrint() {
    // PRINT appends its own newline; SCROLL appends a second one.
    runProgram("10 PRINT \"A\" : SCROLL", "A\n\n");
  }

  @Test
  void testScrollMovesCursorDown() {
    // PRINT "ROW1" produces "ROW1\n", SCROLL adds "\n", PRINT "ROW2" adds "ROW2\n".
    runProgram(
        """
        10 PRINT "ROW1"
        20 SCROLL
        30 PRINT "ROW2"
        """,
        "ROW1\n\nROW2\n");
  }

  @Test
  void testMultipleScrolls() {
    // Three consecutive SCROLLs should produce three newlines.
    runProgram("10 SCROLL : SCROLL : SCROLL", "\n\n\n");
  }
}
