package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.BazLangReplHandler;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramEditor;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForNextProgramTest extends BaseProgramTest {

  @Test
  void testForInMultiStatementLine() {
    // Should print "BEFORE", "1", "2", "3", "AFTER".
    runProgram(
        """
            10 PRINT "BEFORE" : FOR I=1 TO 3 : PRINT I : NEXT I : PRINT "AFTER" : STOP
            """,
        "BEFORE\n1\n2\n3\nAFTER\n");
  }

  @Test
  void testForInsideIfThen() {
    runProgram(
        """
            10 IF 1=1 THEN PRINT "BEFORE" : FOR I=1 TO 3 : PRINT I : NEXT I : PRINT "AFTER" : STOP
            """,
        "BEFORE\n1\n2\n3\nAFTER\n");
  }

  @Test
  void testForLoop() {
    runProgram(
        """
        10 FOR i = 1 TO 3
        20 PRINT i
        30 NEXT i
        """,
        "1\n2\n3\n");
  }

  @Test
  void testForLoopGOSUB() {
    final String source =
        """
        10 FOR M=1 TO 2
        20 GOSUB 30
        30 PRINT "M=";M
        40 NEXT M
        50 RETURN
        """;
    final var ex = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, ex.reportCode());

    final var screen = new MockScreen();
    try {
      final var program = PARSER.parseProgramLines(source);
      final var state = new EvalState();
      final var executor = new ProgramManager(state, screen);
      final var interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
    } catch (ReportException re) {
      // Ignore expected
    }
    assertEquals("M=1\nM=2\nM=3\nM=4\n", screen.getOutput());
  }

  @Test
  void testForSkipVariableRetention() {
    // Documented: Loop variable is initialised but NOT incremented if skipped.
    final var state =
        runProgram(
            """
        10 LET I = 0
        20 FOR I = 10 TO 1
        30 NEXT I
        """);
    assertEquals(10.0, state.numVar("I"));
  }

  @Test
  void testForWithoutNextError() {
    // Check that ReportCode.FOR_WITHOUT_NEXT is thrown when matching NEXT is not found
    final var ex =
        assertThrows(
            ReportException.class,
            () ->
                runProgram(
                    """
        10 FOR I = 10 TO 1
        20 PRINT I
        """));
    assertEquals(ReportCode.FOR_WITHOUT_NEXT, ex.reportCode());
  }

  @Test
  void testImmediateModeForLoop() {
    // REPL statements are executed via immediate mode (label 0).
    final var state = new EvalState();
    final var screen = new MockScreen(List.of());
    final var executor = new ProgramManager(state, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    final var repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("FOR I=1 TO 3 : PRINT I : NEXT I", null);
    assertEquals("1\n2\n3\n", screen.getOutput());
  }

  @Test
  void testOverlappingForLoops() {
    runProgram(
        """
        10 FOR M=1 TO 3
        20 FOR N=1 TO M
        30 PRINT M;N
        40 NEXT M
        50 NEXT N
        """,
        "11\n21\n31\n42\n53\n");
  }

  @Test
  void testForSkipSameLine() {
    runProgram(
        """
        10 FOR I = 2 TO 1 : PRINT "SKIPPED" : NEXT I : PRINT "AFTER"
        """,
        "AFTER\n");
  }

  @Test
  void testForSkipFindsNextInsideIfBody() {
    // On a Sinclair ZX Spectrum, the FOR body-skip scan is a flat, linear pass through all
    // statements in program order, including those nested inside IF...THEN bodies. Verified on a
    // Sinclair ZX Spectrum: `FOR i=1 TO 0 / IF 0 THEN NEXT i / PRINT "A" / NEXT i / PRINT "B"`
    // prints "A" then "B", confirming that the NEXT inside `IF 0 THEN` is found by the skip scan
    // even though the condition is always false.
    runProgram(
        """
        10 FOR I = 1 TO 0
        20 IF 0 THEN NEXT I
        30 PRINT "A"
        40 NEXT I
        50 PRINT "B"
        """,
        "A\nB\n");
  }

  @Test
  void testForSkipFindsNextInsideIfBodyFullTrace() {
    // Full program verified on a Sinclair ZX Spectrum, by `FOR i=1 TO 0`. Expected output: 40, 60,
    // 130, 2, 3, 4 — confirming the skip lands after the `IF i=1 THEN NEXT i` on line 30 (not at
    // the standalone `NEXT i` on line 120).
    runProgram(
        """
        10 FOR I=1 TO 0
        20 GO SUB 100
        30 IF I=1 THEN NEXT I
        40 PRINT 40
        50 IF I=2 THEN NEXT I
        60 PRINT 60
        70 GOTO 120
        80 PRINT 80
        90 STOP
        100 PRINT I
        110 RETURN
        120 NEXT I
        130 PRINT 130
        140 PRINT I
        150 NEXT I
        160 PRINT I
        170 NEXT I
        180 PRINT I
        """,
        "40\n60\n130\n2\n3\n4\n");
  }
}
