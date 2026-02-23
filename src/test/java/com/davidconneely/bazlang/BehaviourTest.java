package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures that specific documented behaviors are tested. */
class BehaviourTest {
  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState runProgram(String source) {
    return runProgram(source, List.of());
  }

  private EvalState runProgram(String source, List<String> inputs) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();

    MockDisplay display = new MockDisplay(inputs);

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

  private String runProgramCapture(String source) {
    return runProgramCapture(source, List.of());
  }

  private String runProgramCapture(String source, List<String> inputs) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();

    MockDisplay display = new MockDisplay(inputs);

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
  void testForSkipVariableRetention() {
    // Documented: Loop variable is initialized but NOT incremented if skipped.
    EvalState state =
        runProgram(
            """
        10 LET I = 0
        20 FOR I = 10 TO 1
        30 NEXT I
        """);
    assertEquals(10.0, state.numScalars().get("I"));
  }

  @Test
  void testInputToArray() {
    // Test INPUT into an array element.
    EvalState state =
        runProgram(
            """
        10 DIM A(10)
        20 INPUT A(5)
        30 DIM B$(5, 10)
        40 INPUT B$(2)
        """,
            List.of("42", "HELLO"));

    assertEquals(42.0, state.numArrays().get("A").data()[4]); // 1-based index 5 is data[4]
    String b2 = new String(state.charArrays().get("B$").data(), 10, 10);
    assertTrue(b2.startsWith("HELLO"));
  }

  @Test
  void testInputSyntaxErrorRetry() {
    // Test that syntax errors in numeric INPUT re-prompt with "Syntax error? "
    // First input "(1" is a syntax error (unbalanced parens), second input "42" is valid
    String output =
        runProgramCapture(
            """
        10 INPUT X
        20 PRINT X
        """,
            List.of("(1", "42"));

    assertTrue(output.contains("Syntax error?"));
    assertTrue(output.contains("42"));
  }

  @Test
  void testInputUndefinedVariableEndsProgram() {
    // Undefined variable in INPUT should end program with error, not retry
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
            10 INPUT X
            """,
                List.of("NOTDEF")));
  }

  @Test
  void testPowerPrecedence() {
    // -2**2 should be -4
    EvalState state = runProgram("10 LET A = -2**2\n20 LET B = (-2)**2");
    assertEquals(-4.0, state.numScalars().get("A"));
    assertEquals(4.0, state.numScalars().get("B"));
  }

  @Test
  void testScroll() {
    String output = runProgramCapture("10 SCROLL");
    assertEquals(System.lineSeparator(), output);
  }

  @Test
  void testStopStatementBehavior() {
    // STOP should terminate execution cleanly.
    String output =
        runProgramCapture(
            """
        10 PRINT "START"
        20 STOP
        30 PRINT "END"
        """);
    assertTrue(output.contains("START"));
    assertFalse(output.contains("END"));
  }

  @Test
  void testTabPositioning() {
    // TAB moves head to next 16-char zone.
    String output = runProgramCapture("10 PRINT \"A\", \"B\"");
    // "A" is at 0, next tab is at 16. So "B" should be at index 16.
    int aPos = output.indexOf('A');
    int bPos = output.indexOf('B');
    assertEquals(16, bPos - aPos);
  }

  @Test
  void testAtPositioning() {
    // PRINT AT row, col should work.
    String output = runProgramCapture("10 PRINT AT 5, 10; \"X\"");
    assertTrue(output.contains("X"));
  }

  @Test
  void testPlotUnplot() {
    // PLOT and UNPLOT should use Unicode █
    String output = runProgramCapture("10 PLOT 0, 0\n20 UNPLOT 0, 0");
    assertTrue(output.contains("█"));
    assertTrue(output.contains(" "));
  }

  @Test
  void testEmptyLinesAndComments() {
    // Documented: blank lines and # comments are ignored.
    String output =
        runProgramCapture(
            """
        # comment

        10 PRINT "OK"

        # endLabel
        """);
    assertEquals("OK" + System.lineSeparator(), output);
  }

  @Test
  void testCaseInsensitivity() {
    // Documented: Keywords are case-insensitive.
    String output =
        runProgramCapture(
            """
        10 let A = 1
        20 pRiNt A
        """);
    assertEquals("1" + System.lineSeparator(), output);
  }

  @Test
  void testNoMultiStatementLines() {
    // Documented: BazLang DOES NOT support ':'
    assertThrows(ReportException.class, () -> runProgram("10 PRINT 1 : PRINT 2"));
  }

  @Test
  void testAddressBasedNavigationMissingLabel() {
    // Documented: Target label resolves to next highest.
    String output =
        runProgramCapture(
            """
        10 GOTO 15
        20 PRINT "SKIP"
        30 PRINT "TARGET"
        """);
    // GOTO 15 should jump to 20.
    assertEquals("SKIP" + System.lineSeparator() + "TARGET" + System.lineSeparator(), output);
  }

  @Test
  void testZx81AndOperator() {
    // ZX81: A AND B = A if B ≠ 0, 0 if B = 0 (numeric)
    EvalState state =
        runProgram(
            """
        10 LET A = 5 AND 1
        20 LET B = 5 AND 0
        30 LET C = 0 AND 1
        40 LET D = 3.5 AND 2
        """);
    assertEquals(5.0, state.numScalars().get("A")); // 5 AND 1 = 5
    assertEquals(0.0, state.numScalars().get("B")); // 5 AND 0 = 0
    assertEquals(0.0, state.numScalars().get("C")); // 0 AND 1 = 0
    assertEquals(3.5, state.numScalars().get("D")); // 3.5 AND 2 = 3.5
  }

  @Test
  void testZx81AndOperatorWithStrings() {
    // ZX81: str AND n = str if n ≠ 0, "" if n = 0
    String output =
        runProgramCapture(
            """
        10 PRINT "A" AND 1
        20 PRINT "[" + ("A" AND 0) + "]"
        30 PRINT "HELLO" AND 5
        40 PRINT "[" + ("HELLO" AND 0) + "]"
        """);
    String[] lines = output.split(System.lineSeparator());
    assertEquals("A", lines[0]); // "A" AND 1 = "A"
    assertEquals("[]", lines[1]); // "A" AND 0 = "" (wrapped in brackets)
    assertEquals("HELLO", lines[2]); // "HELLO" AND 5 = "HELLO"
    assertEquals("[]", lines[3]); // "HELLO" AND 0 = "" (wrapped in brackets)
  }

  @Test
  void testZx81OrOperator() {
    // ZX81: A OR B = 1 if B ≠ 0, A if B = 0
    EvalState state =
        runProgram(
            """
        10 LET A = 5 OR 1
        20 LET B = 5 OR 0
        30 LET C = 0 OR 1
        40 LET D = 3.5 OR 0
        """);
    assertEquals(1.0, state.numScalars().get("A")); // 5 OR 1 = 1
    assertEquals(5.0, state.numScalars().get("B")); // 5 OR 0 = 5
    assertEquals(1.0, state.numScalars().get("C")); // 0 OR 1 = 1
    assertEquals(3.5, state.numScalars().get("D")); // 3.5 OR 0 = 3.5
  }

  @Test
  void testValEvaluatesExpression() {
    // ZX81: VAL evaluates a string as a numeric expression
    String output =
        runProgramCapture(
            """
        10 PRINT VAL "6-4"
        20 PRINT VAL "2*3+1"
        30 PRINT VAL (STR$ LEN "123456" + "-4")
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("2", lines[0]); // 6-4 = 2
    assertEquals("7", lines[1]); // 2*3+1 = 7
    assertEquals("2", lines[2]); // STR$ 6 + "-4" = "6-4" -> 6-4 = 2
  }

