package com.davidconneely.bazlang.io;

public enum QuadrantMode implements PixelMode {
  INSTANCE;

  // Index = state bits (UL=bit0, UR=bit1, LL=bit2, LR=bit3)
  private static final int[] CODEPOINTS = {
    ' ', '▘', '▝', '▀', '▖', '▌', '▞', '▛', '▗', '▚', '▐', '▜', '▄', '▙', '▟', '█'
  };

  @Override
  public int pixelsPerCellX() {
    return 2;
  }

  @Override
  public int pixelsPerCellY() {
    return 2;
  }

  @Override
  public int encode(int stateBits) {
    return CODEPOINTS[stateBits & 0xF];
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
    // subY=1 is top half, subY=0 is bottom half; subX=0 is left, subX=1 is right
    if (subX == 0 && subY == 1) {
      return 1; // upper-left
    }
    if (subX == 1 && subY == 1) {
      return 2; // upper-right
    }
    if (subX == 0 && subY == 0) {
      return 4; // lower-left
    }
    return 8; // lower-right
  }
}
