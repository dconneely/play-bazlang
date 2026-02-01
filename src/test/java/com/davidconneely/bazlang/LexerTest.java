package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.Test;

/**
 * Behaviour-biased tests for the Lexer/Parser. These tests ensure that basic tokens are correctly
 * identified and parsed.
 */
class LexerTest {
  private final AntlrParser parser = new AntlrParser();

  @Test
  void testValidTokens() {
    assertParses("10 PRINT 10");
    assertParses("20 LET A$ = \"HELLO\"");
    assertParses("# This is a comment\n30 REM BASIC COMMENT");
    assertParses("40 PRINT \"a \"\"b\"\" c\"");
  }

  @Test
  void testCaseInsensitivity() {
    assertParses("10 print 1\n20 PRINT 2\n30 Print 3");
  }

  @Test
  void testIdentifiersAndNumbers() {
    assertParses("100 LET A = 123.45\n110 LET A1 = 1E10");
  }

  private void assertParses(String source) {
    assertDoesNotThrow(
        () -> parser.parseProgramLines(source), "Source should parse successfully: " + source);
  }
}
