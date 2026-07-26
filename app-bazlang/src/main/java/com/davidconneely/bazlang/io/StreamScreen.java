package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class StreamScreen implements VirtualScreen, VirtualInput {
  private final InputStream in;
  private final PrintStream out;
  private BufferedReader reader;

  // Simple cursor tracking for currentRow/currentCol
  private int currentCol = 0;

  public StreamScreen(InputStream in, PrintStream out) {
    this.in = in;
    this.out = out;
  }

  public StreamScreen() {
    this(System.in, System.out);
  }

  public static StreamScreen nullScreen() {
    final var nullOut =
        new PrintStream(java.io.OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    return new StreamScreen(InputStream.nullInputStream(), nullOut);
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
    return 25;
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
  public void scroll() {
    out.print('\n');
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
    out.print('\n');
    currentCol = 0;
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
    // No-op: stream screen writes through immediately
  }

  @Override
  public String readln(InputMode mode) {
    return readln("");
  }

  @Override
  public String readReplInput() {
    return readln((String) null);
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
  public BStr inkey() {
    try {
      if (in.available() > 0) {
        int c = in.read();
        if (c >= 0) {
          return BStr.fromByte(c);
        }
      }
    } catch (IOException e) {
      // Ignore
    }
    return BStr.EMPTY;
  }

  @Override
  public BStr uinkey() {
    try {
      if (in.available() == 0) {
        return BStr.EMPTY;
      }
      final BStr seq = KeyDecoder.decodeSequence(() -> in.available() > 0 ? in.read() : -1);
      return seq != null ? seq : BStr.EMPTY;
    } catch (IOException e) {
      return BStr.EMPTY;
    }
  }

  @Override
  public void close() {
    // Do not close standard streams
  }
}
