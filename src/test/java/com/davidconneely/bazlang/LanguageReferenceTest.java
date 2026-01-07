package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests covering the language features described in docs/language_features.md. */
class LanguageReferenceTest {

  private EvalState runProgram(String source) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();
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
}
