package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.NumExpr;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.io.MockScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Component tests for {@link AstStatementExecutor}, covering every statement kind (Phase 2
 * sub-phases 2a, 2b, and 2c), per {@code localonly-plan-CUSTOM-AST.md}.
 */
class AstStatementExecutorTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState state;
  private MockScreen screen;
  private AstExpressionEvaluator evaluator;
  private ProgramStorage storage;
  private AstStatementExecutor executor;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    state.setCurrentLineLabel(10);
    screen = new MockScreen();
    evaluator = new AstExpressionEvaluator(state, screen, screen, PARSER);
    storage = new ProgramStorage(state, PARSER);
    executor = new AstStatementExecutor(state, screen, screen, evaluator, storage, new TreeMap<>());
  }

  private Stmt firstStmt(String source) {
    return AstLowering.lowerStatements(PARSER.parseStatementsContext(source), 10).getFirst();
  }

  private void exec(String source) {
    for (var stmt : AstLowering.lowerStatements(PARSER.parseStatementsContext(source), 10)) {
      executor.execute(stmt);
    }
  }

  /**
   * Parses a multi-line program (e.g. {@code "10 LET X=1\n20 GOTO 10\n"}) into an AST-flavoured
   * program map, exactly as {@code AstStatementExecutor} expects it.
   */
  private NavigableMap<Integer, List<Stmt>> buildProgram(String source) {
    final var oldLines = PARSER.parseProgramLines(source);
    final NavigableMap<Integer, List<Stmt>> program = new TreeMap<>();
    for (final var entry : oldLines.entrySet()) {
      final int lineNum = entry.getKey();
      program.put(
          lineNum,
          AstLowering.lowerStatements(
              PARSER.parseStatementsContext(entry.getValue().sourceText()), lineNum));
    }
    return program;
  }

  /**
   * A minimal driver loop mirroring {@code Interpreter.resume()}'s statement-address bookkeeping,
   * scoped to what sub-phase 2a needs (no break-polling, no execution listener).
   */
  private void run(NavigableMap<Integer, List<Stmt>> program, AstStatementExecutor exec) {
    state.setPendingJumpLocation(program.firstKey(), 1);
    state.setRunning(true);
    while (state.isRunning()) {
      Integer nextLabel;
      int startIndex = 1;
      if (state.hasPendingJump()) {
        nextLabel = state.pendingJumpLabel();
        startIndex = state.pendingJumpStatementIndex();
        state.clearPendingJump();
      } else {
        nextLabel = program.higherKey(state.currentLineLabel());
      }
      if (nextLabel == null) {
        state.setRunning(false);
        break;
      }
      final var stmts = program.get(nextLabel);
      state.setCurrentLineLabel(nextLabel);
      int index = 1;
      for (final var stmt : stmts) {
        if (index >= startIndex) {
          state.setCurrentStatementIndex(index);
          exec.execute(stmt);
          if (state.hasPendingJump() || !state.isRunning()) {
            break;
          }
        }
        index++;
      }
    }
  }

  @Nested
  class ClearAndNew {
    @Test
    void clearResetsVariables() {
      state.setNumVar("X", 5.0);
      executor.execute(firstStmt("CLEAR"));
      assertFalse(state.hasNumVar("X"));
    }

    @Test
    void newClearsVariablesAndProgram() {
      final var program = buildProgram("10 LET X=1\n");
      final var exec2 =
          new AstStatementExecutor(state, screen, screen, evaluator, storage, program);
      state.setNumVar("X", 5.0);
      exec2.execute(firstStmt("NEW"));
      assertFalse(state.hasNumVar("X"));
      assertTrue(program.isEmpty());
    }
  }

  @Nested
  class LetAndDim {
    @Test
    void letNumericScalar() {
      exec("LET X=42");
      assertEquals(42.0, state.numVar("X"));
    }

    @Test
    void letNumericArray() {
      executor.execute(firstStmt("DIM A(3)"));
      exec("LET A(2)=99");
      assertEquals(99.0, state.numArray("A").data()[1]);
    }

    @Test
    void letStringScalar() {
      exec("LET A$=\"HELLO\"");
      assertEquals(
          BStr.fromJavaString("HELLO"), ((EvalState.StrVar.Scalar) state.strVar("A$")).value());
    }

    @Test
    void letStringSlice() {
      exec("LET A$=\"HELLO\"");
      exec("LET A$(2 TO 4)=\"XYZ\"");
      assertEquals(
          BStr.fromJavaString("HXYZO"), ((EvalState.StrVar.Scalar) state.strVar("A$")).value());
    }

    @Test
    void dimNumericArrayDefaultsToZero() {
      exec("DIM A(3)");
      assertArrayEquals(new double[] {0, 0, 0}, state.numArray("A").data());
    }

    @Test
    void dimStringArraySpacePadded() {
      exec("DIM A$(2, 5)");
      final var arr = (EvalState.StrVar.Array) state.strVar("A$");
      assertEquals(5, arr.stringLength());
      assertEquals((byte) 32, arr.data()[0]);
    }

    @Test
    void dimNegativeSizeThrows() {
      assertThrows(ReportException.class, () -> exec("DIM A(0)"));
    }
  }

  @Nested
  class ForNext {
    @Test
    void loopsCorrectly() {
      final var program = buildProgram("10 LET S=0\n20 FOR I=1 TO 3\n30 LET S=S+I\n40 NEXT I\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(6.0, state.numVar("S"));
      assertEquals(4.0, state.numVar("I")); // loop var ends one step past the limit
    }

    @Test
    void skipsBodyWhenStartAfterEndWithPositiveStep() {
      final var program =
          buildProgram("10 LET S=0\n20 FOR I=5 TO 1\n30 LET S=S+1\n40 NEXT I\n50 LET DONE=1\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(0.0, state.numVar("S")); // body never executed
      assertEquals(1.0, state.numVar("DONE"));
    }

    @Test
    void nextWithoutForThrows() {
      assertThrows(ReportException.class, () -> exec("NEXT I"));
    }
  }

  @Nested
  class GotoGosubReturn {
    @Test
    void gotoJumpsToLine() {
      final var program = buildProgram("10 LET X=1\n20 GOTO 40\n30 LET X=99\n40 LET Y=2\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(1.0, state.numVar("X")); // line 30 never ran
      assertEquals(2.0, state.numVar("Y"));
    }

    @Test
    void gosubAndReturn() {
      final var program =
          buildProgram("10 GOSUB 100\n20 LET DONE=1\n30 STOP\n100 LET X=5\n110 RETURN\n");
      final var exec2 =
          new AstStatementExecutor(state, screen, screen, evaluator, storage, program);
      assertThrows(ReportException.class, () -> run(program, exec2)); // STOP throws
      assertEquals(5.0, state.numVar("X"));
      assertEquals(1.0, state.numVar("DONE"));
    }

    @Test
    void returnWithoutGosubThrows() {
      assertThrows(ReportException.class, () -> exec("RETURN"));
    }

    @Test
    void gotoOutOfRangeThrows() {
      assertThrows(ReportException.class, () -> executor.execute(firstStmt("GOTO -1")));
    }
  }

  @Nested
  class If {
    @Test
    void trueConditionFallsThroughToNextFlatStatement() {
      final var flat =
          AstLowering.lowerStatements(PARSER.parseStatementsContext("IF 1 THEN LET A=1"), 10);
      for (final var s : flat) {
        executor.execute(s);
      }
      assertEquals(1.0, state.numVar("A"));
    }

    @Test
    void falseConditionSkipsToNextLine() {
      final var program = buildProgram("10 IF 0 THEN LET A=1\n20 LET B=2\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertFalse(state.hasNumVar("A"));
      assertEquals(2.0, state.numVar("B"));
    }

    @Test
    void falseConditionInImmediateModeSkipsRestOfLine() {
      state.setCurrentLineLabel(0);
      executor.execute(new Stmt.IfStmt(new NumExpr.NumLiteral(0.0), List.of()));
      assertEquals(0, (int) state.pendingJumpLabel());
      assertEquals(Integer.MAX_VALUE, (int) state.pendingJumpStatementIndex());
    }
  }

  @Nested
  class Cont {
    @Test
    void afterStopResumesAtNextStatement() {
      state.setLastReport(new EvalState.ReportState(ReportCode.STOP_STATEMENT, 10, 2));
      executor.execute(firstStmt("CONT"));
      assertEquals(10, (int) state.pendingJumpLabel());
      assertEquals(3, (int) state.pendingJumpStatementIndex());
    }

    @Test
    void withNoPriorStopIsNoOp() {
      executor.execute(firstStmt("CONT"));
      assertFalse(state.hasPendingJump());
    }
  }

  @Nested
  class StopAndRun {
    @Test
    void stopThrowsAndStopsRunning() {
      assertThrows(ReportException.class, () -> executor.execute(firstStmt("STOP")));
      assertFalse(state.isRunning());
    }

    @Test
    void runClearsStateAndJumpsToFirstLine() {
      final var program = buildProgram("10 LET X=1\n");
      final var exec2 =
          new AstStatementExecutor(state, screen, screen, evaluator, storage, program);
      state.setNumVar("X", 99.0);
      exec2.execute(firstStmt("RUN"));
      assertFalse(state.hasNumVar("X")); // cleared
      assertEquals(10, (int) state.pendingJumpLabel());
    }
  }

  @Nested
  class DataReadRestore {
    @Test
    void readConsumesDataInOrder() {
      final var program = buildProgram("10 DATA 1, 2, 3\n20 READ A, B, C\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(1.0, state.numVar("A"));
      assertEquals(2.0, state.numVar("B"));
      assertEquals(3.0, state.numVar("C"));
    }

    @Test
    void readAcrossMultipleDataStatements() {
      final var program = buildProgram("10 DATA 1\n20 DATA 2\n30 READ A, B\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(1.0, state.numVar("A"));
      assertEquals(2.0, state.numVar("B"));
    }

    @Test
    void readPastEndThrowsOutOfData() {
      final var program = buildProgram("10 DATA 1\n20 READ A, B\n");
      final var exec2 =
          new AstStatementExecutor(state, screen, screen, evaluator, storage, program);
      assertThrows(ReportException.class, () -> run(program, exec2));
    }

    @Test
    void restoreResetsDataPointer() {
      final var program = buildProgram("10 DATA 1, 2\n20 READ A\n30 RESTORE\n40 READ B\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(1.0, state.numVar("A"));
      assertEquals(1.0, state.numVar("B")); // restored, re-reads from the start
    }

    @Test
    void readStringData() {
      final var program = buildProgram("10 DATA \"HI\"\n20 READ A$\n");
      run(program, new AstStatementExecutor(state, screen, screen, evaluator, storage, program));
      assertEquals(
          BStr.fromJavaString("HI"), ((EvalState.StrVar.Scalar) state.strVar("A$")).value());
    }
  }

  @Nested
  class DefFn {
    @Test
    void storesDefinition() {
      executor.execute(firstStmt("DEF FN F(X)=X*2"));
      assertTrue(executor.fnDefs().containsKey("F"));
      assertEquals(List.of("X"), executor.fnDefs().get("F").params());
    }

    @Test
    void typeMismatchThrows() {
      assertThrows(ReportException.class, () -> executor.execute(firstStmt("DEF FN F(X)=\"HI\"")));
    }

    @Test
    void duplicateParamThrows() {
      assertThrows(ReportException.class, () -> executor.execute(firstStmt("DEF FN F(X,X)=X")));
    }
  }

  @Test
  void remIsNoOp() {
    executor.execute(firstStmt("REM this is a comment"));
  }

  @Nested
  class StyleStatements {
    @Test
    void allSixUpdateScreenAndDefaults() {
      exec("INK 1 : PAPER 2 : BRIGHT 1 : FLASH 1 : INVERSE 1 : OVER 1");
      assertEquals(1, state.defaultInk());
      assertEquals(2, state.defaultPaper());
      assertEquals(1, state.defaultBright());
      assertEquals(1, state.defaultFlash());
      assertEquals(1, state.defaultInverse());
      assertEquals(1, state.defaultOver());
    }
  }

  @Nested
  class ScreenModeStatements {
    @Test
    void clsResetsCursor() {
      exec("PRINT \"X\"");
      exec("CLS");
      assertEquals(0, screen.currentRow());
      assertEquals(0, screen.currentCol());
    }

    @Test
    void scrollAdvancesOutput() {
      exec("SCROLL");
      assertTrue(screen.getOutput().contains("\n"));
    }

    @Test
    void fastAndSlowDoNotThrow() {
      exec("FAST");
      exec("SLOW");
    }
  }

  @Nested
  class Plotmode {
    @Test
    void validModesDoNotThrow() {
      exec("PLOTMODE 1");
      exec("PLOTMODE 8");
    }

    @Test
    void invalidModeThrows() {
      assertThrows(ReportException.class, () -> exec("PLOTMODE 3"));
    }
  }

  @Nested
  class Graphics {
    @Test
    void plotSetsGraphicsCursorAndPixel() {
      assertEquals(0, screen.point(5, 5));
      exec("PLOT 5, 5");
      assertEquals(5, state.graphicsCursorX());
      assertEquals(5, state.graphicsCursorY());
      assertNotEquals(0, screen.point(5, 5));
    }

    @Test
    void drawMovesFromCurrentGraphicsCursor() {
      exec("PLOT 0, 0");
      exec("DRAW 5, 0");
      assertEquals(5, state.graphicsCursorX());
      assertEquals(0, state.graphicsCursorY());
    }

    @Test
    void circleLeavesGraphicsCursorAtCentre() {
      exec("CIRCLE 20, 20, 5");
      assertEquals(20, state.graphicsCursorX());
      assertEquals(20, state.graphicsCursorY());
    }
  }

  @Nested
  class Print {
    @Test
    void printsValueThenNewline() {
      exec("PRINT \"HI\"");
      assertEquals("HI\n", screen.getOutput());
    }

    @Test
    void semicolonConcatenatesButStillNewlinesAtEnd() {
      exec("PRINT \"A\";\"B\"");
      assertEquals("AB\n", screen.getOutput());
    }

    @Test
    void trailingSemicolonSuppressesNewline() {
      exec("PRINT \"A\";");
      assertEquals("A", screen.getOutput());
    }

    @Test
    void barePrintOutputsJustNewline() {
      exec("PRINT");
      assertEquals("\n", screen.getOutput());
    }

    @Test
    void atPositionsCursor() {
      // Trailing ';' suppresses the auto-newline so the cursor position is still observable.
      exec("PRINT AT 2, 3; \"X\";");
      assertEquals(2, screen.currentRow());
    }

    @Test
    void inlineStyleDoesNotChangePersistentDefaults() {
      exec("PRINT INK 2; \"X\"");
      assertEquals(-1, state.defaultInk()); // inline style, not the INK statement
    }

    @Test
    void atOutOfBoundsThrows() {
      assertThrows(ReportException.class, () -> exec("PRINT AT -1, 0"));
    }
  }

  @Nested
  class Input {
    @Test
    void numericInputAssignsValue() {
      screen.queueInput("42");
      exec("INPUT X");
      assertEquals(42.0, state.numVar("X"));
    }

    @Test
    void stringInputAssignsRawLine() {
      screen.queueInput("HELLO");
      exec("INPUT A$");
      assertEquals(
          BStr.fromJavaString("HELLO"), ((EvalState.StrVar.Scalar) state.strVar("A$")).value());
    }

    @Test
    void typingStopThrows() {
      screen.queueInput("STOP");
      assertThrows(ReportException.class, () -> exec("INPUT X"));
    }
  }

  @Nested
  class Pause {
    @Test
    void zeroFramesReturnsImmediately() {
      exec("PAUSE 0");
    }

    @Test
    void breakDuringPauseThrows() {
      screen.triggerBreak();
      assertThrows(ReportException.class, () -> exec("PAUSE 1"));
    }
  }

  @Nested
  class Rand {
    @Test
    void explicitSeedIsDeterministic() {
      exec("RANDOMIZE 42");
      final double first = state.nextRandom();
      exec("RANDOMIZE 42");
      final double second = state.nextRandom();
      assertEquals(first, second);
    }

    @Test
    void bareRandomizeDoesNotThrow() {
      exec("RANDOMIZE");
    }
  }

  @Nested
  class ListStatement {
    @Test
    void listsAllLinesWithNoRange() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n20 LET Y=2\n"));
      exec("LIST");
      assertEquals("10 LET X=1\n20 LET Y=2\n", screen.getOutput());
    }

    @Test
    void listsFromASingleLineNumberToEnd() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n20 LET Y=2\n"));
      exec("LIST 20");
      assertEquals("20 LET Y=2\n", screen.getOutput());
    }

    @Test
    void listsAnExplicitRange() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n20 LET Y=2\n30 LET Z=3\n"));
      exec("LIST 10 TO 20");
      assertEquals("10 LET X=1\n20 LET Y=2\n", screen.getOutput());
    }
  }

  @Nested
  class ProgramManagement {
    @TempDir Path tempDir;

    @Test
    void saveThenLoadRoundTrips() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n20 LET Y=2\n"));
      final String file = tempDir.resolve("prog.bas").toString();
      exec("SAVE \"" + file + "\"");

      state.setProgram(new TreeMap<>());
      exec("LOAD \"" + file + "\"");
      assertEquals(2, state.program().size());
      assertEquals("LET X=1", state.program().get(10).sourceText());
    }

    @Test
    void mergeAddsLinesWithoutClearingExisting() throws IOException {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n"));
      final var file = tempDir.resolve("merge.bas");
      Files.writeString(file, "20 LET Y=2\n");
      exec("MERGE \"" + file + "\"");
      assertTrue(state.program().containsKey(10));
      assertTrue(state.program().containsKey(20));
    }

    @Test
    void verifySucceedsAfterSave() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n"));
      final String file = tempDir.resolve("verify.bas").toString();
      exec("SAVE \"" + file + "\"");
      exec("VERIFY \"" + file + "\""); // must not throw
    }

    @Test
    void verifyFailsOnMismatch() {
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n"));
      final String file = tempDir.resolve("verify2.bas").toString();
      exec("SAVE \"" + file + "\"");
      state.setProgram(PARSER.parseProgramLines("10 LET X=2\n"));
      assertThrows(ReportException.class, () -> exec("VERIFY \"" + file + "\""));
    }
  }
}
