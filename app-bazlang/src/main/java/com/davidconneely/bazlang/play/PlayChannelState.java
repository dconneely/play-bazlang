package com.davidconneely.bazlang.play;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks one channel's token list, tracking that channel's own persistent state (octave, duration,
 * volume, envelope-in-use - mirroring the ROM's per-channel data block) and applying every non-note
 * token as a side effect (on either its own state, or {@link SharedRegisters} for the chip-wide
 * ones) until it reaches a note or rest, mirroring the ROM's "process characters until a note is
 * found" loop.
 */
final class PlayChannelState {
  // Indices 1-9 are the plain codes (1=semi-quaver ... 9=semi-breve), per the ZX Spectrum 128
  // manual's duration table. Indices 10-12 are its triplet codes - "three notes played in the time
  // normally used for two" of the base note they triplet (10=semi-quaver, 11=quaver, 12=crotchet),
  // i.e. each is 2 * that base duration's ticks, divided by 3: 10 = 2*6/3 = 4, 11 = 2*12/3 = 8,
  // 12 = 2*24/3 = 16.
  private static final int[] DURATION_TICKS = {0, 6, 9, 12, 18, 24, 36, 48, 72, 96, 4, 8, 16};
  private static final int DEFAULT_OCTAVE = 4; // reference octave containing middle C
  private static final int DEFAULT_DURATION_CODE = 5; // crotchet, per the ROM's confirmed default
  private static final int DEFAULT_VOLUME = 15;

  private final List<PlayToken> tokens;
  private final int lineLabel;
  private final boolean honoursTempo;
  private final Map<Integer, Integer> repeatCounters = new HashMap<>();

  private int cursor;
  private int octave = DEFAULT_OCTAVE;
  private int durationCode = DEFAULT_DURATION_CODE;
  private int volume = DEFAULT_VOLUME;
  private boolean useEnvelope;
  private boolean halted;
  // Set by a TiedDuration token, consumed by the very next Note/Rest (see consumeDurationTicks) -
  // one-shot rather than persisted, since a tie's combined length applies to exactly one note.
  private Integer tiedTicksOverride;

  /** One resolved note/rest event: {@code noteNumber} is {@code null} for a rest. */
  record ChannelNote(int durationTicks, Integer noteNumber, int volume, boolean useEnvelope) {}

  PlayChannelState(List<PlayToken> tokens, boolean honoursTempo, int lineLabel) {
    this.tokens = tokens;
    this.honoursTempo = honoursTempo;
    this.lineLabel = lineLabel;
  }

  /** Advances to the next note/rest, applying side effects along the way. {@code null} = done. */
  ChannelNote nextNote(SharedRegisters shared) {
    if (halted) {
      return null;
    }
    while (cursor < tokens.size()) {
      final PlayToken token = tokens.get(cursor);
      switch (token) {
        case PlayToken.Note n -> {
          cursor++;
          return new ChannelNote(consumeDurationTicks(), resolveNoteNumber(n), volume, useEnvelope);
        }
        case PlayToken.Rest _ -> {
          cursor++;
          return new ChannelNote(consumeDurationTicks(), null, volume, useEnvelope);
        }
        case PlayToken.SetOctave o -> {
          octave = o.octave();
          cursor++;
        }
        case PlayToken.SetDuration d -> {
          durationCode = d.code();
          cursor++;
        }
        case PlayToken.TiedDuration td -> {
          tiedTicksOverride = DURATION_TICKS[td.firstCode()] + DURATION_TICKS[td.secondCode()];
          durationCode = td.secondCode(); // persists for later untied notes, per the manual
          cursor++;
        }
        case PlayToken.SetVolume v -> {
          volume = v.level();
          cursor++;
        }
        case PlayToken.UseEnvelope _ -> {
          useEnvelope = true;
          cursor++;
        }
        case PlayToken.SetTempo t -> {
          if (honoursTempo) {
            shared.setTempoBpm(t.bpm());
          }
          cursor++;
        }
        case PlayToken.SetMixer m -> {
          shared.setMixerMask(m.mask());
          cursor++;
        }
        case PlayToken.SetEnvelopeShape w -> {
          shared.setEnvelopeShape(w.shape());
          cursor++;
        }
        case PlayToken.SetEnvelopeDuration x -> {
          shared.setEnvelopePeriodTicks(x.period());
          cursor++;
        }
        case PlayToken.Halt _ -> {
          halted = true;
          return null;
        }
        case PlayToken.RepeatStart _ -> cursor++;
        case PlayToken.RepeatEnd re -> advancePastRepeatEnd(re);
      }
    }
    return null;
  }

  private void advancePastRepeatEnd(PlayToken.RepeatEnd re) {
    if (re.infinite()) {
      cursor = 0;
      return;
    }
    final int endIndex = cursor;
    final int remainingPlays = repeatCounters.getOrDefault(endIndex, 1);
    if (remainingPlays > 0) {
      repeatCounters.put(endIndex, remainingPlays - 1);
      cursor = re.matchingStartIndex() + 1;
    } else {
      repeatCounters.remove(endIndex); // reset in case an outer loop re-enters this bracket later
      cursor++;
    }
  }

  /** Returns the ticks for the note/rest about to be emitted, consuming a pending tie if any. */
  private int consumeDurationTicks() {
    if (tiedTicksOverride != null) {
      final int ticks = tiedTicksOverride;
      tiedTicksOverride = null;
      return ticks;
    }
    return DURATION_TICKS[durationCode];
  }

  private int resolveNoteNumber(PlayToken.Note note) {
    final int noteNumber = (octave + (note.octaveUp() ? 1 : 0)) * 12 + note.semitoneOffset();
    if (noteNumber < 0 || noteNumber > 127) {
      throw new ReportException(
          ReportCode.INVALID_ARGUMENT, lineLabel, "PLAY note out of range: " + noteNumber);
    }
    return noteNumber;
  }
}
