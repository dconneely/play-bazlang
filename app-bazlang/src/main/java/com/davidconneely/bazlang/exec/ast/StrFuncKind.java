package com.davidconneely.bazlang.exec.ast;

/**
 * One case per {@code strFunc} grammar alternative, resolved once at lowering time. See
 * docs/spec/language.md's "Built-in Functions" section for full semantics.
 */
public enum StrFuncKind {
  /** {@code CHR$ x}: single-byte string with raw byte value {@code x} (0-255). */
  CHR_STR,
  /** {@code INKEY$}: polls for a single raw byte from the input queue. */
  INKEY_STR,
  /** {@code SCREEN$(row, col)}: character at the given screen cell, as a single-byte string. */
  SCREEN_STR,
  /** {@code STR$ x}: converts a number to its string representation. */
  STR_STR,
  /** {@code TL$ s$}: string with the first byte removed ({@code ""} if already empty). */
  TL_STR,
  /** {@code UCHR$ x}: string containing the UTF-8 encoding of Unicode codepoint {@code x}. */
  UCHR_STR,
  /** {@code UINKEY$}: polls for input, returning a complete UTF-8 sequence or escape sequence. */
  UINKEY_STR,
  /** {@code USCREEN$(row, col)}: character at the given screen cell, as a UTF-8 string. */
  USCREEN_STR,
  /** {@code UTL$ s$}: string with the first whole Unicode character removed, however many bytes. */
  UTL_STR,
  /** {@code VAL$ s$}: evaluates a string as a string expression. */
  VAL_STR
}
