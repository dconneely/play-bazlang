package com.davidconneely.repl.jline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.repl.LineTokenizer;
import com.davidconneely.repl.TextStyle;
import java.util.List;
import java.util.Map;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

class TokenizingHighlighterTest {

  private static final AttributedStyle KEYWORD_STYLE =
      AttributedStyle.DEFAULT.foreground(0, 0xD7, 0xD7);
  private static final AttributedStyle COMMAND_STYLE =
      AttributedStyle.DEFAULT.foreground(0, 0xD7, 0);
  private static final AttributedStyle LINE_NUMBER_STYLE =
      AttributedStyle.DEFAULT.foreground(0x80, 0x80, 0x80);

  private static final Map<String, TextStyle> PALETTE =
      Map.of(
          "keyword", new TextStyle(0, 0xD7, 0xD7),
          "command", new TextStyle(0, 0xD7, 0),
          "lineNumber", new TextStyle(0x80, 0x80, 0x80));

  @Test
  void colouredSpansGetTheirStyleAndGapsStayDefault() {
    LineTokenizer fake = line -> List.of(new LineTokenizer.Span(0, 2, "lineNumber"));
    TokenizingHighlighter highlighter = new TokenizingHighlighter(fake, PALETTE);

    var result = highlighter.highlight(null, "10 PRINT");

    assertEquals("10 PRINT", result.toString());
    assertEquals(LINE_NUMBER_STYLE, result.styleAt(0));
    assertEquals(LINE_NUMBER_STYLE, result.styleAt(1));
    assertEquals(AttributedStyle.DEFAULT, result.styleAt(2));
    assertEquals(AttributedStyle.DEFAULT, result.styleAt(3));
  }

  @Test
  void multipleSpansEachGetTheirOwnStyle() {
    LineTokenizer fake =
        line ->
            List.of(
                new LineTokenizer.Span(0, 6, "command"), new LineTokenizer.Span(7, 9, "keyword"));
    TokenizingHighlighter highlighter = new TokenizingHighlighter(fake, PALETTE);

    var result = highlighter.highlight(null, "DELETE 10 TO 20");

    assertEquals("DELETE 10 TO 20", result.toString());
    assertEquals(COMMAND_STYLE, result.styleAt(0));
    assertEquals(AttributedStyle.DEFAULT, result.styleAt(6));
    assertEquals(KEYWORD_STYLE, result.styleAt(7));
    assertEquals(AttributedStyle.DEFAULT, result.styleAt(9));
  }

  @Test
  void noSpansRendersEntirelyDefault() {
    LineTokenizer fake = line -> List.of();
    TokenizingHighlighter highlighter = new TokenizingHighlighter(fake, PALETTE);

    var result = highlighter.highlight(null, "X = 1");

    assertEquals("X = 1", result.toString());
    assertEquals(AttributedStyle.DEFAULT, result.styleAt(0));
  }

  @Test
  void anUnknownStyleNameRendersAsDefaultRatherThanThrowing() {
    LineTokenizer fake = line -> List.of(new LineTokenizer.Span(0, 1, "typo"));
    TokenizingHighlighter highlighter = new TokenizingHighlighter(fake, PALETTE);

    var result = highlighter.highlight(null, "X");

    assertEquals(AttributedStyle.DEFAULT, result.styleAt(0));
  }
}
