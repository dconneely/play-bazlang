package com.davidconneely.bazlang.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.io.Pitch;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the exact {@code APLAY} DSL strings used in the example games (converted from the
 * original blocking {@code BEEP} sound effects) reproduce the intended pitch sequence exactly.
 * Durations are deliberately only approximated to the nearest achievable duration code at {@code
 * T240} (the DSL's coarser, quantized durations can't reproduce arbitrary BEEP-second values
 * exactly) — but pitch fidelity is verified precisely here rather than trusted from hand
 * arithmetic, since a wrong semitone would be an audible, silent-until-heard regression (most
 * notably hangman's losing sting, which quotes a real piece and would simply be wrong, rather than
 * merely different, if a note were off).
 *
 * <p>Every game sound is deliberately pure tone: the noise generator was tried for the "bad event"
 * sounds and sounded harsh rather than percussive, so no game string enables it. {@link
 * #assertPitchSequence} asserts that for every note it checks, so noise can't quietly creep back in
 * — which matters more than it looks, because the mixer is a *shared* register and several games
 * reuse one live {@code APLAY} session, so a single noisy string would contaminate every later
 * sound in that game too.
 */
class ExampleGameSoundEffectsTest {

  private static final double LARGE = 10.0;

  /**
   * Pulls frames until one is actually sounding, skipping the short articulation gap the sequencer
   * inserts after every note (see {@code PlaySequencer.ARTICULATION_GAP_TICKS}). Returns {@code
   * null} once the source is finished.
   */
  private static PlayFrame nextSoundingFrame(PlaySource source) {
    for (int guard = 0; guard < 100; guard++) {
      final var frame = source.next(LARGE);
      if (frame.finished()) {
        return null;
      }
      if (frame.a().toneOn() || frame.b().toneOn() || frame.c().toneOn()) {
        return frame;
      }
    }
    throw new AssertionError("no sounding frame found -- only gaps?");
  }

  private static void assertPitchSequence(String dsl, int... expectedSemitonesAboveMiddleC) {
    final var source = PlayParser.buildSequencer(List.of(dsl), 10);
    for (final int semitones : expectedSemitonesAboveMiddleC) {
      final var frame = nextSoundingFrame(source);
      assertTrue(frame != null && frame.a().toneOn(), "expected a tone, got silence");
      assertFalse(frame.a().noiseOn(), "game sounds are pure tone -- noise sounded harsh");
      assertEquals(Pitch.hzFromSemitonesAboveMiddleC(semitones), frame.a().frequencyHz(), 0.05);
    }
    assertEquals(null, nextSoundingFrame(source), "expected no further notes");
  }

  @Test
  void uhOh() {
    // The standard bad-event sound, shared by every game (and by pong's "ball left on the player's
    // side" at line 3200). Was: BEEP 0.15, 0 : BEEP 0.2, -5
    assertPitchSequence("T240N3cO3N4g", 0, -5);
  }

  @Test
  void tada() {
    // The standard good-event sound, shared by every game (and by pong's "ball left on the
    // computer's side" at line 3210). Was: BEEP 0.08, 0 : BEEP 0.08, 4 : BEEP 0.15, 7
    assertPitchSequence("T240N2c2e3g", 0, 4, 7);
  }

  @Test
  void landerCampaignComplete() {
    // Was: BEEP 0.08, 0 : BEEP 0.08, 4 : BEEP 0.08, 7 : BEEP 0.2, 12
    assertPitchSequence("T240N2c2e2g4C", 0, 4, 7, 12);
  }

  @Test
  void invadersUfoBonus() {
    // Was: BEEP 0.06, 12 : BEEP 0.06, 16 : BEEP 0.1, 19
    assertPitchSequence("T240N1C1E2G", 12, 16, 19);
  }

  @Test
  void invadersAlienHitAndHangmanCorrectGuess() {
    // Was: BEEP 0.02, 4 (invaders); BEEP 0.05, 4 (hangman)
    assertPitchSequence("T240N1e", 4);
  }

  @Test
  void pongBatContactIsABrightPing() {
    // A "ping" on ball-to-bat contact: E6, two octaves above the original dull middle-C click, and
    // short. Volume is pulled back to V11 since it fires on every rally and shouldn't dominate.
    assertPitchSequence("T240V11O6N1e", 28); // 28 semitones above middle C = E6
    final var source = PlayParser.buildSequencer(List.of("T240V11O6N1e"), 10);
    assertEquals(11.0 / 15.0, source.next(LARGE).a().amplitude(), 1e-9);
  }

  @Test
  void hangmanWrongGuess() {
    // Was: BEEP 0.05, -4
    assertPitchSequence("T240O3N1#g", -4);
  }

  @Test
  void hangmanWin() {
    // Was: BEEP 0.1,0 : BEEP 0.1,2 : BEEP 0.1,4 : BEEP 0.1,5 : BEEP 0.1,7 : BEEP 0.1,9 :
    //      BEEP 0.1,11 : BEEP 0.4,12
    assertPitchSequence("T240N2c2d2e2f2g2a2b6C", 0, 2, 4, 5, 7, 9, 11, 12);
  }

  @Test
  void hangmanLoseIsChopinsFuneralMarchInThreeVoices() {
    // The opening phrase of Chopin's Marche Funebre (Piano Sonata No. 2, 3rd movement), in its
    // original key of B-flat minor: melody Bb Bb Bb Bb | Db C C Bb.
    //
    // Two things drive whether this is recognisable at all, both learned the hard way:
    //
    // 1. The rhythm -- dotted-quarter, eighth, quarter, quarter ("DUUM-da DUM DUM"), four beats to
    //    the bar. An earlier attempt used a dotted-eighth/sixteenth figure that left each bar a
    //    beat short of 4/4, and the tune was much harder to place.
    // 2. All three voices strike *together* in that rhythm. The piece is chordal -- the left hand
    //    marches with the melody -- so sustaining the accompaniment underneath (as an earlier
    //    version did) gives something closer to an organ pad than a march, however correct the
    //    harmony is.
    //
    // Voiced open (bass / fifth / melody rather than close triads) and spread across registers --
    // Bb2 / F4 / Bb4 -- so the melody stands clear of the accompaniment. Square waves a fifth apart
    // in the same octave mask each other badly, which is what an earlier Bb3-over-F3 voicing did.
    // The harmony moves only where it must, to F major (F2 / A4) under the C, the one melody note
    // outside Bb minor, then back.
    //
    // T120 puts the phrase at ~4.5s, the closing Bb held long to land the ending. The accompanying
    // voices sit at V11 so the melody stays on top.
    final var source =
        PlayParser.buildSequencer(
            List.of(
                "T120O4N6$b3$b5$b5$bO5N6$d3c5cO4N7$b",
                "V11O4N6f3f5f5f6f3a5a7f",
                "V11O2N6$b3$b5$b5$b6$b3f5f7$b"),
            10);
    // Asserts the harmonic progression rather than a frame-by-frame transcript: each channel's
    // articulation gaps fall at its own note boundaries, so the frame grid is finely fragmented and
    // its exact shape isn't the interesting property. Collapsing to the sequence of distinct chords
    // (ignoring frames where any voice is mid-gap) captures what actually matters -- and still
    // catches a misaligned harmony change, which is how an earlier version of this arrangement was
    // caught leaving the closing Bb sounding briefly over an A.
    final var progression = new java.util.ArrayList<List<Integer>>();
    for (PlayFrame frame = nextSoundingFrame(source);
        frame != null;
        frame = nextSoundingFrame(source)) {
      if (!frame.a().toneOn() || !frame.b().toneOn() || !frame.c().toneOn()) {
        continue; // one of the voices is between notes
      }
      final var chord =
          List.of(
              semitonesOf(frame.a().frequencyHz()),
              semitonesOf(frame.b().frequencyHz()),
              semitonesOf(frame.c().frequencyHz()));
      if (progression.isEmpty() || !progression.get(progression.size() - 1).equals(chord)) {
        progression.add(chord);
      }
    }
    assertEquals(
        List.of(
            List.of(
                10, 5, -14), // Bb minor: Bb4 / F4 / Bb2, struck on each of the four repeated Bbs
            List.of(13, 5, -14), // Db5 -- still Bb minor (Db is its third)
            List.of(12, 9, -19), // C5 over A4 / F2 -- F major, the dominant
            List.of(10, 5, -14)), // back to Bb minor to close
        progression);
  }

  /** Inverse of {@link Pitch#hzFromSemitonesAboveMiddleC}, rounded to the nearest semitone. */
  private static int semitonesOf(double frequencyHz) {
    return (int) Math.round(12.0 * Math.log(frequencyHz / Pitch.MIDDLE_C_HZ) / Math.log(2.0));
  }
}
