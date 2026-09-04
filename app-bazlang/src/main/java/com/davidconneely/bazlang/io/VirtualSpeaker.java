package com.davidconneely.bazlang.io;

/**
 * Audio output for {@code BEEP}. Deliberately its own interface rather than another {@link
 * VirtualScreen} default method: audio is not a screen concern at all (unlike graphics vs. text,
 * which are at least both visual), and {@code VirtualScreen} is already flagged as too wide (see
 * the architecture assessment in {@code localonly-BAZLANG-IMPROVEMENTS.md}) - defining this at
 * birth avoids ever having a "wide VirtualScreen" problem for audio in the first place.
 *
 * <p>Both methods default to a no-op, so headless implementations ({@link MockScreen}, {@link
 * StreamScreen}, {@link TerminalScreen}) get silent {@code BEEP} for free, exactly like {@link
 * VirtualScreen#setFastMode}. Only {@link JavaSoundSpeaker} overrides them, playing a real tone.
 *
 * <p>{@link #beep} is expected to start playback and return promptly rather than block for {@code
 * durationSeconds} - the caller ({@code StatementExecutor.executeBeepStmt}) drives its own chunked
 * wait, polling for {@code BREAK} exactly as {@code PAUSE} does, and calls {@link #stopBeep} to cut
 * a tone short if {@code BREAK} fires. An implementation that needs a thread to honour that
 * contract (e.g. a real audio line, whose {@code write}/{@code drain} would otherwise block the
 * caller for the tone's duration) is free to own one internally; the interface makes no promise
 * either way, and callers must not assume one exists.
 */
public interface VirtualSpeaker {

  /**
   * Starts a square-wave tone at {@code durationSeconds}/{@code pitch}; see the class doc.
   *
   * @param durationSeconds the tone's duration, in seconds.
   * @param pitch the tone's pitch, in Hz.
   */
  default void beep(double durationSeconds, double pitch) {}

  /** Stops whatever tone is currently sounding, if any. No-op if nothing is playing. */
  default void stopBeep() {}

  /**
   * Renders exactly {@code durationSeconds} of up to 3 simultaneous {@code PLAY}/{@code APLAY}
   * voices, writing that audio directly to the output.
   *
   * <p>Deliberately writes only real, requested audio - it must never free-run, continuously
   * queueing silence while idle. An audio line is a FIFO whose {@code write} blocks only once the
   * buffer is full, so a loop that keeps writing silence stays a whole buffer ahead of the speaker
   * at all times, and every subsequently-triggered note lands *behind* that backlog: constant
   * latency equal to the buffer depth, plus short notes getting clipped by the coarse sampling
   * granularity such a design needs. That was a real, user-visible bug (game sound effects arriving
   * late, or seeming to go missing entirely) - this contract exists specifically to rule it out.
   * Writing only real audio also means the line's own backpressure provides playback pacing for
   * free, so callers need no sleep-based cadence of their own.
   *
   * <p>Blocks for roughly {@code durationSeconds} once the line's buffer is saturated (that being
   * the backpressure above), so callers should keep individual calls short enough to stay
   * responsive to {@code BREAK}. Entirely independent of {@link #beep}/{@link #stopBeep}'s own
   * state - a {@code BEEP} sound effect can still sound while {@code PLAY}/{@code APLAY} music is
   * playing, matching the real hardware's independent beeper/AY circuits.
   *
   * @param a channel A's voice for this frame.
   * @param b channel B's voice for this frame.
   * @param c channel C's voice for this frame.
   * @param durationSeconds how long this frame lasts, in seconds.
   */
  default void playFrame(VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds) {}

  /**
   * Abandons {@code PLAY}/{@code APLAY} audio immediately, discarding anything already queued but
   * not yet heard. For cutting a sound short - {@code BREAK}, or a fresh call replacing it. Use
   * {@link #drainPlay} instead when a sound has simply reached its natural end, since discarding
   * the queue there would clip its tail.
   */
  default void stopPlay() {}

  /**
   * Lets already-queued audio finish playing, then parks the output until the next {@link
   * #playFrame}. Called once when a sound reaches its natural end, so that (unlike {@link
   * #stopPlay}) the tail isn't clipped, and so the output isn't left running empty and starved
   * indefinitely between sounds - some audio backends handle a permanently-underrunning line badly,
   * replaying stale buffer contents as spurious repeats of earlier sounds.
   */
  default void drainPlay() {}
}
