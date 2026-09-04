package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A lowered statement node, one case per {@code #XxxStmt} grammar alternative. Walked by {@code
 * AstStatementExecutor} via {@code switch} pattern matching.
 *
 * <p>{@link IfStmt#body()} carries the {@code THEN}-clause's statements in their natural nested
 * shape - it is not itself what the executor's normal top-to-bottom driver walks (that driver walks
 * a single flat {@code List<Stmt>} per program line, produced by {@code
 * AstLowering.lowerStatements}, which recursively inlines {@code IfStmt} bodies into the flat list
 * exactly as {@code ProgramLine.flatten()} does today (the "flat skip-scan" quirk - see {@link
 * Stmt}'s class Javadoc). This is the list a {@code ProgramLine}/{@code Interpreter} walks for
 * execution; {@link Stmt.IfStmt#body()} itself holds the un-flattened nested form.
 */
public sealed interface Stmt {
  /**
   * {@code APLAY} - plays up to 3 simultaneous channels asynchronously, returning immediately.
   *
   * @param channels each given channel's note-string source, in order (A, B, C); fewer than 3
   *     entries leaves the remaining channels silent.
   */
  record AplayStmt(List<StrExpr> channels) implements Stmt {}

  /**
   * {@code BEEP duration, pitch}.
   *
   * @param duration the tone's duration, in seconds.
   * @param pitch the tone's pitch.
   */
  record BeepStmt(NumExpr duration, NumExpr pitch) implements Stmt {}

  /**
   * {@code BRIGHT n} - sets the persistent default brightness.
   *
   * @param value the new brightness value.
   */
  record BrightStmt(NumExpr value) implements Stmt {}

  /**
   * {@code CIRCLE x, y, r}.
   *
   * @param styles any {@code INK}/{@code PAPER}/... prefix on the statement.
   * @param cx the centre's x-coordinate.
   * @param cy the centre's y-coordinate.
   * @param radius the radius.
   */
  record CircleStmt(List<StyleItem> styles, NumExpr cx, NumExpr cy, NumExpr radius)
      implements Stmt {}

  /** {@code CLEAR} - resets variables and runtime state without reloading the program. */
  record ClearStmt() implements Stmt {}

  /** {@code CLS} - clears the screen. */
  record ClsStmt() implements Stmt {}

  /** {@code CONT} - resumes a stopped program from where it left off. */
  record ContStmt() implements Stmt {}

  /**
   * {@code DATA v1, v2, ...} - values consumed in order by {@code READ}.
   *
   * @param values the statement's literal values, in source order.
   */
  record DataStmt(List<Expr> values) implements Stmt {}

  /**
   * {@code name} ends with {@code $} for a string-valued function; {@code body} is checked against
   * that at lowering time, matching the {@code visitDefFnStmt} type-mismatch check.
   *
   * @param name the function's name.
   * @param params the function's parameter names.
   * @param body the single-expression function body.
   */
  record DefFnStmt(String name, List<String> params, Expr body) implements Stmt {}

  /**
   * {@code DIM name(dims)} - declares a numeric or string array.
   *
   * @param name the array's name.
   * @param isString whether this declares a string array (as opposed to numeric).
   * @param dims the array's size in each dimension.
   */
  record DimStmt(String name, boolean isString, List<NumExpr> dims) implements Stmt {}

  /**
   * {@code DRAW dx, dy} - draws a line relative to the graphics cursor.
   *
   * @param styles any {@code INK}/{@code PAPER}/... prefix on the statement.
   * @param dx the x displacement.
   * @param dy the y displacement.
   */
  record DrawStmt(List<StyleItem> styles, NumExpr dx, NumExpr dy) implements Stmt {}

  /** {@code FAST} - disables visual flourishes (e.g. per-character render delay). */
  record FastStmt() implements Stmt {}

  /**
   * {@code FLASH n} - sets the persistent default flash (blink) setting.
   *
   * @param value the new flash value.
   */
  record FlashStmt(NumExpr value) implements Stmt {}

  /**
   * {@code step} defaults to a literal {@code 1.0} at lowering time when the grammar's optional
   * {@code STEP} clause is absent, so the executor never needs a nullability check for it.
   *
   * @param forVar the loop variable's name.
   * @param start the loop's initial value.
   * @param end the loop's terminating value.
   * @param step the amount to increment {@code forVar} by each iteration.
   */
  record ForStmt(String forVar, NumExpr start, NumExpr end, NumExpr step) implements Stmt {}

  /**
   * {@code GOSUB target}.
   *
   * @param target the line to call.
   */
  record GosubStmt(NumExpr target) implements Stmt {}

  /**
   * {@code GOTO target}.
   *
   * @param target the line to jump to.
   */
  record GotoStmt(NumExpr target) implements Stmt {}

  /**
   * {@code IF condition THEN body}. See the class Javadoc for why {@code body} is not what the
   * executor's normal driver walks.
   *
   * @param condition the condition; the body executes when this is nonzero.
   * @param body the {@code THEN}-clause's statements, in their natural nested shape.
   */
  record IfStmt(NumExpr condition, List<Stmt> body) implements Stmt {}

  /**
   * {@code INK n} - sets the persistent default ink (foreground) colour.
   *
   * @param value the new ink colour.
   */
  record InkStmt(NumExpr value) implements Stmt {}

  /**
   * {@code INPUT target}.
   *
   * @param target the variable to assign the entered value to.
   */
  record InputStmt(AssignTarget target) implements Stmt {}

  /**
   * {@code INVERSE n} - sets the persistent default inverse-video setting.
   *
   * @param value the new inverse value.
   */
  record InverseStmt(NumExpr value) implements Stmt {}

  /**
   * {@code LET target = value} (the {@code LET} keyword itself is optional in the grammar).
   *
   * @param target the assignment's destination.
   * @param value the expression to assign.
   */
  record LetStmt(AssignTarget target, Expr value) implements Stmt {}

  /**
   * {@code range} is {@code null} when no line range was given (list everything).
   *
   * @param range the line range to list, or {@code null} for the whole program.
   */
  record ListStmt(LineRange range) implements Stmt {}

  /**
   * {@code LOAD fileName}.
   *
   * @param fileName the file to load.
   */
  record LoadStmt(StrExpr fileName) implements Stmt {}

  /**
   * {@code MERGE fileName} - loads another program's lines into the current one, overlaying rather
   * than replacing.
   *
   * @param fileName the file to merge.
   */
  record MergeStmt(StrExpr fileName) implements Stmt {}

  /** {@code NEW} - clears the program and all runtime state. */
  record NewStmt() implements Stmt {}

  /**
   * {@code NEXT forVar} - closes the innermost {@code FOR} loop matching {@code forVar}.
   *
   * @param forVar the loop variable's name.
   */
  record NextStmt(String forVar) implements Stmt {}

  /**
   * {@code OVER n} - sets the persistent default overlay (XOR-plot) setting.
   *
   * @param value the new over value.
   */
  record OverStmt(NumExpr value) implements Stmt {}

  /**
   * {@code PAPER n} - sets the persistent default paper (background) colour.
   *
   * @param value the new paper colour.
   */
  record PaperStmt(NumExpr value) implements Stmt {}

  /**
   * {@code PAUSE frames} - waits, at 50 Hz frame granularity, for a keypress or the timeout.
   *
   * @param frames how many 50 Hz frames to wait, or {@code 0} to wait indefinitely.
   */
  record PauseStmt(NumExpr frames) implements Stmt {}

  /**
   * {@code PLAY} - plays up to 3 simultaneous channels synchronously, blocking until they finish.
   *
   * @param channels each given channel's note-string source, in order (A, B, C); fewer than 3
   *     entries leaves the remaining channels silent.
   */
  record PlayStmt(List<StrExpr> channels) implements Stmt {}

  /**
   * {@code PLOT x, y}.
   *
   * @param styles any {@code INK}/{@code PAPER}/... prefix on the statement.
   * @param x the pixel x-coordinate.
   * @param y the pixel y-coordinate.
   */
  record PlotStmt(List<StyleItem> styles, NumExpr x, NumExpr y) implements Stmt {}

  /**
   * {@code PLOTMODE mode} - selects the {@link com.davidconneely.cell.PixelMode} used by subsequent
   * {@code PLOT}/{@code UNPLOT}.
   *
   * @param mode the new plot mode code.
   */
  record PlotmodeStmt(NumExpr mode) implements Stmt {}

  /**
   * {@code items} is empty when {@code PRINT} has no {@code printList} at all (bare {@code PRINT},
   * which just prints a newline).
   *
   * @param items the print list's elements, in source order.
   */
  record PrintStmt(List<PrintElement> items) implements Stmt {}

  /**
   * {@code seed} is {@code null} for bare {@code RANDOMIZE} (system-entropy reseed - the same
   * effect as an explicit {@code RANDOMIZE 0}, see {@code visitRandStmt}).
   *
   * @param seed the seed expression, or {@code null} for a system-entropy reseed.
   */
  record RandStmt(NumExpr seed) implements Stmt {}

  /**
   * {@code READ target1, target2, ...} - assigns the next {@code DATA} values in order.
   *
   * @param targets the variables to assign, in order.
   */
  record ReadStmt(List<AssignTarget> targets) implements Stmt {}

  /** {@code REM} - a comment; a no-op at runtime. */
  record RemStmt() implements Stmt {}

  /**
   * {@code target} is {@code null} for bare {@code RESTORE} (defaults to line 0).
   *
   * @param target the line to restore the {@code DATA} pointer to, or {@code null} for line 0.
   */
  record RestoreStmt(NumExpr target) implements Stmt {}

  /** {@code RETURN} - resumes after the matching {@code GOSUB}. */
  record ReturnStmt() implements Stmt {}

  /**
   * {@code target} is {@code null} for bare {@code RUN} (defaults to the program's first line).
   *
   * @param target the line to start execution at, or {@code null} for the first line.
   */
  record RunStmt(NumExpr target) implements Stmt {}

  /**
   * {@code SAVE fileName}.
   *
   * @param fileName the file to save to.
   */
  record SaveStmt(StrExpr fileName) implements Stmt {}

  /** {@code SCROLL} - scrolls the screen up by one line. */
  record ScrollStmt() implements Stmt {}

  /** {@code SLOW} - re-enables visual flourishes disabled by {@code FAST}. */
  record SlowStmt() implements Stmt {}

  /** {@code STOP} - halts the program, reporting a stop rather than an error. */
  record StopStmt() implements Stmt {}

  /**
   * {@code VERIFY fileName} - loads a file and compares it against the current program without
   * replacing it.
   *
   * @param fileName the file to verify against.
   */
  record VerifyStmt(StrExpr fileName) implements Stmt {}
}
