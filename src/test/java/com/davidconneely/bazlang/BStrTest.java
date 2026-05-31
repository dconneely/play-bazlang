package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BStr} byte-array string semantics and invalid-byte Latin-1 fallback. */
class BStrTest {

  // Helper: utf8-c8 synthetic for a single invalid byte, e.g. c8(0xFF) = "?xFF"
  private static String c8(int b) {
    return "\uDBFF\uDFFDx" + String.format("%02X", b & 0xFF);
  }

  // --- fromJavaString / toJavaString round-trip ---

  @Test
  void testRoundTripAscii() {
    assertEquals("Hello", BStr.fromJavaString("Hello").toJavaString());
  }

  @Test
  void testRoundTripMultiByteUtf8() {
    // █ is U+2588, encoded as 3-byte UTF-8 E2 96 88
    assertEquals("█", BStr.fromJavaString("█").toJavaString());
  }

  @Test
  void testRoundTripFourByteUtf8() {
    // 😀 is U+1F600, encoded as 4-byte UTF-8 F0 9F 98 80
    assertEquals("😀", BStr.fromJavaString("😀").toJavaString());
  }

  @Test
  void testRoundTripEmpty() {
    assertSame(BStr.EMPTY, BStr.fromJavaString(""));
    assertEquals("", BStr.EMPTY.toJavaString());
  }

  @Test
  void testRoundTripNull() {
    assertSame(BStr.EMPTY, BStr.fromJavaString(null));
  }

  // --- fromByte: raw byte semantics ---

  @Test
  void testFromByteAscii() {
    BStr s = BStr.fromByte(65);
    assertEquals(1, s.length());
    assertEquals(65, s.byteAt(0));
    assertEquals("A", s.toJavaString());
  }

  @Test
  void testFromByteHighByte() {
    // 0xFF is not valid UTF-8; utf8-c8 synthetic is ?xFF
    BStr s = BStr.fromByte(0xFF);
    assertEquals(1, s.length());
    assertEquals(0xFF, s.byteAt(0));
    assertEquals(c8(0xFF), s.toJavaString());
  }

  @Test
  void testFromByteNul() {
    BStr s = BStr.fromByte(0);
    assertEquals(1, s.length());
    assertEquals(0, s.byteAt(0));
    assertEquals("\u0000", s.toJavaString());
  }

  @Test
  void testFromByte0x80() {
    // 0x80 is a lone continuation byte; utf8-c8 synthetic is ?x80
    BStr s = BStr.fromByte(0x80);
    assertEquals(c8(0x80), s.toJavaString());
  }

  // --- Latin-1 fallback decoding of invalid sequences ---

