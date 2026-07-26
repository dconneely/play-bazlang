package com.davidconneely.bazlang.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class QuotedArgTest {

  @Test
  void testParseSimple() {
    assertEquals("hello", QuotedArg.parse("\"hello\""));
    assertEquals("", QuotedArg.parse("\"\""));
  }

  @Test
  void testParseEscapes() {
    assertEquals("say \"hi\"", QuotedArg.parse("\"say \\\"hi\\\"\""));
    assertEquals("a\\b", QuotedArg.parse("\"a\\\\b\""));
    assertEquals("line1\nline2", QuotedArg.parse("\"line1\\nline2\""));
    assertEquals("cr\rlf", QuotedArg.parse("\"cr\\rlf\""));
    assertEquals("[1m", QuotedArg.parse("\"\\e[1m\""));
    // Unknown escapes pass through with the backslash preserved
    assertEquals("\\q", QuotedArg.parse("\"\\q\""));
  }

  @Test
  void testParseInvalid() {
    assertNull(QuotedArg.parse("hello"), "must start with a quote");
    assertNull(QuotedArg.parse("\"unclosed"), "missing closing quote");
    assertNull(QuotedArg.parse("\"trailing\" extra"), "content after closing quote");
    assertNull(QuotedArg.parse("\"dangling\\"), "unmatched trailing backslash");
  }

  @Test
  void testFormatRoundTrip() {
    for (String s :
        new String[] {"hello", "", "say \"hi\"", "a\\b", "line1\nline2", "cr\rlf", "[1m"}) {
      assertEquals(s, QuotedArg.parse(QuotedArg.format(s)));
    }
  }
}
