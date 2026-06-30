package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.QuadrantMode;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MockScreen extends AbstractCellBufferedScreen {
  private static final int ROWS = 25;
  private static final int COLS = 80;

  private final StringBuilder output = new StringBuilder();
  private final List<String> inputs;
  private int inputIdx = 0;
  private String status = null;
  private String prefillText = null;
  private boolean simulatedBreak = false;

  private final Queue<BStr> inkeyQueue = new ConcurrentLinkedQueue<>();
  private final Queue<BStr> uinkeyQueue = new ConcurrentLinkedQueue<>();

  public MockScreen() {
    this(Collections.emptyList());
  }

  public MockScreen(List<String> inputs) {
    super(new CellBuffer(ROWS, COLS, QuadrantMode.INSTANCE));
    this.inputs = inputs;
  }

  public String getOutput() {
    return output.toString();
  }

  @Override
  public void cls() {
    cursorRow = 0;
    cursorCol = 0;
    cellBuffer.clear();
  }

  @Override
  public void print(String text) {
    output.append(text);
    if (text != null) {
      int idx = 0;
      while (idx < text.length()) {
        int cp = text.codePointAt(idx);
        int r = cursorRow;
        int c = cursorCol;
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
          int ink = activeInverse == 1 ? activePaper : activeInk;
          int paper = activeInverse == 1 ? activeInk : activePaper;
          int cellFg = getMappedColour(ink, paper);
          int cellBg = getMappedColour(paper, ink);
          int currentStyle = cellBuffer.getStyle(r, c);
          int cellStyle = getMappedStyle(currentStyle);
          cellBuffer.setCell(r, c, cp, cellFg, cellBg, cellStyle);
        }
        cursorCol++;
        idx += Character.charCount(cp);
      }
    }
  }

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    output.append('\n');
    cursorRow++;
    cursorCol = 0;
  }

  @Override
  public void scroll() {
    println();
  }

  @Override
  public void lprint(String text) {
    output.append(text);
  }

  @Override
  public void lprintln(String text) {
    output.append(text).append('\n');
  }

  @Override
  public void lprintln() {
    output.append('\n');
  }

  @Override
  public String readln(String prompt) {
    if (inputIdx < inputs.size()) {
      String input = inputs.get(inputIdx++);
      if (prompt != null) {
        print(prompt);
      }
      println(input);
      return input;
    }
    return "";
  }

  @Override
  public void flush() {
    // No-op in tests
  }

  @Override
  protected void afterPlot() {
    // No-op in tests
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
  public void systemPrintln(String text) {
    println(text);
  }

  @Override
  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }

  @Override
  public void prefillInput(String text) {
    this.prefillText = text;
  }

  public String getPrefillText() {
    return prefillText;
  }

  public void triggerBreak() {
    simulatedBreak = true;
  }

  @Override
  public boolean pollForBreak() {
    if (simulatedBreak) {
      simulatedBreak = false;
      return true;
    }
    return false;
  }

  public void queueInkey(BStr val) {
    inkeyQueue.add(val);
  }

  public void queueUinkey(BStr val) {
    uinkeyQueue.add(val);
  }

  @Override
  public BStr inkey() {
    final var val = inkeyQueue.poll();
    return val != null ? val : BStr.EMPTY;
  }

  @Override
  public BStr uinkey() {
    final var val = uinkeyQueue.poll();
    return val != null ? val : BStr.EMPTY;
  }

  @Override
  public boolean isInteractive() {
    return true;
  }

  @Override
  public void close() {
    // No-op
  }
}
