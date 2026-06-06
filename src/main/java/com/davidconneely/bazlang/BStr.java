package com.davidconneely.bazlang;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * BazLang string value: an immutable sequence of bytes, normally containing valid UTF-8 but capable
 * of storing any byte sequence including NUL (0x00) and arbitrary byte values (e.g., 0xFF).
 *
 * <p>Indexing is by byte offset (1-based in BazLang expressions; 0-based in the Java API). {@code
 * LEN} returns the byte count. {@code CODE} returns the first byte value (0-255). {@code CHR$}
 * produces a single-byte BStr for codes 0-255; {@code CODEPOINT$} produces the UTF-8 encoding of
 * any Unicode codepoint.
 *
 * <p>Conversion to {@code java.lang.String} uses UTF-8 Clean-8 (utf8-c8): valid UTF-8 sequences are
 * decoded to their natural Unicode codepoints; any invalid or lone byte 0xNN is represented as the
 * 4-codepoint synthetic {@code [U+10FFFD, 'x', upper-hex-nibble, lower-hex-nibble]}, e.g. byte 0xFF
 * → {@code ?xFF}. {@code fromJavaString} is standard UTF-8 encoding only (synthetics are not
 * decoded back to raw bytes, as this path is only used for string literals and input).
 */
public final class BStr implements Comparable<BStr> {
  public static final BStr EMPTY = new BStr(new byte[0]);

  private final byte[] bytes;

