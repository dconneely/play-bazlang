package com.davidconneely.cell;

public final class CellAttributes {
  private CellAttributes() {}

  public static final int COLOR_TYPE_MASK = 0xFF000000;
  public static final int COLOR_TYPE_INDEX = 0x00000000;
  public static final int COLOR_TYPE_RGB = 0xFF000000;

  // High byte is 01, meaning "not index, not RGB, use default terminal color"
  public static final int COLOR_DEFAULT = 0x01000000;

  public static final int STYLE_BOLD = 1 << 0;
  public static final int STYLE_ITALIC = 1 << 1;
  public static final int STYLE_FAINT = 1 << 2;
  public static final int STYLE_UNDERLINE = 1 << 3;
  public static final int STYLE_BLINK = 1 << 4;
  public static final int STYLE_INVERSE = 1 << 5;
}
