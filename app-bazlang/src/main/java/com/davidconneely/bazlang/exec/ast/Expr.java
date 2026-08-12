package com.davidconneely.bazlang.exec.ast;

/**
 * Common supertype of every lowered expression node, mirroring the grammar's {@code expression}
 * rule ({@code numExpr | strExpr}). Used wherever a value can be either type: {@code LET}
 * assignment values, {@code DEF FN} bodies, {@code READ}/{@code DATA} items, {@code PRINT} items,
 * user-{@code FN} call arguments.
 */
public sealed interface Expr permits NumExpr, StrExpr {}
