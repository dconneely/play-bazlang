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

  @Test
  void testPrintApostropheSeparator() {
    // Apostrophe separator moves print position down a line,
    // and suppresses default trailing newline if trailing.
    String output1 =
        runProgramCapture(
            """
        10 PRINT "A" ' "B"
        """);
    String[] lines1 = output1.split(System.lineSeparator());
    assertEquals("A", lines1[0]);
    assertEquals("B", lines1[1]);

    String output2 =
        runProgramCapture(
            """
        10 PRINT "A" ' ' "B"
        """);
    String[] lines2 = output2.split(System.lineSeparator());
    assertEquals("A", lines2[0]);
    assertEquals("", lines2[1]); // Consecutive apostrophe prints a blank line
    assertEquals("B", lines2[2]);

    // Test consecutive apostrophes without spaces ('' and ''')
    String output2b =
        runProgramCapture(
            """
        10 PRINT "A" '' "B"
        """);
    String[] lines2b = output2b.split(System.lineSeparator());
    assertEquals("A", lines2b[0]);
    assertEquals("", lines2b[1]);
    assertEquals("B", lines2b[2]);

    String output2c =
        runProgramCapture(
            """
        10 PRINT "A" ''' "B"
        """);
    String[] lines2c = output2c.split(System.lineSeparator());
    assertEquals("A", lines2c[0]);
    assertEquals("", lines2c[1]);
    assertEquals("", lines2c[2]);
    assertEquals("B", lines2c[3]);

    String output3 =
        runProgramCapture(
            """
        10 PRINT "A" '
        20 PRINT "B"
        """);
    String[] lines3 = output3.split(System.lineSeparator());
    assertEquals("A", lines3[0]);
    assertEquals("B", lines3[1]); // Trailing apostrophe suppresses automatic newline

    String output4 =
        runProgramCapture(
            """
        10 PRINT ' "A"
        """);
    String[] lines4 = output4.split(System.lineSeparator());
    assertEquals("", lines4[0]); // Leading apostrophe prints a blank line
    assertEquals("A", lines4[1]);

    // Test LPRINT behavior with apostrophe separators
    String outputL1 =
        runProgramCapture(
            """
        10 LPRINT "A" '' "B"
        """);
    String[] linesL1 = outputL1.split(System.lineSeparator());
    assertEquals("A", linesL1[0]);
    assertEquals("", linesL1[1]);
    assertEquals("B", linesL1[2]);

    // Test case: 10 PRINT "first", 20 PRINT ' "second" -> single blank line
    String output5 =
        runProgramCapture(
            """
        10 PRINT "first"
        20 PRINT ' "second"
        """);
    String[] lines5 = output5.split(System.lineSeparator());
    assertEquals("first", lines5[0]);
    assertEquals("", lines5[1]); // Blank line
    assertEquals("second", lines5[2]);

    // Test case: 10 PRINT "first" '', 20 PRINT "second" -> single blank line
    String output6 =
        runProgramCapture(
            """
        10 PRINT "first" ''
        20 PRINT "second"
        """);
    String[] lines6 = output6.split(System.lineSeparator());
    assertEquals("first", lines6[0]);
    assertEquals("", lines6[1]); // Blank line
    assertEquals("second", lines6[2]);
  }

  @Test
  void testForWithoutNextError() {
    // Check that ReportCode.FOR_WITHOUT_NEXT is thrown when matching NEXT is not found
    try {
      runProgramCapture(
          """
          10 FOR I = 10 TO 1
          20 PRINT I
          """);
      org.junit.jupiter.api.Assertions.fail("Expected ReportException");
    } catch (ReportException e) {
      assertEquals(ReportCode.FOR_WITHOUT_NEXT, e.reportCode());
    }
  }

  @Test
  void testStatementLostReturn() {
    Map<Integer, ProgramLine> program = new java.util.HashMap<>();
    program.put(10, new ProgramLine(10, "GO SUB 30"));
    program.put(20, new ProgramLine(20, "STOP"));
    program.put(30, new ProgramLine(30, "STOP"));
    program.put(40, new ProgramLine(40, "RETURN"));

    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    // Run GOSUB 30 -> stops at line 30 STOP
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_STATEMENT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // Delete line 10 (the GOSUB caller) while stopped at line 30
    state.program().remove(10);

    // CONTINUE -> resumes from line 30, falls through to line 40, executes RETURN,
    // which pops line 10 and tries to jump there. Since line 10 is deleted,
    // it should throw STATEMENT_LOST!
    try {
      executor.visitContStmt(null);
      interpreter.resume();
      org.junit.jupiter.api.Assertions.fail("Expected STATEMENT_LOST");
    } catch (ReportException e) {
      assertEquals(ReportCode.STATEMENT_LOST, e.reportCode());
    }
  }

  @Test
  void testStopInInputAndCont() {
    Map<Integer, ProgramLine> program = new java.util.HashMap<>();
    program.put(10, new ProgramLine(10, "INPUT A"));
    program.put(20, new ProgramLine(20, "PRINT A"));

    EvalState state = new EvalState();
    // Provide "STOP" first, then "42" when we continue
    MockDisplay display = new MockDisplay(List.of("STOP", "42"));
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.STOP_IN_INPUT, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // CONTINUE -> should repeat the INPUT statement
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("STOP\n42\n42\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testBreakIntoProgramAndCont() {
    Map<Integer, ProgramLine> program = new java.util.HashMap<>();
    program.put(10, new ProgramLine(10, "PRINT \"A\""));
    program.put(20, new ProgramLine(20, "PRINT \"B\""));
    program.put(30, new ProgramLine(30, "PRINT \"C\""));

    EvalState state = new EvalState();
    MockDisplay display =
        new MockDisplay(List.of()) {
          @Override
          public void print(String text) {
            super.print(text);
            if (text.equals("A")) {
              triggerBreak();
            }
          }
        };
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);

    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      assertEquals(ReportCode.BREAK_INTO_PROGRAM, e.reportCode());
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
    }

    // CONTINUE -> should resume at line 20 (does not repeat line 10)
    executor.visitContStmt(null);
    interpreter.resume();

    assertEquals("A\nB\nC\n", display.getOutput().replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testDataReadBasic() {
    EvalState state =
        runProgram(
            """
        10 DATA 10, "HELLO", 20, BIN 1010
        20 READ A, B$, C, D
        """);
    assertEquals(10.0, state.numVar("A"));
    assertEquals("HELLO", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    assertEquals(20.0, state.numVar("C"));
    assertEquals(10.0, state.numVar("D"));
  }

  @Test
  void testDataReadExpressions() {
    EvalState state =
        runProgram(
            """
        10 DATA X + 5, A$ + "WORLD"
        20 LET X = 10
        30 LET A$ = "HELLO "
        40 READ Y, B$
        """);
    assertEquals(15.0, state.numVar("Y"));
    assertEquals(
        "HELLO WORLD", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
  }

  @Test
  void testDataReadOutOfData() {
    ReportException ex =
        assertThrows(
            ReportException.class,
            () -> {
              runProgram(
                  """
          10 DATA 42
          20 READ A, B
          """);
            });
    assertEquals(ReportCode.OUT_OF_DATA, ex.reportCode());
  }

  @Test
  void testDataReadRestore() {
    EvalState state =
        runProgram(
            """
        10 DATA 1, 2
        20 DATA 3, 4
        30 READ A, B
        40 RESTORE 20
        50 READ C, D
        60 RESTORE
        70 READ E
        """);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(2.0, state.numVar("B"));
    assertEquals(3.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
  }

  @Test
  void testDataReadSpectrumExample() {
    String output =
        runProgramCapture(
            """
        10 LET A$="ABC"
        20 DATA A$, "DEF"
        30 READ X$, Y$
        40 PRINT X$, Y$
        """);
    assertEquals("ABC             DEF\n", output.replace(System.lineSeparator(), "\n"));
  }

  @Test
  void testDataReadTypeMismatch() {
    ReportException ex1 =
        assertThrows(
            ReportException.class,
            () -> {
              runProgram(
                  """
          10 DATA "HELLO"
          20 READ A
          """);
            });
    assertEquals(ReportCode.NONSENSE_IN_BASIC, ex1.reportCode());

    ReportException ex2 =
        assertThrows(
            ReportException.class,
            () -> {
              runProgram(
                  """
          10 DATA 42
          20 READ A$
          """);
            });
    assertEquals(ReportCode.NONSENSE_IN_BASIC, ex2.reportCode());
  }
}
