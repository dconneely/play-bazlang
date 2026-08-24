package com.davidconneely.bazlang.exec.ast;

import com.davidconneely.bazlang.exec.EvalState;
import java.util.List;

/**
 * A lowered {@code assignmentTarget}: the destination of {@code LET}, {@code INPUT}, and {@code
 * READ}. Mirrors the mutable-reference-cache design of {@link NumExpr}'s variable nodes (see its
 * class Javadoc) - the cache is resolved on first assignment, not at lowering time.
 */
public sealed interface AssignTarget {
  /** A scalar numeric variable target, e.g. {@code x}. */
  final class NumScalarTarget implements AssignTarget {
    public final String name;
    public EvalState.NumVarRef ref;

    public NumScalarTarget(String name) {
      this.name = name;
    }
  }

  /** A numeric array element target, e.g. {@code a(1, 2)}. */
  final class NumArrayTarget implements AssignTarget {
    public final String name;
    public final List<NumExpr> indices;
    public EvalState.NumArrayRef ref;

    public NumArrayTarget(String name, List<NumExpr> indices) {
      this.name = name;
      this.indices = indices;
    }
  }

  /**
   * A string variable target, scalar or subscripted, e.g. {@code a$}, {@code a$(1)}, {@code a$(1 TO
   * 5)}. {@code subscript} is {@code null} for a plain scalar target ({@code a$}).
   */
  final class StrTarget implements AssignTarget {
    public final String name;
    public final StrSubscript subscript;
    public EvalState.StrVarRef ref;

    public StrTarget(String name, StrSubscript subscript) {
      this.name = name;
      this.subscript = subscript;
    }
  }
}
