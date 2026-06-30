package com.davidconneely.cell;

public enum SextantMode implements PixelMode {
  INSTANCE;

  private static final int[] CODEPOINTS = new int[64];

  static {
    CODEPOINTS[0] = ' ';
    CODEPOINTS[21] = '▌'; // LEFT HALF BLOCK
    CODEPOINTS[42] = '▐'; // RIGHT HALF BLOCK
    CODEPOINTS[63] = '█'; // FULL BLOCK
    for (int i = 1; i <= 20; i++) {
      CODEPOINTS[i] = 0x1FB00 + (i - 1);
    }
    for (int i = 22; i <= 41; i++) {
      CODEPOINTS[i] = 0x1FB14 + (i - 22);
    }
    for (int i = 43; i <= 62; i++) {
      CODEPOINTS[i] = 0x1FB28 + (i - 43);
    }
  }

  @Override
  public int pixelsPerCellX() {
    return 2;
  }

  @Override
  public int pixelsPerCellY() {
    return 3;
  }

  @Override
  public int encode(int stateBits) {
    return CODEPOINTS[stateBits & 0x3F];
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
    // subY: 2=top, 1=middle, 0=bottom; subX: 0=left, 1=right
    return switch (subY * 2 + subX) {
      case 4 -> 1; // top-left
      case 5 -> 2; // top-right
      case 2 -> 4; // mid-left
      case 3 -> 8; // mid-right
      case 0 -> 16; // bot-left
      default -> 32; // bot-right
    };
  }
}