  @Test
  void testLetKeywordMandatory() {
    // ZX81: LET cannot be omitted
    assertThrows(ReportException.class, () -> runProgram("10 A = 5"));
  }

  @Test
  void testZx81NumberFormatting() {
    // ZX81 rules:
    // - 0 prints as "0"
    // - Integers print without decimal point
    // - Scientific notation for |x| < 10^-5 or |x| >= 10^13
    // - Up to 8 significant digits, no trailing zeros
    // - Leading zero dropped for |x| < 0.1 (e.g., 0.03 -> .03)
    String output =
        runProgramCapture(
            """
        10 PRINT 0
        20 PRINT 1
        30 PRINT 42
        40 PRINT -7
        50 PRINT 3.14159
        60 PRINT 0.5
        70 PRINT 0.03
        80 PRINT -0.03
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("0", lines[0]);
    assertEquals("1", lines[1]);
    assertEquals("42", lines[2]);
    assertEquals("-7", lines[3]);
    assertEquals("3.14159", lines[4]);
    assertEquals("0.5", lines[5]);
    assertEquals("0.03", lines[6]);
    assertEquals("-0.03", lines[7]);
  }

  @Test
  void testScientificNotation() {
    // Very large/small numbers use scientific notation
    String output =
        runProgramCapture(
            """
        10 PRINT 1E13
        20 PRINT 1E-6
        30 PRINT 1.23E15
        40 PRINT -5E14
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("1E+13", lines[0]);
    assertEquals("1E-6", lines[1]);
    assertEquals("1.23E+15", lines[2]);
    assertEquals("-5E+14", lines[3]);
  }

  @Test
  void testStrDollarUsesZx81Formatting() {
    // STR$ should use the same formatting as PRINT
    String output =
        runProgramCapture(
            """
        10 PRINT STR$ 0
        20 PRINT STR$ 42
        30 PRINT STR$ 3.14159
        40 PRINT STR$ (-7)
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("0", lines[0]);
    assertEquals("42", lines[1]);
    assertEquals("3.14159", lines[2]);
    assertEquals("-7", lines[3]);
  }

  @Test
  void testNoTrailingZeros() {
    // Numbers should not have trailing zeros after decimal point
    String output =
        runProgramCapture(
            """
        10 PRINT 1.5
        20 PRINT 2.25
        30 PRINT 10.0
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("1.5", lines[0]);
    assertEquals("2.25", lines[1]);
    assertEquals("10", lines[2]); // Integer, no decimal point
  }
}
