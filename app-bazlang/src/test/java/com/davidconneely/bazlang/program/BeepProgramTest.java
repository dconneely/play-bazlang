package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising the BEEP statement through the full parse-and-execute pipeline. */
class BeepProgramTest extends BaseProgramTest {

  @Test
  void testBeepDoesNotCrash() {
    // BEEP is a no-op in tests (MockScreen inherits VirtualSpeaker's no-op default), matching how
    // FAST/SLOW are already tested (see PauseFastSlowProgramTest).
    runProgram("10 BEEP 0.1, 0 : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testBeepZeroDurationDoesNotCrash() {
    runProgram("10 BEEP 0, 0 : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testBeepNegativeDurationIsNoOp() {
    // Negative duration is clamped to 0, mirroring PAUSE's negative-frame clamping.
    runProgram("10 BEEP -5, 0 : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testBeepAcceptsFractionalPitchAndDuration() {
    runProgram("10 BEEP 0.05, 4.5 : PRINT \"OK\"", "OK\n");
  }
}
