package com.davidconneely.bazlang;

import java.util.List;

public sealed interface Statement {
  record Clear() implements Statement {}

  record Cls() implements Statement {}

  record Cont() implements Statement {}

  record Copy() implements Statement {}

  record Dim(String variable, List<Expression.Numeric> dimensions) implements Statement {}

  record Fast() implements Statement {}

  record For(
      String variable, Expression.Numeric start, Expression.Numeric end, Expression.Numeric step)
      implements Statement {}

  record Gosub(Expression.Numeric targetLabel) implements Statement {}

  record Goto(Expression.Numeric targetLabel) implements Statement {}

  record If(Expression.Numeric condition, Statement thenStatement) implements Statement {}

  record Input(Expression target) implements Statement {}

  record Let(Expression target, Expression value) implements Statement {}

  record ListStmt(Expression.Numeric start, Expression.Numeric end) implements Statement {}

  record LList(Expression.Numeric start, Expression.Numeric end) implements Statement {}

  record Load(Expression.String filename) implements Statement {}

  record LPrint(List<PrintItem> items, boolean newline) implements Statement {}

  record New() implements Statement {}

  record Next(String variable) implements Statement {}

  record Pause(Expression.Numeric frames) implements Statement {}

  record Plot(Expression.Numeric x, Expression.Numeric y) implements Statement {}

  record Poke(Expression.Numeric address, Expression.Numeric data) implements Statement {}

  record Print(List<PrintItem> items, boolean newline) implements Statement {}

  record Rand(Expression.Numeric seed) implements Statement {}

  record Rem(String comment) implements Statement {}

  record Return() implements Statement {}

  record Run(Expression.Numeric targetLabel) implements Statement {}

  record Save(Expression.String filename) implements Statement {}

  record Scroll() implements Statement {}

  record Slow() implements Statement {}

  record Stop() implements Statement {}

  record Unplot(Expression.Numeric x, Expression.Numeric y) implements Statement {}
}
