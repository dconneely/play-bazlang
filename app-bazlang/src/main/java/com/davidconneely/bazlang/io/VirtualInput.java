package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;

public interface VirtualInput {
  enum InputMode {
    INPUT_NUMERIC,
    INPUT_STRING
  }

  String readln(InputMode mode);

  String readln(String prompt);

  default boolean isInteractive() {
    return false;
  }

  default void prefillInput(String text) {}

  default boolean pollForBreak() {
    return false;
  }

  default BStr inkey() {
    return BStr.EMPTY;
  }

  /** Reads a multibyte sequence (UTF-8 character or terminal escape sequence) without blocking. */
  default BStr uinkey() {
    return BStr.EMPTY;
  }
}
