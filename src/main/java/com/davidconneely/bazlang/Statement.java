package com.davidconneely.bazlang;

import java.util.List;

public sealed interface Statement {
  record Clear() implements Statement {}

  record Cls() implements Statement {}

  record Cont() implements Statement {}

  record Copy() implements Statement {}

  record Dim(String variable, List<Expression.NumExpr> dimensions) implements Statement {}

  record Fast() implements Statement {}

  record For(
      String variable, Expression.NumExpr start, Expression.NumExpr end, Expression.NumExpr step)
      implements Statement {}

  record Gosub(Expression.NumExpr targetLabel) implements Statement {}

  record Goto(Expression.NumExpr targetLabel) implements Statement {}

  record If(Expression.NumExpr condition, Statement thenStmt) implements Statement {}

  record Input(Expression target) implements Statement {}

  record Let(Expression target, Expression value) implements Statement {}

  record ListStmt(Expression.NumExpr startLabel, Expression.NumExpr endLabel)
      implements Statement {}

  record LList(Expression.NumExpr startLabel, Expression.NumExpr endLabel) implements Statement {}

  record Load(Expression.StrExpr filename) implements Statement {}

  record LPrint(List<PrintItem> items, boolean newline) implements Statement {}

  record New() implements Statement {}

  record Next(String variable) implements Statement {}

  record Pause(Expression.NumExpr frames) implements Statement {}

  record Plot(Expression.NumExpr x, Expression.NumExpr y) implements Statement {}

  record Poke(Expression.NumExpr address, Expression.NumExpr data) implements Statement {}

  record Print(List<PrintItem> items, boolean newline) implements Statement {}

  record Rand(Expression.NumExpr seed) implements Statement {}

  record Rem(String comment) implements Statement {}

  record Return() implements Statement {}

  record Run(Expression.NumExpr targetLabel) implements Statement {}

  record Save(Expression.StrExpr filename) implements Statement {}

  record Scroll() implements Statement {}

  record Slow() implements Statement {}

  record Stop() implements Statement {}

  record Unplot(Expression.NumExpr x, Expression.NumExpr y) implements Statement {}
}
