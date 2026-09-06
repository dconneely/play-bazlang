package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementsContext;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.exec.ast.VarIdAllocator;
import java.util.List;

/**
 * Represents a single line in a BazLang program. Stores the original source text and lazily lowers
 * to a flat {@link Stmt} list on first execution.
 */
public class ProgramLine {
  private final int lineNumber;
  private final String sourceText;
  private List<Stmt> cachedFlatStatements;

  /**
   * Create a line with the given source text, not yet parsed.
   *
   * @param lineNumber the line number.
   * @param sourceText the line's source text, after the line number.
   */
  public ProgramLine(int lineNumber, String sourceText) {
    this.lineNumber = lineNumber;
    this.sourceText = sourceText;
    this.cachedFlatStatements = null;
  }

  /**
   * The line number.
   *
   * @return the line number.
   */
  public int lineNumber() {
    return lineNumber;
  }

  /**
   * The line's source text, after the line number.
   *
   * @return the source text.
   */
  public String sourceText() {
    return sourceText;
  }

  /**
   * Returns the flattened, lowered statement list for this line, parsing and lowering lazily on
   * first access. {@code IfStmt} bodies are inlined into the flat list - see {@link
   * AstLowering#lowerStatements} and {@link Stmt}'s class Javadoc for the "flat skip-scan" quirk
   * this preserves.
   *
   * <p>{@code ids} must be the same allocator across every line of one programme (normally the
   * {@code EvalState} that owns it), so that two lines lowered at different times still agree on
   * the id for a given variable name - each AST node bakes its id in as a {@code final} field at
   * lowering time (see {@link com.davidconneely.bazlang.exec.ast.NumExpr} class Javadoc), so once a
   * line is cached here, its ids don't change even if {@code CLEAR} resets the values they point
   * at.
   *
   * @param parser the parser to use if this line hasn't been lowered yet.
   * @param ids assigns/reuses variable ids for the AST nodes constructed, if not already cached.
   * @return the flattened, lowered statement list.
   */
  public List<Stmt> getFlattenedStatements(AntlrParser parser, VarIdAllocator ids) {
    if (cachedFlatStatements == null) {
      cachedFlatStatements =
          AstLowering.lowerStatements(parser.parseStatementsContext(sourceText), lineNumber, ids);
    }
    return cachedFlatStatements;
  }

  /**
   * Returns a freshly parsed, independent ANTLR parse tree for this line - used by text-preserving
   * operations ({@code REFORMAT} and various parser/grammar tests) that need the raw parse tree,
   * not the lowered AST used for execution. Always re-parses; shares no state with {@link
   * #getFlattenedStatements}, so callers never observe (or mutate) the cached execution form.
   *
   * @param parser the parser to use.
   * @return the freshly parsed tree.
   */
  public StatementsContext getStatements(AntlrParser parser) {
    return parser.parseStatementsContext(sourceText);
  }
}
