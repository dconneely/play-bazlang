package com.davidconneely.bazlang.exec.ast;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.exec.EvalState;
import java.util.List;

/**
 * A lowered string expression node. See {@link NumExpr}'s class Javadoc for the atom/expr collapse
 * and the mutable-reference-cache design shared by {@link StrVarExpr} and {@link StrSubscriptExpr}.
 */
public sealed interface StrExpr extends Expr {
  /** A string literal, resolved once at lowering time (quotes stripped, {@code ""} un-doubled). */
  record StrLiteral(BStr value) implements StrExpr {}

  /** A scalar string variable reference, e.g. {@code a$}. */
  final class StrVarExpr implements StrExpr {
    public final String name;
    public EvalState.StrVarRef ref;

    public StrVarExpr(String name) {
      this.name = name;
    }
  }

  /** A string array element or slice reference, e.g. {@code a$(1)}, {@code a$(1 TO 5)}. */
  final class StrSubscriptExpr implements StrExpr {
    public final String name;
    public final StrSubscript subscript;
    public EvalState.StrVarRef ref;

    public StrSubscriptExpr(String name, StrSubscript subscript) {
      this.name = name;
      this.subscript = subscript;
    }
  }

  /** String concatenation: {@code left + right}. */
  record StrConcat(StrExpr left, StrExpr right) implements StrExpr {}

  /** A call to one of the built-in string functions (e.g. {@code CHR$}, {@code STR$}). */
  record StrFuncCall(StrFuncKind kind, List<Expr> args) implements StrExpr {}

  /** A call to a user-defined string {@code DEF FN}. */
  record FnStrCall(String name, List<Expr> args) implements StrExpr {}

  /** {@code left AND right}: {@code left} if {@code right != 0}, else {@code ""}. */
  record StrAnd(StrExpr left, NumExpr right) implements StrExpr {}
}
