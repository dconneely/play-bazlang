package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.exec.ast.VarIdAllocator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owns numeric scalar/array variables, string scalar/array variables, and {@code DEF FN}
 * definitions - the four name-keyed variable namespaces {@link EvalState} previously held directly.
 * Extracted so {@code NEW}/{@code CLEAR} have one cohesive collaborator to reset, separate from
 * execution-position and control-flow state.
 *
 * <p>Also implements {@link VarIdAllocator}: each numeric/string scalar or array name is assigned a
 * small integer id, the first time it is seen, alongside its {@code Ref} object - the id and the
 * {@code Ref} are added together, so the two are always in step. {@code AstLowering} calls the
 * allocator once per name at lowering time and bakes the id into the AST node as a {@code final}
 * field (see {@link com.davidconneely.bazlang.exec.ast.NumExpr.NumVarExpr} and its siblings); the
 * execution-time fast path then looks the {@code Ref} up by id (an {@code ArrayList} index) instead
 * of caching a direct reference to it on the node. {@code DEF FN} definitions are looked up by name
 * on every call already (never cached on an AST node), so they carry no id.
 */
final class VariableStore implements VarIdAllocator {
  private final Map<String, Integer> numScalarIds = new HashMap<>();
  private final List<EvalState.NumVarRef> numScalarsById = new ArrayList<>();

  private final Map<String, Integer> numArrayIds = new HashMap<>();
  private final List<EvalState.NumArrayRef> numArraysById = new ArrayList<>();

  private final Map<String, Integer> strVarIds = new HashMap<>();
  private final List<EvalState.StrVarRef> strVarsById = new ArrayList<>();

  private final Map<String, EvalState.FnDefRef> fnDefinitions = new HashMap<>();

  @Override
  public int numVarId(String name) {
    return numScalarIds.computeIfAbsent(
        name,
        n -> {
          numScalarsById.add(new EvalState.NumVarRef(n));
          return numScalarsById.size() - 1;
        });
  }

  @Override
  public int numArrayId(String name) {
    return numArrayIds.computeIfAbsent(
        name,
        n -> {
          numArraysById.add(new EvalState.NumArrayRef(n));
          return numArraysById.size() - 1;
        });
  }

  @Override
  public int strVarId(String name) {
    return strVarIds.computeIfAbsent(
        name,
        n -> {
          strVarsById.add(new EvalState.StrVarRef(n));
          return strVarsById.size() - 1;
        });
  }

  EvalState.NumVarRef getOrAddNumVar(String name) {
    return numScalarsById.get(numVarId(name));
  }

  EvalState.NumArrayRef getOrAddNumArray(String name) {
    return numArraysById.get(numArrayId(name));
  }

  EvalState.StrVarRef getOrAddStrVar(String name) {
    return strVarsById.get(strVarId(name));
  }

  EvalState.FnDefRef getOrAddFnDef(String name) {
    return fnDefinitions.computeIfAbsent(name, EvalState.FnDefRef::new);
  }

  // ===== Id-based lookups (the AST fast path) =====

  EvalState.NumVarRef numVarRefById(int id) {
    return numScalarsById.get(id);
  }

  EvalState.NumArrayRef numArrayRefById(int id) {
    return numArraysById.get(id);
  }

  EvalState.StrVarRef strVarRefById(int id) {
    return strVarsById.get(id);
  }

  // ===== Numeric scalar variables =====

  boolean hasNumVar(String name) {
    Integer id = numScalarIds.get(name);
    EvalState.NumVarRef ref = (id != null) ? numScalarsById.get(id) : null;
    return ref != null && ref.initialised;
  }

  double numVar(String name) {
    Integer id = numScalarIds.get(name);
    EvalState.NumVarRef ref = (id != null) ? numScalarsById.get(id) : null;
    if (ref != null && ref.initialised) {
      return ref.value;
    }
    throw new IllegalArgumentException("Undefined variable: " + name);
  }

  EvalState.NumVarRef getNumVarRef(String name) {
    Integer id = numScalarIds.get(name);
    return (id != null) ? numScalarsById.get(id) : null;
  }

  void setNumVar(String name, double val) {
    EvalState.NumVarRef ref = getOrAddNumVar(name);
    ref.value = val;
    ref.initialised = true;
  }

  void removeNumVar(String name) {
    Integer id = numScalarIds.get(name);
    if (id != null) {
      numScalarsById.get(id).initialised = false;
    }
  }

