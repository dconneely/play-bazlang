package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.MockScreen;
import com.davidconneely.bazlang.io.VirtualInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for DELETE and EDIT REPL-only commands. */
class DeleteEditProgramTest extends BaseProgramTest {

  private EvalState state;
  private MockScreen screen;
  private StatementExecutor executor;
  private ProgramEditor editor;
  private AntlrParser parser;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    screen = new MockScreen();
    executor = new StatementExecutor(state, screen, screen);
    parser = AntlrParser.INSTANCE;
    editor = new ProgramEditor(state, screen, parser, executor::evalNum);
    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));
  }

  private void executeReplCommand(String command) {
    final var parsed = parser.parseReplLine(command);
    if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
      handleReplCommand(ctx, executor, editor, screen, state);
    } else {
      fail("Expected ReplCommand but got: " + parsed.getClass().getSimpleName());
    }
  }

  private static void handleReplCommand(
      BazLangParser.ReplCommandContext ctx,
      StatementExecutor executor,
      ProgramEditor editor,
      VirtualInput ui,
      EvalState state) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      editor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      final int lineNum = (int) executor.evalNum(edit.numExpr());
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      final var programLine = state.program().get(lineNum);
      if (programLine != null) {
        ui.prefillInput(lineNum + " " + programLine.sourceText());
      } else {
        ui.prefillInput(lineNum + " ");
      }
    }
  }

  @Test
  void testDeleteFromStart() {
    executeReplCommand("DELETE TO 20");
    assertFalse(state.program().containsKey(10));
    assertFalse(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteRange() {
    executeReplCommand("DELETE 20 TO 30");
    assertFalse(state.program().containsKey(20));
    assertFalse(state.program().containsKey(30));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteSingleLine() {
    executeReplCommand("DELETE 20");
    assertFalse(state.program().containsKey(20));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
    assertEquals(3, state.program().size());
  }

  @Test
  void testDeleteToEnd() {
    executeReplCommand("DELETE 30 TO");
    assertFalse(state.program().containsKey(30));
    assertFalse(state.program().containsKey(40));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteWithoutNumber() {
    final var ex1 = assertThrows(ReportException.class, () -> executeReplCommand("DELETE"));
    assertTrue(ex1.getMessage().contains("requires at least one line number"));

    // DELETE TO should delete everything (matching LIST TO behaviour)
    executeReplCommand("DELETE TO");
    assertTrue(state.program().isEmpty());
  }

  @Test
  void testEditExistingLine() {
    executeReplCommand("EDIT 10");
    assertEquals("10 PRINT \"HELLO\"", screen.getPrefillText());
  }

  @Test
  void testEditNonExistentLine() {
    executeReplCommand("EDIT 100");
    assertEquals("100 ", screen.getPrefillText());
  }

  @Test
  void testEditOutOfRangeThrowsError() {
    final var ex = assertThrows(ReportException.class, () -> executeReplCommand("EDIT 0"));
    assertTrue(ex.getMessage().contains("out of range"));
  }
}
