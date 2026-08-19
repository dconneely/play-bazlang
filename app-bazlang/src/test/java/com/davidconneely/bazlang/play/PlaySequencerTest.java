package com.davidconneely.bazlang.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.io.Pitch;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link PlaySequencer} (via {@link PlayParser#buildSequencer}), the scheduling/timing/mix
 * logic behind {@code PLAY}/{@code APLAY} — independent of any real audio device.
 */
class PlaySequencerTest {

  private static final double LARGE = 10.0;

  /** Seconds per duration tick at the default 120bpm: 60 / (120 * 24) = 1/48. */
  private static final double TICK = 1.0 / 48.0;

  /**
   * Every note ends with a short silence so consecutive notes are articulated rather than running
   * together. Nominally one tick, but capped so it stays a note boundary rather than an audible
   * hole — at the default tempo a tick exceeds that cap, so the cap is what applies here. See
   * {@code PlaySequencer.MAX_ARTICULATION_GAP_SECONDS}.
   */
  private static final double GAP = 0.010;

  @Test
  void defaultDurationAndTempoGiveAHalfSecondCrotchet() {
    // Default duration is a crotchet: 24 ticks = 0.5s, of which all but the trailing gap sounds.
    final var source = PlayParser.buildSequencer(List.of("c"), 10);
    final var tone = source.next(LARGE);
    assertEquals(24 * TICK - GAP, tone.durationSeconds(), 1e-9);
    assertTrue(tone.a().toneOn());
    assertFalse(tone.finished());
    final var gap = source.next(LARGE);
    assertEquals(GAP, gap.durationSeconds(), 1e-9);
    assertFalse(gap.a().toneOn());
    assertEquals(0.5, tone.durationSeconds() + gap.durationSeconds(), 1e-9); // 24 ticks in total
  }

  @Test
  void middleCPlaysAtTheExpectedFrequency() {
    final var source = PlayParser.buildSequencer(List.of("c"), 10);
    final var frame = source.next(LARGE);
    assertEquals(Pitch.MIDDLE_C_HZ, frame.a().frequencyHz(), 0.01);
    assertTrue(frame.a().toneOn());
  }

  @Test
  void shortestDurationDigitGivesASixTickNote() {
    // 1 = semi-quaver = 6 ticks -> 0.125s at the default tempo, less the trailing gap.
    final var source = PlayParser.buildSequencer(List.of("1c"), 10);
    assertEquals(6 * TICK - GAP, source.next(LARGE).durationSeconds(), 1e-9);
  }

  @Test
  void restIsSilent() {
    final var source = PlayParser.buildSequencer(List.of("&"), 10);
    final var frame = source.next(LARGE);
    assertFalse(frame.a().toneOn());
    assertFalse(frame.a().noiseOn());
    assertEquals(0.0, frame.a().amplitude());
  }

  @Test
  void pullingLessThanTheNoteDurationReturnsAPartialSlice() {
    final var source = PlayParser.buildSequencer(List.of("1c"), 10); // 0.125s, less the gap
    final var first = source.next(0.05);
    assertEquals(0.05, first.durationSeconds(), 1e-9);
    assertFalse(first.finished());
    final var second = source.next(LARGE); // the rest of the same note's tone
    assertEquals(6 * TICK - GAP - 0.05, second.durationSeconds(), 1e-6);
  }

  @Test
  void multiChannelSliceIsBoundedByTheShortestRemainingNote() {
    // Channel A: 1c (6 ticks, less the gap). Channel B: 9c (96 ticks) -- the slice must be
    // capped to channel A's shorter note.
    final var source = PlayParser.buildSequencer(List.of("1c", "9c"), 10);
    final var frame = source.next(LARGE);
    assertEquals(6 * TICK - GAP, frame.durationSeconds(), 1e-9);
    assertTrue(frame.a().toneOn());
    assertTrue(frame.b().toneOn());
  }

  @Test
  void nonRepeatingChannelEventuallyFinishes() {
    final var source = PlayParser.buildSequencer(List.of("1c"), 10);
    source.next(LARGE); // the note's tone
    source.next(LARGE); // its trailing articulation gap
    assertTrue(source.next(LARGE).finished());
  }

  @Test
  void infiniteRepeatNeverFinishes() {
    final var source = PlayParser.buildSequencer(List.of("1c)"), 10); // unmatched ')' -> infinite
    for (int i = 0; i < 50; i++) {
      assertFalse(source.next(LARGE).finished());
    }
  }

  @Test
  void matchedBracketPlaysContentTwice() {
    // "(1c)1d" -- c plays twice, then d once -- verified by counting how many notes are produced
    // before the source finishes.
    final var source = PlayParser.buildSequencer(List.of("(1c)1d"), 10);
    int notes = 0;
    while (true) {
      final var frame = source.next(LARGE);
      if (frame.finished()) {
        break;
      }
      if (frame.a().toneOn()) {
        notes++; // count only sounding frames, not the articulation gap after each note
      }
      assertTrue(notes <= 10); // safety net against an accidental infinite loop in the test itself
    }
    assertEquals(3, notes); // c, c, d
  }

  @Test
  void unsetMixerDefaultsToToneOnlyForAPlayingNote() {
    final var source = PlayParser.buildSequencer(List.of("c"), 10);
    final var frame = source.next(LARGE);
    assertTrue(frame.a().toneOn());
    assertFalse(frame.a().noiseOn());
  }

  @Test
  void explicitMixerSelectsToneAndNoiseBitsPerChannel() {
    // M9 = tone A (1) + noise A (8) -- both enabled for channel A specifically.
    final var source = PlayParser.buildSequencer(List.of("M9c"), 10);
    final var frame = source.next(LARGE);
    assertTrue(frame.a().toneOn());
    assertTrue(frame.a().noiseOn());
  }

  @Test
  void haltStopsThatChannelWithoutAffectingOthers() {
    final var source = PlayParser.buildSequencer(List.of("H", "1c"), 10);
    final var frame = source.next(LARGE);
    assertFalse(frame.a().toneOn()); // halted channel contributes nothing
    assertTrue(frame.b().toneOn());
  }

  @Test
  void dashPlaceholderInAFreshBuildIsSilent() {
    // "-" has nothing to "leave alone" in a fresh build (no prior state exists), so it falls back
    // to meaning silent, same as omitting the channel entirely.
    final var source = PlayParser.buildSequencer(List.of("-", "1c"), 10);
    final var frame = source.next(LARGE);
    assertFalse(frame.a().toneOn());
    assertTrue(frame.b().toneOn());
  }

  @Test
  void replaceChannelLeavesOtherChannelsInFlightNoteUnaffected() {
    // Channels A/B start long notes (96 ticks = 2.0s); a partial pull establishes pending state.
    final var source = PlayParser.buildSequencer(List.of("9c", "9e"), 10);
    final var first = source.next(0.05);
    assertTrue(first.a().toneOn());
    assertTrue(first.b().toneOn());
    assertFalse(first.c().toneOn()); // nothing given for C yet
    final double freqABefore = first.a().frequencyHz();
    final double freqBBefore = first.b().frequencyHz();

    source.replaceChannel(2, "1c", 10); // touch channel C only

    final var second = source.next(0.01); // well within A/B's remaining ~1.95s
    assertEquals(freqABefore, second.a().frequencyHz(), 0.01); // A's in-progress note is untouched
    assertEquals(freqBBefore, second.b().frequencyHz(), 0.01); // B's in-progress note is untouched
    assertTrue(second.c().toneOn()); // C now has a fresh tone
  }

  @Test
  void replaceChannelWithDashIsSilent() {
    final var source = PlayParser.buildSequencer(List.of("1c"), 10);
    source.replaceChannel(0, "-", 10);
    assertFalse(source.next(LARGE).a().toneOn());
  }

  @Test
  void replaceChannelWithHaltStopsThatChannelPromptlyRatherThanWaitingOutTheCurrentNote() {
    // The "APLAY \"H\", \"H\", effect$\" idiom for stopping background music at game end.
    final var source = PlayParser.buildSequencer(List.of("9c"), 10); // a long note (2.0s)
    source.next(0.05); // establish pending state mid-note
    source.replaceChannel(0, "H", 10);
    final var frame = source.next(LARGE);
    assertFalse(frame.a().toneOn()); // halted immediately, not after the original long note ends
    assertTrue(frame.finished()); // the only channel with content is now halted
  }
}
