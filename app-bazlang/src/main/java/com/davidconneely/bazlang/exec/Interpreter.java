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
    if (state.program().isEmpty()) {
      return;
    }
    resume(state.program().firstKey(), 1);
  }

  public void executeImmediate(String rawLine) {
    final var immediateLine = new ProgramLine(0, rawLine);
    state.program().put(0, immediateLine);
    try {
      resume(0, 1);
    } finally {
      state.program().remove(0);
    }
  }

  /**
   * Runs from {@code label}/{@code statementIndex} until the program stops, pauses (via {@link
   * #executionListener}), or throws. Line 0 - the synthetic line {@link #executeImmediate} uses -
   * has no natural successor line: finishing it without an explicit jump means "back to the REPL",
   * not falling through into whatever real program line happens to sort after it, which is why that
   * case is handled separately from the general "advance to the next line" fallthrough below.
   */
  public void resume(int label, int statementIndex) {
    state.setRunning(true);
    Integer nextLabel = label;
    int startIndex = statementIndex;
    while (state.isRunning()) {
      if (nextLabel != null && nextLabel < 0) {
        break; // Line numbers must be >= 0
      }
      if (nextLabel == null) {
        state.setRunning(false);
        break;
      }

      if (executor.input().pollForBreak()) {
        state.setRunning(false);
        // Silences any background APLAY music (and, for consistency, a BEEP) even though nothing
        // is "inside" a wait loop here to catch it - this is the one place every BREAK passes
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
      Integer jumpLabel = null;
      int jumpIndex = 1;
      for (var stmt : stmts) {
        if (index >= startIndex) {
          state.setCurrentStatementIndex(index);
          if (executionListener != null) {
            executionListener.beforeStatement(nextLabel, index);
            if (!state.isRunning()) {
              break;
            }
          }
          final ControlFlow flow = executor.execute(stmt);
          // An exhaustive switch (no default arm) so a future ControlFlow variant is a compile
          // error here, not a statement whose jump/stop silently gets ignored.
          switch (flow) {
            case ControlFlow.Continue _ -> {
              /* advance to the next statement in this line */
            }
            case ControlFlow.Jump j -> {
              jumpLabel = j.label();
              jumpIndex = j.statementIndex();
            }
            case ControlFlow.EndOfProgram _ -> state.setRunning(false);
          }
          if (jumpLabel != null || !state.isRunning()) {
            break;
          }
        }
        index++;
      }

      if (!state.isRunning()) {
        break;
      }
      if (jumpLabel != null) {
        nextLabel = jumpLabel;
        startIndex = jumpIndex;
      } else if (nextLabel == 0) {
        state.setRunning(false); // immediate mode: no jump, no next line - back to the REPL
        break;
      } else {
        nextLabel = state.program().higherKey(nextLabel);
        startIndex = 1;
      }
    }
  }
}
