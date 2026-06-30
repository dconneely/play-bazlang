package com.davidconneely.cell;

public enum HalfCellMode implements PixelMode {
  INSTANCE;

  private static final int[] CODEPOINTS = {' ', '▀', '▄', '█'};

  @Override
  public int pixelsPerCellX() {
    return 1;
  }

  @Override
  public int pixelsPerCellY() {
    return 2;
  }

  @Override
  public int encode(int stateBits) {
    return CODEPOINTS[stateBits & 3];
  }

  @Override
  public int decode(int codepoint) {
    for (int i = 0; i < CODEPOINTS.length; i++) {
      if (CODEPOINTS[i] == codepoint) {
        return i;
      }
    }
    return 0;
  }

  @Override
  public int bitMask(int subX, int subY) {
    return subY == 1 ? 1 : 2; // top=1, bottom=2
  }
}
