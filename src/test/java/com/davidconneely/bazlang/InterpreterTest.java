package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.Test;

class InterpreterTest {
  private static final AntlrParser PARSER = new AntlrParser();

  private void runProgram(String source, String expectedOutput) {
    var program = PARSER.parseProgramLines(source);

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    assertEquals(expectedOutput, display.getOutput());
  }

  private void runProgram(String source) {
    // Overloaded for exception tests
    var program = PARSER.parseProgramLines(source);

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
  }

  // Overloaded for arithmetic test that does custom split check
  private String runProgramCapture(String source) {
    var program = PARSER.parseProgramLines(source);

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    BazLangExecutor executor = new BazLangExecutor(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return display.getOutput();
  }

  @Test
  void testHelloWorld() {
    runProgram("10 PRINT \"HELLO\"", "HELLO" + System.lineSeparator());
  }

  @Test
  void testLetAndPrint() {
    runProgram(
        "10 LET a = 42\n20 LET b$ = \"world\"\n30 PRINT a; b$", "42world" + System.lineSeparator());
  }

  @Test
  void testPrintWithTrailingSemicolon() {
    runProgram("10 PRINT \"HELLO\";", "HELLO");
  }

  @Test
  void testPrintWithTrailingComma() {
    // Trailing comma suppresses newline but adds tab spacing
    String output = runProgramCapture("10 PRINT \"A\",");
    assertFalse(output.endsWith(System.lineSeparator()));
    assertTrue(output.length() >= 16); // "A" plus padding to tab stop
  }

  @Test
  void testPrintCommaSeparators() {
    // Comma moves to next tab stop (16 chars)
    String output = runProgramCapture("10 PRINT \"X\", \"Y\", \"Z\"");
    assertEquals(0, output.indexOf('X'));
    assertEquals(16, output.indexOf('Y'));
    assertEquals(32, output.indexOf('Z'));
  }

  @Test
  void testPrintSemicolonConcatenates() {
    // Semicolon concatenates without spacing
    runProgram("10 PRINT \"A\"; \"B\"; \"C\"", "ABC" + System.lineSeparator());
  }

  @Test
  void testPrintMixedSeparators() {
    // Mixed separators
    String output = runProgramCapture("10 PRINT \"A\"; \"B\", \"C\"");
    assertEquals(0, output.indexOf('A'));
    assertEquals(1, output.indexOf('B'));
    assertEquals(16, output.indexOf('C'));
  }

  @Test
  void testForLoop() {
    String expected =
        "1" + System.lineSeparator() + "2" + System.lineSeparator() + "3" + System.lineSeparator();
    runProgram("10 FOR i = 1 TO 3\n20 PRINT i\n30 NEXT i", expected);
  }

  @Test
  void testGoto() {
    runProgram("10 GOTO 30\n20 PRINT \"SKIP\"\n30 PRINT \"END\"", "END" + System.lineSeparator());
  }

  @Test
  void testGosub() {
    runProgram(
        "10 GOSUB 100\n20 PRINT \"WORLD\"\n30 STOP\n100 PRINT \"HELLO \"\n110 RETURN",
        "HELLO " + System.lineSeparator() + "WORLD" + System.lineSeparator());
  }

  @Test
  void testIfStatement() {
    runProgram(
        "10 LET a = 1\n20 IF a = 1 THEN PRINT \"Y\"\n30 IF a = 0 THEN PRINT \"N\"",
        "Y" + System.lineSeparator());
  }

  @Test
  void testArithmetic() {
    String output = runProgramCapture("10 PRINT 1 + 2, 3 - 4, 5 * 6, 8 / 4, 2 ** 3");
    String[] results = output.trim().split("\\s+");
    assertArrayEquals(new String[] {"3", "-1", "30", "2", "8"}, results);
  }

  @Test
  void testValAndStrFunctions() {
    runProgram(
        "10 PRINT VAL(\"123.4\") + 1\n20 PRINT STR$(42)",
        "124.4" + System.lineSeparator() + "42" + System.lineSeparator());
  }

  @Test
  void testDivisionByZero() {
    ReportException e = assertThrows(ReportException.class, () -> runProgram("10 PRINT 1 / 0"));
    assertEquals(ReportCode.NUMBER_TOO_BIG, e.reportCode());
  }

  @Test
  void testReturnWithoutGosub() {
    ReportException e = assertThrows(ReportException.class, () -> runProgram("10 RETURN"));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, e.reportCode());
  }

  @Test
  void testLooseLineLabelMatching() {
    runProgram(
        "1 GOTO 11\n10 PRINT \"Line label 10\"\n20 PRINT \"Line label 20\"",
        "Line label 20" + System.lineSeparator());
  }

  @Test
  void testJumpBeyondLastLine() {
    runProgram("10 GOTO 20", "");
  }

  @Test
  void testOverlappingForLoops() {
    String expected =
        "11"
            + System.lineSeparator()
            + "21"
            + System.lineSeparator()
            + "31"
            + System.lineSeparator()
            + "42"
            + System.lineSeparator()
            + "53"
            + System.lineSeparator();
    runProgram("10 FOR M=1 TO 3\n20 FOR N=1 TO M\n30 PRINT M;N\n40 NEXT M\n50 NEXT N", expected);
  }

  @Test
  void testForLoopGOSUB() {
    String source = "10 FOR M=1 TO 2\n20 GOSUB 30\n30 PRINT \"M=\";M\n40 NEXT M\n50 RETURN";
    ReportException e = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, e.reportCode());

    // We can't easily check partial output with the exception helper unless we split it
    // But verify the exception is the main goal here.
    // If we want to check output before crash, we'd need to catch inside test.
    MockDisplay display = new MockDisplay();
    try {
      var program = PARSER.parseProgramLines(source);
      EvalState state = new EvalState();
      BazLangExecutor executor = new BazLangExecutor(state, display);
      Interpreter interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
    } catch (ReportException re) {
      // Ignore expected
    }
    String expected =
        "M=1"
            + System.lineSeparator()
            + "M=2"
            + System.lineSeparator()
            + "M=3"
            + System.lineSeparator()
            + "M=4"
            + System.lineSeparator();
    assertEquals(expected, display.getOutput());
  }

  @Test
  void testEmptyPrint() {
    // PRINT with no arguments just prints a newline
    runProgram("10 PRINT", System.lineSeparator());
  }

  @Test
  void testRemStatement() {
    // REM should be ignored
    runProgram("10 REM This is a comment\n20 PRINT \"OK\"", "OK" + System.lineSeparator());
  }

  @Test
  void testMultiplePrintOnSameLine() {
    // Multiple PRINTs across lines with semicolons should accumulate
    String output = runProgramCapture("10 PRINT \"A\";\n20 PRINT \"B\";\n30 PRINT \"C\"");
    assertEquals("ABC" + System.lineSeparator(), output);
  }
}
