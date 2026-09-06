package com.davidconneely.bazlang.exec.ast;

/**
 * Assigns (or reuses) a small integer id for a numeric/string scalar or array name, for {@link
 * AstLowering} to bake into an AST node's immutable {@code id} field at lowering time - see {@link
 * NumExpr}'s class Javadoc. Implemented by {@code EvalState}, so this interface is the only
 * dependency lowering has on execution state, keeping {@code AstLowering} free of a direct
 * dependency on the concrete {@code EvalState} class.
 *
 * <p>Ids are scoped to one implementing instance's lifetime, not shared across separate {@code
 * EvalState}s - the same name lowered against two different allocators may (and generally will) get
 * different ids.
 */
public interface VarIdAllocator {
  /**
   * Returns the given scalar numeric variable name's id, assigning a new one on first use.
   *
   * @param name the variable's name.
   * @return the id.
   */
  int numVarId(String name);

  /**
   * Returns the given numeric array name's id, assigning a new one on first use.
   *
   * @param name the array's name.
   * @return the id.
   */
  int numArrayId(String name);

  /**
   * Returns the given string variable (scalar or array) name's id, assigning a new one on first
   * use.
   *
   * @param name the variable's name.
   * @return the id.
   */
  int strVarId(String name);
}
