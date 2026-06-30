package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.BazLangReplHandler;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramEditor;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class GosubReturnProgramTest extends BaseProgramTest {

  @Test
  void testGosub() {
    runProgram(
        """
        10 GOSUB 100
        20 PRINT "WORLD"
        30 STOP
        100 PRINT "HELLO "
        110 RETURN
        """,
        "HELLO \nWORLD\n");
  }

  @Test
  void testGosubInMultiStatementLine() {
    // Should print "BEFORE", "SUBROUTINE", "AFTER".
    runProgram(
        """
        10 PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN : PRINT "NOT"
        """,
        "BEFORE\nSUBROUTINE\nAFTER\n");
  }

  @Test
  void testGosubInsideIfThen() {
    runProgram(
        """
        10 IF 1=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN
        """,
        "BEFORE\nSUBROUTINE\nAFTER\n");

    runProgram(
        """
        10 IF 0=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        20 STOP
        100 PRINT "NOT_RUN" : RETURN
        """,
        "");
  }

  @Test
  void testImmediateModeGosub() {
    // Tests that an immediate GOSUB to a subroutine containing a RETURN gracefully returns to the
    // REPL
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new ProgramManager(state, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var repl = new BazLangReplHandler(screen, PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"SUB\" : RETURN");
    repl.handleReplInput("GOSUB 10");

    // The output should be the subroutine's line being echoed, then the subroutine executing,
    // and then returning to the REPL cleanly without throwing "RETURN without GOSUB".
    assertEquals("❯ 10 PRINT \"SUB\" : RETURN\n❯ GOSUB 10\nSUB\n", screen.getOutput());
    assertFalse(state.isRunning());
  }

  @Test
  void testReturnWithoutGosub() {
    final var ex = assertThrows(ReportException.class, () -> runProgram("10 RETURN"));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, ex.reportCode());
  }

  @Test
  void testStatementLostReturn() {
    final var program = new HashMap<Integer, ProgramLine>();
    program.put(10, new ProgramLine(10, "GO SUB 30"));
    program.put(20, new ProgramLine(20, "STOP"));
    program.put(30, new ProgramLine(30, "STOP"));
    program.put(40, new ProgramLine(40, "RETURN"));

    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new ProgramManager(state, screen);
    final var interpreter = new Interpreter(state, executor);

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
      fail("Expected STATEMENT_LOST");
    } catch (ReportException e) {
      assertEquals(ReportCode.STATEMENT_LOST, e.reportCode());
    }
  }
}
