package com.davidconneely.bazlang;

public interface Limits {
  int MIN_TARGET_LABEL = 0;
  int MIN_LINE_LABEL = 1;
  int MAX_LINE_LABEL = 999_999_999;
  int MAX_TARGET_LABEL = MAX_LINE_LABEL + 1;

  int MAX_ARRAY_ELEMENTS = 100_000_000;

  // Matches the ZX Spectrum print-zone width
  int TAB_WIDTH = 16;
}
