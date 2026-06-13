package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.IfStmtContext;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementsContext;
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

  /** Returns the parsed StatementsContext for this line, parsing lazily on first access. */
  public List<StatementContext> getFlattenedStatements(AntlrParser parser) {
    if (cachedParseTree == null) {
      cachedParseTree = parser.parseStatementsContext(sourceText);
      new AstAnnotator(lineNumber).visit(cachedParseTree);
    }
    List<StatementContext> flat = new ArrayList<>();
    flatten(cachedParseTree, flat);
    return flat;
  }

  public StatementsContext getStatements(AntlrParser parser) {
    if (cachedParseTree == null) {
      cachedParseTree = parser.parseStatementsContext(sourceText);
      new AstAnnotator(lineNumber).visit(cachedParseTree);
    }
    return cachedParseTree;
  }

  private void flatten(StatementsContext ctx, List<StatementContext> flat) {
    if (ctx == null) {
      return;
    }
    for (StatementContext stmt : ctx.statement()) {
      flat.add(stmt);
      if (stmt instanceof IfStmtContext ifStmt) {
        flatten(ifStmt.statements(), flat);
      }
    }
  }
}
