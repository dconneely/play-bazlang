package com.davidconneely.bazlang;

import java.util.List;

public sealed interface Expression {
  sealed interface Numeric extends Expression {
    record Literal(double value) implements Numeric {}

    record ScalarRef(java.lang.String name) implements Numeric {}

    record SubscriptRef(java.lang.String name, List<Expression.Numeric> indices)
        implements Numeric {}

    record UnaryOp(TokenType operator, Numeric operand) implements Numeric {}

    record BinaryOp(Numeric left, TokenType operator, Numeric right) implements Numeric {}

    record NumericComparison(Numeric left, TokenType operator, Numeric right) implements Numeric {}

    record StringComparison(String left, TokenType operator, String right) implements Numeric {}

    record FuncCall(TokenType func, Numeric argument) implements Numeric {}

    record FuncCallStr(TokenType func, String argument) implements Numeric {}

    record NullaryCall(TokenType func) implements Numeric {}
  }

  sealed interface String extends Expression {
    record Literal(java.lang.String value) implements String {}

    record ScalarRef(java.lang.String name) implements String {}

    record SubscriptRef(java.lang.String name, List<Expression.Numeric> indices, Slice slice)
        implements String {}

    record Concatenation(String left, String right) implements String {}

    record FuncCall(TokenType func, Numeric argument) implements String {}

    record NullaryCall(TokenType func) implements String {}
  }

  record Slice(Numeric start, Numeric end) {}
}
