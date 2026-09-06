package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A lowered {@code assignmentTarget}: the destination of {@code LET}, {@code INPUT}, and {@code
 * READ}. Mirrors the immutable-id design of {@link NumExpr}'s variable nodes (see its class
 * Javadoc) - the id is assigned at lowering time, not resolved lazily on first assignment.
 */
public sealed interface AssignTarget {
  /** A scalar numeric variable target, e.g. {@code x}. */
  final class NumScalarTarget implements AssignTarget {
    /** The variable's name. */
    public final String name;

    /** The variable's id, assigned at lowering time; see the class Javadoc. */
    public final int id;

    /**
     * Create a target for the named scalar variable.
     *
     * @param name the variable's name.
     * @param id the variable's id, from {@link VarIdAllocator#numVarId}.
     */
    public NumScalarTarget(String name, int id) {
      this.name = name;
      this.id = id;
    }
  }

  /** A numeric array element target, e.g. {@code a(1, 2)}. */
  final class NumArrayTarget implements AssignTarget {
    /** The array's name. */
    public final String name;

    /** The element's index expressions. */
    public final List<NumExpr> indices;

    /** The array's id, assigned at lowering time; see the class Javadoc. */
    public final int id;

    /**
     * Create a target for one element of the named array.
     *
     * @param name the array's name.
     * @param indices the element's index expressions.
     * @param id the array's id, from {@link VarIdAllocator#numArrayId}.
     */
    public NumArrayTarget(String name, List<NumExpr> indices, int id) {
      this.name = name;
      this.indices = indices;
      this.id = id;
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

    /** The variable's id, assigned at lowering time; see the class Javadoc. */
    public final int id;

    /**
     * Create a target for the named string variable, optionally subscripted.
     *
     * @param name the variable's name.
     * @param subscript the subscript/slice, or {@code null} for a plain scalar target.
     * @param id the variable's id, from {@link VarIdAllocator#strVarId}.
     */
    public StrTarget(String name, StrSubscript subscript, int id) {
      this.name = name;
      this.subscript = subscript;
      this.id = id;
    }
  }
}
