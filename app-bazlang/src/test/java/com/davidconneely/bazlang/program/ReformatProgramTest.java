package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.StreamScreen;
import org.junit.jupiter.api.Test;

class ReformatProgramTest extends BaseProgramTest {

  private ProgramEditor makeEditor(EvalState state) {
    final var screen = StreamScreen.nullScreen();
    final var executor = new StatementExecutor(state, screen, screen);
    return new ProgramEditor(state, screen, PARSER, executor::evalNum);
  }

  @Test
  void testReformatAll() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "let a = 1 + 2 * 3"));
    state.program().put(20, new ProgramLine(20, "print \"hello\", a"));

    editor.executeReformat(null);

    assertEquals("LET a = 1 + 2 * 3", state.program().get(10).sourceText());
    assertEquals("PRINT \"hello\", a", state.program().get(20).sourceText());
  }

  @Test
  void testReformatComplex() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "if a=1 then goto 100"));
    state.program().put(20, new ProgramLine(20, "for i=1 to 10 step 2"));
    state.program().put(30, new ProgramLine(30, "rem this is a comment"));

    editor.executeReformat(null);

    assertEquals("IF a = 1 THEN GO TO 100", state.program().get(10).sourceText());
    assertEquals("FOR i = 1 TO 10 STEP 2", state.program().get(20).sourceText());
    assertEquals(
        "REM this is a comment", state.program().get(30).sourceText()); // REM is capitalized
  }

  @Test
  void testReformatFunctions() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "let x = sin(0) + cos(pi)"));

    editor.executeReformat(null);

    assertEquals("LET x = SIN (0) + COS (PI)", state.program().get(10).sourceText());
  }

  @Test
  void testReformatNakedTo() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "let a = 1"));
    state.program().put(20, new ProgramLine(20, "let b = 2"));

    final var parsed = PARSER.parseReplLine("REFORMAT TO");
    editor.executeReformat(
        ((BazLangParser.ReformatCmdContext) ((AntlrParser.ParsedLine.ReplCommand) parsed).context())
            .lineRange());

    assertEquals("LET a = 1", state.program().get(10).sourceText());
    assertEquals("LET b = 2", state.program().get(20).sourceText());
  }

  @Test
  void testReformatOmitDefaults() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "FOR I = 1 TO 10 STEP 1"));
    state.program().put(20, new ProgramLine(20, "RAND 0"));
    state.program().put(30, new ProgramLine(30, "RUN 0"));
    state.program().put(40, new ProgramLine(40, "LIST 0"));

    editor.executeReformat(null);

    assertEquals("FOR i = 1 TO 10", state.program().get(10).sourceText());
    assertEquals("RANDOMIZE", state.program().get(20).sourceText());
    assertEquals("RUN", state.program().get(30).sourceText());
    assertEquals("LIST", state.program().get(40).sourceText());
  }

  @Test
  void testReformatRange() {
    final var state = new EvalState();
    final var editor = makeEditor(state);

    state.program().put(10, new ProgramLine(10, "let a = 1"));
    state.program().put(20, new ProgramLine(20, "let b = 2"));
    state.program().put(30, new ProgramLine(30, "let c = 3"));

    final var parsed = PARSER.parseReplLine("REFORMAT 15 TO 25");
    editor.executeReformat(
        ((BazLangParser.ReformatCmdContext) ((AntlrParser.ParsedLine.ReplCommand) parsed).context())
            .lineRange());

    assertEquals("let a = 1", state.program().get(10).sourceText()); // Untouched
    assertEquals("LET b = 2", state.program().get(20).sourceText()); // Reformatted
    assertEquals("let c = 3", state.program().get(30).sourceText()); // Untouched
  }
}
