package com.davidconneely.bazlang.io;

import com.davidconneely.cell.PixelMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.repl.Display;
import java.util.Collections;
import java.util.List;

public class MockDisplay implements BazLangDisplay {
  private final StringBuilder output = new StringBuilder();
  private final List<String> inputs;
  private int inputIdx = 0;
  private int currentRow = 0;
  private int currentCol = 0;
  private String status = null;
  private final int[][] grid = new int[24][80];
  private final int[][] fgGrid = new int[24][80];
  private final int[][] bgGrid = new int[24][80];
  private final int[][] styleGrid = new int[24][80];

  private int activeInk = -1;
  private int activePaper = -1;
  private int activeBright = 0;
  private int activeFlash = 0;
  private int activeInverse = 0;

  public MockDisplay() {
    this(Collections.emptyList());
  }

  public MockDisplay(List<String> inputs) {
    this.inputs = inputs;
    initGrid();
  }

  private void initGrid() {
    for (int r = 0; r < 24; r++) {
      java.util.Arrays.fill(grid[r], 32);
      java.util.Arrays.fill(fgGrid[r], -1);
      java.util.Arrays.fill(bgGrid[r], -1);
      java.util.Arrays.fill(styleGrid[r], 0);
    }
  }

  public String getOutput() {
    return output.toString();
  }

  public void clearOutput() {
    output.setLength(0);
    initGrid();
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
  public void setInk(int colour) {
    this.activeInk = colour;
  }

  @Override
  public void setPaper(int colour) {
    this.activePaper = colour;
  }

  @Override
  public void setBright(int bright) {
    this.activeBright = bright;
  }

  @Override
  public void setFlash(int flash) {
    this.activeFlash = flash;
  }

  @Override
  public void setInverse(int inverse) {
    this.activeInverse = inverse;
  }

  @Override
  public void setOver(int over) {}

  @Override
  public int plotMode() {
    return 4; // QuadrantMode
  }

  @Override
  public void cls() {
    currentRow = 0;
    currentCol = 0;
    initGrid();
  }

  @Override
  public void locate(int row, int col) {
    currentRow = row;
    currentCol = col;
  }

  @Override
  public void plot(int x, int y) {
    print("█");
  }

  @Override
  public int point(int x, int y) {
    return 0; // Not fully mocked for tests yet
  }

  @Override
  public int getScreenCodepoint(int row, int col) {
    if (row < 0 || row >= 24 || col < 0 || col >= 80) {
      return 32;
    }
    return grid[row][col];
  }

  @Override
  public void scroll() {
    println();
  }

  private int getMappedColour(int colour, int bright) {
    if (colour >= 0 && colour <= 7) {
      final int[] normal = {
        0x000000, 0x0000D7, 0xD70000, 0xD700D7, 0x00D700, 0x00D7D7, 0xD7D700, 0xD7D7D7
      };
      final int[] br = {
        0x000000, 0x0000FF, 0xFF0000, 0xFF00FF, 0x00FF00, 0x00FFFF, 0xFFFF00, 0xFFFFFF
      };
      return 16_777_216 + (bright == 1 ? br[colour] : normal[colour]);
    }
    return colour;
  }

  @Override
  public void print(String text) {
    output.append(text);
    if (text != null) {
      int idx = 0;
      while (idx < text.length()) {
        int cp = text.codePointAt(idx);
        int r = currentRow;
        int c = currentCol;
        if (r >= 0 && r < 24 && c >= 0 && c < 80) {
          grid[r][c] = cp;
          int fg = activeInverse == 1 ? activePaper : activeInk;
          int bg = activeInverse == 1 ? activeInk : activePaper;
          fgGrid[r][c] = getMappedColour(fg, activeBright);
          bgGrid[r][c] = getMappedColour(bg, activeBright);
          int style = 0;
          if (activeFlash == 1) {
            style |= 16;
          }
          if (activeBright == 1) {
            style |= 1;
          }
          styleGrid[r][c] = style;
        }
        currentCol++;
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

  @Override
  public void flush() {
    // No-op in tests
  }

  @Override
  public String readln(Display.InputMode mode) {
    return readln("");
  }

  @Override
  public String readReplLine() {
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

  private String prefillText = null;

  @Override
  public void prefillInput(String text) {
    this.prefillText = text;
  }

  public String getPrefillText() {
    return prefillText;
  }

  private PixelMode plotMode = QuadrantMode.INSTANCE;

  @Override
  public void setPlotMode(PixelMode mode) {
    this.plotMode = mode;
  }

  public PixelMode getPlotMode() {
    return plotMode;
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
  public String uinkey() {
    return inkey();
  }

  @Override
  public void setFastMode(boolean fast) {}

  @Override
  public void close() {
    // No-op
  }

  @Override
  public int getScreenAttributes(int row, int col) {
    if (row < 0 || row >= 24 || col < 0 || col >= 80) {
      return 56;
    }
    int fg = fgGrid[row][col];
    int bg = bgGrid[row][col];
    int style = styleGrid[row][col];

    int flash = (style & 16) != 0 ? 1 : 0;
    int bright = (style & 1) != 0 ? 1 : 0;
    int ink = resolveZxColour(fg, true);
    int paper = resolveZxColour(bg, false);

    return (flash * 128) + (bright * 64) + (paper * 8) + ink;
  }

  private int resolveZxColour(int cellColour, boolean isInk) {
    if (cellColour >= 0 && cellColour <= 7) {
      return cellColour;
    }
    if (cellColour >= 256 && cellColour <= 511) {
      int idx = cellColour - 256;
      if (idx >= 0 && idx <= 15) {
        final int[] mapping = {0, 2, 4, 6, 1, 3, 5, 7};
        return mapping[idx & 7];
      }
      if (idx >= 16 && idx <= 231) {
        int code = idx - 16;
        int rVal = code / 36;
        int gVal = (code % 36) / 6;
        int bVal = code % 6;
        int bitR = rVal >= 3 ? 1 : 0;
        int bitG = gVal >= 3 ? 1 : 0;
        int bitB = bVal >= 3 ? 1 : 0;
        return (bitG << 2) | (bitR << 1) | bitB;
      }
      if (idx >= 232 && idx <= 255) {
        return idx < 244 ? 0 : 7;
      }
    }
    if (cellColour >= 16_777_216 && cellColour <= 33_554_431) {
      int rgb = cellColour - 16_777_216;
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      int bitR = r > 155 ? 1 : 0;
      int bitG = g > 155 ? 1 : 0;
      int bitB = b > 155 ? 1 : 0;
      return (bitG << 2) | (bitR << 1) | bitB;
    }
    return isInk ? 7 : 0;
  }

  @Override
  public int getXAttributes(int row, int col, int select) {
    if (row < 0 || row >= 24 || col < 0 || col >= 80) {
      if (select == 0 || select == 1) {
        return -1;
      }
      return 0;
    }
    switch (select) {
      case 0:
        return fgGrid[row][col];
      case 1:
        return bgGrid[row][col];
      case 2:
        return (styleGrid[row][col] & 16) != 0 ? 1 : 0;
      case 3:
        return (styleGrid[row][col] & 1) != 0 ? 1 : 0;
      default:
        return 0;
    }
  }
}
