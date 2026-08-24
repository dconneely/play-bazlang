package com.davidconneely.bazlang.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link TerminalScreen#frequencyForPitch}, the pure pitch-to-Hz conversion behind {@code
 * BEEP}'s real playback - package-visible specifically so it's unit-testable without an audio
 * device or a real {@code TerminalEngine} (see its own Javadoc).
 */
class TerminalScreenTest {

  @Test
  void pitchZeroIsMiddleC() {
    assertEquals(261.6256, TerminalScreen.frequencyForPitch(0), 0.001);
  }

  @Test
  void pitchNinePositiveIsConcertA() {
    // A4 (concert pitch, 440Hz) is 9 semitones above middle C - a real-world sanity check for the
    // 2^(pitch/12) formula, not just an internal round-trip against itself.
    assertEquals(440.0, TerminalScreen.frequencyForPitch(9), 0.01);
  }

  @Test
  void pitchTwelveIsOneOctaveUp() {
    assertEquals(
        TerminalScreen.frequencyForPitch(0) * 2, TerminalScreen.frequencyForPitch(12), 0.001);
  }

  @Test
  void pitchMinusTwelveIsOneOctaveDown() {
    assertEquals(
        TerminalScreen.frequencyForPitch(0) / 2, TerminalScreen.frequencyForPitch(-12), 0.001);
  }
}
