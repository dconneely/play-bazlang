package com.davidconneely.bazlang.exec;

/**
 * Execution position: the current line/statement, whether the interpreter should keep running, and
 * any pending {@code GO TO}/{@code GO SUB}/{@code RETURN}/{@code NEXT} jump for {@link
 * Interpreter#resume()} to act on once the current statement finishes executing. Deliberately not
 * reset by {@code CLEAR} beyond the pending jump - matching {@code EvalState}'s original behaviour,
 * {@code NEW}/{@code CLEAR} leave the current execution position alone.
 */
final class ProgramCounter {
  private boolean running = true;
  private int currentLineLabel = 0;
  private int currentStatementIndex = 1;
  private EvalState.StatementAddress pendingJump = null;

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

  Integer pendingJumpLabel() {
    return pendingJump != null ? pendingJump.lineLabel() : null;
  }

  Integer pendingJumpStatementIndex() {
    return pendingJump != null ? pendingJump.statementIndex() : null;
  }

  boolean hasPendingJump() {
    return pendingJump != null;
  }

  void setPendingJumpLocation(int label, int statementIndex) {
    this.pendingJump = new EvalState.StatementAddress(label, statementIndex);
  }

  void clearPendingJump() {
    this.pendingJump = null;
  }
}
