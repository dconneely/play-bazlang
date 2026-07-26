package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.StatementExecutor;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests exercising interpreter STOP statement execution and runtime behaviour. */
class StopProgramTest extends BaseProgramTest {

  @Test
  void testStopInInputAndCont() {
    final var program = new HashMap<Integer, ProgramLine>();
    program.put(10, new ProgramLine(10, "INPUT A"));
    program.put(20, new ProgramLine(20, "PRINT A"));

    final var state = new EvalState();
    // Provide "STOP" first, then "42" when we continue
    final var screen = new MockScreen(List.of("STOP", "42"));
    final var executor = new StatementExecutor(state, screen, screen);
    final var interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_IN_INPUT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // CONTINUE -> should repeat the INPUT statement
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("STOP\n42\n42\n", screen.getOutput());
  }

  @Test
  void testStopStatementBehaviour() {
    // STOP should terminate execution cleanly.
    final String output =
        runProgramCapture(
            """
        10 PRINT "START"
        20 STOP
        30 PRINT "END"
        """);
    assertTrue(output.contains("START"));
    assertFalse(output.contains("END"));
  }
}
