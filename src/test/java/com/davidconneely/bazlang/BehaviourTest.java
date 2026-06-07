package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures that specific documented behaviors are tested. */
class BehaviourTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private EvalState runProgram(String source) {
    return runProgram(source, List.of());
  }

  private EvalState runProgram(String source, List<String> inputs) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();

    MockDisplay display = new MockDisplay(inputs);

    ProgramManager executor = new ProgramManager(state, display);
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

    ProgramManager executor = new ProgramManager(state, display);
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
    assertEquals(10.0, state.numVar("I"));
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

    assertEquals(42.0, state.numArray("A").data()[4]); // 1-based index 5 is data[4]
    String b2 = ((EvalState.StrVar.Array) state.strVar("B$")).elements()[1].toJavaString();
    assertTrue(b2.startsWith("HELLO"));
  }

  @Test
  void testInputSyntaxErrorRetry() {
    // Test that syntax errors in numeric INPUT re-prompt with error message
    // First input "(1" is a syntax error (unbalanced parens), second input "42" is valid
    String output =
        runProgramCapture(
            """
        10 INPUT X
        20 PRINT X
        """,
            List.of("(1", "42"));

    assertTrue(output.contains("Syntax error in expression"));
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
    assertEquals(-4.0, state.numVar("A"));
    assertEquals(4.0, state.numVar("B"));
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
  void testMultiStatementLines() {
    String output = runProgramCapture("10 PRINT 1 : PRINT 2");
    assertEquals("1\n2\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testRemConsumesRestOfLine() {
    String output = runProgramCapture("10 REM PRINT 1 : PRINT 2");
    assertEquals("", output);
  }

  @Test
  void testIfThenConsumesRestOfLine() {
    String output = runProgramCapture("10 IF 0 = 1 THEN PRINT 1 : PRINT 2");
    assertEquals("", output);

    output = runProgramCapture("10 IF 1 = 1 THEN PRINT 1 : PRINT 2");
    assertEquals("1\n2\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testGosubInMultiStatementLine() {
    // Should print "BEFORE", "SUBROUTINE", "AFTER".
    String output =
        runProgramCapture(
            """
        10 PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN : PRINT "NOT"
        """);
    assertEquals("BEFORE\nSUBROUTINE\nAFTER\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testGosubInsideIfThen() {
    String output =
        runProgramCapture(
            """
        10 IF 1=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        100 PRINT "SUBROUTINE" : RETURN
        """);
    assertEquals("BEFORE\nSUBROUTINE\nAFTER\n", output.replace(System.lineSeparator(), "\n"));

    String skippedOutput =
        runProgramCapture(
            """
        10 IF 0=1 THEN PRINT "BEFORE" : GOSUB 100 : PRINT "AFTER" : STOP
        20 STOP
        100 PRINT "NOT_RUN" : RETURN
        """);
    assertEquals("", skippedOutput);
  }

  @Test
  void testForInsideIfThen() {
    String output =
        runProgramCapture(
            "10 IF 1=1 THEN PRINT \"BEFORE\" "
                + ": FOR I=1 TO 3 : PRINT I : NEXT I : PRINT \"AFTER\" : STOP");
    assertEquals("BEFORE\n1\n2\n3\nAFTER\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testForInMultiStatementLine() {
    // Should print "BEFORE", "1", "2", "3", "AFTER".
    String output =
        runProgramCapture(
            "10 PRINT \"BEFORE\" : FOR I=1 TO 3 : PRINT I : NEXT I : PRINT \"AFTER\" : STOP");
    assertEquals("BEFORE\n1\n2\n3\nAFTER\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testContInMultiStatementLine() {
    // Should print "BEFORE", then if the user enters CONT, should print "AFTER".
    Map<Integer, ProgramLine> program =
        PARSER.parseProgramLines("10 PRINT \"BEFORE\" : STOP : PRINT \"AFTER\"");
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_STATEMENT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // Simulate REPL running CONT
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("BEFORE\nAFTER\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testImmediateModeForLoop() {
    // REPL statements are executed via immediate mode (label 0).
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("FOR I=1 TO 3 : PRINT I : NEXT I", null);
    assertEquals("1\n2\n3\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testImmediateModeRun() {
    // Tests that RUN executed from REPL properly runs a stored program without infinite loop
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"HELLO\"", display);
    repl.handleReplInput("RUN", display);

    assertEquals(
        "10 PRINT \"HELLO\"\nHELLO\n", display.getOutput().replace(System.lineSeparator(), "\n"));
    assertFalse(state.isRunning()); // Should stop gracefully
  }

  @Test
  void testImmediateModeList() {
    // Tests that LIST executed from REPL doesn't echo itself as line 0
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"HELLO\"", display);
    repl.handleReplInput("LIST", display);

    // The output should just be the line 10 being echoed, then the list showing just line 10.
    assertEquals(
        "10 PRINT \"HELLO\"\n10 PRINT \"HELLO\"\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testImmediateModeGosub() {
    // Tests that an immediate GOSUB to a subroutine containing a RETURN gracefully returns to the
    // REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"SUB\" : RETURN", display);
    repl.handleReplInput("GOSUB 10", display);

    // The output should be the subroutine's line being echoed, then the subroutine executing,
    // and then returning to the REPL cleanly without throwing "RETURN without GOSUB".
    assertEquals(
        "10 PRINT \"SUB\" : RETURN\nSUB\n",
        display.getOutput().replace(System.lineSeparator(), "\n"));
    assertFalse(state.isRunning());
  }

  @Test
  void testImmediateModeStopExitsRepl() {
    // Tests that STOP executed from REPL as an immediate statement returns false to exit the REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    boolean continueRepl = repl.handleReplInput("STOP", display);

    assertFalse(continueRepl, "Immediate STOP should return false to exit the REPL");
    assertEquals("9 STOP statement, 0:1", display.getStatus());
  }

  @Test
  void testStoredStopContinuesRepl() {
    // Tests that STOP executed inside a program returns true to continue the REPL
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("10 PRINT \"A\"", display);
    repl.handleReplInput("20 STOP", display);
    boolean continueRepl = repl.handleReplInput("RUN", display);

    assertTrue(continueRepl, "Stored STOP should return true to continue the REPL");
    assertEquals("9 STOP statement, 20:1", display.getStatus());
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
    assertEquals(5.0, state.numVar("A")); // 5 AND 1 = 5
    assertEquals(0.0, state.numVar("B")); // 5 AND 0 = 0
    assertEquals(0.0, state.numVar("C")); // 0 AND 1 = 0
    assertEquals(3.5, state.numVar("D")); // 3.5 AND 2 = 3.5
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
    assertEquals(1.0, state.numVar("A")); // 5 OR 1 = 1
    assertEquals(5.0, state.numVar("B")); // 5 OR 0 = 5
    assertEquals(1.0, state.numVar("C")); // 0 OR 1 = 1
    assertEquals(3.5, state.numVar("D")); // 3.5 OR 0 = 3.5
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
