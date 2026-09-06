package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A lowered numeric expression node. Collapses the grammar's {@code numExpr}/{@code numAtom} split
 * (a syntax-level precedence rule, not a runtime one - see {@code numFunc}'s comment on why {@code
 * SIN PI/2} parses as {@code SIN(PI)/2}): lowering either context type for the same underlying
 * expression produces the same node type here.
 *
 * <p>Most cases are plain records. {@link NumVarExpr} and {@link NumArrayExpr} carry a small final
 * {@code id} field instead (replacing the pre-AST grammar's untyped {@code ctx.varRef}, now
 * removed): an integer assigned by a {@link VarIdAllocator} at lowering time, that the
 * execution-time fast path uses to look its {@code EvalState.NumVarRef}/{@code NumArrayRef} up by
 * array index instead of a hash-map lookup per access in tight loops. Baking the id in at
 * construction, rather than caching the resolved {@code Ref} lazily on first evaluation, keeps
 * these nodes strictly immutable - see {@code AstLowering}'s class Javadoc for why that matters.
 */
public sealed interface NumExpr extends Expr {
  /**
   * A numeric literal, resolved once at lowering time from either {@code NUM_LITERAL} or {@code
   * BIN_LITERAL} token text.
   *
   * @param value the literal's numeric value.
   */
  record NumLiteral(double value) implements NumExpr {}

  /** A scalar numeric variable reference, e.g. {@code x}. */
  final class NumVarExpr implements NumExpr {
    /** The variable's name. */
    public final String name;

    /** The variable's id, assigned at lowering time; see the class Javadoc. */
    public final int id;

    /**
     * Create a reference to the named scalar variable.
     *
     * @param name the variable's name.
     * @param id the variable's id, from {@link VarIdAllocator#numVarId}.
     */
    public NumVarExpr(String name, int id) {
      this.name = name;
      this.id = id;
    }
  }

  /** A numeric array element reference, e.g. {@code a(1, 2)}. */
  final class NumArrayExpr implements NumExpr {
    /** The array's name. */
    public final String name;

    /** The element's index expressions. */
    public final List<NumExpr> indices;

    /** The array's id, assigned at lowering time; see the class Javadoc. */
    public final int id;

    /**
     * Create a reference to one element of the named array.
     *
     * @param name the array's name.
     * @param indices the element's index expressions.
     * @param id the array's id, from {@link VarIdAllocator#numArrayId}.
     */
    public NumArrayExpr(String name, List<NumExpr> indices, int id) {
      this.name = name;
      this.indices = indices;
      this.id = id;
    }
  }

  /**
   * A call to one of the built-in numeric functions (e.g. {@code SIN}, {@code POINT}).
   *
   * @param kind which built-in function.
   * @param args the call's argument expressions.
   */
  record NumFuncCall(NumFuncKind kind, List<Expr> args) implements NumExpr {}

  /**
   * A call to a user-defined numeric {@code DEF FN}.
   *
   * @param name the function's name.
   * @param args the call's argument expressions.
   */
  record FnNumCall(String name, List<Expr> args) implements NumExpr {}

  /**
   * A binary arithmetic operation: {@code *}, {@code /}, {@code +}, {@code -}, {@code **}/{@code
   * ^}.
   *
   * @param op which operation.
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record NumBinaryOp(Op op, NumExpr left, NumExpr right) implements NumExpr {}

  /**
   * Unary negation, e.g. {@code -x}.
   *
   * @param operand the expression being negated.
   */
  record NumUnaryMinus(NumExpr operand) implements NumExpr {}

  /**
   * A numeric comparison, e.g. {@code x < y}; result is {@code 1.0} or {@code 0.0}.
   *
   * @param op which comparison.
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record NumCompare(Op op, NumExpr left, NumExpr right) implements NumExpr {}

  /**
   * A string comparison, e.g. {@code a$ < b$}; result is {@code 1.0} or {@code 0.0}. Part of {@code
   * numExpr}, not {@code strExpr}, in the grammar.
   *
   * @param op which comparison.
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record StrCompare(Op op, StrExpr left, StrExpr right) implements NumExpr {}

  /**
   * Logical negation: {@code NOT x} is {@code 1.0} if {@code x = 0}, else {@code 0.0}.
   *
   * @param operand the expression being negated.
   */
  record NumNot(NumExpr operand) implements NumExpr {}

  /**
   * {@code x AND y}: {@code x} if {@code y != 0}, else {@code 0.0}.
   *
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record NumAnd(NumExpr left, NumExpr right) implements NumExpr {}

  /**
   * {@code x OR y}: {@code 1.0} if {@code y != 0}, else {@code x}.
   *
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record NumOr(NumExpr left, NumExpr right) implements NumExpr {}
}
