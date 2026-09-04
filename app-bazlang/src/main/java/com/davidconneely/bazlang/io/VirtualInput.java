package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.repl.ReplReader;

/** The interpreter's abstraction over a source of user input, for {@code INPUT} and key reads. */
public interface VirtualInput extends ReplReader {
  /** Which kind of value {@link #readln(InputMode)} should parse the line as. */
  enum InputMode {
    /** Parse the line as a number. */
    INPUT_NUMERIC,
    /** Take the line as a string, unparsed. */
    INPUT_STRING
  }

  /**
   * Read a line of input for {@code INPUT}, re-prompting on a value that doesn't fit {@code mode}.
   *
   * @param mode which kind of value is expected.
   * @return the line entered.
   */
  String readln(InputMode mode);

  /**
   * Read a line of input, displaying the given prompt first.
   *
   * @param prompt the prompt text to display.
   * @return the line entered.
   */
  String readln(String prompt);

  /**
   * Whether this input source is an interactive terminal (as opposed to, e.g., a fixed test
   * script).
   *
   * @return {@code true} if interactive; {@code false} by default.
   */
  default boolean isInteractive() {
    return false;
  }

  /**
   * Pre-populate the next line read with the given text, if this input source supports it. No-op by
   * default.
   *
   * @param text the text to pre-populate.
   */
  default void prefillInput(String text) {}

  /**
   * Poll for a pending break (Ctrl+C-style interrupt) without blocking.
   *
   * @return {@code true} if a break is pending; {@code false} by default.
   */
  default boolean pollForBreak() {
    return false;
  }

  /**
   * Read a single pending key without blocking.
   *
   * @return the key read, or {@link BStr#EMPTY} if none is pending, by default.
   */
  default BStr inkey() {
    return BStr.EMPTY;
  }

  /**
   * Reads a multibyte sequence (UTF-8 character or terminal escape sequence) without blocking.
   *
   * @return the sequence read, or {@link BStr#EMPTY} if none is pending, by default.
   */
  default BStr uinkey() {
    return BStr.EMPTY;
  }
}
