package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.QuadrantMode;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A headless screen implementation used primarily for testing, but located in src/main/java because
 * it is also used as a load-bearing dependency by the headless AgentDebugger.
 */
public class MockScreen extends AbstractCellBufferedScreen {
  private int rows;
  private int cols;

  private final StringBuilder output = new StringBuilder();
  private final List<String> inputs;
  private int inputIdx = 0;
  private String status = null;
  private String prefillText = null;
  private boolean simulatedBreak = false;

  private final Queue<BStr> inkeyQueue = new ConcurrentLinkedQueue<>();
  private final Queue<BStr> uinkeyQueue = new ConcurrentLinkedQueue<>();
  private final Queue<String> inputQueue = new ConcurrentLinkedQueue<>();

  public MockScreen() {
    this(25, 80, Collections.emptyList());
  }

  public MockScreen(List<String> inputs) {
    this(25, 80, inputs);
  }

  public MockScreen(int rows, int cols) {
    this(rows, cols, Collections.emptyList());
  }

  public MockScreen(int rows, int cols, List<String> inputs) {
    super(new CellBuffer(rows, cols, QuadrantMode.INSTANCE));
    this.rows = rows;
    this.cols = cols;
    this.inputs = inputs;
  }

  public void resize(int newRows, int newCols) {
    this.rows = newRows;
    this.cols = newCols;
    cellBuffer.resize(newRows, newCols);
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
        if (r >= 0 && r < rows && c >= 0 && c < cols) {
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

  public void queueInput(String text) {
    inputQueue.add(text);
  }

  @Override
  public String readln(String prompt) {
    String queued = inputQueue.poll();
    if (queued != null) {
      if (prompt != null) {
        print(prompt);
      }
      println(queued);
      return queued;
    }
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

  /**
   * Discards all queued {@code INKEY$}/{@code UINKEY$}/{@code INPUT} text without consuming it.
   * Used when switching to a different programme, so input queued for one programme can never be
   * silently consumed by a different one that happens to read a different input primitive.
   */
  public void clearInputQueues() {
    inkeyQueue.clear();
    uinkeyQueue.clear();
    inputQueue.clear();
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

  private boolean interactive = true;

  public void setInteractive(boolean interactive) {
    this.interactive = interactive;
  }

  @Override
  public boolean isInteractive() {
    return interactive;
  }

  @Override
  public void close() {
    // No-op
  }
}
