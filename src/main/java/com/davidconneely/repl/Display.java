package com.davidconneely.repl;

public interface Display extends AutoCloseable {
  enum InputMode {
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

  void flush();

  String readln(InputMode mode);

  String readln(String prompt);

  void prefillInput(String text);

  String inkey();

  boolean pollForBreak();

  void setFastMode(boolean fast);

  @Override
  void close();
}
