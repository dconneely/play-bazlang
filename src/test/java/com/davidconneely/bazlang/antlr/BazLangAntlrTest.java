package com.davidconneely.bazlang.antlr;

import static org.junit.jupiter.api.Assertions.*;

import com.davidconneely.bazlang.*;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.util.NavigableMap;
import org.junit.jupiter.api.Test;

/**
 * Tests ANTLR parsing produces correct ParseTree structure. Behavioral testing is done in
 * InterpreterTest and other test classes.
 */
class BazLangAntlrTest {

  private final AntlrParser parser = AntlrParser.INSTANCE;

  @Test
  void testSimplePrint() {
    String code = "10 PRINT \"Hello, World!\"\n";
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertEquals(1, program.size());
    assertTrue(program.containsKey(10));
    StatementContext stmt = program.get(10).getStatement(parser);
    assertInstanceOf(PrintStmtContext.class, stmt);
  }

  @Test
  void testForLoop() {
    String code = "20 FOR I = 1 TO 10 STEP 2\n";
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertEquals(1, program.size());
    StatementContext stmt = program.get(20).getStatement(parser);
    assertInstanceOf(ForStmtContext.class, stmt);
    ForStmtContext forStmt = (ForStmtContext) stmt;
    assertEquals("I", forStmt.NUM_IDENTIFIER().getText().toUpperCase());
  }

  @Test
  void testIfStatement() {
    String code = "30 IF X > 5 THEN PRINT \"Big\"\n";
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertEquals(1, program.size());
    StatementContext stmt = program.get(30).getStatement(parser);
    assertInstanceOf(IfStmtContext.class, stmt);
    IfStmtContext ifStmt = (IfStmtContext) stmt;
    assertInstanceOf(PrintStmtContext.class, ifStmt.statement());
  }

  @Test
  void testExpressionParsing() {
    String code = "40 LET X = 2 ** 3 + 4 * 5\n";
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertEquals(1, program.size());
    StatementContext stmt = program.get(40).getStatement(parser);
    assertInstanceOf(LetStmtContext.class, stmt);
  }

  @Test
  void testMultipleLines() {
    String code =
        """
        10 LET A = 1
        20 LET B = 2
        30 PRINT A + B
        """;
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertEquals(3, program.size());
    assertTrue(program.containsKey(10));
    assertTrue(program.containsKey(20));
    assertTrue(program.containsKey(30));
  }

  @Test
  void testDimStatement() {
    String code = "50 DIM A(10, 20)\n";
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    StatementContext stmt = program.get(50).getStatement(parser);
    assertInstanceOf(DimStmtContext.class, stmt);
  }

  @Test
  void testGotoGosub() {
    String code =
        """
        100 GOTO 200
        110 GOSUB 300
        """;
    NavigableMap<Integer, ProgramLine> program = parser.parseProgramLines(code);

    assertInstanceOf(GotoStmtContext.class, program.get(100).getStatement(parser));
    assertInstanceOf(GosubStmtContext.class, program.get(110).getStatement(parser));
  }

  @Test
  void testReplImmediateLine() {
    AntlrParser.ParsedLine result = parser.parseReplLine("PRINT \"Hello\"");
    assertInstanceOf(AntlrParser.ParsedLine.Immediate.class, result);
    AntlrParser.ParsedLine.Immediate immediate = (AntlrParser.ParsedLine.Immediate) result;
    assertInstanceOf(PrintStmtContext.class, immediate.statement());
  }

  @Test
  void testReplNumberedLine() {
    AntlrParser.ParsedLine result = parser.parseReplLine("100 REM Test");
    assertInstanceOf(AntlrParser.ParsedLine.Numbered.class, result);
    AntlrParser.ParsedLine.Numbered numbered = (AntlrParser.ParsedLine.Numbered) result;
    assertEquals(100, numbered.lineNumber());
    assertEquals("REM Test", numbered.statementText());
  }

  @Test
  void testReplLineNumberOnlyDeletesLine() {
    // Typing just a line number should parse as a Numbered line with empty statement
    AntlrParser.ParsedLine result = parser.parseReplLine("100");
    assertInstanceOf(AntlrParser.ParsedLine.Numbered.class, result);
    AntlrParser.ParsedLine.Numbered numbered = (AntlrParser.ParsedLine.Numbered) result;
    assertEquals(100, numbered.lineNumber());
    assertEquals("", numbered.statementText());
  }

  @Test
  void testReplEditCommand() {
    // EDIT is REPL-only (not in statement rule, only in replLine rule)
    AntlrParser.ParsedLine result = parser.parseReplLine("EDIT 100");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testReplDeleteCommand() {
    // DELETE is REPL-only (not in statement rule, only in replLine rule)
    AntlrParser.ParsedLine result = parser.parseReplLine("DELETE 100");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testReplRenumCommand() {
    // RENUM is REPL-only (not in statement rule, only in replLine rule)
    AntlrParser.ParsedLine result = parser.parseReplLine("RENUM");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testCaseInsensitiveKeywords() {
    // All variations should parse successfully
    assertDoesNotThrow(
        () -> parser.parseProgramLines("10 print \"test\"\n").get(10).getStatement(parser));
    assertDoesNotThrow(
        () -> parser.parseProgramLines("10 PRINT \"test\"\n").get(10).getStatement(parser));
    assertDoesNotThrow(
        () -> parser.parseProgramLines("10 PrInT \"test\"\n").get(10).getStatement(parser));
  }

  @Test
  void testCaseInsensitiveVariables() {
    // Variables should be normalized but parse correctly
    assertDoesNotThrow(
        () -> parser.parseProgramLines("10 LET a = 1\n").get(10).getStatement(parser));
    assertDoesNotThrow(
        () -> parser.parseProgramLines("10 LET A = 1\n").get(10).getStatement(parser));
  }
}
