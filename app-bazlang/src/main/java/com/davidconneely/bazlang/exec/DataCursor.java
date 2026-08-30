package com.davidconneely.bazlang.exec;

/** The {@code READ}/{@code RESTORE} position within the program's flattened {@code DATA} values. */
final class DataCursor {
  private static final EvalState.DataPointer INITIAL = new EvalState.DataPointer(-1, -1, -1);

  private EvalState.DataPointer pointer = INITIAL;

  EvalState.DataPointer get() {
    return pointer;
  }

  void set(EvalState.DataPointer pointer) {
    this.pointer = pointer;
  }

  void clear() {
    pointer = INITIAL;
  }
}
