package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Tests exercising interactive REPL mode, CONT command, and breakpoints. */
class ReplProgramTest extends BaseProgramTest {

  @Test
  void testBreakIntoProgramAndCont() {
    Map<Integer, ProgramLine> program = new java.util.HashMap<>();
    program.put(10, new ProgramLine(10, "PRINT \"A\""));
    program.put(20, new ProgramLine(20, "PRINT \"B\""));
    program.put(30, new ProgramLine(30, "PRINT \"C\""));

    EvalState state = new EvalState();
    MockDisplay display =
        new MockDisplay(List.of()) {
          @Override
          public void print(String text) {
            super.print(text);
            if (text.equals("A")) {
              triggerBreak();
            }
          }
        };
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.BREAK_INTO_PROGRAM, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // CONTINUE -> should resume at line 20 (does not repeat line 10)
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("A\nB\nC\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testContInMultiStatementLine() {
    // Should print "BEFORE", then if the user enters CONT, should print "AFTER".
    Map<Integer, ProgramLine> program =
        PARSER.parseProgramLines("10 PRINT \"BEFORE\" : STOP : PRINT \"AFTER\"");
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_STATEMENT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // Simulate REPL running CONT
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("BEFORE\nAFTER\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testImmediateModeList() {
    // Tests that LIST executed from REPL doesn't echo itself as line 0
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"HELLO\"", display);
    repl.handleReplInput("LIST", display);

    // The output should just be the line 10 being echoed, then the list showing just line 10.
    assertEquals(
        "❯ 10 PRINT \"HELLO\"\n❯ LIST\n10 PRINT \"HELLO\"\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testImmediateModeRun() {
    // Tests that RUN executed from REPL properly runs a stored program without infinite loop
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"HELLO\"", display);
    repl.handleReplInput("RUN", display);

    assertEquals(
        "❯ 10 PRINT \"HELLO\"\n❯ RUN\nHELLO\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
    assertFalse(state.isRunning()); // Should stop gracefully
  }

  @Test
  void testImmediateModeStopExitsRepl() {
    // Tests that STOP executed from REPL as an immediate statement returns false to exit the REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    boolean continueRepl = repl.handleReplInput("STOP", display);

    assertFalse(continueRepl, "Immediate STOP should return false to exit the REPL");
    assertEquals("9 STOP statement, 0:1", display.getStatus());
  }

  @Test
  void testStoredStopContinuesRepl() {
    // Tests that STOP executed inside a program returns true to continue the REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"A\"", display);
    repl.handleReplInput("20 STOP", display);
    boolean continueRepl = repl.handleReplInput("RUN", display);

    assertTrue(continueRepl, "Stored STOP should return true to continue the REPL");
    assertEquals("9 STOP statement, 20:1", display.getStatus());
  }

  @Test
  void testImmediateModeMultiStatement() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("PRINT \"hello\" : PRINT \"there\"", display);

    assertEquals(
        "❯ PRINT \"hello\" : PRINT \"there\"\nhello\nthere\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
  }
}
