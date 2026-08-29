package com.davidconneely.bazlang.io;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The only {@link VirtualSpeaker} implementation that plays real audio - via the JDK's {@code
 * javax.sound.sampled} API (hence the name; nothing here is terminal-specific, unlike {@link
 * TerminalScreen}). {@code beep()} starts a square-wave {@link SourceDataLine} write on its own
 * daemon thread and returns immediately, so the interpreter thread stays free to run {@code
 * PAUSE}-style chunked BREAK-polling ({@code StatementExecutor.executeBeepStmt}) instead of
 * blocking inside the audio write for the tone's whole duration; {@code stopBeep()} cuts a tone
 * short on BREAK. {@code playFrame()} synthesises exactly the requested duration of up to 3 mixed
 * voices and writes it straight to a second, entirely independent persistent {@link SourceDataLine}
 * (opened lazily, reused for the session) on whichever thread called it - no render thread of its
 * own, per {@link VirtualSpeaker#playFrame}'s no-idle-silence contract (see ADR-0007); {@code
 * stopPlay()} flushes audio already queued but not yet heard, so a note cut short by BREAK or a
 * replacement stops promptly, while {@code drainPlay()} instead lets a naturally-finished note's
 * queued tail play out before parking the line, called only when a sound reaches its natural end
 * rather than being cut short. Keeping {@code PLAY}/{@code APLAY} on their own line means a {@code
 * BEEP} sound effect can layer over music without either interfering with the other, matching real
 * hardware's independent beeper/AY circuits. {@link LineUnavailableException} (no audio device -
 * e.g. a headless/SSH session) is caught and silently swallowed everywhere, matching the no-op
 * fallback {@link VirtualSpeaker} already provides elsewhere.
 *
 * <p>Owns two {@link SourceDataLine}s that need closing - {@link AutoCloseable} rather than tying
 * their lifetime to a screen. {@code MainClass} closes this alongside (not instead of) whichever
 * {@code VirtualScreen} it constructs.
 */
public final class JavaSoundSpeaker implements VirtualSpeaker, AutoCloseable {
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // ===== BEEP =====

  private static final float BEEP_SAMPLE_RATE = 44_100f;
  private static final int BEEP_CHUNK_SAMPLES = 4_410; // ~100ms/chunk: how often stopBeep() polls
  private static final short BEEP_AMPLITUDE = (short) (Short.MAX_VALUE * 0.3);
  // Explicit buffer size for both persistent lines, rather than whatever line.open(format) would
  // pick. Deliberately modest: since nothing ever queues idle silence (see playFrame's contract),
  // this bounds how much *real* audio can sit queued ahead of the speaker, and therefore the worst
  // case latency between triggering a sound and hearing it -- which matters for game feedback like
  // a paddle hit. Large enough to absorb ordinary OS scheduling jitter (comfortably above
  // Windows' ~15.6ms default scheduling quantum) without underrunning mid-note.
  private static final int LINE_BUFFER_BYTES = (int) (BEEP_SAMPLE_RATE * 0.08f) * 2; // ~80ms, mono

  // Opened lazily on the first BEEP and then kept open for the rest of the session (closed in
  // close()), rather than opened/closed fresh per call. Opening a line has real device/driver
  // startup latency - commonly tens of milliseconds - which is negligible against a 300ms+ jingle
  // note but can dominate (or exceed) a short ~50ms feedback blip's *nominal* duration, making it
  // barely audible. Reuse removes that latency from every call after the first, and also closes
  // the gap between chained notes in a jingle. Guarded by beepLock since beep() calls run on their
  // own short-lived thread (see beep()) and could otherwise race to open it concurrently - in
  // practice this can't happen from normal BASIC execution (StatementExecutor blocks for one
  // BEEP's duration before the interpreter can issue another), but the lock costs nothing and
  // keeps the field access correct regardless.
  private final ReentrantLock beepLock = new ReentrantLock();
  private SourceDataLine beepLine;
  private volatile AtomicBoolean beepPlaying;

