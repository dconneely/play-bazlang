package com.davidconneely.bazlang.exec;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owns numeric scalar/array variables, string scalar/array variables, and {@code DEF FN}
 * definitions - the four name-keyed variable namespaces {@link EvalState} previously held directly.
 * Extracted so {@code NEW}/{@code CLEAR} have one cohesive collaborator to reset, separate from
 * execution-position and control-flow state.
 */
final class VariableStore {
  private final Map<String, EvalState.NumVarRef> numScalars = new HashMap<>();
  private final Map<String, EvalState.NumArrayRef> numArrays = new HashMap<>();
  private final Map<String, EvalState.StrVarRef> strVars = new HashMap<>();
  private final Map<String, EvalState.FnDefRef> fnDefinitions = new HashMap<>();

  EvalState.NumVarRef getOrAddNumVar(String name) {
    return numScalars.computeIfAbsent(name, EvalState.NumVarRef::new);
  }

  EvalState.NumArrayRef getOrAddNumArray(String name) {
    return numArrays.computeIfAbsent(name, EvalState.NumArrayRef::new);
  }

  EvalState.StrVarRef getOrAddStrVar(String name) {
    return strVars.computeIfAbsent(name, EvalState.StrVarRef::new);
  }

  EvalState.FnDefRef getOrAddFnDef(String name) {
    return fnDefinitions.computeIfAbsent(name, EvalState.FnDefRef::new);
  }

  // ===== Numeric scalar variables =====

  boolean hasNumVar(String name) {
    EvalState.NumVarRef ref = numScalars.get(name);
    return ref != null && ref.initialised;
  }

  double numVar(String name) {
    EvalState.NumVarRef ref = numScalars.get(name);
    if (ref != null && ref.initialised) {
      return ref.value;
    }
    throw new IllegalArgumentException("Undefined variable: " + name);
  }

  EvalState.NumVarRef getNumVarRef(String name) {
    return numScalars.get(name);
  }

  void setNumVar(String name, double val) {
    EvalState.NumVarRef ref = getOrAddNumVar(name);
    ref.value = val;
    ref.initialised = true;
  }

  void removeNumVar(String name) {
    EvalState.NumVarRef ref = numScalars.get(name);
    if (ref != null) {
      ref.initialised = false;
    }
  }

  // ===== Numeric arrays =====

  boolean hasNumArray(String name) {
    EvalState.NumArrayRef ref = numArrays.get(name);
    return ref != null && ref.array != null;
  }

  EvalState.NumArray numArray(String name) {
    EvalState.NumArrayRef ref = numArrays.get(name);
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
    for (var entry : numArrays.entrySet()) {
      if (entry.getValue().array != null) {
        result.put(entry.getKey(), entry.getValue().array);
      }
    }
    return result;
  }

  // ===== String variables (Scalar and Array) =====

  boolean hasStrVar(String name) {
    EvalState.StrVarRef ref = strVars.get(name);
    return ref != null && ref.value != null;
  }

  EvalState.StrVar strVar(String name) {
    EvalState.StrVarRef ref = strVars.get(name);
    return (ref != null) ? ref.value : null;
  }

  void setStrVar(String name, EvalState.StrVar val) {
    EvalState.StrVarRef ref = getOrAddStrVar(name);
    ref.value = val;
  }

  void removeStrVar(String name) {
    EvalState.StrVarRef ref = strVars.get(name);
    if (ref != null) {
      ref.value = null;
    }
  }

  Map<String, Double> variablesSnapshot() {
    Map<String, Double> result = new TreeMap<>();
    for (var entry : numScalars.entrySet()) {
      if (entry.getValue().initialised) {
        result.put(entry.getKey(), entry.getValue().value);
      }
    }
    return result;
  }

  Map<String, String> stringVariablesSnapshot() {
    Map<String, String> result = new TreeMap<>();
    for (var entry : strVars.entrySet()) {
      if (entry.getValue().value instanceof EvalState.StrVar.Scalar scalar) {
        result.put(entry.getKey(), scalar.value().toJavaString());
      }
    }
    return result;
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned string array, for debugger inspection.
   */
  Map<String, EvalState.StrVar.Array> strArraysSnapshot() {
    Map<String, EvalState.StrVar.Array> result = new TreeMap<>();
    for (var entry : strVars.entrySet()) {
      if (entry.getValue().value instanceof EvalState.StrVar.Array array) {
        result.put(entry.getKey(), array);
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
   * name-keyed ref objects themselves - other AST nodes may already hold a cached reference to one.
   */
  void clear() {
    for (EvalState.NumVarRef ref : numScalars.values()) {
      ref.initialised = false;
    }
    for (EvalState.NumArrayRef ref : numArrays.values()) {
      ref.array = null;
    }
    for (EvalState.StrVarRef ref : strVars.values()) {
      ref.value = null;
    }
    for (EvalState.FnDefRef ref : fnDefinitions.values()) {
      ref.def = null;
    }
  }
}
