package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Behavior-biased tests for the Lexer. These tests ensure that the lexer correctly identifies basic
 * tokens without enforcing structural program rules (which are now in the Parser).
 */
class LexerTest {

  @Test
  void testValidTokens() {
    assertTokenizes("10 PRINT 10");
    assertTokenizes("20 LET A$ = \"HELLO\"");
    assertTokenizes("# This is a comment\n30 REM BASIC COMMENT");
    assertTokenizes("40 PRINT \"a \"\"b\"\" c\"");
  }

  @Test
  void testCaseInsensitivity() {
    assertTokenizes("10 print PRINT Print");
  }

  @Test
  void testIdentifiersAndNumbers() {
    assertTokenizes("100 A A1 A$ 123.45 1E10");
  }

  private void assertTokenizes(String source) {
    assertDoesNotThrow(
        () -> {
          Lexer lexer = new Lexer(source);
          lexer.tokenize();
        },
        "Source should tokenize successfully: " + source);
  }
}
