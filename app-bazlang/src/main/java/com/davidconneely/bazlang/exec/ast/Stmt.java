package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A lowered statement node, one case per {@code #XxxStmt} grammar alternative. Walked by {@code
 * AstStatementExecutor} via {@code switch} pattern matching.
 *
 * <p>{@link IfStmt#body()} carries the {@code THEN}-clause's statements in their natural nested
 * shape — it is not itself what the executor's normal top-to-bottom driver walks (that driver walks
 * a single flat {@code List<Stmt>} per program line, produced by {@code
 * AstLowering.lowerStatements}, which recursively inlines {@code IfStmt} bodies into the flat list
 * exactly as {@code ProgramLine.flatten()} does today — the "flat skip-scan" quirk documented in
 * {@code docs/quirks.md}, needed so a {@code GOTO}/{@code NEXT} target can address a statement
 * nested inside an {@code IF} by flat index). Consequently {@code visitIfStmt}-equivalent
 * evaluation logic never walks {@code body()} itself: when the condition is true, execution falls
 * through to the next flat-list entry, which is the (already-inlined) first body statement; when
 * false, it jumps past the rest of the line. {@code body()} exists for structural completeness and
 * for {@code AstLowering}'s own flattening step, which walks the just-lowered body rather than
 * re-walking the ANTLR tree.
 */
public sealed interface Stmt {
  record BrightStmt(NumExpr value) implements Stmt {}

  record CircleStmt(List<StyleItem> styles, NumExpr cx, NumExpr cy, NumExpr radius)
      implements Stmt {}

  record ClearStmt() implements Stmt {}

  record ClsStmt() implements Stmt {}

  record ContStmt() implements Stmt {}

  record DataStmt(List<Expr> values) implements Stmt {}

  /**
   * {@code name} ends with {@code $} for a string-valued function; {@code body} is checked against
   * that at lowering time, matching the {@code visitDefFnStmt} type-mismatch check.
   */
  record DefFnStmt(String name, List<String> params, Expr body) implements Stmt {}

  record DimStmt(String name, boolean isString, List<NumExpr> dims) implements Stmt {}

  record DrawStmt(List<StyleItem> styles, NumExpr dx, NumExpr dy) implements Stmt {}

  record FastStmt() implements Stmt {}

  record FlashStmt(NumExpr value) implements Stmt {}

  /**
   * {@code step} defaults to a literal {@code 1.0} at lowering time when the grammar's optional
   * {@code STEP} clause is absent, so the executor never needs a nullability check for it.
   */
  record ForStmt(String forVar, NumExpr start, NumExpr end, NumExpr step) implements Stmt {}

  record GosubStmt(NumExpr target) implements Stmt {}

  record GotoStmt(NumExpr target) implements Stmt {}

  record IfStmt(NumExpr condition, List<Stmt> body) implements Stmt {}

  record InkStmt(NumExpr value) implements Stmt {}

  record InputStmt(AssignTarget target) implements Stmt {}

  record InverseStmt(NumExpr value) implements Stmt {}

  record LetStmt(AssignTarget target, Expr value) implements Stmt {}

  /** {@code range} is {@code null} when no line range was given (list everything). */
  record ListStmt(LineRange range) implements Stmt {}

  record LoadStmt(StrExpr fileName) implements Stmt {}

  record MergeStmt(StrExpr fileName) implements Stmt {}

  record NewStmt() implements Stmt {}

  record NextStmt(String forVar) implements Stmt {}

  record OverStmt(NumExpr value) implements Stmt {}

  record PaperStmt(NumExpr value) implements Stmt {}

  record PauseStmt(NumExpr frames) implements Stmt {}

  record PlotStmt(List<StyleItem> styles, NumExpr x, NumExpr y) implements Stmt {}

  record PlotmodeStmt(NumExpr mode) implements Stmt {}

  /**
   * {@code items} is empty when {@code PRINT} has no {@code printList} at all (bare {@code PRINT},
   * which just prints a newline).
   */
  record PrintStmt(List<PrintElement> items) implements Stmt {}

  /**
   * {@code seed} is {@code null} for bare {@code RANDOMIZE} (system-entropy reseed — the same
   * effect as an explicit {@code RANDOMIZE 0}, see {@code visitRandStmt}).
   */
  record RandStmt(NumExpr seed) implements Stmt {}

  record ReadStmt(List<AssignTarget> targets) implements Stmt {}

  record RemStmt() implements Stmt {}

  /** {@code target} is {@code null} for bare {@code RESTORE} (defaults to line 0). */
  record RestoreStmt(NumExpr target) implements Stmt {}

  record ReturnStmt() implements Stmt {}

  /** {@code target} is {@code null} for bare {@code RUN} (defaults to the program's first line). */
  record RunStmt(NumExpr target) implements Stmt {}

  record SaveStmt(StrExpr fileName) implements Stmt {}

  record ScrollStmt() implements Stmt {}

  record SlowStmt() implements Stmt {}

  record StopStmt() implements Stmt {}

  record VerifyStmt(StrExpr fileName) implements Stmt {}
}
