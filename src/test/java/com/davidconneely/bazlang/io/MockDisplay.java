package com.davidconneely.bazlang.io;

import java.util.Collections;
import java.util.List;

public class MockDisplay implements Display {
  private final StringBuilder output = new StringBuilder();
  private final List<String> inputs;
  private int inputIdx = 0;
  private int currentRow = 0;
  private int currentCol = 0;

  public MockDisplay() {
    this(Collections.emptyList());
  }

  public MockDisplay(List<String> inputs) {
    this.inputs = inputs;
  }

  public String getOutput() {
    return output.toString();
  }

  public void clearOutput() {
    output.setLength(0);
  }

  @Override
  public int currentRow() {
    return currentRow;
  }

  @Override
  public int currentCol() {
    return currentCol;
  }

  @Override
  public void cls() {
    // Ideally simulate screen clear, but for now just reset cursor
    currentRow = 0;
    currentCol = 0;
  }

  @Override
  public void locate(int row, int col) {
    currentRow = row;
    currentCol = col;
    // Note: This doesn't simulate moving the "cursor" in the output buffer for tests
    // that check simple string output, but it updates state for AT/TAB logic.
  }

  @Override
  public void plot(int x, int y) {
    print("█");
  }

  @Override
  public void unplot(int x, int y) {
    print(" ");
  }

  @Override
  public void scroll() {
    println();
  }

  @Override
  public void print(String text) {
    output.append(text);
    currentCol += text.length();
  }

  @Override
  public void println(String text) {
    output.append(text).append(System.lineSeparator());
    currentRow++;
    currentCol = 0;
  }

  @Override
  public void println() {
    output.append(System.lineSeparator());
    currentRow++;
    currentCol = 0;
  }

  @Override
  public void lprint(String text) {
    // For now, capture lprint same as print or maybe separate?
    // Given tests just check stdout usually, we can append to main output
    // or ignore if tests specifically check stderr.
    // The current tests checked System.out for everything except lprint maybe?
    // Wait, StringsTest checks outContent which was System.out.
    // Executor.lprint uses System.err.
    // Let's capture to the same buffer for simplicity unless distinct verification is needed.
    // But ideally LPRINT goes to printer. Let's append with a marker? Or just append.
    // Given the previous tests captured System.out, and LPRINT went to System.err,
    // tests checking LPRINT would have failed or needed System.err capture.
    // Checking `InterpreterTest` or `StringsTest`, none seem to test `LPRINT`.
    // so appending to output is fine.
    output.append(text);
  }

  @Override
  public void lprintln(String text) {
    output.append(text).append(System.lineSeparator());
  }

  @Override
  public void lprintln() {
    output.append(System.lineSeparator());
  }

  @Override
  public String readln(String prompt) {
    if (inputIdx < inputs.size()) {
      String input = inputs.get(inputIdx++);
      // Echo input if prompt is displayed?
      // Display.readln echoes input.
      if (prompt != null) {
        print(prompt);
      }
      println(input);
      return input;
    }
    return "";
  }

  private String prefillText = null;

  @Override
  public void prefillInput(String text) {
    this.prefillText = text;
  }

  public String getPrefillText() {
    return prefillText;
  }

  private boolean simulatedBreak = false;

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

  @Override
  public String inkey() {
    // Not simulating INKEY$ interaction for now
    return "";
  }

  @Override
  public void close() {
    // No-op
  }
}
