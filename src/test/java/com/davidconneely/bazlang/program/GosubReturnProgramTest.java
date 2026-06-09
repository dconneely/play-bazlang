package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.BazLangReplHandler;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramEditor;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GosubReturnProgramTest extends BaseProgramTest {

  @Test
  void testGosub() {
    runProgram(
        "10 GOSUB 100\n20 PRINT \"WORLD\"\n30 STOP\n100 PRINT \"HELLO \"\n110 RETURN",
        "HELLO " + System.lineSeparator() + "WORLD" + System.lineSeparator());
  }

  @Test
  void testGosubInMultiStatementLine() {
    // Should print "BEFORE", "SUBROUTINE", "AFTER".
    String output =
        runProgramCapture(
            """
        10 PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN : PRINT "NOT"
        """);
    assertEquals("BEFORE\nSUBROUTINE\nAFTER\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testGosubInsideIfThen() {
    String output =
        runProgramCapture(
            """
        10 IF 1=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN
        """);
    assertEquals("BEFORE\nSUBROUTINE\nAFTER\n", output.replace(System.lineSeparator(), "\n"));

    String skippedOutput =
        runProgramCapture(
            """
        10 IF 0=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        20 STOP
        100 PRINT "NOT_RUN" : RETURN
        """);
    assertEquals("", skippedOutput);
  }

  @Test
  void testImmediateModeGosub() {
    // Tests that an immediate GOSUB to a subroutine containing a RETURN gracefully returns to the
    // REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"SUB\" : RETURN", display);
    repl.handleReplInput("GOSUB 10", display);

    // The output should be the subroutine's line being echoed, then the subroutine executing,
    // and then returning to the REPL cleanly without throwing "RETURN without GOSUB".
    assertEquals(
        "❯ 10 PRINT \"SUB\" : RETURN\n❯ GOSUB 10\nSUB\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
    assertFalse(state.isRunning());
  }

  @Test
  void testReturnWithoutGosub() {
    ReportException e = assertThrows(ReportException.class, () -> runProgram("10 RETURN"));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, e.reportCode());
  }

  @Test
  void testStatementLostReturn() {
    Map<Integer, ProgramLine> program = new java.util.HashMap<>();
    program.put(10, new ProgramLine(10, "GO SUB 30"));
    program.put(20, new ProgramLine(20, "STOP"));
    program.put(30, new ProgramLine(30, "STOP"));
    program.put(40, new ProgramLine(40, "RETURN"));

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    // Run GOSUB 30 -> stops at line 30 STOP
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_STATEMENT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // Delete line 10 (the GOSUB caller) while stopped at line 30
    state.program().remove(10);

    // CONTINUE -> resumes from line 30, falls through to line 40, executes RETURN,
    // which pops line 10 and tries to jump there. Since line 10 is deleted,
    // it should throw STATEMENT_LOST!
    try {
      executor.visitContStmt(null);
      interpreter.resume();
      org.junit.jupiter.api.Assertions.fail("Expected STATEMENT_LOST");
    } catch (ReportException e) {
      assertEquals(ReportCode.STATEMENT_LOST, e.reportCode());
    }
  }
}
