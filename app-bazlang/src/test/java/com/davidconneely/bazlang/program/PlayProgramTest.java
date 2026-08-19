package com.davidconneely.bazlang.program;

import org.junit.jupiter.api.Test;

/** Tests exercising the PLAY/APLAY statements through the full parse-and-execute pipeline. */
class PlayProgramTest extends BaseProgramTest {

  @Test
  void testPlaySingleChannelDoesNotCrash() {
    // PLAY is silent in tests (MockScreen inherits VirtualSpeaker's no-op default), matching how
    // BEEP is already tested (see BeepProgramTest) — but StatementExecutor's own timing/BREAK-poll
    // loop still runs for real, so this exercises the full DSL parse + playback-loop pipeline.
    runProgram("10 PLAY \"1c1d1e\" : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testPlayThreeChannelsDoesNotCrash() {
    runProgram("10 PLAY \"1c\", \"1e\", \"1g\" : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testAplayReturnsImmediately() {
    runProgram("10 APLAY \"9c\" : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testPlayWithFullDslDoesNotCrash() {
    // Exercises octave, sharps/flats, rest, tempo, volume, envelope, mixer, repeat, comment, and
    // halt all in one string.
    runProgram("10 PLAY \"T240O4V15UW2X500M7!intro!(1c1#d1$e1&)H\" : PRINT \"OK\"", "OK\n");
  }

  @Test
  void testAplayDashPlaceholderTargetsAPartialUpdate() {
    // APLAY "-", "-", effect$ updates only channel C, leaving A/B's background music running --
    // and APLAY "H", "H", effect$ is the idiom for stopping it outright (e.g. at game end).
    runProgram(
        "10 APLAY \"9c\", \"9d\" : APLAY \"-\", \"-\", \"1c\" : APLAY \"H\", \"H\", \"1c\" "
            + ": PRINT \"OK\"",
        "OK\n");
  }
}
