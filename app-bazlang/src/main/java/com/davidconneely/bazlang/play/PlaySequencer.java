package com.davidconneely.bazlang.play;

import com.davidconneely.bazlang.io.Pitch;
import com.davidconneely.bazlang.io.VoiceFrame;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The top-level {@code PLAY}/{@code APLAY} scheduler: owns exactly 3 {@link PlayChannelState}s
 * (channels not given content by the caller are simply empty/silent — see {@link
 * PlayParser#buildSequencer}) plus the chip-wide {@link SharedRegisters}, and implements {@link
 * PlaySource}'s pull interface. Mirrors the ROM's own scheduling loop (find the channel with the
 * least remaining time, advance every channel by that much, refill whichever just hit zero) but
 * measured in real seconds rather than the ROM's raw duration ticks — durations convert via {@code
 * secondsPerTick = 60 / (tempo * 24)} (a crotchet is 24 ticks, so this is exactly "1 beat at {@code
 * tempo} bpm, divided into 24"), a clean musical formula chosen deliberately over replicating the
 * ROM's literal T-state busy-wait arithmetic, since cycle-exact timing isn't a goal here (see
 * {@code docs/quirks.md}).
 *
 * <p>The shared envelope generator is modelled functionally rather than at the AY chip's literal
 * 16-step register resolution: amplitude is a continuous ramp/triangle per {@code W} shape over a
 * period derived from {@code X} (an undocumented raw-value-to-seconds mapping in every source
 * consulted; {@code X} is treated as milliseconds, and an unset/zero {@code X} — "maximum duration"
 * per the ROM — as a fixed 2 second default), consistent with this project's existing choice to
 * exceed rather than replicate hardware register-precision limits elsewhere (e.g. BEEP's pitch, or
 * the octave 0-1 note-precision item in the roadmap).
 *
 * <p>Long-lived and mutable: {@code APLAY} keeps one {@code PlaySequencer} instance alive across
 * calls (as long as its background thread is still running) so a follow-up {@code APLAY} call can
 * target {@link #replaceChannel} at just one index, leaving the other channels' state — including
 * an in-progress infinite repeat — completely untouched. {@link #lock} guards every method's whole
 * body against the background thread's concurrent {@link #next} calls; contention is negligible
 * since {@code replaceChannel} calls are rare and {@code next}'s own critical section is just array
 * arithmetic, no I/O.
 */
final class PlaySequencer implements PlaySource {
  private static final double EPSILON_SECONDS = 1e-9;
  private static final double DEFAULT_ENVELOPE_PERIOD_SECONDS = 2.0;

  /**
   * Silence inserted at the end of every note, so consecutive notes are articulated rather than
   * running together. Without it, repeated notes at the same pitch merge into one continuous tone
   * and the rhythm simply disappears — which is exactly what happened to hangman's funeral march
   * (four repeated B-flats rendering as a single drone).
   *
   * <p>One tick is 1/96th of a whole note, matching the real ROM, whose own bug list records that
   * {@code PLAY "abc"} "renders a 1/96th of a note gap of silence between playing each note". The
   * one deliberate difference: the ROM *adds* that gap, making every note fractionally longer than
   * written, whereas this takes it out of the note's own length so the tempo stays exact.
   */
  private static final int ARTICULATION_GAP_TICKS = 1;

  /**
   * Ceiling on that gap. A tick is only a short moment at a brisk tempo, but at a slow one it
   * becomes long enough to hear as the sound stopping rather than as one note ending and the next
   * beginning — and when several voices move together, as in a chordal arrangement, the whole
   * texture drops out at once and the result stutters. A few milliseconds is all the ear needs to
   * register a note boundary, so cap it there and let faster tempos keep the ROM's exact figure.
   */
  private static final double MAX_ARTICULATION_GAP_SECONDS = 0.010;

  private final ReentrantLock lock = new ReentrantLock();
  private final PlayChannelState[] channels;
  private final SharedRegisters shared = new SharedRegisters();
  private final double[] remainingSeconds;
  private final double[] pendingGapSeconds;
  private final Integer[] noteNumber; // null = rest
  private final int[] volume;
  private final boolean[] useEnvelope;
  private final boolean[] done;
  private double elapsedSeconds;

  PlaySequencer(List<List<PlayToken>> channelTokenLists, int lineLabel) {
    final int n = channelTokenLists.size();
    channels = new PlayChannelState[n];
    for (int i = 0; i < n; i++) {
      channels[i] = new PlayChannelState(channelTokenLists.get(i), i == 0, lineLabel);
    }
    remainingSeconds = new double[n];
    pendingGapSeconds = new double[n];
    noteNumber = new Integer[n];
    volume = new int[n];
    useEnvelope = new boolean[n];
    done = new boolean[n];
  }

  @Override
  public void replaceChannel(int index, String channelDsl, int lineLabel) {
    if (index < 0 || index >= channels.length) {
      return;
    }
    final String normalized = "-".equals(channelDsl.trim()) ? "" : channelDsl;
    final var tokens = PlayParser.parse(normalized, lineLabel);
    lock.lock();
    try {
      channels[index] = new PlayChannelState(tokens, index == 0, lineLabel);
      remainingSeconds[index] = 0; // forces an immediate re-fetch on the very next tick
      pendingGapSeconds[index] = 0; // don't carry the replaced note's trailing gap into the new one
      done[index] = false;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public PlayFrame next(double maxDurationSeconds) {
    lock.lock();
    try {
      fillPendingNotes();
      boolean allDone = true;
      double sliceSeconds = maxDurationSeconds;
      for (int i = 0; i < channels.length; i++) {
        if (!done[i]) {
          allDone = false;
          sliceSeconds = Math.min(sliceSeconds, remainingSeconds[i]);
        }
      }
      if (allDone) {
        return new PlayFrame(VoiceFrame.SILENT, VoiceFrame.SILENT, VoiceFrame.SILENT, 0, true);
      }

      final VoiceFrame[] frames = {VoiceFrame.SILENT, VoiceFrame.SILENT, VoiceFrame.SILENT};
      for (int i = 0; i < channels.length; i++) {
        if (!done[i]) {
          frames[i] = voiceFrameFor(i);
        }
      }
      elapsedSeconds += sliceSeconds;
      for (int i = 0; i < channels.length; i++) {
        if (!done[i]) {
          remainingSeconds[i] -= sliceSeconds;
          if (remainingSeconds[i] <= EPSILON_SECONDS) {
            remainingSeconds[i] = 0; // fillPendingNotes() refills this channel on the next call
          }
        }
      }
      return new PlayFrame(frames[0], frames[1], frames[2], sliceSeconds, false);
    } finally {
      lock.unlock();
    }
  }

  // Caller must hold `lock`.
  private void fillPendingNotes() {
    for (int i = 0; i < channels.length; i++) {
      if (!done[i] && remainingSeconds[i] <= EPSILON_SECONDS) {
        if (pendingGapSeconds[i] > 0) {
          // The note itself has finished; play out its trailing articulation gap before moving on.
          remainingSeconds[i] = pendingGapSeconds[i];
          pendingGapSeconds[i] = 0;
          noteNumber[i] = null; // silent, exactly like a rest
          continue;
        }
        final var note = channels[i].nextNote(shared);
        if (note == null) {
          done[i] = true;
        } else {
          final double noteSeconds = ticksToSeconds(note.durationTicks());
          final double gapSeconds =
              Math.min(
                  Math.min(ticksToSeconds(ARTICULATION_GAP_TICKS), MAX_ARTICULATION_GAP_SECONDS),
                  noteSeconds / 2); // never let the gap dominate a very short note
          remainingSeconds[i] = noteSeconds - gapSeconds;
          pendingGapSeconds[i] = gapSeconds;
          noteNumber[i] = note.noteNumber();
          volume[i] = note.volume();
          useEnvelope[i] = note.useEnvelope();
        }
      }
    }
  }

  private double ticksToSeconds(int ticks) {
    final double secondsPerTick = 60.0 / (shared.tempoBpm() * 24.0);
    return ticks * secondsPerTick;
  }

  // Caller must hold `lock`.
  private VoiceFrame voiceFrameFor(int channelIndex) {
    if (noteNumber[channelIndex] == null) {
      return VoiceFrame.SILENT; // a rest is fully silent, regardless of mixer/noise settings
    }
    final double amplitude =
        useEnvelope[channelIndex] ? envelopeAmplitude() : volume[channelIndex] / 15.0;
    final double frequencyHz =
        Pitch.hzFromSemitonesAboveMiddleC(
            noteNumber[channelIndex] - 48); // octave 4 (48 semitones) is the reference octave
    final Integer mask = shared.mixerMask();
    final boolean toneOn;
    final boolean noiseOn;
    if (mask == null) {
      toneOn = true;
      noiseOn = false;
    } else {
      toneOn = (mask & (1 << channelIndex)) != 0;
      noiseOn = (mask & (8 << channelIndex)) != 0;
    }
    return new VoiceFrame(frequencyHz, amplitude, toneOn, noiseOn);
  }

  private double envelopeAmplitude() {
    final int raw = shared.envelopePeriodTicks();
    final double periodSeconds = raw <= 0 ? DEFAULT_ENVELOPE_PERIOD_SECONDS : raw / 1000.0;
    final double t = elapsedSeconds % periodSeconds;
    final double phase = t / periodSeconds;
    final boolean completedOnce = elapsedSeconds >= periodSeconds;
    return switch (shared.envelopeShape()) {
      case 0 -> completedOnce ? 0 : 1 - phase; // single decay then off
      case 1 -> completedOnce ? 0 : phase; // single attack then off
      case 2 ->
          completedOnce ? 0 : 1 - phase; // single decay then hold (hold level is 0 either way)
      case 3 -> completedOnce ? 1 : phase; // single attack then hold (holds at the peak)
      case 4 -> 1 - phase; // repeated decay
      case 5 -> phase; // repeated attack
      case 6 -> phase < 0.5 ? phase * 2 : (1 - phase) * 2; // repeated attack-decay (triangle)
      case 7 -> phase < 0.5 ? 1 - phase * 2 : (phase - 0.5) * 2; // repeated decay-attack
      default -> 1.0;
    };
  }
}
