package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising the PAUSE, FAST, and SLOW statements. */
class PauseFastSlowProgramTest extends BaseProgramTest {

  @Test
  void testPauseZeroDoesNotCrash() {
    // PAUSE 0 means zero frames (no actual sleep); the subsequent PRINT must succeed.
    runProgram("10 PAUSE 0 : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testFastDoesNotCrash() {
    // FAST is a no-op in tests (MockScreen inherits the default no-op setFastMode).
    runProgram("10 FAST : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testSlowDoesNotCrash() {
    // SLOW is a no-op in tests (MockScreen inherits the default no-op setFastMode).
    runProgram("10 SLOW : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testFastSlowCombination() {
    // FAST and SLOW may appear together without error; both PRINTs must complete.
    runProgram("10 FAST : PRINT \"A\" : SLOW : PRINT \"B\"", "A\nB\n");
  }

  @Test
  void testPauseNegativeIsNoOp() {
    // Negative frame count is clamped to 0 via Math.max(0, ...) in the implementation.
    runProgram("10 PAUSE -5 : PRINT \"OK\"", "OK\n");
  }
}
