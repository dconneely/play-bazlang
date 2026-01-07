package com.davidconneely.bazlang;

public sealed interface PrintItem {
  record Expr(Expression expr) implements PrintItem {}

  record At(Expression.NumExpr row, Expression.NumExpr col) implements PrintItem {}

  record Tab(Expression.NumExpr col) implements PrintItem {}
}
