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
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForNextProgramTest extends BaseProgramTest {

  @Test
  void testForInMultiStatementLine() {
    // Should print "BEFORE", "1", "2", "3", "AFTER".
    String output =
        runProgramCapture(
            "10 PRINT \"BEFORE\" : FOR I=1 TO 3 : PRINT I : NEXT I : PRINT \"AFTER\" : STOP");
    assertEquals("BEFORE\n1\n2\n3\nAFTER\n", output);
  }

  @Test
  void testForInsideIfThen() {
    String output =
        runProgramCapture(
            "10 IF 1=1 THEN PRINT \"BEFORE\" "
                + ": FOR I=1 TO 3 : PRINT I : NEXT I : PRINT \"AFTER\" : STOP");
    assertEquals("BEFORE\n1\n2\n3\nAFTER\n", output);
  }

  @Test
  void testForLoop() {
    String expected = "1\n2\n3\n";
    runProgram("10 FOR i = 1 TO 3\n20 PRINT i\n30 NEXT i", expected);
  }

  @Test
  void testForLoopGOSUB() {
    String source = "10 FOR M=1 TO 2\n20 GOSUB 30\n30 PRINT \"M=\";M\n40 NEXT M\n50 RETURN";
    ReportException e = assertThrows(ReportException.class, () -> runProgram(source));
    assertEquals(ReportCode.RETURN_WITHOUT_GOSUB, e.reportCode());

    MockDisplay display = new MockDisplay();
    try {
      var program = PARSER.parseProgramLines(source);
      EvalState state = new EvalState();
      ProgramManager executor = new ProgramManager(state, display);
      Interpreter interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
    } catch (ReportException re) {
      // Ignore expected
    }
    String expected = "M=1\nM=2\nM=3\nM=4\n";
    assertEquals(expected, display.getOutput());
  }

  @Test
  void testForSkipVariableRetention() {
    // Documented: Loop variable is initialised but NOT incremented if skipped.
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
  void testImmediateModeForLoop() {
    // REPL statements are executed via immediate mode (label 0).
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay(List.of());
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    ProgramEditor editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    BazLangReplHandler repl = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);

    repl.handleReplInput("FOR I=1 TO 3 : PRINT I : NEXT I", null);
    assertEquals("1\n2\n3\n", display.getOutput());
  }

  @Test
  void testOverlappingForLoops() {
    String expected = "11\n21\n31\n42\n53\n";
    runProgram("10 FOR M=1 TO 3\n20 FOR N=1 TO M\n30 PRINT M;N\n40 NEXT M\n50 NEXT N", expected);
  }
}
