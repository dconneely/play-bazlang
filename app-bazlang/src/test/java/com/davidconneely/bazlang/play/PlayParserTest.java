package com.davidconneely.bazlang.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.ReportException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests {@link PlayParser} against the exact DSL confirmed from the Spectrum 128 ROM 0. */
class PlayParserTest {

  @Test
  void aNoteWithNoDurationEmitsJustANoteToken() {
    final var tokens = PlayParser.parse("c", 10);
    assertEquals(1, tokens.size());
    final var note = assertInstanceOf(PlayToken.Note.class, tokens.get(0));
    assertEquals(0, note.semitoneOffset()); // C is 0 semitones above C
    assertFalse(note.octaveUp());
  }

  @Test
  void upperCaseNoteSetsOctaveUp() {
    final var tokens = PlayParser.parse("C", 10);
    final var note = assertInstanceOf(PlayToken.Note.class, tokens.get(0));
    assertTrue(note.octaveUp());
  }

  @Test
  void leadingDurationDigitEmitsSetDurationThenNote() {
    final var tokens = PlayParser.parse("9c", 10);
    assertEquals(2, tokens.size());
    assertEquals(9, ((PlayToken.SetDuration) tokens.get(0)).code());
    assertInstanceOf(PlayToken.Note.class, tokens.get(1));
  }

  @Test
  void bareDurationDigitWithNothingFollowingIsJustSetDuration() {
    final var tokens = PlayParser.parse("5", 10);
    assertEquals(1, tokens.size());
    assertEquals(5, ((PlayToken.SetDuration) tokens.get(0)).code());
  }

  @Test
  void sharpAndFlatAdjustSemitoneOffset() {
    assertEquals(1, ((PlayToken.Note) PlayParser.parse("#c", 10).get(0)).semitoneOffset());
    assertEquals(-1, ((PlayToken.Note) PlayParser.parse("$c", 10).get(0)).semitoneOffset());
    assertEquals(2, ((PlayToken.Note) PlayParser.parse("##c", 10).get(0)).semitoneOffset());
  }

  @Test
  void ampersandIsARest() {
    assertInstanceOf(PlayToken.Rest.class, PlayParser.parse("&", 10).get(0));
  }

  @Test
  void octaveCommandParsesInRange() {
    final var octave = (PlayToken.SetOctave) PlayParser.parse("O4", 10).get(0);
    assertEquals(4, octave.octave());
  }

  @Test
  void octaveOutOfRangeThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("O9", 10));
  }

  @Test
  void tempoOutOfRangeThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("T59", 10));
    assertThrows(ReportException.class, () -> PlayParser.parse("T241", 10));
  }

  @Test
  void volumeOutOfRangeThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("V16", 10));
  }

  @Test
  void envelopeShapeOutOfRangeThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("W8", 10));
  }

  @Test
  void mixerAcceptsFullZeroToSixtyThreeRangeAndRejectsSixtyFour() {
    PlayParser.parse("M0", 10); // 0 is technically accepted (loose range check), see PlayParser
    PlayParser.parse("M63", 10);
    assertThrows(ReportException.class, () -> PlayParser.parse("M64", 10));
  }

  @Test
  void midiCommandsAreRejected() {
    assertThrows(ReportException.class, () -> PlayParser.parse("Y1", 10));
    assertThrows(ReportException.class, () -> PlayParser.parse("Z1", 10));
  }

  @Test
  void tripletDurationCodesParseLikeAnyOtherDuration() {
    final var tokens = PlayParser.parse("10c", 10);
    assertEquals(2, tokens.size());
    assertEquals(10, ((PlayToken.SetDuration) tokens.get(0)).code());
    assertInstanceOf(PlayToken.Note.class, tokens.get(1));
    assertEquals(12, ((PlayToken.SetDuration) PlayParser.parse("12c", 10).get(0)).code());
  }

  @Test
  void durationOutOfRangeThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("0c", 10));
    assertThrows(ReportException.class, () -> PlayParser.parse("13c", 10));
  }

  @Test
  void tiedDurationEmitsATiedDurationTokenThenTheNote() {
    // The ZX Spectrum 128 manual's own worked example: a crotchet+quaver-length A.
    final var tokens = PlayParser.parse("3_5A", 10);
    assertEquals(2, tokens.size());
    final var tied = assertInstanceOf(PlayToken.TiedDuration.class, tokens.get(0));
    assertEquals(3, tied.firstCode());
    assertEquals(5, tied.secondCode());
    assertInstanceOf(PlayToken.Note.class, tokens.get(1));
  }

  @Test
  void tiedDurationAppliesToARestToo() {
    final var tied =
        assertInstanceOf(PlayToken.TiedDuration.class, PlayParser.parse("3_5&", 10).get(0));
    assertEquals(3, tied.firstCode());
    assertEquals(5, tied.secondCode());
  }

  @Test
  void bareTiedDurationWithNothingFollowingKeepsOnlyTheSecondCode() {
    final var tokens = PlayParser.parse("3_5", 10);
    assertEquals(1, tokens.size());
    assertEquals(5, ((PlayToken.SetDuration) tokens.get(0)).code());
  }

  @Test
  void tieMissingSecondDurationThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("3_c", 10));
    assertThrows(ReportException.class, () -> PlayParser.parse("3_", 10));
  }

  @Test
  void tieNotFollowingADurationDigitThrows() {
    // Real syntax is <duration>_<duration><note>, not note_note.
    assertThrows(ReportException.class, () -> PlayParser.parse("c_d", 10));
  }

  @Test
  void invalidNoteNameThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("z", 10));
  }

  @Test
  void commentsAreStrippedEntirely() {
    final var tokens = PlayParser.parse("!a comment!c", 10);
    assertEquals(1, tokens.size());
    assertInstanceOf(PlayToken.Note.class, tokens.get(0));
  }

  @Test
  void separatorIsATrueNoOp() {
    final var tokens = PlayParser.parse("NcNd", 10);
    assertEquals(2, tokens.size());
    assertInstanceOf(PlayToken.Note.class, tokens.get(0));
    assertInstanceOf(PlayToken.Note.class, tokens.get(1));
  }

  @Test
  void haltEmitsHaltToken() {
    assertInstanceOf(PlayToken.Halt.class, PlayParser.parse("H", 10).get(0));
  }

  @Test
  void matchedBracketsResolveStartIndex() {
    final List<PlayToken> tokens = PlayParser.parse("(c)", 10);
    assertInstanceOf(PlayToken.RepeatStart.class, tokens.get(0));
    final var end = (PlayToken.RepeatEnd) tokens.get(2);
    assertEquals(0, end.matchingStartIndex());
    assertFalse(end.infinite());
  }

  @Test
  void unmatchedClosingBracketIsMarkedInfinite() {
    final var end = (PlayToken.RepeatEnd) PlayParser.parse("c)", 10).get(1);
    assertTrue(end.infinite());
  }

  @Test
  void unmatchedOpeningBracketThrows() {
    assertThrows(ReportException.class, () -> PlayParser.parse("(c", 10));
  }

  @Test
  void tooManyNestedBracketsThrows() {
    // 4 levels of nesting are within the ROM's own limit; a 5th throws.
    assertThrows(ReportException.class, () -> PlayParser.parse("(((((c)))))", 10));
  }

  @Test
  void buildSequencerAcceptsUpToThreeChannels() {
    // Just needs to not throw -- PlaySequencerTest exercises actual playback behaviour.
    PlayParser.buildSequencer(List.of("c", "e", "g"), 10);
  }
}
