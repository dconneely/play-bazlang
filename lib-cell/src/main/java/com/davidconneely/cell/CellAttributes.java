package com.davidconneely.cell;

public final class CellAttributes {
  private CellAttributes() {}

  public static final int COLOUR_TYPE_MASK = 0x03000000;
  public static final int COLOUR_TYPE_INDEX = 0x00000000;
  public static final int COLOUR_DEFAULT = 0x01000000;
  public static final int COLOUR_TYPE_RGB = 0x02000000;

  public static final int STYLE_BOLD = 1 << 0;
  public static final int STYLE_ITALIC = 1 << 1;
  public static final int STYLE_FAINT = 1 << 2;
  public static final int STYLE_UNDERLINE = 1 << 3;
  public static final int STYLE_BLINK = 1 << 4;
  public static final int STYLE_INVERSE = 1 << 5;
}
