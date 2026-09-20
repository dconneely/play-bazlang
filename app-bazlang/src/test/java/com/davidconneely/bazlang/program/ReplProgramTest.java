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
    interpreter.executeImmediate("CONT");

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
  void testAcceptedLineEchoIsSyntaxHighlighted() {
    // "❯ 10 PRINT \"HELLO\"": column 0 is the marker, 2-3 is the line number, 5-9 is PRINT.
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    handler.handleReplInput("10 PRINT \"HELLO\"");

    assertEquals("❯ 10 PRINT \"HELLO\"\n", screen.getOutput());
    final int blue = com.davidconneely.cell.CellAttributes.index(4); // terminal-themed ANSI blue
    final int teal = com.davidconneely.cell.CellAttributes.rgb(0x00D7D7);
    final int darkGrey = com.davidconneely.cell.CellAttributes.rgb(0x808080);
    final int dflt = com.davidconneely.cell.CellAttributes.COLOUR_DEFAULT;
    assertEquals(blue, screen.fgColourAt(0, 0), "marker");
    assertEquals(darkGrey, screen.fgColourAt(0, 2), "'1' of the line number");
    assertEquals(darkGrey, screen.fgColourAt(0, 3), "'0' of the line number");
    assertEquals(teal, screen.fgColourAt(0, 5), "'P' of PRINT");
    assertEquals(teal, screen.fgColourAt(0, 9), "'T' of PRINT");
    assertEquals(dflt, screen.fgColourAt(0, 12), "inside the string literal");
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
  void testImmediateModeStopDoesNotExitRepl() {
    // STOP only raises a report; it never exits the REPL (matching real ZX81/ZX Spectrum BASIC) -
    // that's EXIT's job (see testExitCommandExitsRepl below).
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    final boolean continueRepl = handler.handleReplInput("STOP");

    assertTrue(continueRepl, "Immediate STOP should not exit the REPL");
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
  void testExitCommandExitsRepl() {
    // EXIT is the dedicated REPL-only command for ending the session.
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    final boolean continueRepl = handler.handleReplInput("EXIT");

    assertFalse(continueRepl, "EXIT should return false to exit the REPL");
  }

  @Test
  void testExitCommandCannotBeStoredInProgramLine() {
    // EXIT, like the other REPL-only commands, is not a valid statement inside a numbered line.
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new StatementExecutor(state, screen, screen, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var handler =
        new InterpreterReplHandler(screen, screen, PARSER, state, executor, editor, interpreter);

    final boolean continueRepl = handler.handleReplInput("10 EXIT");

    assertTrue(continueRepl);
    assertEquals(ReportCode.NONSENSE_IN_BASIC, state.lastReport().code());
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