  @Test
  void testInvalidLeadingByte() {
    // 0xC0 0x80 is overlong encoding of NUL — should NOT be decoded as U+0000
    // 0xC0 is < 0xC2 so it is not a valid 2-byte lead; falls through to lone-byte Latin-1 fallback
    // → U+00C0, then 0x80 → U+0080
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xC0, (byte) 0x80});
    // 0xC0 < 0xC2 so not a valid 2-byte lead; utf8-c8 synthetic for each lone byte
    assertEquals(c8(0xC0) + c8(0x80), s.toJavaString());
  }

  @Test
  void testSurrogateRangeNotDecoded() {
    // 0xED 0xA0 0x80 would encode U+D800 (surrogate) — must be rejected
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80});
    // Surrogate range 0xD800-0xDFFF: leading byte emitted as utf8-c8 synthetic, then each
    // subsequent byte is a lone continuation byte → also synthetic
    assertEquals(c8(0xED) + c8(0xA0) + c8(0x80), s.toJavaString());
  }

  @Test
  void testOverlongThreeByteNotDecoded() {
    // 0xE0 0x80 0xAF would be overlong encoding of U+002F — cp < 0x800, must be rejected
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0xAF});
    // isContByte checks (b & 0xC0) == 0x80, so 0x80 IS a continuation byte.
    // Decoded cp = (0 << 12) | (0 << 6) | 0x2F = 0x2F which is < 0x800 → overlong.
    // Leading byte emitted as utf8-c8 synthetic; remaining bytes reprocessed as lone bytes.
    assertEquals(c8(0xE0) + c8(0x80) + c8(0xAF), s.toJavaString());
  }

  @Test
  void testTruncatedSequence() {
    // 3-byte sequence with only 2 bytes present
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xE2, (byte) 0x96});
    // Not enough bytes for the 3-byte sequence; each byte emitted as utf8-c8 synthetic
    assertEquals(c8(0xE2) + c8(0x96), s.toJavaString());
  }

  // --- length() is byte count ---

  @Test
  void testLengthAscii() {
    assertEquals(5, BStr.fromJavaString("Hello").length());
  }

  @Test
  void testLengthMultiByteChar() {
    // █ encodes to 3 bytes in UTF-8
    assertEquals(3, BStr.fromJavaString("█").length());
  }

  @Test
  void testLengthFourByteChar() {
    // 😀 encodes to 4 bytes
    assertEquals(4, BStr.fromJavaString("😀").length());
  }

  @Test
  void testLengthEmpty() {
    assertEquals(0, BStr.EMPTY.length());
  }

  // --- firstCodepoint ---

  @Test
  void testFirstCodepointAscii() {
    assertEquals(65, BStr.fromJavaString("ABC").firstCodepoint());
  }

  @Test
  void testFirstCodepointMultiByte() {
    assertEquals(0x2588, BStr.fromJavaString("█").firstCodepoint()); // U+2588
  }

  @Test
  void testFirstCodepointFourByte() {
    assertEquals(0x1F600, BStr.fromJavaString("😀").firstCodepoint()); // U+1F600
  }

  @Test
  void testFirstCodepointRawByte() {
    // Raw byte 0xFF: firstCodepoint uses Latin-1 (not utf8-c8) since CODE/CODEPOINT need byte
    // values
    assertEquals(0xFF, BStr.fromByte(0xFF).firstCodepoint());
  }

  @Test
  void testFirstCodepointEmpty() {
    assertEquals(-1, BStr.EMPTY.firstCodepoint());
  }

  // --- nextCodepointStart ---

  @Test
  void testNextCodepointStartAscii() {
    BStr s = BStr.fromJavaString("Hello");
    assertEquals(1, s.nextCodepointStart(0)); // 'H' is 1 byte
    assertEquals(5, s.nextCodepointStart(4)); // 'o' is 1 byte, ends at index 5
  }

  @Test
  void testNextCodepointStartThreeByte() {
    // █ = U+2588 = [E2, 96, 88], 3 bytes
    BStr s = BStr.fromJavaString("█");
    assertEquals(3, s.nextCodepointStart(0));
    assertEquals(3, s.nextCodepointStart(3)); // at end → stays at length
  }

  @Test
  void testNextCodepointStartFourByte() {
    // 😀 = U+1F600 = [F0, 9F, 98, 80], 4 bytes
    BStr s = BStr.fromJavaString("😀");
    assertEquals(4, s.nextCodepointStart(0));
  }

  @Test
  void testNextCodepointStartInvalidByte() {
    // 0xFF is invalid: advances by 1 (utf8-c8 synthetic = 1 "codepoint")
    BStr s = BStr.fromByte(0xFF);
    assertEquals(1, s.nextCodepointStart(0));
  }

  @Test
  void testNextCodepointStartBrokenLead() {
    // [0xC2, 0x20]: 0xC2 has no valid continuation → advances by 1
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xC2, (byte) 0x20});
    assertEquals(1, s.nextCodepointStart(0)); // 0xC2 invalid → 1 byte
    assertEquals(2, s.nextCodepointStart(1)); // 0x20 ASCII → 1 byte
  }

  @Test
  void testNextCodepointStartAtEnd() {
    BStr s = BStr.fromJavaString("A");
    assertEquals(1, s.nextCodepointStart(1)); // at end → returns length
  }

  // --- slice ---

  @Test
  void testSliceAscii() {
    BStr s = BStr.fromJavaString("Hello");
    assertEquals("ell", s.slice(2, 4).toJavaString());
  }

  @Test
  void testSliceSingleByte() {
    BStr s = BStr.fromJavaString("Hello");
    assertEquals("H", s.slice(1, 1).toJavaString());
  }

  @Test
  void testSliceEntire() {
    BStr s = BStr.fromJavaString("Hi");
    assertEquals("Hi", s.slice(1, 2).toJavaString());
  }

  // --- concat ---

  @Test
  void testConcatBasic() {
    BStr a = BStr.fromJavaString("Hello");
    BStr b = BStr.fromJavaString(" World");
    assertEquals("Hello World", a.concat(b).toJavaString());
  }

  @Test
  void testConcatWithEmpty() {
    BStr s = BStr.fromJavaString("Hello");
    assertSame(s, s.concat(BStr.EMPTY));
    assertSame(s, BStr.EMPTY.concat(s));
  }

  // --- compareTo / equals ---

  @Test
  void testEqualsIdentical() {
    BStr a = BStr.fromJavaString("abc");
    BStr b = BStr.fromJavaString("abc");
    assertEquals(a, b);
  }

  @Test
  void testCompareToAscii() {
    BStr a = BStr.fromJavaString("abc");
    BStr b = BStr.fromJavaString("abd");
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    assertEquals(0, a.compareTo(BStr.fromJavaString("abc")));
  }

  @Test
  void testCompareToUnsignedBytes() {
    // 0xFF byte should sort AFTER 0x7F (unsigned comparison)
    BStr low = BStr.fromByte(0x7F);
    BStr high = BStr.fromByte(0xFF);
    assertTrue(low.compareTo(high) < 0);
  }

  @Test
  void testIsEmpty() {
    assertTrue(BStr.EMPTY.isEmpty());
    assertFalse(BStr.fromJavaString("x").isEmpty());
  }

  // --- toJavaString: valid 2-byte sequence ---

  @Test
  void testToJavaStringTwoByteValid() {
    // é = U+00E9, encoded as [C3, A9]
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xC3, (byte) 0xA9});
    assertEquals("é", s.toJavaString());
  }

  @Test
  void testToJavaStringTwoByteMinimumValid() {
    // U+0080 is the lowest codepoint needing 2-byte encoding: [C2, 80]
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xC2, (byte) 0x80});
    assertEquals("\u0080", s.toJavaString());
  }

  // --- toJavaString: out-of-range 4-byte sequence ---

  @Test
  void testToJavaStringFourByteOutOfRange() {
    // [F4, 90, 80, 80] decodes cp = 0x110000 > 0x10FFFF → lead emitted as synthetic,
    // then remaining continuation bytes each emitted as synthetic
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80});
    assertEquals(c8(0xF4) + c8(0x90) + c8(0x80) + c8(0x80), s.toJavaString());
  }

  // --- firstCodepoint: fallback cases ---

  @Test
  void testFirstCodepointLoneContinuationByte() {
    // 0x80 is a lone continuation byte; Latin-1 fallback returns raw byte value
    assertEquals(0x80, BStr.fromByte(0x80).firstCodepoint());
  }

  @Test
  void testFirstCodepointOverlongThreeByte() {
    // [E0, 80, AF] decodes cp = 0x2F < 0x800 → overlong; falls back to raw byte 0xE0
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0xAF});
    assertEquals(0xE0, s.firstCodepoint());
  }

  @Test
  void testFirstCodepointSurrogateThreeByte() {
    // [ED, A0, 80] decodes U+D800 (surrogate) → rejected; falls back to raw byte 0xED
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80});
    assertEquals(0xED, s.firstCodepoint());
  }

  // --- nextCodepointStart: 2-byte valid sequence ---

  @Test
  void testNextCodepointStartTwoByte() {
    // é = U+00E9 = [C3, A9], 2 bytes
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xC3, (byte) 0xA9});
    assertEquals(2, s.nextCodepointStart(0));
    assertEquals(2, s.nextCodepointStart(2)); // at end
  }

  // --- nextCodepointStart: truncated sequences at end of string ---

  @Test
  void testNextCodepointStartTruncatedThreeByte() {
    // [E2, 96] — 3-byte lead but only 1 continuation byte, truncated at end → advances by 1
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xE2, (byte) 0x96});
    assertEquals(1, s.nextCodepointStart(0));
    assertEquals(2, s.nextCodepointStart(1)); // 0x96 is a lone continuation byte → +1
  }

  @Test
  void testNextCodepointStartTruncatedFourByte() {
    // [F0, 9F, 98] — 4-byte lead with only 2 continuation bytes → advances by 1
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xF0, (byte) 0x9F, (byte) 0x98});
    assertEquals(1, s.nextCodepointStart(0));
  }

  // --- nextCodepointStart: overlong and surrogate sequences ---

  @Test
  void testNextCodepointStartOverlongThreeByte() {
    // [E0, 80, AF] decodes cp = 0x2F < 0x800 → overlong; lead byte advances by 1 only
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0xAF});
    assertEquals(1, s.nextCodepointStart(0));
  }

  @Test
  void testNextCodepointStartSurrogateThreeByte() {
    // [ED, A0, 80] decodes U+D800 → surrogate; lead byte advances by 1 only
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80});
    assertEquals(1, s.nextCodepointStart(0));
  }

  // --- slice: raw/high-byte content ---

  @Test
  void testSliceHighBytes() {
    // Slice preserves raw byte values without re-encoding
    BStr s = BStr.fromBytes(new byte[] {(byte) 0xFF, (byte) 0x80, (byte) 0xC0});
    BStr mid = s.slice(2, 2); // byte at index 1 = 0x80
    assertEquals(1, mid.length());
    assertEquals(0x80, mid.byteAt(0));
    assertEquals(c8(0x80), mid.toJavaString());
  }
}
