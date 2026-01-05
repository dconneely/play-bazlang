package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterpreterTest {
  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  @BeforeEach
  public void setUpStreams() {
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  public void restoreStreams() {
    System.setOut(originalOut);
  }

  private void runProgram(String source) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    var program = parser.parseProgram();
    Interpreter interpreter = new Interpreter();
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
  }

  @Test
  void testHelloWorld() {
    runProgram("10 PRINT \"HELLO\"");
    assertEquals("HELLO" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testLetAndPrint() {
    runProgram("10 LET a = 42\n20 LET b$ = \"world\"\n30 PRINT a; b$");
    assertEquals("42world" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testPrintWithTrailingSemicolon() {
    runProgram("10 PRINT \"HELLO\";");
    assertEquals("HELLO", outContent.toString());
  }

  @Test
  void testForLoop() {
    runProgram("10 FOR i = 1 TO 3\n20 PRINT i\n30 NEXT i");
    String expected =
        "1" + System.lineSeparator() + "2" + System.lineSeparator() + "3" + System.lineSeparator();
    assertEquals(expected, outContent.toString());
  }

  @Test
  void testGoto() {
    runProgram("10 GOTO 30\n20 PRINT \"SKIP\"\n30 PRINT \"END\"");
    assertEquals("END" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testGosub() {
    runProgram("10 GOSUB 100\n20 PRINT \"WORLD\"\n30 STOP\n100 PRINT \"HELLO \"\n110 RETURN");
    assertEquals(
        "HELLO " + System.lineSeparator() + "WORLD" + System.lineSeparator(),
        outContent.toString());
  }

  @Test
  void testIfStatement() {
    runProgram("10 LET a = 1\n20 IF a = 1 THEN PRINT \"Y\"\n30 IF a = 0 THEN PRINT \"N\"");
    assertEquals("Y" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testArithmetic() {
    runProgram("10 PRINT 1 + 2, 3 - 4, 5 * 6, 8 / 4, 2 ** 3");
    String[] results = outContent.toString().trim().split("\\s+");
    assertArrayEquals(new String[] {"3", "-1", "30", "2", "8"}, results);
  }

  @Test
  void testValAndStrFunctions() {
    runProgram("10 PRINT VAL(\"123.4\") + 1\n20 PRINT STR$(42)");
    assertEquals(
        "124.4" + System.lineSeparator() + "42" + System.lineSeparator(), outContent.toString());
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
    runProgram("1 GOTO 11\n10 PRINT \"Line label 10\"\n20 PRINT \"Line label 20\"");
    assertEquals("Line label 20" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testJumpBeyondLastLine() {
    runProgram("10 GOTO 20");
    assertEquals("", outContent.toString());
  }

  @Test
  void testOverlappingForLoops() {
    runProgram("10 FOR M=1 TO 3\n20 FOR N=1 TO M\n30 PRINT M;N\n40 NEXT M\n50 NEXT N");
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
    assertEquals(expected, outContent.toString());
  }

  @Test
  void testForLoopGOSUB() {
    String source = "10 FOR M=1 TO 2\n20 GOSUB 30\n30 PRINT \"M=\";M\n40 NEXT M\n50 RETURN";
    ReportException e = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, e.reportCode());
    String expected =
        "M=1"
            + System.lineSeparator()
            + "M=2"
            + System.lineSeparator()
            + "M=3"
            + System.lineSeparator()
            + "M=4"
            + System.lineSeparator();
    assertEquals(expected, outContent.toString());
  }
}
