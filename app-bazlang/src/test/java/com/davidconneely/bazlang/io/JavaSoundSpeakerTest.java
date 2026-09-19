package com.davidconneely.bazlang.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link JavaSoundSpeaker#frequencyForPitch}, {@link JavaSoundSpeaker#squareWaveAverage}, and
 * {@link JavaSoundSpeaker#applyBeepEdgeFilter} - pure math behind {@code BEEP}/{@code PLAY} real
 * playback - package-visible specifically so they're unit-testable without an audio device (see
 * their own Javadoc).
 */
class JavaSoundSpeakerTest {

  @Test
  void pitchZeroIsMiddleC() {
    assertEquals(261.6256, JavaSoundSpeaker.frequencyForPitch(0), 0.001);
  }

  @Test
  void pitchNinePositiveIsConcertA() {
    // A4 (concert pitch, 440Hz) is 9 semitones above middle C - a real-world sanity check for the
    // 2^(pitch/12) formula, not just an internal round-trip against itself.
    assertEquals(440.0, JavaSoundSpeaker.frequencyForPitch(9), 0.01);
  }

  @Test
  void pitchTwelveIsOneOctaveUp() {
    assertEquals(
        JavaSoundSpeaker.frequencyForPitch(0) * 2, JavaSoundSpeaker.frequencyForPitch(12), 0.001);
  }

  @Test
  void pitchMinusTwelveIsOneOctaveDown() {
    assertEquals(
        JavaSoundSpeaker.frequencyForPitch(0) / 2, JavaSoundSpeaker.frequencyForPitch(-12), 0.001);
  }

  @Test
  void squareWaveAverageMatchesNaivePointSamplingWhenNoTransitionFallsMidSample() {
    // period=8, half=4: sample 0 sits entirely in the high half, sample 4 entirely in the low half
    // (the boundary itself) - both match what plain point-sampling would have given.
    assertEquals(1.0, JavaSoundSpeaker.squareWaveAverage(0, 8.0), 1e-9);
    assertEquals(-1.0, JavaSoundSpeaker.squareWaveAverage(4, 8.0), 1e-9);
  }

  @Test
  void squareWaveAverageBlendsASampleThatStraddlesATransition() {
    // period=9.5, half=4.75: the sample starting at t=4 covers [4,5), which straddles the
    // high-to-low transition at 4.75 - 0.75 of the sample is high (+1), 0.25 is low (-1),
    // averaging to 0.5 rather than snapping to either extreme.
    assertEquals(0.5, JavaSoundSpeaker.squareWaveAverage(4, 9.5), 1e-9);
  }

  @Test
  void squareWaveAverageWrapsAcrossMultipleCycles() {
    // t=17 is 1 sample into the third cycle of an 8-sample period (17 = 2*8 + 1) - same result as
    // the equivalent first-cycle position (t=1).
    assertEquals(
        JavaSoundSpeaker.squareWaveAverage(1, 8.0),
        JavaSoundSpeaker.squareWaveAverage(17, 8.0),
        1e-9);
  }

  @Test
  void squareWaveAverageStaysBoundedForAnExtremelyHighFrequency() {
    // period < 2 (above the Nyquist limit for a square wave's fundamental) is clamped rather than
    // looping indefinitely or returning something outside [-1, 1].
    final double value = JavaSoundSpeaker.squareWaveAverage(0, 0.001);
    assertTrue(value >= -1.0 && value <= 1.0);
  }

  @Test
  void applyBeepEdgeFilterMovesTowardIdealWithoutJumpingStraightToIt() {
    // A single step from silence toward a fully-high sample should land strictly between the two,
    // not snap straight to 1.0 - the whole point of rounding the edge rather than switching
    // instantaneously.
    final double afterOneStep = JavaSoundSpeaker.applyBeepEdgeFilter(0.0, 1.0);
    assertTrue(afterOneStep > 0.0 && afterOneStep < 1.0);
  }

  @Test
  void applyBeepEdgeFilterRisesFasterThanItFalls() {
    // Rise and fall use different time constants (see BEEP_FILTER_RISE_TAU_SECONDS/
    // BEEP_FILTER_FALL_TAU_SECONDS) - over the same number of steps, closing a same-sized gap
    // should progress further on the rising side than the falling side.
    double rising = 0.0;
    double falling = 1.0;
    for (int i = 0; i < 5; i++) {
      rising = JavaSoundSpeaker.applyBeepEdgeFilter(rising, 1.0);
      falling = JavaSoundSpeaker.applyBeepEdgeFilter(falling, 0.0);
    }
    final double riseProgress = rising; // distance closed from a 0->1 gap
    final double fallProgress = 1.0 - falling; // distance closed from a 1->0 gap
    assertTrue(riseProgress > fallProgress);
  }

  @Test
  void applyBeepEdgeFilterConvergesToASustainedIdealValue() {
    // A long run of the same target value should settle arbitrarily close to it, rather than
    // oscillating or drifting - confirms the filter is stable, not just fast on the first step.
    double state = 0.0;
    for (int i = 0; i < 10_000; i++) {
      state = JavaSoundSpeaker.applyBeepEdgeFilter(state, 1.0);
    }
    assertEquals(1.0, state, 1e-9);
  }
}
