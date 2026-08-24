package com.davidconneely.bazlang.exec.ast;

/**
 * A lowered {@code styleItem}: one {@code INK}/{@code PAPER}/{@code BRIGHT}/{@code FLASH}/{@code
 * INVERSE}/{@code OVER} setting, used both in a {@code styleList} (the prefix on {@code PLOT},
 * {@code DRAW}, {@code CIRCLE}) and inline within a {@code PRINT} item list. Replaces the grammar's
 * six {@code Style*Item} alternatives with one node type (design decision 8 in {@code
 * localonly-plan-CUSTOM-AST.md}).
 *
 * <p>Not used for the six top-level style <em>statements</em> ({@code INK n}, {@code PAPER n}, ...)
 * - those are distinct {@link Stmt} cases, since (unlike a styleList/print-item setting) they also
 * update {@code EvalState}'s persistent default styles, not just the screen's active ones for the
 * current call.
 */
public record StyleItem(StyleKind kind, NumExpr value) {
  /** Which of the six style settings this item is. */
  public enum StyleKind {
    INK,
    PAPER,
    BRIGHT,
    FLASH,
    INVERSE,
    OVER
  }
}
