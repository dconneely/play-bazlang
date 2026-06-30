package com.davidconneely.repl;

public interface Canvas extends AutoCloseable {
  enum InputMode {
    INPUT_NUMERIC,
    INPUT_STRING
  }

  int currentRow();

  int currentCol();

  void cls();

  void locate(int row, int col);

  void plot(int x, int y);

  int point(int x, int y);

  int printWidth();

  int printHeight();

  int plotWidth();

  int plotHeight();

  int plotMode();

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

  /** Reads a multibyte sequence (UTF-8 character or terminal escape sequence) without blocking. */
  String uinkey();

  boolean pollForBreak();

  void setFastMode(boolean fast);

  void setInk(int colour);

  void setPaper(int colour);

  void setBright(int bright);

  void setFlash(int flash);

  void setInverse(int inverse);

  void setOver(int over);

  @Override
  void close();
}
