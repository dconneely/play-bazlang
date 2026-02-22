package com.davidconneely.bazlang.io;

public interface Display extends AutoCloseable {
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

  String readln(String prompt);

  /** Pre-fill the input buffer so next readln() starts with this text. */
  void prefillInput(String text);

  boolean pollForBreak();

  String inkey();

  @Override
  void close();

  class BreakException extends RuntimeException {}
}
