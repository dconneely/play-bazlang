package com.davidconneely.cell;

/**
 * Constants and helpers for the packed colour/style values stored per cell in a {@link CellBuffer}.
 * A colour is a 26-bit value: the top 2 bits select its type ({@link #COLOUR_TYPE_INDEX}, {@link
 * #COLOUR_DEFAULT}, or {@link #COLOUR_TYPE_RGB}) and the low 24 bits hold its payload (an
 * xterm-256color index or a packed RGB triple). Style is an 11-bit set of {@code STYLE_*} flag
 * bits.
 */
public final class CellAttributes {
  private CellAttributes() {}

  /** Bit mask isolating a colour value's 2-bit type field. */
  public static final int COLOUR_TYPE_MASK = 0x03000000;

  /** Colour type: an xterm-256color palette index in the low 8 bits. */
  public static final int COLOUR_TYPE_INDEX = 0x00000000;

  /** Colour type: the terminal's own default foreground/background colour. */
  public static final int COLOUR_DEFAULT = 0x01000000;

  /** Colour type: a 24-bit RGB triple in the low 24 bits. */
  public static final int COLOUR_TYPE_RGB = 0x02000000;

  /** Style flag: bold. */
  public static final int STYLE_BOLD = 1;

  /** Style flag: faint/dim. */
  public static final int STYLE_FAINT = 1 << 1;

  /** Style flag: italic. */
  public static final int STYLE_ITALIC = 1 << 2;

  /** Style flag: underline. */
  public static final int STYLE_UNDERLINE = 1 << 3;

  /** Style flag: blink. */
  public static final int STYLE_BLINK = 1 << 4;

  /** Style flag: inverse (swap foreground/background). */
  public static final int STYLE_INVERSE = 1 << 5;

  /** Style flag: strikethrough. */
  public static final int STYLE_STRIKETHROUGH = 1 << 6;

  /**
   * Whether a colour value is the terminal's default colour.
   *
   * @param colour a colour value.
   * @return {@code true} if its type is {@link #COLOUR_DEFAULT}.
   */
  public static boolean isDefault(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_DEFAULT;
  }

  /**
   * Whether a colour value is a 24-bit RGB colour.
   *
   * @param colour a colour value.
   * @return {@code true} if its type is {@link #COLOUR_TYPE_RGB}.
   */
  public static boolean isRgb(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_TYPE_RGB;
  }

  /**
   * Whether a colour value is an xterm-256color palette index.
   *
   * @param colour a colour value.
   * @return {@code true} if its type is {@link #COLOUR_TYPE_INDEX}.
   */
  public static boolean isIndex(int colour) {
    return (colour & COLOUR_TYPE_MASK) == COLOUR_TYPE_INDEX;
  }

  /**
   * The type-stripped payload of a colour value: an RGB triple for {@link #isRgb(int)}, or a
   * palette index for {@link #isIndex(int)}.
   *
   * @param colour a colour value.
   * @return the low 24 bits of {@code colour}.
   */
  public static int valueOf(int colour) {
    return colour & 0x00FFFFFF;
  }

  /**
   * Encode a 24-bit RGB value as a 26-bit colour value.
   *
   * @param rgb 24-bit RGB value ({@code ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF)}).
   * @return 26-bit encoded colour value.
   */
  public static int rgb(int rgb) {
    return COLOUR_TYPE_RGB | (rgb & 0x00FFFFFF);
  }

  /**
   * Encode an 8-bit xterm-256color index value as a 26-bit colour value.
   *
   * @param index 8-bit xterm-256color index value.
   * @return 26-bit encoded colour value.
   */
  public static int index(int index) {
    return COLOUR_TYPE_INDEX | (index & 0x000000FF);
  }
}
