package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests covering the language features described in docs/language_features.md. */
class LanguageReferenceTest {
  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState runProgram(String source) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
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
    return state;
  }

  @Test
  void testNumFuncs() {
    String source =
        """
        10 LET A = ABS(-5)
        20 LET B = INT(3.14)
        30 LET C = SGN(-10)
        40 LET D = SQR(16)
        50 LET E = LEN("HELLO")
        60 LET F = VAL("123")
        70 LET G = CODE("A")
        """;
    EvalState state = runProgram(source);
    assertEquals(5.0, state.numScalars().get("A"));
    assertEquals(3.0, state.numScalars().get("B"));
    assertEquals(-1.0, state.numScalars().get("C"));
    assertEquals(4.0, state.numScalars().get("D"));
    assertEquals(5.0, state.numScalars().get("E"));
    assertEquals(123.0, state.numScalars().get("F"));
    assertEquals(65.0, state.numScalars().get("G"));
  }

  @Test
  void testTrigFuncs() {
    // Basic check they run and return somewhat sane values
    String source =
        """
        10 LET S = SIN(0)
        20 LET C = COS(0)
        30 LET T = TAN(0)
        """;
    EvalState state = runProgram(source);
    assertEquals(0.0, state.numScalars().get("S"), 0.0001);
    assertEquals(1.0, state.numScalars().get("C"), 0.0001);
    assertEquals(0.0, state.numScalars().get("T"), 0.0001);
  }

  @Test
  void testExpLogFuncs() {
    String source =
        """
        10 LET E = EXP(1)
        20 LET L = LN(E)
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.E, state.numScalars().get("E"), 0.0001);
    assertEquals(1.0, state.numScalars().get("L"), 0.0001);
  }

  @Test
  void testHardwareFuncs() {
    // PEEK and USR return 0.0
    String source =
        """
        10 LET P = PEEK(1234)
        20 LET U = USR(5678)
        """;
    EvalState state = runProgram(source);
    assertEquals(0.0, state.numScalars().get("P"));
    assertEquals(0.0, state.numScalars().get("U"));
  }

  @Test
  void testStrFuncs() {
    String source =
        """
        10 LET A$ = CHR$(65)
        20 LET B$ = STR$(123)
        """;
    EvalState state = runProgram(source);
    assertEquals("A", state.strVars().get("A$"));
    assertEquals("123", state.strVars().get("B$"));
  }

  @Test
  void testNumOps() {
    String source =
        """
        10 LET A = 1 + 2 * 3
        20 LET B = (1 + 2) * 3
        30 LET C = 2 ** 3
        40 LET D = 5 / 2
        """;
    EvalState state = runProgram(source);
    assertEquals(7.0, state.numScalars().get("A"));
    assertEquals(9.0, state.numScalars().get("B"));
    assertEquals(8.0, state.numScalars().get("C"));
    assertEquals(2.5, state.numScalars().get("D"));
  }

  @Test
  void testInverseTrigFuncs() {
    String source =
        """
        10 LET A = ASN(1)
        20 LET B = ACS(0)
        30 LET C = ATN(1)
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI / 2, state.numScalars().get("A"), 0.0001);
    assertEquals(Math.PI / 2, state.numScalars().get("B"), 0.0001);
    assertEquals(Math.PI / 4, state.numScalars().get("C"), 0.0001);
  }

  @Test
  void testNullaryFuncs() {
    String source =
        """
        10 LET P = PI
        20 LET R = RND
        30 LET I$ = INKEY$
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI, state.numScalars().get("P"), 0.0001);
    // RND returns value between 0 and 1
    double r = state.numScalars().get("R");
    assertEquals(true, r >= 0.0 && r < 1.0);
    assertEquals("", state.strVars().get("I$"));
  }

  @Test
  void testLogicalOperators() {
    String source =
        """
        10 LET A = 1 AND 1
        20 LET B = 1 AND 0
        30 LET C = 0 OR 1
        40 LET D = 0 OR 0
        50 LET E = NOT 0
        60 LET F = NOT 5
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numScalars().get("A"));
    assertEquals(0.0, state.numScalars().get("B"));
    assertEquals(1.0, state.numScalars().get("C"));
    assertEquals(0.0, state.numScalars().get("D"));
    assertEquals(1.0, state.numScalars().get("E"));
    assertEquals(0.0, state.numScalars().get("F"));
  }

  @Test
  void testNumericComparisons() {
    String source =
        """
        10 LET A = (5 = 5)
        20 LET B = (5 <> 3)
        30 LET C = (3 < 5)
        40 LET D = (5 > 3)
        50 LET E = (3 <= 3)
        60 LET F = (5 >= 5)
        70 LET G = (3 > 5)
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numScalars().get("A"));
    assertEquals(1.0, state.numScalars().get("B"));
    assertEquals(1.0, state.numScalars().get("C"));
    assertEquals(1.0, state.numScalars().get("D"));
    assertEquals(1.0, state.numScalars().get("E"));
    assertEquals(1.0, state.numScalars().get("F"));
    assertEquals(0.0, state.numScalars().get("G"));
  }

  @Test
  void testStringComparisons() {
    String source =
        """
        10 LET A = ("ABC" = "ABC")
        20 LET B = ("ABC" <> "DEF")
        30 LET C = ("ABC" < "DEF")
        40 LET D = ("DEF" > "ABC")
        50 LET E = ("ABC" <= "ABC")
        60 LET F = ("ABC" >= "ABC")
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numScalars().get("A"));
    assertEquals(1.0, state.numScalars().get("B"));
    assertEquals(1.0, state.numScalars().get("C"));
    assertEquals(1.0, state.numScalars().get("D"));
    assertEquals(1.0, state.numScalars().get("E"));
    assertEquals(1.0, state.numScalars().get("F"));
  }

  @Test
  void testUnaryMinusWithPower() {
    // Standard precedence: ** binds tighter than unary minus
    // So -2**2 = -(2**2) = -4 (not (-2)**2 = 4 as in some BASIC dialects)
    String source =
        """
        10 LET A = -2**2
        20 LET B = (-2)**2
        30 LET C = 0-2**2
        """;
    EvalState state = runProgram(source);
    assertEquals(-4.0, state.numScalars().get("A")); // -(2**2) = -4
    assertEquals(4.0, state.numScalars().get("B")); // (-2)**2 = 4
    assertEquals(-4.0, state.numScalars().get("C")); // 0-(2**2) = -4
  }

  @Test
  void testCaseInsensitiveVariables() {
    // Variable names should be case-insensitive
    String source =
        """
        10 LET myVar = 10
        20 LET MYVAR = MYVAR + 5
        30 LET MyVar = myvar * 2
        """;
    EvalState state = runProgram(source);
    // All refer to same variable, stored as uppercase
    assertEquals(30.0, state.numScalars().get("MYVAR"));
  }

  @Test
  void testCaseInsensitiveStringVariables() {
    // String variable names should be case-insensitive
    String source =
        """
        10 LET name$ = "Hello"
        20 LET NAME$ = NAME$ + " World"
        """;
    EvalState state = runProgram(source);
    assertEquals("Hello World", state.strVars().get("NAME$"));
  }

  @Test
  void testCaseInsensitiveKeywords() {
    // Keywords should be case-insensitive
    String source =
        """
        10 let a = 1
        20 LET b = 2
        30 Let c = 3
        40 IF a = 1 then let d = 4
        50 if b = 2 THEN LET e = 5
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numScalars().get("A"));
    assertEquals(2.0, state.numScalars().get("B"));
    assertEquals(3.0, state.numScalars().get("C"));
    assertEquals(4.0, state.numScalars().get("D"));
    assertEquals(5.0, state.numScalars().get("E"));
  }

  @Test
  void testCaseSensitiveStringValues() {
    // String VALUES should remain case-sensitive
    String source =
        """
        10 LET a$ = "Hello"
        20 LET b$ = "HELLO"
        30 LET eq = (a$ = b$)
        """;
    EvalState state = runProgram(source);
    assertEquals("Hello", state.strVars().get("A$"));
    assertEquals("HELLO", state.strVars().get("B$"));
    assertEquals(0.0, state.numScalars().get("EQ")); // Not equal - case sensitive
  }
}
