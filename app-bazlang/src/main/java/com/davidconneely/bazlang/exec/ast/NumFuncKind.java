package com.davidconneely.bazlang.exec.ast;

/**
 * One case per {@code numFunc} grammar alternative, resolved once at lowering time. See
 * docs/spec/language.md's "Built-in Functions" section for full semantics.
 */
public enum NumFuncKind {
  /** {@code ABS x}: absolute value. */
  ABS,
  /** {@code ACS x}: arccosine. */
  ACS,
  /** {@code ASN x}: arcsine. */
  ASN,
  /** {@code ATTR(row, col)}: Sinclair Spectrum attribute byte at the given cell. */
  ATTR,
  /** {@code ATN x}: arctangent. */
  ATN,
  /** {@code CODE s$}: raw byte value (0-255) of the first byte in the string. */
  CODE,
  /** {@code COLOUR(r, g, b)}: packs 24-bit RGB values to a BazLang colour number. */
  COLOUR,
  /** {@code COS x}: cosine. */
  COS,
  /** {@code EXP x}: e raised to the power x. */
  EXP,
  /** {@code FRAMES}: ticks (1 tick = 20 milliseconds) since epoch, fractional. */
  FRAMES,
  /** {@code INT x}: integer part (truncation, not rounding). */
  INT,
  /** {@code LEN s$}: raw byte length of the string. */
  LEN,
  /** {@code LN x}: natural logarithm. */
  LN,
  /** {@code PI}: the constant &pi;. */
  PI,
  /** {@code PLOTH}: logical plot height for the current pixel mode, in pixels. */
  PLOTH,
  /** {@code PLOTMODE}: the current pixel mode id. */
  PLOTMODE,
  /** {@code PLOTW}: logical plot width for the current pixel mode, in pixels. */
  PLOTW,
  /** {@code PLOTX}: current x-coordinate of the graphics (plot) cursor. */
  PLOTX,
  /** {@code PLOTY}: current y-coordinate of the graphics (plot) cursor. */
  PLOTY,
  /** {@code POINT(x, y)}: {@code 1} if the pixel is set, {@code 0} if erased or non-graphic. */
  POINT,
  /** {@code RND}: a random number between 0 and 1. */
  RND,
  /** {@code SGN x}: signum ({@code -1}, {@code 0}, or {@code 1}). */
  SGN,
  /** {@code SIN x}: sine. */
  SIN,
  /** {@code SQR x}: square root. */
  SQR,
  /** {@code TAN x}: tangent. */
  TAN,
  /** {@code TEXTH}: screen height, in character cells. */
  TEXTH,
  /** {@code TEXTW}: screen width, in character cells. */
  TEXTW,
  /** {@code TEXTX}: current text cursor column. */
  TEXTX,
  /** {@code TEXTY}: current text cursor row. */
  TEXTY,
  /** {@code UCNEXT(s$, i)}: 1-based byte position of the codepoint following position {@code i}. */
  UCNEXT,
  /** {@code UCODE s$}: Unicode codepoint value of the first character (UTF-8 decoded). */
  UCODE,
  /** {@code ULEN s$}: Unicode character (codepoint) length of the string. */
  ULEN,
  /** {@code VAL s$}: parses the string as a number. */
  VAL,
  /** {@code XATTR(row, col, select)}: extended attribute value at the given cell. */
  XATTR
}
