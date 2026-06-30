package com.davidconneely.cell;

public final class CellAttributes {
  private CellAttributes() {}

  public static final int COLOUR_TYPE_MASK = 0x03000000;
  public static final int COLOUR_TYPE_INDEX = 0x00000000;
  public static final int COLOUR_DEFAULT = 0x01000000;
  public static final int COLOUR_TYPE_RGB = 0x02000000;

  public static final int STYLE_BOLD = 1 << 0;
  public static final int STYLE_FAINT = 1 << 1;
  public static final int STYLE_ITALIC = 1 << 2;
  public static final int STYLE_UNDERLINE = 1 << 3;
  public static final int STYLE_BLINK = 1 << 4;
  public static final int STYLE_INVERSE = 1 << 5;
  public static final int STYLE_STRIKETHROUGH = 1 << 6;

  public static boolean isDefault(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_DEFAULT;
  }

  public static boolean isRgb(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_TYPE_RGB;
  }

  public static boolean isIndex(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_TYPE_INDEX;
  }

  public static int valueOf(int colour) {
    return colour & 0x00FFFFFF;
  }

  public static int rgb(int rgb) {
    return COLOUR_TYPE_RGB | (rgb & 0x00FFFFFF);
  }

  public static int rgb(int r, int g, int b) {
    return COLOUR_TYPE_RGB | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
  }

  public static int index(int index) {
    return COLOUR_TYPE_INDEX | (index & 0x00FFFFFF);
  }
}
