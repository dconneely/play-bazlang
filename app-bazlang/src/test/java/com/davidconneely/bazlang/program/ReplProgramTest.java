package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.InterpreterReplHandler;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests exercising interactive REPL mode, CONT command, and breakpoints. */
class ReplProgramTest extends BaseProgramTest {

  @Test
  void testBreakIntoProgramAndCont() {
    final var program = new HashMap<Integer, ProgramLine>();
    program.put(10, new ProgramLine(10, "PRINT \"A\""));
    program.put(20, new ProgramLine(20, "PRINT \"B\""));
    program.put(30, new ProgramLine(30, "PRINT \"C\""));

    final var state = new EvalState();
    final var screen =
        new MockScreen(List.of()) {
          @Override
          public void print(String text) {
            super.print(text);
            if (text.equals("A")) {
              triggerBreak();
            }
          }
        };
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.BREAK_INTO_PROGRAM, e.reportCode());
      state.setLastReport(
          new EvalState.ReportState(e.reportCode(), e.lineLabel(), e.statementIndex()));
    }

    // CONTINUE -> should resume at line 20 (does not repeat line 10)
    executor.execute(new Stmt.ContStmt());
    interpreter.resume();

    assertEquals("A\nB\nC\n", screen.getOutput());
  }

  @Test
  void testContInMultiStatementLine() {
    // Should print "BEFORE", then if the user enters CONT, should print "AFTER".
    final var program = PARSER.parseProgramLines("10 PRINT \"BEFORE\" : STOP : PRINT \"AFTER\"");
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_STATEMENT, e.reportCode());
      state.setLastReport(
          new EvalState.ReportState(e.reportCode(), e.lineLabel(), e.statementIndex()));
    }

    // Simulate REPL running CONT
    interpreter.executeImmediate("CONT");

    assertEquals("BEFORE\nAFTER\n", screen.getOutput());
  }

  @Test
  void testImmediateModeList() {
    // Tests that LIST executed from REPL doesn't echo itself as line 0
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    handler.handleReplInput("10 PRINT \"HELLO\"");
    handler.handleReplInput("LIST");

    // The output should just be the line 10 being echoed, then the list showing just line 10.
    assertEquals("❯ 10 PRINT \"HELLO\"\n❯ LIST\n10 PRINT \"HELLO\"\n", screen.getOutput());
  }

  @Test
  void testImmediateModeRun() {
    // Tests that RUN executed from REPL properly runs a stored program without infinite loop
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    handler.handleReplInput("10 PRINT \"HELLO\"");
    handler.handleReplInput("RUN");

    assertEquals("❯ 10 PRINT \"HELLO\"\n❯ RUN\nHELLO\n", screen.getOutput());
    assertFalse(state.isRunning()); // Should stop gracefully
  }

  @Test
  void testImmediateModeStopExitsRepl() {
    // Tests that STOP executed from REPL as an immediate statement returns false to exit the REPL
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    final boolean continueRepl = handler.handleReplInput("STOP");

    assertFalse(continueRepl, "Immediate STOP should return false to exit the REPL");
    assertEquals("9 STOP statement, 0:1", screen.getStatus());
  }

  @Test
  void testStoredStopContinuesRepl() {
    // Tests that STOP executed inside a program returns true to continue the REPL
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    handler.handleReplInput("10 PRINT \"A\"");
    handler.handleReplInput("20 STOP");
    final boolean continueRepl = handler.handleReplInput("RUN");

    assertTrue(continueRepl, "Stored STOP should return true to continue the REPL");
    assertEquals("9 STOP statement, 20:1", screen.getStatus());
  }

  @Test
  void testImmediateModeMultiStatement() {
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    handler.handleReplInput("PRINT \"hello\" : PRINT \"there\"");

    assertEquals("❯ PRINT \"hello\" : PRINT \"there\"\nhello\nthere\n", screen.getOutput());
  }
}
