package com.davidconneely.bazlang.program;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.List;
import java.util.Map;

class BaseProgramTest {
  protected static final AntlrParser PARSER = AntlrParser.INSTANCE;

  protected EvalState runProgram(String source) {
    return runProgram(source, List.of());
  }

  protected EvalState runProgram(String source, List<String> inputs) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();

    MockDisplay display = new MockDisplay(inputs);

    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
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
    org.junit.jupiter.api.Assertions.assertEquals(expectedOutput, runProgramCapture(source));
  }

  protected String runProgramCapture(String source) {
    return runProgramCapture(source, List.of());
  }

  protected String runProgramCapture(String source, List<String> inputs) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();

    MockDisplay display = new MockDisplay(inputs);

    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
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
