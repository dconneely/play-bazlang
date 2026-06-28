package com.davidconneely.bazlang;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * BazLang string value: an immutable sequence of bytes, normally containing valid UTF-8 but capable
 * of storing any byte sequence including NUL (0x00) and arbitrary byte values (e.g., 0xFF).
 *
 * <p>Indexing is by byte offset (1-based in BazLang expressions; 0-based in the Java API). {@code
 * LEN} returns the byte count. {@code CODE} returns the first byte value (0-255). {@code CHR$}
 * produces a single-byte BStr for codes 0-255; {@code UCHR$} produces the UTF-8 encoding of any
 * Unicode codepoint.
 *
 * <p>Conversion to {@code java.lang.String} uses UTF-8 Clean-8 (utf8-c8): valid UTF-8 sequences are
 * decoded to their natural Unicode codepoints; any invalid or lone byte 0xNN is represented as the
 * 4-codepoint synthetic {@code [U+10FFFD, 'x', upper-hex-nibble, lower-hex-nibble]}, e.g. byte 0xFF
 * → {@code ?xFF}. {@code fromJavaString} is standard UTF-8 encoding only (synthetics are not
 * decoded back to raw bytes, as this path is only used for string literals and input).
 */
public final class BStr implements Comparable<BStr> {
  public static final BStr EMPTY = new BStr(new byte[0], 0, 0);

  private static final BStr[] BYTE_CACHE = new BStr[256];

  static {
    for (int i = 0; i < 256; i++) {
      BYTE_CACHE[i] = new BStr(new byte[] {(byte) i}, 0, 1);
    }
  }

  private final byte[] bytes;
  private final int offset;
  private final int length;

  private BStr(byte[] bytes, int offset, int length) {
    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
  }

  /** Creates a BStr by encoding a Java String using standard UTF-8. */
  public static BStr fromJavaString(String s) {
    if (s == null || s.isEmpty()) {
      return EMPTY;
    }
    final byte[] b = s.getBytes(StandardCharsets.UTF_8);
    return new BStr(b, 0, b.length);
  }

  /** Creates a BStr representing a single byte (0-255). Avoids allocations by using a cache. */
  public static BStr fromByte(int b) {
    return BYTE_CACHE[b & 0xFF];
  }

  /** Creates a BStr directly from a byte array (the array is copied). */
  public static BStr fromBytes(byte[] bytes) {
    if (bytes.length == 0) {
      return EMPTY;
    }
    return new BStr(Arrays.copyOf(bytes, bytes.length), 0, bytes.length);
  }

  static BStr fromBytes(byte[] bytes, int offset, int length) {
    if (length == 0) {
      return EMPTY;
    }
    return new BStr(bytes, offset, length);
  }

  /** Returns a completely isolated copy of this BStr, safe from underlying array mutations. */
  public BStr copy() {
    if (length == 0) {
      return EMPTY;
    }
    return new BStr(Arrays.copyOfRange(bytes, offset, offset + length), 0, length);
  }

  public int length() {
    return length;
  }

  public boolean isEmpty() {
    return length == 0;
  }

  /** Returns the byte at 0-based index {@code i} as an unsigned integer (0-255). */
  public int byteAt(int i) {
    return bytes[offset + i] & 0xFF;
  }

  /**
   * Returns a slice of this BStr (1-based inclusive, matching BazLang indexing semantics). {@code
   * from} and {@code to} are 1-based byte positions.
   */
  public BStr slice(int from, int to) {
    return fromBytes(bytes, offset + from - 1, to - from + 1);
  }

