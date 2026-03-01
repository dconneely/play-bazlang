package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.io.MockDisplay;
import org.junit.jupiter.api.Test;

class RenumTest {
  @Test
  void testRenumLiteralTargets() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);

    // 10 GOTO 20
    // 20 PRINT "Target"
    state.program().put(10, new ProgramLine(10, "GOTO 20"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    AntlrParser parser = new AntlrParser();
    AntlrParser.ParsedLine parsed = parser.parseReplLine("RENUM 100");
    BazLangParser.ReplCommandContext ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    executor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    assertEquals("GOTO 110", state.program().get(100).sourceText());
    assertEquals("PRINT \"Target\"", state.program().get(110).sourceText());
  }

  @Test
  void testRenumNonLiteralTargets() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);

    // 10 LET X = 20
    // 20 GOTO X
    state.program().put(10, new ProgramLine(10, "LET X = 20"));
    state.program().put(20, new ProgramLine(20, "GOTO X"));

    executor.executeRenum(null);

    assertEquals("LET X = 20", state.program().get(10).sourceText());
    assertEquals("GOTO X", state.program().get(20).sourceText());
  }

  @Test
  void testRenumNonExistentTargetInRange() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);

    // 10 GOTO 15
    // 20 PRINT "Target"
    state.program().put(10, new ProgramLine(10, "GOTO 15"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    AntlrParser parser = new AntlrParser();
    AntlrParser.ParsedLine parsed = parser.parseReplLine("RENUM 100");
    BazLangParser.ReplCommandContext ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    executor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // 10 -> 100, 20 -> 110
    // 15 is not there, but it's in range [10, 20]
    // ceilingKey(15) is 20. mapping(20) is 110.
    // So GOTO 15 should become GOTO 110.
    assertEquals("GOTO 110", state.program().get(100).sourceText());
  }

  @Test
  void testRenumLiteralInsideString() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);

    // 10 PRINT "GOTO 20"
    // 20 PRINT "Target"
    state.program().put(10, new ProgramLine(10, "PRINT \"GOTO 20\""));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    AntlrParser parser = new AntlrParser();
    AntlrParser.ParsedLine parsed = parser.parseReplLine("RENUM 100");
    BazLangParser.ReplCommandContext ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    executor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // This SHOULD NOT change the string!
    assertEquals("PRINT \"GOTO 20\"", state.program().get(100).sourceText());
  }

  @Test
  void testRenumInsideIfStatement() {
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);

    // 10 IF X = 1 THEN GOTO 20
    // 20 PRINT "Target"
    state.program().put(10, new ProgramLine(10, "IF X = 1 THEN GOTO 20"));
    state.program().put(20, new ProgramLine(20, "PRINT \"Target\""));

    AntlrParser parser = new AntlrParser();
    AntlrParser.ParsedLine parsed = parser.parseReplLine("RENUM 100");
    BazLangParser.ReplCommandContext ctx = ((AntlrParser.ParsedLine.ReplCommand) parsed).context();
    executor.executeRenum(((BazLangParser.RenumCmdContext) ctx).renumArgs());

    // Should preserve IF...THEN and only change 20 to 110
    assertEquals("IF X = 1 THEN GOTO 110", state.program().get(100).sourceText());
  }
}
