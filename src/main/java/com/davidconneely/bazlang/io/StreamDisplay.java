package com.davidconneely.bazlang.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public class StreamDisplay extends BufferedDisplay {
  private final InputStream in;
  private final PrintStream out;
  private final PrintStream err;
  private Scanner scanner;

  public StreamDisplay(InputStream in, PrintStream out, PrintStream err) {
    super();
    this.in = in;
    this.out = out;
    this.err = err;
  }

  public StreamDisplay() {
    this(System.in, System.out, System.err);
  }

  @Override
  protected void rawPrint(String text) {
    out.print(text);
  }

  @Override
  protected void rawLocate(int row, int col) {
    // Cannot locate on standard stream
  }

  @Override
  protected void rawScroll() {
    out.println();
  }

  @Override
  protected void rawCls() {
    // Cannot clear screen on standard stream
  }

  @Override
  public void lprint(String text) {
    err.print(text);
  }

  @Override
  public void lprintln(String text) {
    err.println(text);
  }

  @Override
  public void lprintln() {
    err.println();
  }

  @Override
  public String readln(String prompt) {
    if (prompt != null) {
      print(prompt);
    }
    if (scanner == null) {
      scanner = new Scanner(in);
    }
    if (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      currentRow++;
      currentCol = 0;
      return line;
    }
    return null;
  }

  @Override
  public boolean pollForBreak() {
    return false;
  }

  @Override
  public String inkey() {
    try {
      if (in.available() > 0) {
        int c = in.read();
        if (c >= 0) {
          return String.valueOf((char) c);
        }
      }
    } catch (IOException e) {
      // Ignore
    }
    return "";
  }

  @Override
  public void close() {
    // Do not close standard streams
  }
}
