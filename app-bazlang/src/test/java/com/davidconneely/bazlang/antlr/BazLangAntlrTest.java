package com.davidconneely.bazlang.antlr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.BazLangParser.*;
import org.junit.jupiter.api.Test;

/**
 * Tests ANTLR parsing produces correct ParseTree structure. Behavioural testing is done in
 * InterpreterTest and other test classes.
 */
class BazLangAntlrTest {

  private final AntlrParser parser = AntlrParser.INSTANCE;

  @Test
  void testSimplePrint() {
    final var program = parser.parseProgramLines("10 PRINT \"Hello, World!\"\n");
    assertEquals(1, program.size());
    assertTrue(program.containsKey(10));
    final var stmt = program.get(10).getStatements(parser).statement(0);
    assertInstanceOf(PrintStmtContext.class, stmt);
  }

  @Test
  void testForLoop() {
    final var program = parser.parseProgramLines("20 FOR I = 1 TO 10 STEP 2\n");
    assertEquals(1, program.size());
    final var stmt = program.get(20).getStatements(parser).statement(0);
    assertInstanceOf(ForStmtContext.class, stmt);
    ForStmtContext forStmt = (ForStmtContext) stmt;
    assertEquals("I", forStmt.NUM_IDENTIFIER().getText().toUpperCase());
  }

  @Test
  void testIfStatement() {
    final var program = parser.parseProgramLines("30 IF X > 5 THEN PRINT \"Big\"\n");
    assertEquals(1, program.size());
    final var stmt = program.get(30).getStatements(parser).statement(0);
    assertInstanceOf(IfStmtContext.class, stmt);
    IfStmtContext ifStmt = (IfStmtContext) stmt;
    assertInstanceOf(PrintStmtContext.class, ifStmt.statements().statement(0));
  }

  @Test
  void testExpressionParsing() {
    final var program = parser.parseProgramLines("40 LET X = 2 ** 3 + 4 * 5\n");
    assertEquals(1, program.size());
    final var stmt = program.get(40).getStatements(parser).statement(0);
    assertInstanceOf(LetStmtContext.class, stmt);
  }

  @Test
  void testMultipleLines() {
    final var program =
        parser.parseProgramLines(
            """
        10 LET A = 1
        20 LET B = 2
        30 PRINT A + B
        """);
    assertEquals(3, program.size());
    assertTrue(program.containsKey(10));
    assertTrue(program.containsKey(20));
    assertTrue(program.containsKey(30));
  }

  @Test
  void testDimStatement() {
    final var program = parser.parseProgramLines("50 DIM A(10, 20)\n");
    final var stmt = program.get(50).getStatements(parser).statement(0);
    assertInstanceOf(DimStmtContext.class, stmt);
  }

  @Test
  void testGotoGosub() {
    final var program =
        parser.parseProgramLines(
            """
        100 GOTO 200
        110 GOSUB 300
        """);
    assertInstanceOf(GotoStmtContext.class, program.get(100).getStatements(parser).statement(0));
    assertInstanceOf(GosubStmtContext.class, program.get(110).getStatements(parser).statement(0));
  }

  @Test
  void testReplImmediateLine() {
    final var result = parser.parseReplLine("PRINT \"Hello\"");
    assertInstanceOf(AntlrParser.ParsedLine.Immediate.class, result);
    AntlrParser.ParsedLine.Immediate immediate = (AntlrParser.ParsedLine.Immediate) result;
    assertInstanceOf(PrintStmtContext.class, immediate.statements().statement(0));
  }

  @Test
  void testReplNumberedLine() {
    final var result = parser.parseReplLine("100 REM Test");
    assertInstanceOf(AntlrParser.ParsedLine.Numbered.class, result);
    AntlrParser.ParsedLine.Numbered numbered = (AntlrParser.ParsedLine.Numbered) result;
    assertEquals(100, numbered.lineNumber());
    assertEquals("REM Test", numbered.statementText());
  }

  @Test
  void testReplLineNumberOnlyDeletesLine() {
    // Typing just a line number should parse as a Numbered line with empty statement
    final var result = parser.parseReplLine("100");
    assertInstanceOf(AntlrParser.ParsedLine.Numbered.class, result);
    AntlrParser.ParsedLine.Numbered numbered = (AntlrParser.ParsedLine.Numbered) result;
    assertEquals(100, numbered.lineNumber());
    assertEquals("", numbered.statementText());
  }

  @Test
  void testReplEditCommand() {
    // EDIT is REPL-only (not in statement rule, only in replLine rule)
    final var result = parser.parseReplLine("EDIT 100");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testReplDeleteCommand() {
    // DELETE is REPL-only (not in statement rule, only in replLine rule)
    final var result = parser.parseReplLine("DELETE 100");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testReplRenumCommand() {
    // RENUM is REPL-only (not in statement rule, only in replLine rule)
    final var result = parser.parseReplLine("RENUM");
    assertInstanceOf(AntlrParser.ParsedLine.ReplCommand.class, result);
  }

  @Test
  void testCaseInsensitiveKeywords() {
    // All variations should parse successfully
    assertDoesNotThrow(
        () ->
            parser
                .parseProgramLines("10 print \"test\"\n")
                .get(10)
                .getStatements(parser)
                .statement(0));
    assertDoesNotThrow(
        () ->
            parser
                .parseProgramLines("10 PRINT \"test\"\n")
                .get(10)
                .getStatements(parser)
                .statement(0));
    assertDoesNotThrow(
        () ->
            parser
                .parseProgramLines("10 PrInT \"test\"\n")
                .get(10)
                .getStatements(parser)
                .statement(0));
  }

  @Test
  void testCaseInsensitiveVariables() {
    // Variables should be normalised but parse correctly
    assertDoesNotThrow(
        () ->
            parser.parseProgramLines("10 LET a = 1\n").get(10).getStatements(parser).statement(0));
    assertDoesNotThrow(
        () ->
            parser.parseProgramLines("10 LET A = 1\n").get(10).getStatements(parser).statement(0));
  }
}
