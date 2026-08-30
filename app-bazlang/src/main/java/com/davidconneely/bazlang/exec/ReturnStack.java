package com.davidconneely.bazlang.exec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The {@code GOSUB}/{@code RETURN} call stack, innermost (most recently called) frame on top. */
final class ReturnStack {
  private final Deque<EvalState.StatementAddress> stack = new ArrayDeque<>();

  boolean isEmpty() {
    return stack.isEmpty();
  }

  /** The current {@code GOSUB} nesting depth, for {@code step over} to detect a call returning. */
  int depth() {
    return stack.size();
  }

  void push(EvalState.StatementAddress loc) {
    stack.push(loc);
  }

  EvalState.StatementAddress pop() {
    return stack.pop();
  }

  /** A read-only snapshot, innermost frame first, for debugger inspection. */
  List<EvalState.StatementAddress> snapshot() {
    return List.copyOf(stack);
  }

  void clear() {
    stack.clear();
  }
}
