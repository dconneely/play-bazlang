package com.davidconneely.bazlang.io;

public interface Display extends AutoCloseable {
  /** Input mode for readln(). */
  enum InputMode {
    REPL,
    INPUT_NUMERIC,
    INPUT_STRING
  }

  int currentRow();

  int currentCol();

  void cls();

  void locate(int row, int col);

  void plot(int x, int y);

  void unplot(int x, int y);

  void scroll();

  void print(String text);

  void println(String text);

  void println();

  void lprint(String text);

  void lprintln(String text);

  void lprintln();

  /** Read a line of input with mode-specific prompts and hints. */
  default String readln(InputMode mode) {
    return readln("");
  }

  String readln(String prompt);

  /** Pre-fill the input buffer so next readln() starts with this text. */
  void prefillInput(String text);

  /** Set the status bar text (shown until next input). */
  default void setStatus(String status) {
    // Default: no-op for displays without status area
  }

  boolean pollForBreak();

  /** Flush any pending output to the display (e.g. after semicolon-terminated PRINT). */
  default void flush() {
    // Default: no-op for displays that write through immediately
  }

  String inkey();

  @Override
  void close();

  class BreakException extends RuntimeException {}
}
