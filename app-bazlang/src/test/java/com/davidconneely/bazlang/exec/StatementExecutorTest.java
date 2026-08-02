package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatementExecutorTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState state;
  private MockScreen screen;
  private StatementExecutor executor;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    screen = new MockScreen(List.of());
    executor = new StatementExecutor(state, screen, screen);
  }

  @Test
  void testLetStatementNumeric() {
    final var stmts = PARSER.parseStatementsContext("LET X = 42");
    AstAnnotator.INSTANCE.annotate(stmts, 0);
    executor.visit(stmts.statement(0));
    assertTrue(state.hasNumVar("X"));
    assertEquals(42.0, state.numVar("X"));
  }

  @Test
  void testPrintStatement() {
    final var stmts = PARSER.parseStatementsContext("PRINT \"HELLO WORLD\"");
    AstAnnotator.INSTANCE.annotate(stmts, 0);
    executor.visit(stmts.statement(0));
    assertEquals("HELLO WORLD\n", screen.getOutput());
  }
}
