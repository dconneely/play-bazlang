package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single line in a BazLang program. Stores the original source text and lazily parses
 * to ParseTree on first execution.
 */
public class ProgramLine {
  private final int lineNumber;
  private final String sourceText;
  private StatementsContext cachedParseTree;
  private List<StatementContext> cachedFlatStatements;

  public ProgramLine(int lineNumber, String sourceText) {
    this.lineNumber = lineNumber;
    this.sourceText = sourceText;
    this.cachedParseTree = null;
    this.cachedFlatStatements = null;
  }

  public int lineNumber() {
    return lineNumber;
  }

  public String sourceText() {
    return sourceText;
  }

  /** Returns the parsed StatementsContext for this line, parsing lazily on first access. */
  public List<StatementContext> getFlattenedStatements(AntlrParser parser) {
    if (cachedFlatStatements == null) {
      ensureParsed(parser);
      final var flat = new ArrayList<StatementContext>();
      flatten(cachedParseTree, flat);
      cachedFlatStatements = flat;
    }
    return cachedFlatStatements;
  }

  public StatementsContext getStatements(AntlrParser parser) {
    ensureParsed(parser);
    return cachedParseTree;
  }

  private void ensureParsed(AntlrParser parser) {
    if (cachedParseTree == null) {
      cachedParseTree = parser.parseStatementsContext(sourceText);
      new AstAnnotator(lineNumber).visit(cachedParseTree);
    }
  }

  private void flatten(StatementsContext ctx, List<StatementContext> flat) {
    if (ctx == null) {
      return;
    }
    for (final var stmt : ctx.statement()) {
      flat.add(stmt);
      if (stmt instanceof IfStmtContext ifStmt) {
        flatten(ifStmt.statements(), flat);
      }
    }
  }
}
