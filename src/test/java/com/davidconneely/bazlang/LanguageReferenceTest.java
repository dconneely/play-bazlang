package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests covering the language features described in docs/language_features.md. */
class LanguageReferenceTest {
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

  private MachineState runProgram(String source) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();
    MachineState state = new MachineState();
    Terminal terminal = new Terminal(false); // Mock terminal (calls System.out)
    Evaluator evaluator = new Evaluator(state, terminal);
    Executor executor = new Executor(state, evaluator, terminal);
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
  void testNumericFunctions() {
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
    MachineState state = runProgram(source);
    assertEquals(5.0, state.numericScalars().get("A"));
    assertEquals(3.0, state.numericScalars().get("B"));
    assertEquals(-1.0, state.numericScalars().get("C"));
    assertEquals(4.0, state.numericScalars().get("D"));
    assertEquals(5.0, state.numericScalars().get("E"));
    assertEquals(123.0, state.numericScalars().get("F"));
    assertEquals(65.0, state.numericScalars().get("G"));
  }

  @Test
  void testTrigFunctions() {
    // Basic check they run and return somewhat sane values
    String source =
        """
        10 LET S = SIN(0)
        20 LET C = COS(0)
        30 LET T = TAN(0)
        """;
    MachineState state = runProgram(source);
    assertEquals(0.0, state.numericScalars().get("S"), 0.0001);
    assertEquals(1.0, state.numericScalars().get("C"), 0.0001);
    assertEquals(0.0, state.numericScalars().get("T"), 0.0001);
  }

  @Test
  void testExpLogFunctions() {
    String source =
        """
        10 LET E = EXP(1)
        20 LET L = LN(E)
        """;
    MachineState state = runProgram(source);
    assertEquals(Math.E, state.numericScalars().get("E"), 0.0001);
    assertEquals(1.0, state.numericScalars().get("L"), 0.0001);
  }

  @Test
  void testHardwareFunctions() {
    // PEEK and USR return 0.0
    String source =
        """
        10 LET P = PEEK(1234)
        20 LET U = USR(5678)
        """;
    MachineState state = runProgram(source);
    assertEquals(0.0, state.numericScalars().get("P"));
    assertEquals(0.0, state.numericScalars().get("U"));
  }

  @Test
  void testStringFunctions() {
    String source =
        """
        10 LET A$ = CHR$(65)
        20 LET B$ = STR$(123)
        """;
    MachineState state = runProgram(source);
    assertEquals("A", state.variableLengthStrings().get("A$"));
    assertEquals("123", state.variableLengthStrings().get("B$"));
  }

  @Test
  void testOperators() {
    String source =
        """
        10 LET A = 1 + 2 * 3
        20 LET B = (1 + 2) * 3
        30 LET C = 2 ** 3
        40 LET D = 5 / 2
        """;
    MachineState state = runProgram(source);
    assertEquals(7.0, state.numericScalars().get("A"));
    assertEquals(9.0, state.numericScalars().get("B"));
    assertEquals(8.0, state.numericScalars().get("C"));
    assertEquals(2.5, state.numericScalars().get("D"));
  }
}
