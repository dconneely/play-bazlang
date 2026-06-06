package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.io.MockDisplay;
import org.junit.jupiter.api.Test;

class ReformatTest {
  private static final AntlrParser PARSER = new AntlrParser();

  private ProgramEditor makeEditor(EvalState state, MockDisplay display) {
    ProgramManager executor = new ProgramManager(state, display);
    return new ProgramEditor(state, display, PARSER, executor::evalNum);
  }

  @Test
  void testReformatAll() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "let a = 1 + 2 * 3"));
    state.program().put(20, new ProgramLine(20, "print \"hello\", a"));

    editor.executeReformat(null);

    assertEquals("LET A = 1 + 2 * 3", state.program().get(10).sourceText());
    assertEquals("PRINT \"hello\", A", state.program().get(20).sourceText());
  }

  @Test
  void testReformatRange() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "let a = 1"));
    state.program().put(20, new ProgramLine(20, "let b = 2"));
    state.program().put(30, new ProgramLine(30, "let c = 3"));

    AntlrParser.ParsedLine parsed = PARSER.parseReplLine("REFORMAT 15 TO 25");
    editor.executeReformat(
        ((BazLangParser.ReformatCmdContext) ((AntlrParser.ParsedLine.ReplCommand) parsed).context())
            .lineRange());

    assertEquals("let a = 1", state.program().get(10).sourceText()); // Untouched
    assertEquals("LET B = 2", state.program().get(20).sourceText()); // Reformatted
    assertEquals("let c = 3", state.program().get(30).sourceText()); // Untouched
  }

  @Test
  void testReformatComplex() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "if a=1 then goto 100"));
    state.program().put(20, new ProgramLine(20, "for i=1 to 10 step 2"));
    state.program().put(30, new ProgramLine(30, "rem this is a comment"));

    editor.executeReformat(null);

    assertEquals("IF A = 1 THEN GOTO 100", state.program().get(10).sourceText());
    assertEquals("FOR I = 1 TO 10 STEP 2", state.program().get(20).sourceText());
    assertEquals(
        "REM this is a comment", state.program().get(30).sourceText()); // REM is capitalized
  }

  @Test
  void testReformatFunctions() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "let x = sin(0) + cos(pi)"));

    editor.executeReformat(null);

    assertEquals("LET X = SIN (0) + COS (PI)", state.program().get(10).sourceText());
  }

  @Test
  void testReformatNakedTo() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "let a = 1"));
    state.program().put(20, new ProgramLine(20, "let b = 2"));

    AntlrParser.ParsedLine parsed = PARSER.parseReplLine("REFORMAT TO");
    editor.executeReformat(
        ((BazLangParser.ReformatCmdContext) ((AntlrParser.ParsedLine.ReplCommand) parsed).context())
            .lineRange());

    assertEquals("LET A = 1", state.program().get(10).sourceText());
    assertEquals("LET B = 2", state.program().get(20).sourceText());
  }

  @Test
  void testReformatOmitDefaults() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramEditor editor = makeEditor(state, display);

    state.program().put(10, new ProgramLine(10, "FOR I = 1 TO 10 STEP 1"));
    state.program().put(20, new ProgramLine(20, "RAND 0"));
    state.program().put(30, new ProgramLine(30, "RUN 0"));
    state.program().put(40, new ProgramLine(40, "LIST 0"));

    editor.executeReformat(null);

    assertEquals("FOR I = 1 TO 10", state.program().get(10).sourceText());
    assertEquals("RAND", state.program().get(20).sourceText());
    assertEquals("RUN", state.program().get(30).sourceText());
    assertEquals("LIST", state.program().get(40).sourceText());
  }
}
