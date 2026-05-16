package com.davidconneely.bazlang.io;

public enum CellMode implements PixelMode {
  INSTANCE;

  @Override
  public int pixelsPerCellX() {
    return 1;
  }

  @Override
  public int pixelsPerCellY() {
    return 1;
  }

  @Override
  public int encode(int stateBits) {
    return (stateBits & 1) == 0 ? ' ' : '█';
  }

  @Override
  public int decode(int codepoint) {
    return codepoint == '█' ? 1 : 0;
  }

  @Override
  public int bitMask(int subX, int subY) {
    return 1; // only one sub-pixel
  }
}