  private BStr(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Creates a BStr by encoding a Java String using standard UTF-8. */
  public static BStr fromJavaString(String s) {
    if (s == null || s.isEmpty()) {
      return EMPTY;
    }
    return new BStr(s.getBytes(StandardCharsets.UTF_8));
  }

  /** Creates a single-byte BStr with the given raw byte value (caller must ensure 0-255). */
  public static BStr fromByte(int b) {
    return new BStr(new byte[] {(byte) b});
  }

  /** Creates a BStr directly from a byte array (the array is copied). */
  public static BStr fromBytes(byte[] bytes) {
    if (bytes.length == 0) {
      return EMPTY;
    }
    return new BStr(Arrays.copyOf(bytes, bytes.length));
  }

  /** Creates a BStr from a region of a byte array (bytes are copied). */
  static BStr fromBytes(byte[] bytes, int offset, int length) {
    if (length == 0) {
      return EMPTY;
    }
    return new BStr(Arrays.copyOfRange(bytes, offset, offset + length));
  }

  /** Returns the number of bytes in this BStr. */
  public int length() {
    return bytes.length;
  }

  public boolean isEmpty() {
    return bytes.length == 0;
  }

  /** Returns the byte at 0-based index {@code i} as an unsigned integer (0-255). */
  public int byteAt(int i) {
    return bytes[i] & 0xFF;
  }

  /**
   * Returns a slice of this BStr (1-based inclusive, matching BazLang indexing semantics). {@code
   * from} and {@code to} are 1-based byte positions.
   */
  public BStr slice(int from, int to) {
    return fromBytes(bytes, from - 1, to - from + 1);
  }

  /** Concatenates this BStr with another. */
  public BStr concat(BStr other) {
    if (isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    byte[] result = new byte[bytes.length + other.bytes.length];
    System.arraycopy(bytes, 0, result, 0, bytes.length);
    System.arraycopy(other.bytes, 0, result, bytes.length, other.bytes.length);
    return new BStr(result);
  }

  /**
   * Returns a new BStr with the specified slice (1-based, inclusive) replaced by the given
   * replacement BStr. The replacement is padded with spaces or truncated to fit the slice length.
   */
  public BStr withSlice(int from, int to, BStr replacement) {
    if (from < 1 || to > bytes.length || from > to + 1) {
      throw new IllegalArgumentException("Slice out of bounds");
    }
    int sliceLen = to - from + 1;
    byte[] result = Arrays.copyOf(bytes, bytes.length);
    for (int i = 0; i < sliceLen; i++) {
      result[from - 1 + i] = (i < replacement.length()) ? (byte) replacement.byteAt(i) : (byte) ' ';
    }
    return new BStr(result);
  }

  /**
   * Returns a new BStr of the specified length. If this BStr is shorter, it is padded with spaces.
   * If it is longer, it is truncated.
   */
  public BStr paddedOrTruncatedTo(int length) {
    if (length == bytes.length) {
      return this;
    }
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (i < bytes.length) ? bytes[i] : (byte) ' ';
    }
    return new BStr(result);
  }

  /**
   * Converts this BStr to a Java String using UTF-8 Clean-8 (utf8-c8): valid UTF-8 sequences are
   * decoded to their natural Unicode codepoints; any invalid or lone byte 0xNN is emitted as the
   * 4-codepoint synthetic {@code [U+10FFFD, 'x', upper-hex-nibble, lower-hex-nibble]}.
   */
  public String toJavaString() {
    if (bytes.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(bytes.length);
    int i = 0;
    while (i < bytes.length) {
      int b = bytes[i] & 0xFF;
      if (b < 0x80) {
        sb.append((char) b);
        i++;
      } else if (b >= 0xC2 && b <= 0xDF && i + 1 < bytes.length && isContByte(bytes[i + 1])) {
        int cp = ((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F);
        sb.appendCodePoint(cp);
        i += 2;
      } else if (b >= 0xE0
          && b <= 0xEF
          && i + 2 < bytes.length
          && isContByte(bytes[i + 1])
          && isContByte(bytes[i + 2])) {
        int cp = ((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F);
        if (cp >= 0x800 && (cp < 0xD800 || cp > 0xDFFF)) {
          sb.appendCodePoint(cp);
          i += 3;
        } else {
          // Overlong or surrogate: emit utf8-c8 synthetic for the leading byte only
          appendC8Synthetic(sb, b);
          i++;
        }
      } else if (b >= 0xF0
          && b <= 0xF4
          && i + 3 < bytes.length
          && isContByte(bytes[i + 1])
          && isContByte(bytes[i + 2])
          && isContByte(bytes[i + 3])) {
        int cp =
            ((b & 0x07) << 18)
                | ((bytes[i + 1] & 0x3F) << 12)
                | ((bytes[i + 2] & 0x3F) << 6)
                | (bytes[i + 3] & 0x3F);
        if (cp >= 0x10000 && cp <= 0x10FFFF) {
          sb.appendCodePoint(cp);
          i += 4;
        } else {
          // Out-of-range 4-byte sequence: emit utf8-c8 synthetic for leading byte only
          appendC8Synthetic(sb, b);
          i++;
        }
      } else {
        // Invalid or lone byte: emit utf8-c8 synthetic
        appendC8Synthetic(sb, b);
        i++;
      }
    }
    return sb.toString();
  }

  /** Appends the 4-codepoint utf8-c8 synthetic for an invalid byte to the StringBuilder. */
  private static void appendC8Synthetic(StringBuilder sb, int b) {
    sb.appendCodePoint(0x10FFFD)
        .append('x')
        .append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
        .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
  }

  /**
   * Returns the first Unicode codepoint decoded from this BStr (valid UTF-8 decoded normally;
   * invalid or lone byte 0xNN returned as the raw value NN), or -1 if this BStr is empty. This uses
   * Latin-1 fallback (not utf8-c8 synthetics) because it is used for the {@code CODE} and {@code
   * CODEPOINT} BASIC functions, which must return byte-level numeric values.
   */
  public int firstCodepoint() {
    if (bytes.length == 0) {
      return -1;
    }
    int b = bytes[0] & 0xFF;
    if (b < 0x80) {
      return b;
    }
    if (b >= 0xC2 && b <= 0xDF && bytes.length >= 2 && isContByte(bytes[1])) {
      return ((b & 0x1F) << 6) | (bytes[1] & 0x3F);
    }
    if (b >= 0xE0
        && b <= 0xEF
        && bytes.length >= 3
        && isContByte(bytes[1])
        && isContByte(bytes[2])) {
      int cp = ((b & 0x0F) << 12) | ((bytes[1] & 0x3F) << 6) | (bytes[2] & 0x3F);
      if (cp >= 0x800 && (cp < 0xD800 || cp > 0xDFFF)) {
        return cp;
      }
      return b;
    }
    if (b >= 0xF0
        && b <= 0xF4
        && bytes.length >= 4
        && isContByte(bytes[1])
        && isContByte(bytes[2])
        && isContByte(bytes[3])) {
      int cp =
          ((b & 0x07) << 18)
              | ((bytes[1] & 0x3F) << 12)
              | ((bytes[2] & 0x3F) << 6)
              | (bytes[3] & 0x3F);
      if (cp >= 0x10000 && cp <= 0x10FFFF) {
        return cp;
      }
      return b;
    }
    // Lone continuation byte or invalid leading byte: map to U+00NN
    return b;
  }

  /**
   * Returns the 0-based byte index of the first byte of the codepoint that follows the codepoint
   * starting at {@code byteIndex0} (0-based). Uses the same UTF-8 validity logic as {@link
   * #toJavaString()}: each invalid or lone byte advances by exactly 1. If {@code byteIndex0 >=
   * length()}, returns {@code length()}.
   */
  public int nextCodepointStart(int byteIndex0) {
    if (byteIndex0 >= bytes.length) {
      return bytes.length;
    }
    int b = bytes[byteIndex0] & 0xFF;
    if (b < 0x80) {
      return byteIndex0 + 1;
    }
    if (b >= 0xC2
        && b <= 0xDF
        && byteIndex0 + 1 < bytes.length
        && isContByte(bytes[byteIndex0 + 1])) {
      return byteIndex0 + 2;
    }
    if (b >= 0xE0
        && b <= 0xEF
        && byteIndex0 + 2 < bytes.length
        && isContByte(bytes[byteIndex0 + 1])
        && isContByte(bytes[byteIndex0 + 2])) {
      int cp =
          ((b & 0x0F) << 12)
              | ((bytes[byteIndex0 + 1] & 0x3F) << 6)
              | (bytes[byteIndex0 + 2] & 0x3F);
      return (cp >= 0x800 && (cp < 0xD800 || cp > 0xDFFF)) ? byteIndex0 + 3 : byteIndex0 + 1;
    }
    if (b >= 0xF0
        && b <= 0xF4
        && byteIndex0 + 3 < bytes.length
        && isContByte(bytes[byteIndex0 + 1])
        && isContByte(bytes[byteIndex0 + 2])
        && isContByte(bytes[byteIndex0 + 3])) {
      int cp =
          ((b & 0x07) << 18)
              | ((bytes[byteIndex0 + 1] & 0x3F) << 12)
              | ((bytes[byteIndex0 + 2] & 0x3F) << 6)
              | (bytes[byteIndex0 + 3] & 0x3F);
      return (cp >= 0x10000 && cp <= 0x10FFFF) ? byteIndex0 + 4 : byteIndex0 + 1;
    }
    return byteIndex0 + 1; // invalid or lone byte
  }

  private static boolean isContByte(byte b) {
    return (b & 0xC0) == 0x80;
  }

  /** Package-private access to the raw byte array (defensive copy). */
  byte[] bytes() {
    return Arrays.copyOf(bytes, bytes.length);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || (obj instanceof BStr other && Arrays.equals(bytes, other.bytes));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public int compareTo(BStr other) {
    int len = Math.min(bytes.length, other.bytes.length);
    for (int i = 0; i < len; i++) {
      int diff = (bytes[i] & 0xFF) - (other.bytes[i] & 0xFF);
      if (diff != 0) {
        return diff;
      }
    }
    return bytes.length - other.bytes.length;
  }

  @Override
  public String toString() {
    return toJavaString();
  }
}
