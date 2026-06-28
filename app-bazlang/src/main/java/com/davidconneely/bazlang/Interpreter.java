package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import java.util.List;
import java.util.Map;

public class Interpreter {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;
  private final EvalState state;
  private final ProgramManager executor;

  public Interpreter(EvalState state, ProgramManager executor) {
    this.state = state;
    this.executor = executor;
  }

  public void execute(Map<Integer, ProgramLine> program) {
    state.setProgram(program);
    if (!state.program().isEmpty()) {
      state.setPendingJumpLocation(state.program().firstKey(), 1);
    }
    resume();
  }

  public void executeImmediate(String rawLine) {
    ProgramLine immediateLine = new ProgramLine(0, rawLine);
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

      if (executor.display().pollForBreak()) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.BREAK_INTO_PROGRAM,
            state.currentLineLabel(),
            state.currentStatementIndex(),
            ReportCode.BREAK_INTO_PROGRAM.getMessage());
      }

      ProgramLine line = state.program().get(nextLabel);
      if (line == null) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.STATEMENT_LOST, state.currentLineLabel(), "Statement lost");
      }
      List<StatementContext> stmts = line.getFlattenedStatements(PARSER);
      if (startIndex < 1 || startIndex > stmts.size() + 1) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.STATEMENT_LOST, state.currentLineLabel(), "Statement lost");
      }

      state.setCurrentLineLabel(nextLabel);
      int index = 1;
      for (StatementContext stmt : stmts) {
        if (index >= startIndex) {
          state.setCurrentStatementIndex(index);
          executor.visit(stmt);
          if (state.hasPendingJump() || !state.isRunning()) {
            break;
          }
        }
        index++;
      }
    }
  }
}
