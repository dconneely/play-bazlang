package com.davidconneely.bazlang.exec.ast;

/**
 * A lowered {@code lineRange}, used by the {@code LIST} statement. Either bound may be {@code
 * null}, meaning "use the default" ({@code Limits.MIN_TARGET_LABEL} / {@code
 * Limits.MAX_TARGET_LABEL} at evaluation time) — a {@code null} {@link Stmt.ListStmt#range} means
 * no range was given at all (list everything).
 *
 * <p>Unlike the grammar rule (which has no alt labels for {@code start}/{@code end}, forcing {@code
 * ProgramEditor}/{@code StatementExecutor} today to sniff {@code ctx.getText()} to tell "{@code n
 * TO}" from "{@code TO n}" when only one bound is given), lowering disambiguates once from tree
 * child order, so no text-sniffing survives into the AST or its evaluator.
 *
 * @param from the lower bound, or {@code null} for the default minimum
 * @param to the upper bound, or {@code null} for the default maximum
 */
public record LineRange(NumExpr from, NumExpr to) {}