  /** Concatenates this BStr with another. */
  public BStr concat(BStr other) {
    if (isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    final byte[] result = new byte[length + other.length];
    System.arraycopy(bytes, offset, result, 0, length);
    System.arraycopy(other.bytes, other.offset, result, length, other.length);
    return new BStr(result, 0, result.length);
  }

  /**
   * Returns a new BStr with the specified slice (1-based, inclusive) replaced by the given
   * replacement BStr. The replacement is padded with spaces or truncated to fit the slice length.
   */
  public BStr withSlice(int from, int to, BStr replacement) {
    if (from < 1 || to > length || from > to + 1) {
      throw new IllegalArgumentException("Slice out of bounds");
    }
    final int sliceLen = to - from + 1;
    final byte[] result = new byte[length];
    System.arraycopy(bytes, offset, result, 0, length);
    for (int i = 0; i < sliceLen; i++) {
      result[from - 1 + i] = (i < replacement.length()) ? (byte) replacement.byteAt(i) : (byte) ' ';
    }
    return new BStr(result, 0, result.length);
  }

  /**
   * Converts this BStr to a Java String using UTF-8 Clean-8 (utf8-c8): valid UTF-8 sequences are
   * decoded to their natural Unicode codepoints; any invalid or lone byte 0xNN is emitted as the
   * 4-codepoint synthetic {@code [U+10FFFD, 'x', upper-hex-nibble, lower-hex-nibble]}.
   */
  public String toJavaString() {
    if (length == 0) {
      return "";
    }
    final StringBuilder sb = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      final int b = bytes[offset + i] & 0xFF;
      if (b < 0x80) {
        sb.append((char) b);
        i++;
      } else if (b >= 0xC2 && b <= 0xDF && i + 1 < length && isContByte(bytes[offset + i + 1])) {
        final int cp = ((b & 0x1F) << 6) | (bytes[offset + i + 1] & 0x3F);
        sb.appendCodePoint(cp);
        i += 2;
      } else if (b >= 0xE0
          && b <= 0xEF
          && i + 2 < length
          && isContByte(bytes[offset + i + 1])
          && isContByte(bytes[offset + i + 2])) {
        int cp =
            ((b & 0x0F) << 12)
                | ((bytes[offset + i + 1] & 0x3F) << 6)
                | (bytes[offset + i + 2] & 0x3F);
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
          && i + 3 < length
          && isContByte(bytes[offset + i + 1])
          && isContByte(bytes[offset + i + 2])
          && isContByte(bytes[offset + i + 3])) {
        final int cp =
            ((b & 0x07) << 18)
                | ((bytes[offset + i + 1] & 0x3F) << 12)
                | ((bytes[offset + i + 2] & 0x3F) << 6)
                | (bytes[offset + i + 3] & 0x3F);
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
   * raw byte fallback (not utf8-c8 synthetics) because it is used for the {@code CODE} and {@code
   * UCODE} BASIC functions, which must return byte-level numeric values.
   */
  public int firstCodepoint() {
    if (length == 0) {
      return -1;
    }
    final int b = bytes[offset] & 0xFF;
    if (b < 0x80) {
      return b;
    }
    if (b >= 0xC2 && b <= 0xDF && length >= 2 && isContByte(bytes[offset + 1])) {
      return ((b & 0x1F) << 6) | (bytes[offset + 1] & 0x3F);
    }
    if (b >= 0xE0
        && b <= 0xEF
        && length >= 3
        && isContByte(bytes[offset + 1])
        && isContByte(bytes[offset + 2])) {
      final int cp =
          ((b & 0x0F) << 12) | ((bytes[offset + 1] & 0x3F) << 6) | (bytes[offset + 2] & 0x3F);
      if (cp >= 0x800 && (cp < 0xD800 || cp > 0xDFFF)) {
        return cp;
      }
      return b;
    }
    if (b >= 0xF0
        && b <= 0xF4
        && length >= 4
        && isContByte(bytes[offset + 1])
        && isContByte(bytes[offset + 2])
        && isContByte(bytes[offset + 3])) {
      final int cp =
          ((b & 0x07) << 18)
              | ((bytes[offset + 1] & 0x3F) << 12)
              | ((bytes[offset + 2] & 0x3F) << 6)
              | (bytes[offset + 3] & 0x3F);
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
    if (byteIndex0 >= length) {
      return length;
    }
    final int b = bytes[offset + byteIndex0] & 0xFF;
    if (b < 0x80) {
      return byteIndex0 + 1;
    }
    if (b >= 0xC2
        && b <= 0xDF
        && byteIndex0 + 1 < length
        && isContByte(bytes[offset + byteIndex0 + 1])) {
      return byteIndex0 + 2;
    }
    if (b >= 0xE0
        && b <= 0xEF
        && byteIndex0 + 2 < length
        && isContByte(bytes[offset + byteIndex0 + 1])
        && isContByte(bytes[offset + byteIndex0 + 2])) {
      final int cp =
          ((b & 0x0F) << 12)
              | ((bytes[offset + byteIndex0 + 1] & 0x3F) << 6)
              | (bytes[offset + byteIndex0 + 2] & 0x3F);
      return (cp >= 0x800 && (cp < 0xD800 || cp > 0xDFFF)) ? byteIndex0 + 3 : byteIndex0 + 1;
    }
    if (b >= 0xF0
        && b <= 0xF4
        && byteIndex0 + 3 < length
        && isContByte(bytes[offset + byteIndex0 + 1])
        && isContByte(bytes[offset + byteIndex0 + 2])
        && isContByte(bytes[offset + byteIndex0 + 3])) {
      final int cp =
          ((b & 0x07) << 18)
              | ((bytes[offset + byteIndex0 + 1] & 0x3F) << 12)
              | ((bytes[offset + byteIndex0 + 2] & 0x3F) << 6)
              | (bytes[offset + byteIndex0 + 3] & 0x3F);
      return (cp >= 0x10000 && cp <= 0x10FFFF) ? byteIndex0 + 4 : byteIndex0 + 1;
    }
    return byteIndex0 + 1; // invalid or lone byte
  }

  private static boolean isContByte(byte b) {
    return (b & 0xC0) == 0x80;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj
        || (obj instanceof BStr other
            && Arrays.equals(
                bytes,
                offset,
                offset + length,
                other.bytes,
                other.offset,
                other.offset + other.length));
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i = 0; i < length; i++) {
      result = 31 * result + bytes[offset + i];
    }
    return result;
  }

  @Override
  public int compareTo(BStr other) {
    final int len = Math.min(length, other.length);
    for (int i = 0; i < len; i++) {
      int diff = (bytes[offset + i] & 0xFF) - (other.bytes[other.offset + i] & 0xFF);
      if (diff != 0) {
        return diff;
      }
    }
    return length - other.length;
  }

  @Override
  public String toString() {
    return toJavaString();
  }
}
