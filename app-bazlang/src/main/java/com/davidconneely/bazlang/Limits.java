package com.davidconneely.bazlang;

/** Shared numeric limits and sizing constants used across the interpreter. */
public interface Limits {
  /** The lowest valid {@code GOTO}/{@code GOSUB} target: line 0, the immediate-mode sentinel. */
  int MIN_TARGET_LABEL = 0;

  /** The lowest valid program line number. */
  int MIN_LINE_LABEL = 1;

  /** The highest valid program line number. */
  int MAX_LINE_LABEL = 999_999_999;

  /** The highest valid {@code GOTO}/{@code GOSUB} target. */
  int MAX_TARGET_LABEL = MAX_LINE_LABEL + 1;

  /** The largest total element count a single {@code DIM}'d array may have. */
  int MAX_ARRAY_ELEMENTS = 100_000_000;

  // Matches the ZX Spectrum print-zone width
  /** Column width of one {@code PRINT} tab zone (the {@code ,} separator's spacing). */
  int TAB_WIDTH = 16;
}
