package com.davidconneely.bazlang;

import java.util.Map;

public class Interpreter {
  private final EvalState state;
  private final Executor executor;

  public Interpreter() {
    this.state = new EvalState();
    final var terminal = new Display();
    final var evaluator = new Evaluator(state, terminal);
    this.executor = new Executor(state, evaluator, terminal);
  }

  public Interpreter(EvalState state, Executor executor) {
    this.state = state;
    this.executor = executor;
  }

  public void execute(Map<Integer, Statement> program) {
    state.setProgram(program);
    state.setCurrentLineLabel(-1);
    resume();
  }

  public void resume() {
    state.setRunning(true);
    long lastCheck = 0;
    while (state.isRunning()) {
      var entry = state.program().higherEntry(state.currentLineLabel());
      if (entry == null) {
        break;
      }
      long now = System.currentTimeMillis();
      if (now - lastCheck > 100) {
        lastCheck = now;
        if (executor.terminal().checkInterrupt()) {
          state.setRunning(false);
          throw new ReportException(
              ReportCode.BREAK_CONT_REPEATS,
              entry.getKey(),
              ReportCode.BREAK_CONT_REPEATS.getMessage());
        }
      }
      state.setCurrentLineLabel(entry.getKey());
      try {
        executor.executeStatement(entry.getValue());
      } catch (ReportException e) {
        throw e;
      } catch (Exception e) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, e.getMessage());
      }
    }
  }

  private ReportException codedException(ReportCode reportCode, String message) {
    return new ReportException(reportCode, state.currentLineLabel(), message);
  }
}