  /** Converts a BEEP pitch (semitones above/below middle C) to Hz. Package-visible for testing. */
  static double frequencyForPitch(double pitch) {
    return Pitch.hzFromSemitonesAboveMiddleC(pitch);
  }

  @Override
  public void beep(double durationSeconds, double pitch) {
    if (durationSeconds <= 0) {
      return;
    }
    stopBeep();
    final double frequency = frequencyForPitch(pitch);
    final long totalSamples = Math.round(BEEP_SAMPLE_RATE * durationSeconds);
    final AtomicBoolean playing = new AtomicBoolean(true);
    beepPlaying = playing;
    final Thread player = new Thread(() -> playBeep(frequency, totalSamples, playing));
    player.setDaemon(true);
    player.setName("bazlang-beep");
    player.start();
  }

  @Override
  public void stopBeep() {
    final AtomicBoolean playing = beepPlaying;
    if (playing != null) {
      playing.set(false);
    }
    final SourceDataLine line;
    beepLock.lock();
    try {
      line = beepLine;
    } finally {
      beepLock.unlock();
    }
    if (line != null) {
      try {
        // flush(), not stop(): the line stays started for the rest of the session (see the field
        // doc above), so this only discards whatever's still queued from the interrupted tone -
        // it doesn't pause the line itself, which would otherwise need restarting before the next
        // beep() could write anything audible.
        line.flush();
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  /** Returns the persistent playback line, opening (and starting) it on first use. */
  private SourceDataLine ensureBeepLine() throws LineUnavailableException {
    beepLock.lock();
    try {
      if (beepLine == null) {
        final AudioFormat format = new AudioFormat(BEEP_SAMPLE_RATE, 16, 1, true, false);
        final SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();
        beepLine = line;
      }
      return beepLine;
    } finally {
      beepLock.unlock();
    }
  }

  // Runs on its own daemon thread (see beep()) so the interpreter thread stays free to run its
  // own PAUSE-style chunked wait/BREAK-poll loop (StatementExecutor.executeBeepStmt) rather than
  // blocking inside SourceDataLine.write()/drain() for the tone's whole duration.
  private void playBeep(double frequencyHz, long totalSamples, AtomicBoolean playing) {
    try {
      final SourceDataLine line = ensureBeepLine();
      final byte[] chunk = new byte[BEEP_CHUNK_SAMPLES * 2];
      long samplesWritten = 0;
      while (samplesWritten < totalSamples && playing.get()) {
        final int chunkSamples = (int) Math.min(BEEP_CHUNK_SAMPLES, totalSamples - samplesWritten);
        fillSquareWave(chunk, chunkSamples, frequencyHz, samplesWritten);
        line.write(chunk, 0, chunkSamples * 2);
        samplesWritten += chunkSamples;
      }
      if (playing.get()) {
        line.drain();
      }
    } catch (LineUnavailableException | IllegalStateException e) {
      // No audio device available (e.g. a headless/SSH session) - BEEP is silent, not fatal.
    }
  }

  /** Fills {@code buffer} with {@code sampleCount} 16-bit signed LE mono square-wave samples. */
  private static void fillSquareWave(
      byte[] buffer, int sampleCount, double frequencyHz, long startSampleIndex) {
    final double samplesPerCycle = BEEP_SAMPLE_RATE / frequencyHz;
    for (int i = 0; i < sampleCount; i++) {
      final boolean highHalf = (startSampleIndex + i) % samplesPerCycle < samplesPerCycle / 2;
      final short sample = highHalf ? BEEP_AMPLITUDE : (short) -BEEP_AMPLITUDE;
      buffer[i * 2] = (byte) (sample & 0xFF);
      buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
  }

  // ===== PLAY / APLAY =====

  // Scaled down from BEEP_AMPLITUDE since up to 3 voices (each possibly tone+noise combined) are
  // summed into one buffer before writing; final samples are clamped defensively regardless.
  private static final short PLAY_TONE_AMPLITUDE = (short) (Short.MAX_VALUE * 0.2);
  // The DSL has no command that sets the shared noise generator's own pitch (unlike a channel's
  // tone, which comes from the note being played) -- no source consulted specifies one either, so
  // this toggle rate is a reasonable, documented default rather than a confirmed hardware value.
  private static final int PLAY_NOISE_TOGGLE_SAMPLES = 8;

  // Entirely independent lock/line from BEEP's own (see ensureBeepLine()) so a BEEP sound effect
  // can still sound while PLAY/APLAY music is playing, matching real hardware's independent
  // beeper/AY circuits -- see VirtualSpeaker's class doc.
  //
  // There is deliberately no render thread here: playFrame() synthesises exactly the audio it was
  // asked for and writes it straight to the line, on whichever thread called it (the interpreter
  // thread for blocking PLAY, the background APLAY thread otherwise). An earlier design used a
  // persistent render loop continuously writing whatever voices were last pushed to it -- which,
  // because a SourceDataLine is a FIFO that only blocks once full, meant it kept a whole buffer of
  // *silence* permanently queued ahead of the speaker, so every newly-triggered note landed behind
  // that backlog and played a full buffer-depth late (and short notes were easily clipped by the
  // fixed sampling granularity such a loop needs). See VirtualSpeaker.playFrame's contract.
  private final ReentrantLock playLock = new ReentrantLock();
  private SourceDataLine playLine;
  // Sample position and noise-LFSR state carried across successive playFrame() calls so a note
  // spanning several calls stays phase-continuous instead of clicking at every boundary. Guarded
  // by playLock, which the whole of playFrame() holds -- PLAY and APLAY never render concurrently
  // by design (a blocking PLAY stops any background APLAY first), but the lock keeps that a
  // correctness property of this class rather than an assumption about its callers.
  private final PlayRenderState playRenderState = new PlayRenderState();

  /** Mutable render-position state; see {@link #playRenderState}. */
  private static final class PlayRenderState {
    private long sampleIndex;
    private long noiseLfsr = 0x1FFFF;
  }

  @Override
  public void playFrame(VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds) {
    if (durationSeconds <= 0) {
      return;
    }
    final int totalSamples = (int) Math.round(BEEP_SAMPLE_RATE * durationSeconds);
    if (totalSamples <= 0) {
      return;
    }
    playLock.lock();
    try {
      final SourceDataLine line = ensurePlayLine();
      // No-op when already running; resumes the line after drainPlay() parked it between sounds.
      line.start();
      final byte[] buffer = new byte[totalSamples * 2];
      playRenderState.noiseLfsr =
          fillPlayMix(
              buffer,
              totalSamples,
              a,
              b,
              c,
              playRenderState.sampleIndex,
              playRenderState.noiseLfsr);
      playRenderState.sampleIndex += totalSamples;
      line.write(buffer, 0, buffer.length);
    } catch (LineUnavailableException | IllegalStateException e) {
      // No audio device available (e.g. a headless/SSH session) - PLAY/APLAY is silent, not fatal.
    } finally {
      playLock.unlock();
    }
  }

  @Override
  public void stopPlay() {
    final SourceDataLine line;
    playLock.lock();
    try {
      line = playLine;
    } finally {
      playLock.unlock();
    }
    if (line != null) {
      try {
        // Discards audio already queued but not yet heard, so a note cut short (BREAK, or a fresh
        // PLAY/APLAY replacing it) stops promptly rather than playing out the rest of the buffer.
        line.stop();
        line.flush();
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  @Override
  public void drainPlay() {
    final SourceDataLine line;
    playLock.lock();
    try {
      line = playLine;
    } finally {
      playLock.unlock();
    }
    if (line != null) {
      try {
        line.drain(); // let the tail of the last note actually play before parking the line
        line.stop();
        line.flush(); // leave no stale samples a starved line could otherwise replay
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  /** Returns the persistent playback line, opening (and starting) it on first use. */
  private SourceDataLine ensurePlayLine() throws LineUnavailableException {
    playLock.lock();
    try {
      if (playLine == null) {
        final AudioFormat format = new AudioFormat(BEEP_SAMPLE_RATE, 16, 1, true, false);
        final SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();
        playLine = line;
      }
      return playLine;
    } finally {
      playLock.unlock();
    }
  }

  /** Mixes up to 3 voices (tone and/or shared noise) into {@code buffer}. */
  private static long fillPlayMix(
      byte[] buffer,
      int sampleCount,
      VoiceFrame a,
      VoiceFrame b,
      VoiceFrame c,
      long startSampleIndex,
      long lfsrIn) {
    long lfsr = lfsrIn;
    for (int i = 0; i < sampleCount; i++) {
      if ((startSampleIndex + i) % PLAY_NOISE_TOGGLE_SAMPLES == 0) {
        final long bit = (lfsr ^ (lfsr >> 3)) & 1;
        lfsr = (lfsr >> 1) | (bit << 16);
      }
      final boolean noiseHigh = (lfsr & 1) != 0;
      final double mixed =
          voiceSample(a, startSampleIndex + i, noiseHigh)
              + voiceSample(b, startSampleIndex + i, noiseHigh)
              + voiceSample(c, startSampleIndex + i, noiseHigh);
      final short sample =
          (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed)));
      buffer[i * 2] = (byte) (sample & 0xFF);
      buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return lfsr;
  }

  private static double voiceSample(VoiceFrame voice, long sampleIndex, boolean noiseHigh) {
    if (voice.amplitude() <= 0 || (!voice.toneOn() && !voice.noiseOn())) {
      return 0;
    }
    double value = 0;
    if (voice.toneOn() && voice.frequencyHz() > 0) {
      final double samplesPerCycle = BEEP_SAMPLE_RATE / voice.frequencyHz();
      value += squareWaveAverage(sampleIndex, samplesPerCycle);
    }
    if (voice.noiseOn()) {
      value += noiseHigh ? 1.0 : -1.0;
    }
    return value * voice.amplitude() * PLAY_TONE_AMPLITUDE;
  }

  /**
   * Averages an ideal +1/-1 square wave (high for the first half of each {@code period}-sample
   * cycle) over the one-sample interval starting at continuous position {@code t}, rather than
   * point-sampling it at {@code t} alone - a transition that falls mid-sample then lands on a
   * fractional value instead of always snapping to a whole-sample boundary, which is what a naive
   * point-sampled square wave does and why it aliases audibly on higher notes. {@code period} is
   * clamped to at least 2 samples/cycle (the Nyquist limit for a square wave's fundamental) so the
   * loop below always terminates in a handful of steps. Package-visible for testing.
   */
  static double squareWaveAverage(long t, double period) {
    final double clampedPeriod = Math.max(period, 2.0);
    final double half = clampedPeriod / 2.0;
    double position = t % clampedPeriod;
    if (position < 0) {
      position += clampedPeriod;
    }
    double remaining = 1.0; // sample interval length still to cover
    double sum = 0.0;
    while (remaining > 0) {
      final boolean high = position < half;
      final double toBoundary = (high ? half : clampedPeriod) - position;
      final double segment = Math.min(remaining, toBoundary);
      sum += segment * (high ? 1.0 : -1.0);
      remaining -= segment;
      position += segment;
      if (position >= clampedPeriod) {
        position -= clampedPeriod;
      }
    }
    return sum; // the interval length is exactly 1, so the sum is already the average
  }

  // ===== AutoCloseable =====

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    stopBeep();
    beepLock.lock();
    try {
      if (beepLine != null) {
        beepLine.close();
        beepLine = null;
      }
    } finally {
      beepLock.unlock();
    }
    stopPlay();
    playLock.lock();
    try {
      if (playLine != null) {
        playLine.close();
        playLine = null;
      }
    } finally {
      playLock.unlock();
    }
  }
}
