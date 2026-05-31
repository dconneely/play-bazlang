package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.io.MockDisplay;
import com.davidconneely.repl.Shell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for REPL-only commands: DELETE, EDIT, RENUM. */
class ReplCommandTest {

  private EvalState state;
  private MockDisplay display;
  private BazLangExecutor executor;
  private ProgramEditor editor;
  private AntlrParser parser;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    display = new MockDisplay();
    executor = new BazLangExecutor(state, display);
    parser = new AntlrParser();
    editor = new ProgramEditor(state, display, parser, executor::evalNum);
    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));
  }

  private void executeReplCommand(String command) {
    var parsed = parser.parseReplLine(command);
    if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
      handleReplCommand(ctx, executor, editor, display, state);
    } else {
      fail("Expected ReplCommand but got: " + parsed.getClass().getSimpleName());
    }
  }

  private static void handleReplCommand(
      BazLangParser.ReplCommandContext ctx,
      BazLangExecutor executor,
      ProgramEditor editor,
      Shell ui,
      EvalState state) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      editor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      int lineNum = (int) executor.evalNum(edit.numExpr());
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      ProgramLine programLine = state.program().get(lineNum);
      if (programLine != null) {
        ui.prefillInput(lineNum + " " + programLine.sourceText());
      } else {
        ui.prefillInput(lineNum + " ");
      }
    } else if (ctx instanceof BazLangParser.RenumCmdContext renum) {
      editor.executeRenum(renum.renumArgs());
    }
  }

  // ==================== DELETE tests ====================

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
  void testDeleteRange() {
    executeReplCommand("DELETE 20 TO 30");
    assertFalse(state.program().containsKey(20));
    assertFalse(state.program().containsKey(30));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
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
  void testDeleteFromStart() {
    executeReplCommand("DELETE TO 20");
    assertFalse(state.program().containsKey(10));
    assertFalse(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteWithoutNumber() {
    var ex1 = assertThrows(ReportException.class, () -> executeReplCommand("DELETE"));
    assertTrue(ex1.getMessage().contains("requires at least one line number"));

    // DELETE TO should delete everything (matching LIST TO behavior)
    executeReplCommand("DELETE TO");
    assertTrue(state.program().isEmpty());
  }

  // ==================== EDIT tests ====================

  @Test
  void testEditExistingLine() {
    executeReplCommand("EDIT 10");
    assertEquals("10 PRINT \"HELLO\"", display.getPrefillText());
  }

  @Test
  void testEditNonExistentLine() {
    executeReplCommand("EDIT 100");
    assertEquals("100 ", display.getPrefillText());
  }

  @Test
  void testEditOutOfRangeThrowsError() {
    var ex = assertThrows(ReportException.class, () -> executeReplCommand("EDIT 0"));
    assertTrue(ex.getMessage().contains("out of range"));
  }

  // ==================== RENUM tests ====================

  @Test
  void testRenumBasic() {
    executeReplCommand("RENUM");
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
  }

  @Test
  void testRenumWithStart() {
    executeReplCommand("RENUM 100");
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(120));
    assertTrue(state.program().containsKey(130));
    assertFalse(state.program().containsKey(10));
  }

  @Test
  void testRenumWithStep() {
    executeReplCommand("RENUM 100 STEP 5");
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(105));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(115));
  }

  @Test
  void testRenumUpdatesGotoTargets() {
    executeReplCommand("RENUM 100 STEP 10");
    // Original line 20 had "GOTO 40", should now be "GOTO 130"
    ProgramLine line = state.program().get(110);
    assertNotNull(line);
    assertEquals("GOTO 130", line.sourceText());
  }

  @Test
  void testRenumStepOnly() {
    executeReplCommand("RENUM STEP 5");
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(15));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(25));
    assertEquals(4, state.program().size());
  }

  @Test
  void testRenumNonExistentGotoTarget() {
    state.program().clear();
    state.program().put(1000, new ProgramLine(1000, "PRINT \"HELLO\""));
    state.program().put(1200, new ProgramLine(1200, "PRINT \"THERE\""));
    state.program().put(1300, new ProgramLine(1300, "GOTO 1100"));

    executeReplCommand("RENUM");

    assertEquals(3, state.program().size());
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));

    // GOTO 1100 should become GOTO 20 (ceiling of 1100 is 1200, which becomes 20)
    ProgramLine line30 = state.program().get(30);
    assertNotNull(line30);
    assertEquals("GOTO 20", line30.sourceText());
  }
}
