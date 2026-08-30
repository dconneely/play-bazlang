package com.davidconneely.bazlang.exec;

/**
 * What executing one statement does to the program's control flow, returned by {@link
 * StatementExecutor#execute(com.davidconneely.bazlang.exec.ast.Stmt)} so {@link
 * Interpreter#resume()} can act on it directly rather than re-inspecting {@link EvalState} for a
 * pending jump/running flag a statement might have forgotten to set. {@code STOP} and a genuine
 * runtime error stay outside this type - both unwind via the exception channel (a real error and a
 * deliberate program stop should look different from an ordinary jump), and {@code BREAK} is
 * detected between statements in {@code resume()} itself, not by a statement's own execution.
 */
sealed interface ControlFlow {
  /** No control-flow effect - proceed to the next statement in sequence. */
  ControlFlow CONTINUE = new Continue();

  /** Ran off the end of the program (no line at or after the target `GOTO`/`IF`/`RUN` wanted). */
  ControlFlow END_OF_PROGRAM = new EndOfProgram();

  record Continue() implements ControlFlow {}

  record Jump(int label, int statementIndex) implements ControlFlow {}

  record EndOfProgram() implements ControlFlow {}
}
