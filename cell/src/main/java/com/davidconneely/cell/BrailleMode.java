package com.davidconneely.cell;

public enum BrailleMode implements PixelMode {
  INSTANCE;

  @Override
  public int pixelsPerCellX() {
    return 2;
  }

  @Override
  public int pixelsPerCellY() {
    return 4;
  }

  @Override
  public int encode(int stateBits) {
    return 0x2800 + (stateBits & 0xFF);
  }

  @Override
  public int decode(int codepoint) {
    return (codepoint >= 0x2800 && codepoint <= 0x28FF) ? codepoint - 0x2800 : 0;
  }

  @Override
  public int bitMask(int subX, int subY) {
    // subY: 3=top, 2=2nd, 1=3rd, 0=bottom; subX: 0=left, 1=right
    return switch (subY * 2 + subX) {
      case 6 -> 1; // top-left (dot 1)
      case 4 -> 2; // 2nd-left (dot 2)
      case 2 -> 4; // 3rd-left (dot 3)
      case 7 -> 8; // top-right (dot 4)
      case 5 -> 16; // 2nd-right (dot 5)
      case 3 -> 32; // 3rd-right (dot 6)
      case 0 -> 64; // bot-left (dot 7)
      default -> 128; // bot-right (dot 8)
    };
  }
}
