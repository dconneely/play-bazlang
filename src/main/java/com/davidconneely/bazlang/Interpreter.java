package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import com.davidconneely.bazlang.io.BazLangDisplay;
import com.davidconneely.bazlang.io.StreamDisplay;
import com.davidconneely.bazlang.io.TerminalDisplay;
import java.io.IOException;
import java.util.Map;

public class Interpreter {
  private static final AntlrParser PARSER = new AntlrParser();
  private final EvalState state;
  private final BazLangExecutor executor;

  public Interpreter() {
    this.state = new EvalState();
    BazLangDisplay display;
    try {
      display = new TerminalDisplay();
    } catch (IOException e) {
      display = new StreamDisplay();
    }
    this.executor = new BazLangExecutor(state, display);
  }

  public Interpreter(EvalState state, BazLangExecutor executor) {
    this.state = state;
    this.executor = executor;
  }

  public void execute(Map<Integer, ProgramLine> program) {
    state.setProgram(program);
    if (!state.program().isEmpty()) {
      state.setPendingJumpLabel(state.program().firstKey());
    }
    resume();
  }

  public void resume() {
    state.setRunning(true);
    while (state.isRunning()) {
      Integer nextLabel;
      if (state.hasPendingJump()) {
        nextLabel = state.pendingJumpLabel();
        state.clearPendingJump();
      } else {
        nextLabel = state.program().higherKey(state.currentLineLabel());
      }

      if (nextLabel == null) {
        break;
      }

      if (executor.display().pollForBreak()) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.BREAK_CONT_REPEATS, nextLabel, ReportCode.BREAK_CONT_REPEATS.getMessage());
      }
      state.setCurrentLineLabel(nextLabel);
      // Lazy parse: ParseTree is built on first execution, cached for loops
      ProgramLine line = state.program().get(nextLabel);
      StatementContext stmt = line.getStatement(PARSER);
      executor.visit(stmt);
    }
  }
}
