package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A user {@code DEF FN} definition in its AST-native form: {@code body} is already-lowered {@link
 * Expr}, not an ANTLR {@code ExpressionContext}.
 *
 * <p>Deliberately <em>not</em> {@code EvalState.FnDefinition} (which still holds an ANTLR {@code
 * ExpressionContext} body, read by the still-live {@code ExpressionEvaluator}): changing that
 * record's body type would touch the live interpreter, out of scope until the Phase 4 cutover (see
 * {@code localonly-plan-CUSTOM-AST.md}, Phase 2 notes). Until then, {@code AstStatementExecutor}
 * keeps its own {@code Map<String, AstFnDefinition>}, separate from {@code EvalState}'s — mirroring
 * how it also keeps its own AST-flavoured program map rather than reading {@code
 * EvalState.program()}. {@code AstExpressionEvaluator}'s {@code FN}-call resolution continues to
 * read {@code EvalState.fn()} (still ANTLR-based) until that cutover, so {@code DEF FN} statements
 * executed via {@code AstStatementExecutor} are not yet visible to {@code FN} calls evaluated via
 * {@code AstExpressionEvaluator} — the two halves are verified independently in this phase, same as
 * the rest of the not-yet-wired-in AST executor.
 */
public record AstFnDefinition(String name, List<String> params, Expr body) {}
