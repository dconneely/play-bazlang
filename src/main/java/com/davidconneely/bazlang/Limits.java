package com.davidconneely.bazlang;

interface Limits {
  int MIN_TARGET_LABEL = 0;
  int MIN_LINE_LABEL = 1;
  int MAX_LINE_LABEL = 999_999_999;
  int MAX_TARGET_LABEL = MAX_LINE_LABEL + 1;

  int MAX_PAUSE_FRAMES = 999_999_999;
  long MAX_RAND_SEED = 999_999_999_999_999L;
  int MAX_ARRAY_ELEMENTS = 1_000_000_000;

  int TAB_WIDTH = 16;
}
