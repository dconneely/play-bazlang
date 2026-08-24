package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementsContext;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.Stmt;
import java.util.List;

/**
 * Represents a single line in a BazLang program. Stores the original source text and lazily lowers
 * to a flat {@link Stmt} list on first execution.
 */
public class ProgramLine {
  private final int lineNumber;
  private final String sourceText;
  private List<Stmt> cachedFlatStatements;

  public ProgramLine(int lineNumber, String sourceText) {
    this.lineNumber = lineNumber;
    this.sourceText = sourceText;
    this.cachedFlatStatements = null;
  }

  public int lineNumber() {
    return lineNumber;
  }

  public String sourceText() {
    return sourceText;
  }

  /**
   * Returns the flattened, lowered statement list for this line, parsing and lowering lazily on
   * first access. {@code IfStmt} bodies are inlined into the flat list - see {@link
   * AstLowering#lowerStatements} and {@link Stmt}'s class Javadoc for the "flat skip-scan" quirk
   * this preserves.
   */
  public List<Stmt> getFlattenedStatements(AntlrParser parser) {
    if (cachedFlatStatements == null) {
      // Mutable state (like EvalState variable references) is cached directly on the AST nodes as
      // an intentional performance optimisation. When CLEAR is executed, EvalState.clear() zeroes
      // out the contents of those cached references in place rather than replacing the objects, so
      // this cached statement list remains valid and does not need to be discarded.
      cachedFlatStatements =
          AstLowering.lowerStatements(parser.parseStatementsContext(sourceText), lineNumber);
    }
    return cachedFlatStatements;
  }

  /**
   * Returns a freshly parsed, independent ANTLR parse tree for this line - used by text-preserving
   * operations ({@code REFORMAT} and various parser/grammar tests) that need the raw parse tree,
   * not the lowered AST used for execution. Always re-parses; shares no state with {@link
   * #getFlattenedStatements}, so callers never observe (or mutate) the cached execution form.
   */
  public StatementsContext getStatements(AntlrParser parser) {
    return parser.parseStatementsContext(sourceText);
  }
}
