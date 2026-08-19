package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.util.Map;

public class Interpreter {
  @FunctionalInterface
  public interface ExecutionListener {
    void beforeStatement(int line, int stmt);
  }

  private final EvalState state;
  private final StatementExecutor executor;
  private final AntlrParser parser;
  private ExecutionListener executionListener;

  public Interpreter(EvalState state, StatementExecutor executor) {
    this.state = state;
    this.executor = executor;
    this.parser = AntlrParser.INSTANCE;
  }

  public void setExecutionListener(ExecutionListener executionListener) {
    this.executionListener = executionListener;
  }

  public void execute(Map<Integer, ProgramLine> program) {
    state.setProgram(program);
    if (!state.program().isEmpty()) {
      state.setPendingJumpLocation(state.program().firstKey(), 1);
    }
    resume();
  }

  public void executeImmediate(String rawLine) {
    final var immediateLine = new ProgramLine(0, rawLine);
    state.program().put(0, immediateLine);
    try {
      state.setPendingJumpLocation(0, 1);
      resume();
    } finally {
      state.program().remove(0);
    }
  }

  public void resume() {
    state.setRunning(true);
    while (state.isRunning()) {
      Integer nextLabel;
      int startIndex = 1;
      if (state.hasPendingJump()) {
        nextLabel = state.pendingJumpLabel();
        if (nextLabel < 0) {
          break; // Line numbers must be >= 0
        }
        startIndex = state.pendingJumpStatementIndex();
        state.clearPendingJump();
      } else {
        if (state.currentLineLabel() == 0) {
          state.setRunning(false);
          break;
        }
        nextLabel = state.program().higherKey(state.currentLineLabel());
      }

      if (nextLabel == null) {
        state.setRunning(false);
        break;
      }

      if (executor.input().pollForBreak()) {
        state.setRunning(false);
        // Silences any background APLAY music (and, for consistency, a BEEP) even though nothing
        // is "inside" a wait loop here to catch it — this is the one place every BREAK passes
        // through regardless of what statement is currently executing, so Ctrl+C reliably stops
        // background audio no matter what's running when it's pressed. A deliberate divergence
        // from real hardware: see the PLAY/APLAY entry in localonly-BAZLANG-ROADMAP.md.
        executor.stopBackgroundAudio();
        throw new ReportException(
            ReportCode.BREAK_INTO_PROGRAM,
            state.currentLineLabel(),
            state.currentStatementIndex(),
            ReportCode.BREAK_INTO_PROGRAM.getMessage());
      }

      final var line = state.program().get(nextLabel);
      if (line == null) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.STATEMENT_LOST, state.currentLineLabel(), "Statement lost");
      }
      final var stmts = line.getFlattenedStatements(parser);
      if (startIndex < 1 || startIndex > stmts.size() + 1) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.STATEMENT_LOST, state.currentLineLabel(), "Statement lost");
      }

      state.setCurrentLineLabel(nextLabel);
      int index = 1;
      for (var stmt : stmts) {
        if (index >= startIndex) {
          state.setCurrentStatementIndex(index);
          if (executionListener != null) {
            executionListener.beforeStatement(nextLabel, index);
            if (!state.isRunning()) {
              break;
            }
          }
          executor.execute(stmt);
          if (state.hasPendingJump() || !state.isRunning()) {
            break;
          }
        }
        index++;
      }
    }
  }
}
