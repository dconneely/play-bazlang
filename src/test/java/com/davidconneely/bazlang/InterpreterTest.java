package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterpreterTest {

  private void runProgram(String source, String expectedOutput) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    var program = parser.parseProgram();

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
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
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    var program = parser.parseProgram();

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
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
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    var program = parser.parseProgram();

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
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
      Lexer lexer = new Lexer(source);
      List<Token> tokens = lexer.tokenize();
      Parser parser = new Parser(tokens);
      var program = parser.parseProgram();
      EvalState state = new EvalState();
      Evaluator evaluator = new Evaluator(state, display);
      Executor executor = new Executor(state, evaluator, display);
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
}
