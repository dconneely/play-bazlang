package com.davidconneely.bazlang.exec;

/**
 * Execution position: the current line/statement, and whether the interpreter should keep running.
 * Deliberately not reset by {@code CLEAR} - matching {@code EvalState}'s original behaviour, {@code
 * NEW}/{@code CLEAR} leave the current execution position alone. Where to jump or resume next is
 * not state here - {@link Interpreter#resume(int, int)} takes it as a parameter and threads it
 * through its own loop as a local, rather than any collaborator holding it.
 */
final class ProgramCounter {
  private boolean running = true;
  private int currentLineLabel = 0;
  private int currentStatementIndex = 1;

  boolean isRunning() {
    return running;
  }

  void setRunning(boolean running) {
    this.running = running;
  }

  int currentLineLabel() {
    return currentLineLabel;
  }

  void setCurrentLineLabel(int label) {
    this.currentLineLabel = label;
    this.currentStatementIndex = 1; // reset on new line
  }

  int currentStatementIndex() {
    return currentStatementIndex;
  }

  void setCurrentStatementIndex(int index) {
    this.currentStatementIndex = index;
  }
}
