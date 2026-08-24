package com.davidconneely.bazlang.play;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Parses one {@code PLAY}/{@code APLAY} channel string into a flat {@link PlayToken} list. The DSL
 * here is exactly what was confirmed from the Spectrum 128 ROM 0 disassembly and cross-checked
 * against real 128K/+2/+3 hardware (see {@code localonly-BAZLANG-ROADMAP.md}'s {@code PLAY} entry)
 * - with one deliberate simplification: a duration-digit sequence and the note/rest it modifies are
 * folded into one parse step here, rather than the ROM's own two separate per-character dispatch
 * cycles. The observable musical result is identical either way, and every prose description of
 * this DSL (both primary manual sources) documents duration-then-note as one conceptual unit, so
 * this keeps the token stream simpler without changing behaviour.
 *
 * <p>Bracket repeats are resolved structurally here - {@code (}/{@code )} nesting up to 4 levels
 * (the ROM's own limit) and a trailing unmatched {@code )} marked {@link
 * PlayToken.RepeatEnd#infinite} - rather than replicating the ROM's low-level position-revisit
 * bookkeeping; {@link PlayChannelState} interprets these as simple runtime loop counters, which
 * matches the ROM's documented *behaviour* ("the whole string up until that point is repeated
 * indefinitely") exactly.
 *
 * <p>Tied notes ({@code _}) and triplet duration codes ({@code 10}-{@code 12}) are Phase 2, not yet
 * implemented - encountering either is a parse error, not silent misbehaviour. MIDI ({@code
 * Y}/{@code Z}) is permanently out of scope per the project's own hard constraint.
 */
public final class PlayParser {
  private PlayParser() {}

  private static final int MAX_BRACKET_DEPTH = 4;
  private static final int[] NOTE_SEMITONES = {9, 11, 0, 2, 4, 5, 7}; // A,B,C,D,E,F,G

  private static final int CHANNEL_COUNT = 3;

  /**
   * Parses 1-3 channel strings and builds the {@link PlaySource} {@code StatementExecutor} hands to
   * {@code VirtualSpeaker.playFrame}. Always builds exactly {@value #CHANNEL_COUNT} channels - any
   * position not given (fewer than {@value #CHANNEL_COUNT} strings) or given as {@code "-"}
   * (trimmed) is padded with an empty channel, silent by construction (an empty token list's {@code
   * PlayChannelState.nextNote} immediately returns {@code null}). {@code "-"} only means "leave
   * this channel alone" against an already-live {@code APLAY} session, via {@link
   * PlaySource#replaceChannel} - that check happens in {@code StatementExecutor} before this method
   * is ever reached, so a fresh build has nothing to "leave alone" and {@code "-"} falls back to
   * meaning silent, same as omitting it. The sole public entry point into this package - {@link
   * PlayToken}, {@link PlayChannelState}, and {@link PlaySequencer} all stay package-private.
   */
  public static PlaySource buildSequencer(List<String> channelStrings, int lineLabel) {
    final List<List<PlayToken>> tokenLists = new ArrayList<>(CHANNEL_COUNT);
    for (int i = 0; i < CHANNEL_COUNT; i++) {
      final String raw = i < channelStrings.size() ? channelStrings.get(i) : "";
      tokenLists.add(parse("-".equals(raw.trim()) ? "" : raw, lineLabel));
    }
    return new PlaySequencer(tokenLists, lineLabel);
  }

  static List<PlayToken> parse(String channelString, int lineLabel) {
    final List<PlayToken> tokens = new ArrayList<>();
    final Deque<Integer> openBrackets = new ArrayDeque<>();
    final int len = channelString.length();
    int i = 0;
    while (i < len) {
      final char c = channelString.charAt(i);
      switch (c) {
        case '!' -> {
          final int end = channelString.indexOf('!', i + 1);
          i = (end < 0) ? len : end + 1;
        }
        case 'N' -> i++;
        case '(' -> {
          if (openBrackets.size() >= MAX_BRACKET_DEPTH) {
            throw invalid(lineLabel, "Too many brackets in PLAY string");
          }
          openBrackets.push(tokens.size());
          tokens.add(new PlayToken.RepeatStart());
          i++;
        }
        case ')' -> {
          tokens.add(
              openBrackets.isEmpty()
                  ? new PlayToken.RepeatEnd(0, true)
                  : new PlayToken.RepeatEnd(openBrackets.pop(), false));
          i++;
        }
        case 'H' -> {
          tokens.add(new PlayToken.Halt());
          i++;
        }
        case 'O' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    0,
                    8,
                    "octave",
                    PlayToken.SetOctave::new,
                    tokens,
                    lineLabel);
        case 'T' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    60,
                    240,
                    "tempo",
                    PlayToken.SetTempo::new,
                    tokens,
                    lineLabel);
        case 'V' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    0,
                    15,
                    "volume",
                    PlayToken.SetVolume::new,
                    tokens,
                    lineLabel);
        case 'U' -> {
          tokens.add(new PlayToken.UseEnvelope());
          i++;
        }
        case 'W' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    0,
                    7,
                    "envelope shape",
                    PlayToken.SetEnvelopeShape::new,
                    tokens,
                    lineLabel);
        case 'X' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    0,
                    65_535,
                    "envelope duration",
                    PlayToken.SetEnvelopeDuration::new,
                    tokens,
                    lineLabel);
        case 'M' ->
            i =
                parseRangedNumber(
                    channelString,
                    i + 1,
                    0,
                    63,
                    "mixer value",
                    PlayToken.SetMixer::new,
                    tokens,
                    lineLabel);
        case 'Y', 'Z' -> throw invalid(lineLabel, "PLAY MIDI commands ('Y'/'Z') are not supported");
        case '_' -> throw invalid(lineLabel, "PLAY tied notes ('_') are not yet supported");
        default -> i = parseNoteOrDuration(channelString, i, tokens, lineLabel);
      }
    }
    if (!openBrackets.isEmpty()) {
      throw invalid(lineLabel, "Unmatched '(' in PLAY string");
    }
    return tokens;
  }

  /**
   * Parses an optional duration-digit sequence, optional {@code #}/{@code $} accidentals, and a
   * terminating note letter or {@code &} rest - emitting a {@code SetDuration} token first if a
   * duration was given, then the {@code Note}/{@code Rest} token. A bare duration (nothing follows
   * it) emits just {@code SetDuration}, updating the persisted duration for future notes.
   */
  private static int parseNoteOrDuration(
      String s, int start, List<PlayToken> tokens, int lineLabel) {
    int i = start;
    Integer duration = null;
    if (Character.isDigit(s.charAt(i))) {
      final var num = parseNumber(s, i);
      if (num.value() >= 10 && num.value() <= 12) {
        throw invalid(lineLabel, "PLAY triplets ('10'-'12') are not yet supported");
      }
      if (num.value() < 1 || num.value() > 9) {
        throw invalid(lineLabel, "PLAY duration out of range (1-9): " + num.value());
      }
      duration = num.value();
      i = num.nextIndex();
    }
    int semitoneAdjust = 0;
    boolean sawAccidental = false;
    while (i < s.length() && (s.charAt(i) == '#' || s.charAt(i) == '$')) {
      semitoneAdjust += (s.charAt(i) == '#') ? 1 : -1;
      sawAccidental = true;
      i++;
    }
    if (i >= s.length()) {
      if (sawAccidental) {
        throw invalid(lineLabel, "PLAY note name missing after accidental");
      }
      if (duration == null) {
        throw invalid(lineLabel, "Invalid note name in PLAY string: '" + s.charAt(start) + "'");
      }
      tokens.add(new PlayToken.SetDuration(duration));
      return i;
    }
    final char c = s.charAt(i);
    if (c == '&') {
      if (sawAccidental) {
        throw invalid(lineLabel, "PLAY accidental cannot apply to a rest");
      }
      if (duration != null) {
        tokens.add(new PlayToken.SetDuration(duration));
      }
      tokens.add(new PlayToken.Rest());
      return i + 1;
    }
    final int letterIndex = Character.toUpperCase(c) - 'A';
    if (letterIndex < 0 || letterIndex > 6) {
      throw invalid(lineLabel, "Invalid note name in PLAY string: '" + c + "'");
    }
    if (duration != null) {
      tokens.add(new PlayToken.SetDuration(duration));
    }
    tokens.add(
        new PlayToken.Note(NOTE_SEMITONES[letterIndex] + semitoneAdjust, Character.isUpperCase(c)));
    return i + 1;
  }

  /**
   * Shared shape of the {@code O}/{@code T}/{@code V}/{@code W}/{@code X}/{@code M} commands: read
   * a following number, range-check it, and add the token {@code factory} builds from it.
   */
  private static int parseRangedNumber(
      String s,
      int i,
      int min,
      int max,
      String label,
      IntFunction<PlayToken> factory,
      List<PlayToken> tokens,
      int lineLabel) {
    final var num = parseNumber(s, i);
    if (num.value() < min || num.value() > max) {
      throw invalid(
          lineLabel, "PLAY " + label + " out of range (" + min + "-" + max + "): " + num.value());
    }
    tokens.add(factory.apply(num.value()));
    return num.nextIndex();
  }

  private static ParsedNumber parseNumber(String s, int i) {
    int j = i;
    long value = 0;
    while (j < s.length() && Character.isDigit(s.charAt(j))) {
      value = Math.min(value * 10 + (s.charAt(j) - '0'), 1_000_000L);
      j++;
    }
    return new ParsedNumber((int) value, j);
  }

  private static ReportException invalid(int lineLabel, String message) {
    return new ReportException(ReportCode.INVALID_ARGUMENT, lineLabel, message);
  }

  private record ParsedNumber(int value, int nextIndex) {}
}
