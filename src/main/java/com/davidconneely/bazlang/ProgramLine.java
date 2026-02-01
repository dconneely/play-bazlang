package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;

/**
 * Represents a single line in a BazLang program. Stores the original source text and lazily parses
 * to ParseTree on first execution.
 */
public class ProgramLine {
  private final int lineNumber;
  private final String sourceText;
  private StatementContext cachedParseTree;

  public ProgramLine(int lineNumber, String sourceText) {
    this.lineNumber = lineNumber;
    this.sourceText = sourceText;
    this.cachedParseTree = null;
  }

  public int lineNumber() {
    return lineNumber;
  }

  public String sourceText() {
    return sourceText;
  }

  /** Returns the parsed StatementContext for this line, parsing lazily on first access. */
  public StatementContext getStatement(AntlrParser parser) {
    if (cachedParseTree == null) {
      cachedParseTree = parser.parseStatementContext(sourceText);
    }
    return cachedParseTree;
  }
}
