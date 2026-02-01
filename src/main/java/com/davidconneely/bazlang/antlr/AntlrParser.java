package com.davidconneely.bazlang.antlr;

import com.davidconneely.bazlang.*;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.*;

/** Parser facade that uses ANTLR to parse BazLang source code. */
public class AntlrParser {
  private static final Pattern LINE_PATTERN = Pattern.compile("^(\\d+)\\s*(.*)$");

  /**
   * Parse a complete BazLang program from source code into ProgramLines. The ParseTree for each
   * line is parsed lazily on first execution.
   *
   * @param source the source code to parse
   * @return a NavigableMap of line numbers to ProgramLines
   */
  public NavigableMap<Integer, ProgramLine> parseProgramLines(String source) {
    NavigableMap<Integer, ProgramLine> result = new TreeMap<>();
    String[] lines = source.split("\n");

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue; // Skip empty lines and comments
      }

      Matcher matcher = LINE_PATTERN.matcher(trimmed);
      if (matcher.matches()) {
        int lineNumber = Integer.parseInt(matcher.group(1));
        if (lineNumber < Limits.MIN_LINE_LABEL || lineNumber > Limits.MAX_LINE_LABEL) {
          throw new ReportException(
              ReportCode.INTEGER_OUT_OF_RANGE,
              lineNumber,
              "Line number out of range: " + lineNumber);
        }
        String statementText = matcher.group(2);
        result.put(lineNumber, new ProgramLine(lineNumber, statementText));
      }
    }

    return result;
  }

  /**
   * Parse a single REPL line (either numbered for program entry or immediate for execution).
   *
   * @param line the line to parse
   * @return ParsedLine containing either a numbered line entry or an immediate statement context
   * @throws ReportException if parsing fails
   */
  public ParsedLine parseReplLine(String line) {
    BazLangParser parser = createParser(line);
    BazLangParser.ReplLineContext tree = parser.replLine();

    if (tree instanceof BazLangParser.NumberedLineContext numbered) {
      int lineNumber = Integer.parseInt(numbered.NUM_LITERAL().getText());
      if (lineNumber == 0) {
        // Line 0 in REPL executes immediately (like on ZX81)
        String statementText = getStatementText(line, lineNumber);
        return new ParsedLine.Immediate(parseStatementContext(statementText));
      }
      if (lineNumber > Limits.MAX_LINE_LABEL) {
        throw new ReportException(
            ReportCode.INTEGER_OUT_OF_RANGE, lineNumber, "Line number out of range: " + lineNumber);
      }
      String statementText = getStatementText(line, lineNumber);
      return new ParsedLine.Numbered(lineNumber, statementText);
    } else if (tree instanceof BazLangParser.ImmediateLineContext immediate) {
      return new ParsedLine.Immediate(immediate.statement());
    }

    throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, "Unexpected parse result");
  }

  /**
   * Parse a single statement and return its StatementContext (ParseTree).
   *
   * @param source the statement to parse
   * @return the parsed StatementContext
   * @throws ReportException if parsing fails
   */
  public StatementContext parseStatementContext(String source) {
    BazLangParser parser = createParser(source);
    BazLangParser.ReplLineContext tree = parser.replLine();

    if (tree instanceof BazLangParser.ImmediateLineContext immediate) {
      return immediate.statement();
    } else if (tree instanceof BazLangParser.NumberedLineContext numbered) {
      return numbered.statement();
    }

    throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, "Expected statement");
  }

  /**
   * Parse a numeric expression string and return its NumExprContext (ParseTree). Used by VAL
   * function and INPUT for numeric variables.
   *
   * @param source the expression to parse
   * @return the parsed NumExprContext
   * @throws ReportException if parsing fails
   */
  public BazLangParser.NumExprContext parseNumExpr(String source) {
    BazLangParser parser = createParser(source);
    return parser.numExpr();
  }

  private String getStatementText(String line, int lineNumber) {
    // Extract the statement part after the line number
    String trimmed = line.trim();
    String prefix = String.valueOf(lineNumber);
    if (trimmed.startsWith(prefix)) {
      return trimmed.substring(prefix.length()).trim();
    }
    return trimmed;
  }

  private BazLangParser createParser(String source) {
    CharStream input = CharStreams.fromString(source);
    BazLangLexer lexer = new BazLangLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(new BazLangErrorListener());

    CommonTokenStream tokens = new CommonTokenStream(lexer);
    BazLangParser parser = new BazLangParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(new BazLangErrorListener());

    return parser;
  }

  /** Result of parsing a REPL line - either numbered (for program) or immediate (for execution). */
  public sealed interface ParsedLine {
    record Numbered(int lineNumber, String statementText) implements ParsedLine {}

    record Immediate(StatementContext statement) implements ParsedLine {}
  }

  /** ANTLR error listener that converts syntax errors to ReportException. */
  private static class BazLangErrorListener extends BaseErrorListener {
    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, msg);
    }
  }
}
