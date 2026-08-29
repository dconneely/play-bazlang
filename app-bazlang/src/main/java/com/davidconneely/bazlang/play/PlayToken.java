package com.davidconneely.bazlang.play;

/**
 * One parsed command from a {@code PLAY}/{@code APLAY} channel string, produced by {@link
 * PlayParser} and walked by {@link PlayChannelState}. Sealed so {@link PlayChannelState}'s switch
 * over these stays exhaustive.
 */
sealed interface PlayToken {
  /**
   * A note. {@code semitoneOffset} is 0-11 for a natural note (already adjusted for any {@code
   * #}/{@code $} accidentals, so it can fall outside 0-11), {@code octaveUp} is {@code true} for an
   * upper-case note letter (one octave above the channel's current {@code O} setting).
   */
  record Note(int semitoneOffset, boolean octaveUp) implements PlayToken {}

  /** A rest ({@code &}): silence for the current note duration. */
  record Rest() implements PlayToken {}

  /** {@code O0}-{@code O8}: sets the current octave. */
  record SetOctave(int octave) implements PlayToken {}

  /** A duration digit {@code 1}-{@code 12}: sets the current note duration (persists). */
  record SetDuration(int code) implements PlayToken {}

  /**
   * {@code <firstCode>_<secondCode>}: ties two duration codes into one note/rest whose length is
   * their sum (e.g. {@code 3_5A} plays a crotchet+quaver-length A - the ZX Spectrum 128 manual's
   * own example) - applies to exactly the note/rest immediately following it. {@code secondCode}
   * becomes the persisted duration for subsequent untied notes, matching the manual's own note that
   * the second duration given "will also apply to any following codes" until changed.
   */
  record TiedDuration(int firstCode, int secondCode) implements PlayToken {}

  /** {@code T60}-{@code T240}: sets the tempo (only honoured from the first channel string). */
  record SetTempo(int bpm) implements PlayToken {}

  /** {@code V0}-{@code V15}: sets the current channel's volume. */
  record SetVolume(int level) implements PlayToken {}

  /** {@code U}: switches this channel's volume source to the shared envelope generator. */
  record UseEnvelope() implements PlayToken {}

  /** {@code W0}-{@code W7}: sets the shared envelope generator's shape. */
  record SetEnvelopeShape(int shape) implements PlayToken {}

  /** {@code X0}-{@code X65535}: sets the shared envelope generator's period. */
  record SetEnvelopeDuration(int period) implements PlayToken {}

  /** {@code M0}-{@code M63}: sets the shared mixer's tone/noise enable bitmask. */
  record SetMixer(int mask) implements PlayToken {}

  /** {@code (}: marks the start of a repeated phrase. */
  record RepeatStart() implements PlayToken {}

  /**
   * {@code )}: marks the end of a repeated phrase. {@code matchingStartIndex} is the token index of
   * the corresponding {@link RepeatStart} (ignored if {@code infinite}). {@code infinite} means
   * this {@code )} had no matching {@code (} - the whole string up to this point repeats forever.
   */
  record RepeatEnd(int matchingStartIndex, boolean infinite) implements PlayToken {}

  /** {@code H}: halts this channel's playback (this string plays no further notes). */
  record Halt() implements PlayToken {}
}
