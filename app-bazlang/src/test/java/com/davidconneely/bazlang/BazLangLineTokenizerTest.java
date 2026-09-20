package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.repl.LineTokenizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class BazLangLineTokenizerTest {
  private final BazLangLineTokenizer tokenizer = new BazLangLineTokenizer();

  @Test
  void numberedLineHighlightsLineNumberAndKeyword() {
    List<LineTokenizer.Span> spans = tokenizer.tokenize("10 PRINT \"HI\"");

    assertEquals(
        List.of(
            new LineTokenizer.Span(0, 2, BazLangLineTokenizer.STYLE_LINE_NUMBER),
            new LineTokenizer.Span(3, 8, BazLangLineTokenizer.STYLE_KEYWORD)),
        spans);
  }

  @Test
  void bareLineNumberAloneIsStillALineNumber() {
    assertEquals(
        List.of(new LineTokenizer.Span(0, 2, BazLangLineTokenizer.STYLE_LINE_NUMBER)),
        tokenizer.tokenize("10"));
  }

  @Test
  void immediateModeStatementHasNoLineNumber() {
    List<LineTokenizer.Span> spans = tokenizer.tokenize("PRINT 1 + 2");

    assertEquals(List.of(new LineTokenizer.Span(0, 5, BazLangLineTokenizer.STYLE_KEYWORD)), spans);
  }

  @Test
  void numericLiteralAfterTheFirstTokenIsNotALineNumber() {
    List<LineTokenizer.Span> spans = tokenizer.tokenize("PRINT 10");

    assertEquals(List.of(new LineTokenizer.Span(0, 5, BazLangLineTokenizer.STYLE_KEYWORD)), spans);
  }

  @Test
  void replOnlyMetaCommandsAreHighlightedAsCommands() {
    for (String command : List.of("DELETE 10", "EDIT 10", "EXIT", "RENUM", "REFORMAT 10")) {
      List<LineTokenizer.Span> spans = tokenizer.tokenize(command);
      assertEquals(
          BazLangLineTokenizer.STYLE_COMMAND,
          spans.get(0).style(),
          "expected STYLE_COMMAND for: " + command);
      assertEquals(0, spans.get(0).start());
    }
  }

  @Test
  void remHighlightsTheWholeCommentAsOneKeywordSpan() {
    String line = "REM this is a comment";
    List<LineTokenizer.Span> spans = tokenizer.tokenize(line);

    assertEquals(
        List.of(new LineTokenizer.Span(0, line.length(), BazLangLineTokenizer.STYLE_KEYWORD)),
        spans);
  }

  @Test
  void clauseKeywordsAndOperatorWordsAreHighlighted() {
    List<LineTokenizer.Span> spans = tokenizer.tokenize("FOR I = 1 TO 10 STEP 2");

    assertEquals(
        List.of(
            new LineTokenizer.Span(0, 3, BazLangLineTokenizer.STYLE_KEYWORD), // FOR
            new LineTokenizer.Span(10, 12, BazLangLineTokenizer.STYLE_KEYWORD), // TO
            new LineTokenizer.Span(16, 20, BazLangLineTokenizer.STYLE_KEYWORD) // STEP
            ),
        spans);
  }

  @Test
  void functionNamesAreHighlightedAsKeywordsToo() {
    List<LineTokenizer.Span> spans = tokenizer.tokenize("PRINT ABS(-1)");

    assertEquals(
        List.of(
            new LineTokenizer.Span(0, 5, BazLangLineTokenizer.STYLE_KEYWORD), // PRINT
            new LineTokenizer.Span(6, 9, BazLangLineTokenizer.STYLE_KEYWORD) // ABS
            ),
        spans);
  }

  @Test
  void punctuationAndIdentifiersAreNotHighlighted() {
    assertEquals(List.of(), tokenizer.tokenize("A = B + 1"));
  }

  @Test
  void everyEmittedStyleNameIsInTheStylesPalette() {
    for (String source :
        List.of("10 PRINT \"HI\"", "DELETE 10", "REM comment", "FOR I = 1 TO 10 STEP 2")) {
      for (final var span : tokenizer.tokenize(source)) {
        assertTrue(
            BazLangLineTokenizer.STYLES.containsKey(span.style()),
            "style '" + span.style() + "' from '" + source + "' has no palette entry");
      }
    }
  }

  @Test
  void inkForMatchesTheRgbEachStylesPaletteEntryGives() {
    assertEquals(
        16_777_216 + 0x00D7D7, BazLangLineTokenizer.inkFor(BazLangLineTokenizer.STYLE_KEYWORD));
    assertEquals(
        16_777_216 + 0x00D700, BazLangLineTokenizer.inkFor(BazLangLineTokenizer.STYLE_COMMAND));
    assertEquals(
        16_777_216 + 0x808080, BazLangLineTokenizer.inkFor(BazLangLineTokenizer.STYLE_LINE_NUMBER));
  }

  @Test
  void partialOrInvalidInputDoesNotThrow() {
    assertDoesNotThrow(() -> tokenizer.tokenize("PRIN"));
    assertDoesNotThrow(() -> tokenizer.tokenize("10 LET A$ = \""));
    assertDoesNotThrow(() -> tokenizer.tokenize("@@@"));
    assertDoesNotThrow(() -> tokenizer.tokenize(""));
  }
}
