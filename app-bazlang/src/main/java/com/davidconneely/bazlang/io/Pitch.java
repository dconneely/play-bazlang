package com.davidconneely.bazlang.io;

/**
 * Equal-temperament pitch math shared by {@code BEEP} and {@code PLAY}, anchored at middle C
 * (concert pitch, A4 = 440Hz). Both statements express pitch as semitones relative to this same
 * reference, so extracting it here (rather than duplicating it per-statement) keeps their tuning
 * identical by construction.
 */
public final class Pitch {
  private Pitch() {}

  /** C4, concert pitch (A4 = 440Hz). */
  public static final double MIDDLE_C_HZ = 261.6256;

  /** Converts semitones above (or below, if negative) middle C to a frequency in Hz. */
  public static double hzFromSemitonesAboveMiddleC(double semitones) {
    return MIDDLE_C_HZ * Math.pow(2.0, semitones / 12.0);
  }
}
