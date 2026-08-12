package com.davidconneely.bazlang.exec.ast;

/**
 * Arithmetic and comparison operators, resolved once at lowering time. Promoted from the {@code
 * int} constants in {@link com.davidconneely.bazlang.exec.Ops} to a real enum so every {@code
 * switch} on an operator is compiler-checked for exhaustiveness.
 */
public enum Op {
  MUL,
  DIV,
  ADD,
  SUB,
  POW,
  EQ,
  NE,
  LT,
  LE,
  GT,
  GE
}
