package com.davidconneely.bazlang.exec.ast;

/**
 * One element of a lowered {@code printList}, in source order — a value, an {@code AT}/{@code TAB}
 * positioning item, an inline style setting, or a separator. Preserves the grammar's {@code
 * printSep}-driven interleaving (separators carry tab-stop/newline meaning in {@code
 * visitPrintStmt}, so they're modelled as elements in the sequence, not discarded structure) — see
 * {@code AstLowering}'s statement Javadoc.
 */
public sealed interface PrintElement {
  /** A value to print: {@code PRINT x}, {@code PRINT a$}. */
  record ValueItem(Expr value) implements PrintElement {}

  /** {@code AT row, col}. */
  record AtItem(NumExpr row, NumExpr col) implements PrintElement {}

  /** {@code TAB col}. */
  record TabItem(NumExpr col) implements PrintElement {}

  /** An inline style setting within the print list, e.g. {@code PRINT INK 2; "x"}. */
  record StyleElement(StyleItem style) implements PrintElement {}

  /**
   * A {@code printSep}: {@code ,} (next tab stop), {@code ;} (concatenate), or {@code '} (newline).
   */
  record Sep(char text) implements PrintElement {}
}
