package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.List;

class BaseProgramTest {
  protected static final AntlrParser PARSER = AntlrParser.INSTANCE;

  protected record RunResult(EvalState state, MockScreen screen) {
    String output() {
      return screen.getOutput();
    }
  }

  protected RunResult run(String source, List<String> inputs, boolean ignoreExceptions) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();
    final var screen = new MockScreen(inputs);
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (!ignoreExceptions && e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return new RunResult(state, screen);
  }

  protected RunResult runWithKeys(String source, List<BStr> inkey, List<BStr> uinkey) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    for (var k : inkey) {
      screen.queueInkey(k);
    }
    for (var k : uinkey) {
      screen.queueUinkey(k);
    }
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return new RunResult(state, screen);
  }

  protected EvalState runProgram(String source) {
    return run(source, List.of(), false).state();
  }

  protected EvalState runProgram(String source, List<String> inputs) {
    return run(source, inputs, false).state();
  }

  protected void runProgram(String source, String expectedOutput) {
    assertEquals(expectedOutput, runProgramCapture(source));
  }

  protected String runProgramCapture(String source) {
    return run(source, List.of(), false).output();
  }

  protected String runProgramCapture(String source, List<String> inputs) {
    return run(source, inputs, false).output();
  }

  protected String runProgramCaptureIgnoringExceptions(String source) {
    return run(source, List.of(), true).output();
  }

  protected String runProgramCaptureIgnoringExceptions(String source, List<String> inputs) {
    return run(source, inputs, true).output();
  }

  protected MockScreen runWithScreen(String source) {
    return run(source, List.of(), false).screen();
  }

  protected MockScreen runWithScreen(String source, List<String> inputs) {
    return run(source, inputs, false).screen();
  }
}
