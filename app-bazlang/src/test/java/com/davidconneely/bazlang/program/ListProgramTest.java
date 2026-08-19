package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.io.MockScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the LIST statement. */
class ListProgramTest extends BaseProgramTest {

  private MockScreen screen;
  private StatementExecutor executor;
  private AntlrParser parser;

  @BeforeEach
  void setUp() {
    final var state = new EvalState();
    screen = new MockScreen();
    executor = new StatementExecutor(state, screen, screen, screen);
    parser = AntlrParser.INSTANCE;
    // Set up a simple program
    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));
  }

  private void execute(String statement) {
    final var parsed = parser.parseReplLine(statement);
    if (parsed instanceof AntlrParser.ParsedLine.Immediate(var stmts)) {
      for (var stmt : AstLowering.lowerStatements(stmts, 0)) {
        executor.execute(stmt);
      }
    }
  }

  @Test
  void testListAll() {
    // LIST - lists all lines
    execute("LIST");
    final String output = screen.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListFromN() {
    // LIST n - lists from n to end
    execute("LIST 30");
    final String output = screen.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertFalse(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListFromNTo() {
    // LIST n TO - lists from n to end
    execute("LIST 30 TO");
    final String output = screen.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertFalse(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListNToM() {
    // LIST n TO m - lists from n to m
    execute("LIST 20 TO 30");
    final String output = screen.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertFalse(output.contains("40 STOP"));
  }

  @Test
  void testListToM() {
    // LIST TO m - lists from MIN to m
    execute("LIST TO 20");
    final String output = screen.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertFalse(output.contains("30 PRINT"));
    assertFalse(output.contains("40 STOP"));
  }

  @Test
  void testListToOnly() {
    // LIST TO - lists all lines (same as LIST)
    execute("LIST TO");
    final String output = screen.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }
}
