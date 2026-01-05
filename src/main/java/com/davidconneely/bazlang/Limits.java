package com.davidconneely.bazlang;

interface Limits {
  final int MIN_TARGET_LABEL = 0;
  final int MIN_LINE_LABEL = 1;
  final int MAX_LINE_LABEL = 999_999_999;
  final int MAX_TARGET_LABEL = MAX_LINE_LABEL + 1;

  final int MAX_PAUSE_FRAMES = 999_999_999;
  final long MAX_RAND_SEED = 999_999_999_999_999L;
  final int MAX_ARRAY_ELEMENTS = 1_000_000_000;

  final int TAB_WIDTH = 16;
}
