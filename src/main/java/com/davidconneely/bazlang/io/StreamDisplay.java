package com.davidconneely.bazlang.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class StreamDisplay implements BazLangDisplay {
  private final InputStream in;
  private final PrintStream out;
  private final PrintStream err;
  private BufferedReader reader;

  // Simple cursor tracking for currentRow/currentCol
  private int currentCol = 0;

  public StreamDisplay(InputStream in, PrintStream out, PrintStream err) {
    this.in = in;
    this.out = out;
    this.err = err;
  }

  public StreamDisplay() {
    this(System.in, System.out, System.err);
  }

  @Override
  public int currentRow() {
    return 0;
  }

  @Override
  public int currentCol() {
    return currentCol;
  }

  @Override
  public int printWidth() {
    return 80;
  }

  @Override
  public int printHeight() {
    return 24;
  }

  @Override
  public int plotWidth() {
    return 80;
  }

  @Override
  public int plotHeight() {
    return 24;
  }

  @Override
  public int plotMode() {
    return 4; // QuadrantMode
  }

  @Override
  public void cls() {
    currentCol = 0;
    // Cannot clear screen on standard stream
  }

  @Override
  public void locate(int row, int col) {
    currentCol = col;
    // Cannot position cursor on standard stream
  }

  @Override
  public void plot(int x, int y) {
    // No-op for stream display
  }

  @Override
  public void unplot(int x, int y) {
    // No-op for stream display
  }

  @Override
  public void scroll() {
    out.println();
  }

  @Override
  public void print(String text) {
    if (text != null) {
      out.print(text);
      currentCol += text.length();
    }
  }

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    out.println();
    currentCol = 0;
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
    if (reader == null) {
      reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    try {
      String line = reader.readLine();
      if (line != null) {
        currentCol = 0;
      }
      return line;
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public void flush() {
    // No-op: stream display writes through immediately
  }

  @Override
  public String readln(com.davidconneely.repl.Display.InputMode mode) {
    return readln("");
  }

  @Override
  public String readReplLine() {
    return readln((String) null);
  }

  @Override
  public void prefillInput(String text) {
    // No-op: pre-filling is a REPL-specific hint; has no meaning in stream/batch mode
  }

  @Override
  public void setStatus(String status) {
    if (status != null && !status.isEmpty() && !status.equals("READY")) {
      println(status);
    }
  }

  @Override
  public void systemPrintln(String text) {
    if (currentCol > 0) {
      println();
    }
    println(text);
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
  public String uinkey() {
    // Simple fallback for streams without ANSI handling
    return inkey();
  }

  @Override
  public void setFastMode(boolean fast) {
    // No-op for stream display
  }

  @Override
  public void close() {
    // Do not close standard streams
  }
}
