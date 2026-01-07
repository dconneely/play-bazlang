package com.davidconneely.bazlang;

import java.util.List;

public sealed interface Expression {
  sealed interface NumExpr extends Expression {
    record Literal(double value) implements NumExpr {}

    record ScalarRef(String name) implements NumExpr {}

    record SubscriptRef(String name, List<NumExpr> indices) implements NumExpr {}

    record UnaryOp(TokenType operator, NumExpr operand) implements NumExpr {}

    record BinaryOp(NumExpr left, TokenType operator, NumExpr right) implements NumExpr {}

    record NumComp(NumExpr left, TokenType operator, NumExpr right) implements NumExpr {}

    record StrComp(StrExpr left, TokenType operator, StrExpr right) implements NumExpr {}

    record NumFunc(TokenType func, NumExpr argument) implements NumExpr {}

    record StrFunc(TokenType func, StrExpr argument) implements NumExpr {}

    record NullFunc(TokenType func) implements NumExpr {}
  }

  sealed interface StrExpr extends Expression {
    record Literal(String value) implements StrExpr {}

    record ScalarRef(String name) implements StrExpr {}

    record SubscriptRef(String name, List<NumExpr> indices, Slice slice) implements StrExpr {}

    record StrConcat(StrExpr left, StrExpr right) implements StrExpr {}

    record NumFunc(TokenType func, NumExpr argument) implements StrExpr {}

    record NullFunc(TokenType func) implements StrExpr {}
  }

  record Slice(NumExpr start, NumExpr end) {}
}
