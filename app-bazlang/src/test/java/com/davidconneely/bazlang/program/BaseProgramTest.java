package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.List;

class BaseProgramTest {
  protected static final AntlrParser PARSER = AntlrParser.INSTANCE;

  protected EvalState runProgram(String source) {
    return runProgram(source, List.of());
  }

  protected EvalState runProgram(String source, List<String> inputs) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();
    final var display = new MockDisplay(inputs);
    final var executor = new ProgramManager(state, display);
    final var interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return state;
  }

  protected void runProgram(String source, String expectedOutput) {
    assertEquals(expectedOutput, runProgramCapture(source));
  }

  protected String runProgramCapture(String source) {
    return runProgramCapture(source, List.of());
  }

  protected String runProgramCapture(String source, List<String> inputs) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();
    final var display = new MockDisplay(inputs);
    final var executor = new ProgramManager(state, display);
    final var interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return display.getOutput();
  }
}
