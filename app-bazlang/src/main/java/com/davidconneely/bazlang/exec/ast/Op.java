package com.davidconneely.bazlang.exec.ast;

/**
 * Arithmetic and comparison operators, resolved once at lowering time. Promoted from the {@code
 * int} constants formerly held in a class named {@code Ops} (since removed) to a real enum so every
 * {@code switch} on an operator is compiler-checked for exhaustiveness.
 */
public enum Op {
  /** {@code *}. */
  MUL,
  /** {@code /}. */
  DIV,
  /** {@code +}. */
  ADD,
  /** {@code -}. */
  SUB,
  /** {@code **}/{@code ^}. */
  POW,
  /** {@code =}. */
  EQ,
  /** {@code <>}. */
  NE,
  /** {@code <}. */
  LT,
  /** {@code <=}. */
  LE,
  /** {@code >}. */
  GT,
  /** {@code >=}. */
  GE
}
