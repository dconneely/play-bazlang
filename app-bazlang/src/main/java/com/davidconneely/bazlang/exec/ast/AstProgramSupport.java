package com.davidconneely.bazlang.exec.ast;

import com.davidconneely.bazlang.exec.EvalState;
import java.util.List;
import java.util.NavigableMap;

/**
 * AST-flavoured equivalents of {@code Program.findFirstData}/{@code findMatchingNext}, matching
 * {@code instanceof Stmt.DataStmt}/{@code Stmt.NextStmt} via pattern matching instead of {@code
 * instanceof ...Context}. Standalone and independently unit-tested (Phase 2 of {@code
 * localonly-plan-CUSTOM-AST.md}) — not wired into the live {@code Program} class yet, so these
 * operate on a plain {@code NavigableMap<Integer, List<Stmt>>} (each line's already-flattened
 * statement list, as produced by {@code AstLowering.lowerStatements}) rather than on {@code
 * Program}/{@code ProgramLine} themselves.
 */
public final class AstProgramSupport {
  private AstProgramSupport() {}

  /**
   * Finds the first {@code DATA} statement at or after {@code fromLabel}, scanning flattened
   * statements in program order. Returns its address, or {@code null} if there is none.
   */
  public static EvalState.StatementAddress findFirstData(
      NavigableMap<Integer, List<Stmt>> lines, int fromLabel) {
    Integer label = lines.ceilingKey(fromLabel);
    while (label != null) {
      final var stmts = lines.get(label);
      for (int i = 1; i <= stmts.size(); i++) {
        if (stmts.get(i - 1) instanceof Stmt.DataStmt) {
          return new EvalState.StatementAddress(label, i);
        }
      }
      label = lines.higherKey(label);
    }
    return null;
  }

  /**
   * Finds the first {@code NEXT forVar} at or after {@code (fromLabel, fromStatementIndex)},
   * scanning flattened statements in program order (deliberately including {@code IF} bodies — see
   * {@code docs/quirks.md} "FOR loop flat skip scan"). Returns its address, or {@code null}.
   */
  public static EvalState.StatementAddress findMatchingNext(
      NavigableMap<Integer, List<Stmt>> lines,
      String forVar,
      int fromLabel,
      int fromStatementIndex) {
    Integer label = lines.ceilingKey(fromLabel); // == fromLabel itself when present
    int startIdx = fromStatementIndex;
    while (label != null) {
      final var stmts = lines.get(label);
      for (int i = startIdx; i <= stmts.size(); i++) {
        if (stmts.get(i - 1) instanceof Stmt.NextStmt(String next)
            && next.equalsIgnoreCase(forVar)) {
          return new EvalState.StatementAddress(label, i);
        }
      }
      label = lines.higherKey(label);
      startIdx = 1;
    }
    return null;
  }
}
