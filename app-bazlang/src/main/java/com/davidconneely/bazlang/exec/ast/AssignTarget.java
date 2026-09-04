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
    /** The variable's name. */
    public final String name;

    /** Lazily-populated variable-reference cache; see the class Javadoc. */
    public EvalState.NumVarRef ref;

    /**
     * Create a target for the named scalar variable.
     *
     * @param name the variable's name.
     */
    public NumScalarTarget(String name) {
      this.name = name;
    }
  }

  /** A numeric array element target, e.g. {@code a(1, 2)}. */
  final class NumArrayTarget implements AssignTarget {
    /** The array's name. */
    public final String name;

    /** The element's index expressions. */
    public final List<NumExpr> indices;

    /** Lazily-populated variable-reference cache; see the class Javadoc. */
    public EvalState.NumArrayRef ref;

    /**
     * Create a target for one element of the named array.
     *
     * @param name the array's name.
     * @param indices the element's index expressions.
     */
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
    /** The variable's name. */
    public final String name;

    /** The subscript/slice, or {@code null} for a plain scalar target. */
    public final StrSubscript subscript;

    /** Lazily-populated variable-reference cache; see the class Javadoc. */
    public EvalState.StrVarRef ref;

    /**
     * Create a target for the named string variable, optionally subscripted.
     *
     * @param name the variable's name.
     * @param subscript the subscript/slice, or {@code null} for a plain scalar target.
     */
    public StrTarget(String name, StrSubscript subscript) {
      this.name = name;
      this.subscript = subscript;
    }
  }
}
