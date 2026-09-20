package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.BazLangLexer;
import com.davidconneely.repl.LineTokenizer;
import com.davidconneely.repl.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/**
 * A {@link LineTokenizer} built on the real {@link BazLangLexer} - the same lexer used for actual
 * parsing, so highlighting cannot drift from the grammar. {@code lib-repl} knows nothing of
 * BazLang, so the meaning of a style name - and the colour it maps to - is owned entirely here:
 * {@link #STYLES} is the palette to register alongside this tokenizer (see {@code
 * JLineTerminalEngine}'s constructor), and {@code STYLE_*} are the names into it this class's spans
 * use.
 */
public final class BazLangLineTokenizer implements LineTokenizer {
  /** Shared stateless instance; construct directly instead where a test needs its own. */
  public static final BazLangLineTokenizer INSTANCE = new BazLangLineTokenizer();

  /** Style name for a reserved word. */
  public static final String STYLE_KEYWORD = "keyword";

  /** Style name for a command recognised only by the REPL itself, never inside stored source. */
  public static final String STYLE_COMMAND = "command";

  /** Style name for a line number. */
  public static final String STYLE_LINE_NUMBER = "lineNumber";

  /** Style name for a {@code REM} comment's text (excluding the {@code REM} keyword itself). */
  public static final String STYLE_COMMENT = "comment";

  /** Style name for a string literal. */
  public static final String STYLE_STRING = "string";

  /** The palette {@code STYLE_*} names above map into. */
  public static final Map<String, TextStyle> STYLES =
      Map.of(
          STYLE_KEYWORD, new TextStyle(0, 0xD7, 0xD7), // Teal.
          STYLE_COMMAND, new TextStyle(0, 0xD7, 0), // Green.
          STYLE_LINE_NUMBER, new TextStyle(0x80, 0x80, 0x80), // Dark grey.
          STYLE_COMMENT, new TextStyle(0x80, 0x80, 0x80, true), // Dark grey, italic.
          STYLE_STRING, new TextStyle(0xD7, 0xD7, 0)); // Yellow.

  private static final Set<Integer> REPL_COMMAND_TYPES =
      Set.of(
          BazLangLexer.DELETE,
          BazLangLexer.EDIT,
          BazLangLexer.EXIT,
          BazLangLexer.RENUM,
          BazLangLexer.REFORMAT);

  // Every alphabetic reserved word BazLangLexer's vocabulary defines, derived structurally so this
  // cannot drift from the grammar. REM is deliberately excluded: its lexer rule consumes the rest
  // of the line as part of the token ('REM' ~[\r\n]*), so ANTLR gives it no quoted literal name -
  // and it needs its own two-span treatment (see tokenize()) rather than one keyword-coloured span.
  private static final Set<Integer> KEYWORD_TYPES = computeKeywordTypes();

  private static Set<Integer> computeKeywordTypes() {
    final var vocabulary = BazLangLexer.VOCABULARY;
    final Set<Integer> types = new HashSet<>();
    for (int type = 1; type <= vocabulary.getMaxTokenType(); type++) {
      final String literal = vocabulary.getLiteralName(type);
      if (literal != null) {
        final String bare = literal.substring(1, literal.length() - 1);
        if (!bare.isEmpty() && Character.isLetter(bare.charAt(0))) {
          types.add(type);
        }
      }
    }
    return types;
  }

  /**
   * The {@code VirtualScreen#setInk} colour code for a style name from {@link #STYLES}, derived
   * from its {@link TextStyle} so highlighting on a {@code VirtualScreen} (e.g. {@code LIST}, the
   * REPL's accepted-line echo) can never drift from the colours {@link #STYLES} gives JLine's
   * live-typing highlighter.
   *
   * @param style a style name previously returned by {@link #tokenize}.
   * @return an RGB-encoded ink code (the {@code 2^24..} range {@code StyleState} documents).
   */
  public static int inkFor(String style) {
    final TextStyle rgb = STYLES.get(style);
    return 16_777_216
        + ((rgb.red() & 0xFF) << 16 | (rgb.green() & 0xFF) << 8 | (rgb.blue() & 0xFF));
  }

  @Override
  public List<Span> tokenize(String line) {
    final var lexer = new BazLangLexer(CharStreams.fromString(line));
    lexer.removeErrorListeners();

    final List<Span> spans = new ArrayList<>();
    boolean first = true;
    Token token;
    while ((token = lexer.nextToken()).getType() != Token.EOF) {
      final int start = token.getStartIndex();
      final int end = token.getStopIndex() + 1;
      if (first && token.getType() == BazLangLexer.NUM_LITERAL) {
        spans.add(new Span(start, end, STYLE_LINE_NUMBER));
      } else if (REPL_COMMAND_TYPES.contains(token.getType())) {
        spans.add(new Span(start, end, STYLE_COMMAND));
      } else if (token.getType() == BazLangLexer.REM) {
        // 'REM' itself is a keyword; anything after it on the line is comment text, not a
        // separate token - REM's own lexer rule swallows it all as one token.
        final int keywordEnd = start + 3; // The literal 'REM' is always exactly 3 characters.
        spans.add(new Span(start, keywordEnd, STYLE_KEYWORD));
        if (keywordEnd < end) {
          spans.add(new Span(keywordEnd, end, STYLE_COMMENT));
        }
      } else if (token.getType() == BazLangLexer.STR_LITERAL) {
        spans.add(new Span(start, end, STYLE_STRING));
      } else if (KEYWORD_TYPES.contains(token.getType())) {
        spans.add(new Span(start, end, STYLE_KEYWORD));
      }
      first = false;
    }
    return spans;
  }
}
