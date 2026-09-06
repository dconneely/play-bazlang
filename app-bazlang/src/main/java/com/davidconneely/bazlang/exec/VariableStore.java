package com.davidconneely.bazlang.exec;

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
 * <p>Numeric/string scalar and array {@code Ref}s are stored by id, not name - the owning {@link
 * Program} is the actual authority for "what id does variable name X have" (see its {@code
 * VarIdAllocator} implementation), since a {@code Program} - unlike this store - can be reused
 * across more than one {@code EvalState}. This store just holds one session's *values*, indexed by
 * whatever id {@code Program} hands back for a name, growing its id-indexed lists on demand (asking
 * {@code Program} for a newly-visible slot's name, since a slot minted by a *different* session may
 * be new to this one). {@code DEF FN} definitions are looked up by name on every call already
 * (never id-cached), so they stay name-keyed and per-session, with no `Program` involvement.
 */
final class VariableStore {
  private final Program program;

  private final List<EvalState.NumVarRef> numScalarsById = new ArrayList<>();
  private final List<EvalState.NumArrayRef> numArraysById = new ArrayList<>();
  private final List<EvalState.StrVarRef> strVarsById = new ArrayList<>();

  private final Map<String, EvalState.FnDefRef> fnDefinitions = new HashMap<>();

  VariableStore(Program program) {
    this.program = program;
  }

  EvalState.NumVarRef getOrAddNumVar(String name) {
    return numVarRefById(program.numVarId(name));
  }

  EvalState.NumArrayRef getOrAddNumArray(String name) {
    return numArrayRefById(program.numArrayId(name));
  }

  EvalState.StrVarRef getOrAddStrVar(String name) {
    return strVarRefById(program.strVarId(name));
  }

  EvalState.FnDefRef getOrAddFnDef(String name) {
    return fnDefinitions.computeIfAbsent(name, EvalState.FnDefRef::new);
  }

  // ===== Id-based lookups (the AST fast path) =====

  EvalState.NumVarRef numVarRefById(int id) {
    while (numScalarsById.size() <= id) {
      numScalarsById.add(new EvalState.NumVarRef(program.numVarName(numScalarsById.size())));
    }
    return numScalarsById.get(id);
  }

  EvalState.NumArrayRef numArrayRefById(int id) {
    while (numArraysById.size() <= id) {
      numArraysById.add(new EvalState.NumArrayRef(program.numArrayName(numArraysById.size())));
    }
    return numArraysById.get(id);
  }

  EvalState.StrVarRef strVarRefById(int id) {
    while (strVarsById.size() <= id) {
      strVarsById.add(new EvalState.StrVarRef(program.strVarName(strVarsById.size())));
    }
    return strVarsById.get(id);
  }

  // ===== Numeric scalar variables =====

  boolean hasNumVar(String name) {
    EvalState.NumVarRef ref = knownNumVarRef(name);
    return ref != null && ref.initialised;
  }

  double numVar(String name) {
    EvalState.NumVarRef ref = knownNumVarRef(name);
    if (ref != null && ref.initialised) {
      return ref.value;
    }
    throw new IllegalArgumentException("Undefined variable: " + name);
  }

  EvalState.NumVarRef getNumVarRef(String name) {
    return knownNumVarRef(name);
  }

  void setNumVar(String name, double val) {
    EvalState.NumVarRef ref = getOrAddNumVar(name);
    ref.value = val;
    ref.initialised = true;
  }

  void removeNumVar(String name) {
    EvalState.NumVarRef ref = knownNumVarRef(name);
    if (ref != null) {
      ref.initialised = false;
    }
  }

  /**
   * Looks up a scalar numeric variable by name without minting a new id, and without growing this
   * session's storage past what it already holds - a name known to {@code Program} but never
   * touched by *this* session (e.g. assigned by a different session reusing the same {@code
   * Program}) correctly reports "not present" rather than an uninitialised slot it just created.
   */
  private EvalState.NumVarRef knownNumVarRef(String name) {
    Integer id = program.numVarIdIfPresent(name);
    return (id != null && id < numScalarsById.size()) ? numScalarsById.get(id) : null;
  }

  // ===== Numeric arrays =====

  boolean hasNumArray(String name) {
    EvalState.NumArrayRef ref = knownNumArrayRef(name);
    return ref != null && ref.array != null;
  }

  EvalState.NumArray numArray(String name) {
    EvalState.NumArrayRef ref = knownNumArrayRef(name);
    return (ref != null) ? ref.array : null;
  }

  void setNumArray(String name, EvalState.NumArray arr) {
    EvalState.NumArrayRef ref = getOrAddNumArray(name);
    ref.array = arr;
  }

  private EvalState.NumArrayRef knownNumArrayRef(String name) {
    Integer id = program.numArrayIdIfPresent(name);
    return (id != null && id < numArraysById.size()) ? numArraysById.get(id) : null;
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
    EvalState.StrVarRef ref = knownStrVarRef(name);
    return ref != null && ref.value != null;
  }

  EvalState.StrVar strVar(String name) {
    EvalState.StrVarRef ref = knownStrVarRef(name);
    return (ref != null) ? ref.value : null;
  }

  void setStrVar(String name, EvalState.StrVar val) {
    EvalState.StrVarRef ref = getOrAddStrVar(name);
    ref.value = val;
  }

  void removeStrVar(String name) {
    EvalState.StrVarRef ref = knownStrVarRef(name);
    if (ref != null) {
      ref.value = null;
    }
  }

  private EvalState.StrVarRef knownStrVarRef(String name) {
    Integer id = program.strVarIdIfPresent(name);
    return (id != null && id < strVarsById.size()) ? strVarsById.get(id) : null;
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
   * ref objects or their ids - other AST nodes already hold the id baked in as an immutable field.
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
