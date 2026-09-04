package com.davidconneely.bazlang.play;

/**
 * A live, pull-based {@code PLAY}/{@code APLAY} sequencer. Pull-based (rather than a pre-flattened
 * list of frames) because the DSL supports an infinite repeat - a static list cannot represent
 * looping background music of unbounded length. Consumed entirely by {@code StatementExecutor}'s
 * own wait loop (mirroring {@code executeBeepStmt}'s chunked-sleep/BREAK-poll shape), which pushes
 * each resolved frame's voices to {@code VirtualSpeaker.playFrame} - {@code VirtualSpeaker} itself
 * never touches this interface, keeping DSL-specific pull/timing logic entirely out of the {@code
 * io} package.
 */
@FunctionalInterface
public interface PlaySource {
  /**
   * Advances playback by up to {@code maxDurationSeconds} and returns the voice parameters that
   * were current for that slice. The returned {@link PlayFrame#durationSeconds} may be less than
   * requested (playback stops early at a note boundary) but is never more.
   *
   * @param maxDurationSeconds the maximum duration to advance by, in seconds.
   * @return the frame covering the advanced slice.
   */
  PlayFrame next(double maxDurationSeconds);

  /**
   * Replaces just channel {@code index}'s content, leaving every other channel's in-progress state
   * - including an in-progress infinite repeat - completely untouched. {@code "-"} (trimmed) is a
   * reserved placeholder meaning "leave this channel alone"; only meaningful against a live {@code
   * APLAY} session (see {@code StatementExecutor.executeAplayStmt}), where it's checked before this
   * method is even called. A default no-op so {@code PlaySource} stays usable without every
   * implementation needing to support targeted updates.
   *
   * @param index the channel to replace (0=A, 1=B, 2=C).
   * @param channelDsl the new channel content, in {@code PLAY}/{@code APLAY} DSL syntax.
   * @param lineLabel the line the update came from, for error reporting.
   * @param statementIndex the flat statement index the update came from, for error reporting.
   */
  default void replaceChannel(int index, String channelDsl, int lineLabel, int statementIndex) {}
}
