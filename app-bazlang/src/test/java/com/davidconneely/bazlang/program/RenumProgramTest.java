package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.StreamScreen;
import org.junit.jupiter.api.Test;

class RenumProgramTest extends BaseProgramTest {

  private ProgramEditor makeEditor(EvalState state) {
    final var screen = StreamScreen.nullScreen();
    final var executor = new StatementExecutor(state, screen, screen);
    return new ProgramEditor(state, screen, PARSER, executor::evalNum);
  }

  private void executeRenumCommand(String command, ProgramEditor editor) {
    final var parsed = PARSER.parseReplLine(command);
    if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
      if (ctx instanceof BazLangParser.RenumCmdContext renum) {
        editor.executeRenum(renum.renumArgs());
      } else {
        fail("Expected RenumCmd but got: " + ctx.getClass().getSimpleName());
      }
    } else {
      fail("Expected ReplCommand but got: " + parsed.getClass().getSimpleName());
    }
  }

  @Test
  void testRenumBasic() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));

    executeRenumCommand("RENUM", editor);
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
  }

  @Test
  void testRenumInsideIfStatement() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "IF X = 1 THEN GOTO 20"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    final var parsed = PARSER.parseReplLine("RENUM 100");
    final var ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    editor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // Should preserve IF...THEN and only change 20 to 110.
    assertEquals("IF X = 1 THEN GOTO 110", state.program().get(100).sourceText());
  }

  @Test
  void testRenumLiteralInsideString() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"GOTO 20\""));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    final var parsed = PARSER.parseReplLine("RENUM 100");
    final var ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    editor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // Should NOT change the string literal.
    assertEquals("PRINT \"GOTO 20\"", state.program().get(100).sourceText());
  }

  @Test
  void testRenumLiteralTargets() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "GOTO 20"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    final var parsed = PARSER.parseReplLine("RENUM 100");
    final var ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    editor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    assertEquals("GOTO 110", state.program().get(100).sourceText());
    assertEquals("PRINT \"Target\"", state.program().get(110).sourceText());
  }

  @Test
  void testRenumNonExistentGotoTarget() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(1000, new ProgramLine(1000, "PRINT \"HELLO\""));
    state.program().put(1200, new ProgramLine(1200, "PRINT \"THERE\""));
    state.program().put(1300, new ProgramLine(1300, "GOTO 1100"));

    executeRenumCommand("RENUM", editor);

    assertEquals(3, state.program().size());
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));

    // GOTO 1100 should become GOTO 20 (ceiling of 1100 is 1200, which becomes 20)
    final var line30 = state.program().get(30);
    assertNotNull(line30);
    assertEquals("GOTO 20", line30.sourceText());
  }

  @Test
  void testRenumNonExistentTargetInRange() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "GOTO 15"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    final var parsed = PARSER.parseReplLine("RENUM 100");
    final var ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    editor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // 10 -> 100, 20 -> 110
    // 15 is not there, but it's in range [10, 20]
    // ceilingKey(15) is 20. mapping(20) is 110.
    // So GOTO 15 should become GOTO 110.
    assertEquals("GOTO 110", state.program().get(100).sourceText());
  }

  @Test
  void testRenumNonLiteralTargets() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "LET X = 20"));
    state.program().put(20, new ProgramLine(20, "GOTO X"));

    editor.executeRenum(null);

    assertEquals("LET X = 20", state.program().get(10).sourceText());
    assertEquals("GOTO X", state.program().get(20).sourceText());
  }

  @Test
  void testRenumStepOnly() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));

    executeRenumCommand("RENUM STEP 5", editor);
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(15));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(25));
    assertEquals(4, state.program().size());
  }

  @Test
  void testRenumUpdatesGotoTargets() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));

    executeRenumCommand("RENUM 100 STEP 10", editor);
    // Original line 20 had "GOTO 40", should now be "GOTO 130"
    final var line = state.program().get(110);
    assertNotNull(line);
    assertEquals("GOTO 130", line.sourceText());
  }

  @Test
  void testRenumWithStart() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));

    executeRenumCommand("RENUM 100", editor);
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(120));
    assertTrue(state.program().containsKey(130));
    assertFalse(state.program().containsKey(10));
  }

  @Test
  void testRenumWithStep() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));

    executeRenumCommand("RENUM 100 STEP 5", editor);
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(105));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(115));
  }
}
