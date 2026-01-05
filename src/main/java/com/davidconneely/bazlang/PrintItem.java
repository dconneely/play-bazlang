package com.davidconneely.bazlang;

public sealed interface PrintItem {
  record Expr(Expression expr) implements PrintItem {}

  record At(Expression.Numeric row, Expression.Numeric col) implements PrintItem {}

  record Tab(Expression.Numeric col) implements PrintItem {}
}
