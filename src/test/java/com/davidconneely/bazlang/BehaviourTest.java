package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ensures that specific documented behaviors are tested. */
class BehaviourTest {
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
    return runProgram(source, List.of());
  }

  private MachineState runProgram(String source, List<String> inputs) {
    Lexer lexer = new Lexer(source);
    List<Token> tokens = lexer.tokenize();
    Parser parser = new Parser(tokens);
    Map<Integer, Statement> program = parser.parseProgram();
    MachineState state = new MachineState();

    Terminal terminal = new MockTerminal(inputs);

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
  void testForSkipVariableRetention() {
    // Documented: Loop variable is initialized but NOT incremented if skipped.
    MachineState state =
        runProgram(
            """
        10 LET I = 0
        20 FOR I = 10 TO 1
        30 NEXT I
        """);
    assertEquals(10.0, state.numericScalars().get("I"));
  }

  @Test
  void testInputToArray() {
    // Test INPUT into an array element.
    MachineState state =
        runProgram(
            """
        10 DIM A(10)
        20 INPUT A(5)
        30 DIM B$(5, 10)
        40 INPUT B$(2)
        """,
            List.of("42", "HELLO"));

    assertEquals(42.0, state.numericArrays().get("A").data()[4]); // 1-based index 5 is data[4]
    String b2 = new String(state.characterArrays().get("B$").data(), 10, 10);
    assertTrue(b2.startsWith("HELLO"));
  }

  @Test
  void testPowerPrecedence() {
    // -2**2 should be -4
    MachineState state = runProgram("10 LET A = -2**2\n20 LET B = (-2)**2");
    assertEquals(-4.0, state.numericScalars().get("A"));
    assertEquals(4.0, state.numericScalars().get("B"));
  }

  private static class MockTerminal extends Terminal {
    private final List<String> inputs;
    private int inputIdx = 0;

    public MockTerminal(List<String> inputs) {
      super(false);
      this.inputs = inputs;
    }

    @Override
    public String readln(String prompt) {
      if (inputIdx < inputs.size()) {
        String input = inputs.get(inputIdx++);
        // Don't println here, strict testing of state
        return input;
      }
      return "";
    }

    @Override
    public boolean isTerminal() {
      return false;
    }
  }

  @Test
  void testScroll() {
    runProgram("10 SCROLL");
    assertEquals(System.lineSeparator(), outContent.toString());
  }

  @Test
  void testStopStatementBehavior() {
    // STOP should terminate execution cleanly.
    runProgram(
        """
        10 PRINT "START"
        20 STOP
        30 PRINT "END"
        """);
    String output = outContent.toString();
    assertTrue(output.contains("START"));
    assertFalse(output.contains("END"));
  }

  @Test
  void testTabPositioning() {
    // TAB moves head to next 16-char zone.
    runProgram("10 PRINT \"A\", \"B\"");
    String output = outContent.toString();
    // "A" is at 0, next tab is at 16. So "B" should be at index 16.
    int aPos = output.indexOf("A");
    int bPos = output.indexOf("B");
    assertEquals(16, bPos - aPos);
  }

  @Test
  void testAtPositioning() {
    // PRINT AT row, col should work.
    runProgram("10 PRINT AT 5, 10; \"X\"");
    assertTrue(outContent.toString().contains("X"));
  }

  @Test
  void testPlotUnplot() {
    // PLOT and UNPLOT should use Unicode █
    runProgram("10 PLOT 0, 0\n20 UNPLOT 0, 0");
    String output = outContent.toString();
    assertTrue(output.contains("█"));
    assertTrue(output.contains(" "));
  }

  @Test
  void testEmptyLinesAndComments() {
    // Documented: blank lines and # comments are ignored.
    runProgram(
        """
        # comment

        10 PRINT "OK"

        # end
        """);
    assertEquals("OK" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testCaseInsensitivity() {
    // Documented: Keywords are case-insensitive.
    runProgram(
        """
        10 let A = 1
        20 pRiNt A
        """);
    assertEquals("1" + System.lineSeparator(), outContent.toString());
  }

  @Test
  void testNoMultiStatementLines() {
    // Documented: BazLang DOES NOT support ':'
    assertThrows(ReportException.class, () -> runProgram("10 PRINT 1 : PRINT 2"));
  }

  @Test
  void testAddressBasedNavigationMissingLabel() {
    // Documented: Target label resolves to next highest.
    runProgram(
        """
        10 GOTO 15
        20 PRINT "SKIP"
        30 PRINT "TARGET"
        """);
    // GOTO 15 should jump to 20.
    assertEquals(
        "SKIP" + System.lineSeparator() + "TARGET" + System.lineSeparator(), outContent.toString());
  }

  private void assertThrows(Class<? extends Throwable> exceptionClass, Runnable runnable) {
    try {
      runnable.run();
      org.junit.jupiter.api.Assertions.fail("Expected " + exceptionClass.getSimpleName());
    } catch (Throwable t) {
      if (!exceptionClass.isInstance(t)) {
        throw t;
      }
    }
  }
}
