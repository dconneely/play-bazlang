package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import com.davidconneely.bazlang.io.Display;
import com.davidconneely.bazlang.io.StreamDisplay;
import com.davidconneely.bazlang.io.TerminalDisplay;
import java.util.Map;

public class Interpreter {
  private static final AntlrParser parser = new AntlrParser();
  private final EvalState state;
  private final BazLangExecutor executor;

  public Interpreter() {
    this.state = new EvalState();
    Display terminal;
    try {
      terminal = new TerminalDisplay();
    } catch (Exception e) {
      terminal = new StreamDisplay();
    }
    this.executor = new BazLangExecutor(state, terminal);
  }

  public Interpreter(EvalState state, BazLangExecutor executor) {
    this.state = state;
    this.executor = executor;
  }

  public void execute(Map<Integer, ProgramLine> program) {
    state.setProgram(program);
    state.setCurrentLineLabel(-1);
    resume();
  }

  public void resume() {
    state.setRunning(true);
    while (state.isRunning()) {
      var entry = state.program().higherEntry(state.currentLineLabel());
      if (entry == null) {
        break;
      }
      if (executor.terminal().pollForBreak()) {
        state.setRunning(false);
        throw new ReportException(
            ReportCode.BREAK_CONT_REPEATS,
            entry.getKey(),
            ReportCode.BREAK_CONT_REPEATS.getMessage());
      }
      state.setCurrentLineLabel(entry.getKey());
      try {
        // Lazy parse: ParseTree is built on first execution, cached for loops
        ProgramLine line = entry.getValue();
        StatementContext stmt = line.getStatement(parser);
        executor.visit(stmt);
      } catch (Exception e) {
        if (e instanceof ReportException re) {
          throw re;
        }
        throw codedException(ReportCode.NONSENSE_IN_BASIC, e.getMessage());
      }
    }
  }

  private ReportException codedException(ReportCode reportCode, String message) {
    return new ReportException(reportCode, state.currentLineLabel(), message);
  }
}