  // ===== Numeric arrays =====

  boolean hasNumArray(String name) {
    Integer id = numArrayIds.get(name);
    EvalState.NumArrayRef ref = (id != null) ? numArraysById.get(id) : null;
    return ref != null && ref.array != null;
  }

  EvalState.NumArray numArray(String name) {
    Integer id = numArrayIds.get(name);
    EvalState.NumArrayRef ref = (id != null) ? numArraysById.get(id) : null;
    return (ref != null) ? ref.array : null;
  }

  void setNumArray(String name, EvalState.NumArray arr) {
    EvalState.NumArrayRef ref = getOrAddNumArray(name);
    ref.array = arr;
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned numeric array, for debugger inspection.
   */
  Map<String, EvalState.NumArray> numArraysSnapshot() {
    Map<String, EvalState.NumArray> result = new TreeMap<>();
    for (EvalState.NumArrayRef ref : numArraysById) {
      if (ref.array != null) {
        result.put(ref.name, ref.array);
      }
    }
    return result;
  }

  // ===== String variables (Scalar and Array) =====

  boolean hasStrVar(String name) {
    Integer id = strVarIds.get(name);
    EvalState.StrVarRef ref = (id != null) ? strVarsById.get(id) : null;
    return ref != null && ref.value != null;
  }

  EvalState.StrVar strVar(String name) {
    Integer id = strVarIds.get(name);
    EvalState.StrVarRef ref = (id != null) ? strVarsById.get(id) : null;
    return (ref != null) ? ref.value : null;
  }

  void setStrVar(String name, EvalState.StrVar val) {
    EvalState.StrVarRef ref = getOrAddStrVar(name);
    ref.value = val;
  }

  void removeStrVar(String name) {
    Integer id = strVarIds.get(name);
    if (id != null) {
      strVarsById.get(id).value = null;
    }
  }

  Map<String, Double> variablesSnapshot() {
    Map<String, Double> result = new TreeMap<>();
    for (EvalState.NumVarRef ref : numScalarsById) {
      if (ref.initialised) {
        result.put(ref.name, ref.value);
      }
    }
    return result;
  }

  Map<String, String> stringVariablesSnapshot() {
    Map<String, String> result = new TreeMap<>();
    for (EvalState.StrVarRef ref : strVarsById) {
      if (ref.value instanceof EvalState.StrVar.Scalar scalar) {
        result.put(ref.name, scalar.value().toJavaString());
      }
    }
    return result;
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned string array, for debugger inspection.
   */
  Map<String, EvalState.StrVar.Array> strArraysSnapshot() {
    Map<String, EvalState.StrVar.Array> result = new TreeMap<>();
    for (EvalState.StrVarRef ref : strVarsById) {
      if (ref.value instanceof EvalState.StrVar.Array array) {
        result.put(ref.name, array);
      }
    }
    return result;
  }

  // ===== Functions =====

  boolean hasFn(String name) {
    EvalState.FnDefRef ref = fnDefinitions.get(name);
    return ref != null && ref.def != null;
  }

  EvalState.FnDefinition fn(String name) {
    EvalState.FnDefRef ref = fnDefinitions.get(name);
    return (ref != null) ? ref.def : null;
  }

  void setFn(String name, EvalState.FnDefinition def) {
    EvalState.FnDefRef ref = getOrAddFnDef(name);
    ref.def = def;
  }

  /** A read-only, name-sorted snapshot of every defined {@code DEF FN}, for debugger inspection. */
  Map<String, EvalState.FnDefinition> fnDefinitionsSnapshot() {
    Map<String, EvalState.FnDefinition> result = new TreeMap<>();
    for (var entry : fnDefinitions.entrySet()) {
      if (entry.getValue().def != null) {
        result.put(entry.getKey(), entry.getValue().def);
      }
    }
    return result;
  }

  /**
   * Clears every variable/array/function's *value* (matching {@code CLEAR}) without discarding the
   * name-keyed ref objects or their ids - other AST nodes already hold the id baked in as an
   * immutable field.
   */
  void clear() {
    for (EvalState.NumVarRef ref : numScalarsById) {
      ref.initialised = false;
    }
    for (EvalState.NumArrayRef ref : numArraysById) {
      ref.array = null;
    }
    for (EvalState.StrVarRef ref : strVarsById) {
      ref.value = null;
    }
    for (EvalState.FnDefRef ref : fnDefinitions.values()) {
      ref.def = null;
    }
  }
}
