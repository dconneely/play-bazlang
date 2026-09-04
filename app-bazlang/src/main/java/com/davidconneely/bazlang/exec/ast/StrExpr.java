package com.davidconneely.bazlang.exec.ast;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.exec.EvalState;
import java.util.List;

/**
 * A lowered string expression node. See {@link NumExpr}'s class Javadoc for the atom/expr collapse
 * and the mutable-reference-cache design shared by {@link StrVarExpr} and {@link StrSubscriptExpr}.
 */
public sealed interface StrExpr extends Expr {
  /**
   * A string literal, resolved once at lowering time (quotes stripped, {@code ""} un-doubled).
   *
   * @param value the literal's string value.
   */
  record StrLiteral(BStr value) implements StrExpr {}

  /** A scalar string variable reference, e.g. {@code a$}. */
  final class StrVarExpr implements StrExpr {
    /** The variable's name. */
    public final String name;

    /** Lazily-populated variable-reference cache; see the class Javadoc. */
    public EvalState.StrVarRef ref;

    /**
     * Create a reference to the named scalar variable.
     *
     * @param name the variable's name.
     */
    public StrVarExpr(String name) {
      this.name = name;
    }
  }

  /** A string array element or slice reference, e.g. {@code a$(1)}, {@code a$(1 TO 5)}. */
  final class StrSubscriptExpr implements StrExpr {
    /** The array's name. */
    public final String name;

    /** The element's index/slice. */
    public final StrSubscript subscript;

    /** Lazily-populated variable-reference cache; see the class Javadoc. */
    public EvalState.StrVarRef ref;

    /**
     * Create a reference to one element/slice of the named array.
     *
     * @param name the array's name.
     * @param subscript the element's index/slice.
     */
    public StrSubscriptExpr(String name, StrSubscript subscript) {
      this.name = name;
      this.subscript = subscript;
    }
  }

  /**
   * String concatenation: {@code left + right}.
   *
   * @param left the left-hand operand.
   * @param right the right-hand operand.
   */
  record StrConcat(StrExpr left, StrExpr right) implements StrExpr {}

  /**
   * A call to one of the built-in string functions (e.g. {@code CHR$}, {@code STR$}).
   *
   * @param kind which built-in function.
   * @param args the call's argument expressions.
   */
  record StrFuncCall(StrFuncKind kind, List<Expr> args) implements StrExpr {}

  /**
   * A call to a user-defined string {@code DEF FN}.
   *
   * @param name the function's name.
   * @param args the call's argument expressions.
   */
  record FnStrCall(String name, List<Expr> args) implements StrExpr {}

  /**
   * {@code left AND right}: {@code left} if {@code right != 0}, else {@code ""}.
   *
   * @param left the string operand.
   * @param right the numeric condition.
   */
  record StrAnd(StrExpr left, NumExpr right) implements StrExpr {}
}
