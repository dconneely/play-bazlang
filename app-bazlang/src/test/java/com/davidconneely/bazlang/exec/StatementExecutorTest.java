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
import com.davidconneely.bazlang.io.Pitch;
import com.davidconneely.bazlang.io.VirtualSpeaker;
import com.davidconneely.bazlang.io.VoiceFrame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Component tests for {@link StatementExecutor}, covering every statement kind. */
class StatementExecutorTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState state;
  private MockScreen screen;
  private StatementExecutor executor;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    state.setCurrentLineLabel(10);
    screen = new MockScreen();
    executor = new StatementExecutor(state, screen, screen, screen);
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
   * Loads {@code source} as the current program and runs it to completion via {@link Interpreter},
   * exactly as the live interpreter would.
   */
  private void run(String source) {
    new Interpreter(state, executor).execute(PARSER.parseProgramLines(source));
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
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n"));
      state.setNumVar("X", 5.0);
      executor.execute(firstStmt("NEW"));
      assertFalse(state.hasNumVar("X"));
      assertTrue(state.program().isEmpty());
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
      run("10 LET S=0\n20 FOR I=1 TO 3\n30 LET S=S+I\n40 NEXT I\n");
      assertEquals(6.0, state.numVar("S"));
      assertEquals(4.0, state.numVar("I")); // loop var ends one step past the limit
    }

    @Test
    void skipsBodyWhenStartAfterEndWithPositiveStep() {
      run("10 LET S=0\n20 FOR I=5 TO 1\n30 LET S=S+1\n40 NEXT I\n50 LET DONE=1\n");
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
      run("10 LET X=1\n20 GOTO 40\n30 LET X=99\n40 LET Y=2\n");
      assertEquals(1.0, state.numVar("X")); // line 30 never ran
      assertEquals(2.0, state.numVar("Y"));
    }

    @Test
    void gosubAndReturn() {
      final String source = "10 GOSUB 100\n20 LET DONE=1\n30 STOP\n100 LET X=5\n110 RETURN\n";
      assertThrows(ReportException.class, () -> run(source)); // STOP throws
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
      run("10 IF 0 THEN LET A=1\n20 LET B=2\n");
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
      state.setProgram(PARSER.parseProgramLines("10 LET X=1\n"));
      state.setNumVar("X", 99.0);
      executor.execute(firstStmt("RUN"));
      assertFalse(state.hasNumVar("X")); // cleared
      assertEquals(10, (int) state.pendingJumpLabel());
    }
  }

  @Nested
  class DataReadRestore {
    @Test
    void readConsumesDataInOrder() {
      run("10 DATA 1, 2, 3\n20 READ A, B, C\n");
      assertEquals(1.0, state.numVar("A"));
      assertEquals(2.0, state.numVar("B"));
      assertEquals(3.0, state.numVar("C"));
    }

    @Test
    void readAcrossMultipleDataStatements() {
      run("10 DATA 1\n20 DATA 2\n30 READ A, B\n");
      assertEquals(1.0, state.numVar("A"));
      assertEquals(2.0, state.numVar("B"));
    }

    @Test
    void readPastEndThrowsOutOfData() {
      final String source = "10 DATA 1\n20 READ A, B\n";
      assertThrows(ReportException.class, () -> run(source));
    }

    @Test
    void restoreResetsDataPointer() {
      run("10 DATA 1, 2\n20 READ A\n30 RESTORE\n40 READ B\n");
      assertEquals(1.0, state.numVar("A"));
      assertEquals(1.0, state.numVar("B")); // restored, re-reads from the start
    }

    @Test
    void readStringData() {
      run("10 DATA \"HI\"\n20 READ A$\n");
      assertEquals(
          BStr.fromJavaString("HI"), ((EvalState.StrVar.Scalar) state.strVar("A$")).value());
    }
  }

  @Nested
  class DefFn {
    @Test
    void storesDefinition() {
      executor.execute(firstStmt("DEF FN F(X)=X*2"));
      assertTrue(state.hasFn("F"));
      assertEquals(List.of("X"), state.fn("F").params());
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
  class Beep {
    // MockScreen inherits VirtualSpeaker's no-op default (see AbstractCellBufferedScreen), so
    // these exercise executeBeepStmt's own timing/BREAK-poll loop without any real audio device -
    // exactly the "headless fallback for free" VirtualSpeaker was designed around.

    @Test
    void zeroDurationReturnsImmediately() {
      exec("BEEP 0, 0");
    }

    @Test
    void negativeDurationIsClampedToZero() {
      exec("BEEP -1, 0");
    }

    @Test
    void breakDuringBeepThrows() {
      screen.triggerBreak();
      assertThrows(ReportException.class, () -> exec("BEEP 1, 0"));
    }
  }

  @Nested
  class Play {
    // MockScreen inherits VirtualSpeaker's no-op default for playFrame()/stopPlay(), so these
    // exercise executePlayStmt's own timing/BREAK-poll loop without any real audio device - the
    // loop's timing/BREAK logic lives entirely in StatementExecutor (unlike a naive design keyed
    // off the speaker's own "am I playing" state), so it works identically headless.

    @Test
    void singleNoteDoesNotCrash() {
      // Duration digit 1 (semiquaver, the shortest) keeps this test's real wall-clock cost small.
      exec("PLAY \"1c\"");
    }

    @Test
    void breakDuringPlayThrows() {
      screen.triggerBreak();
      assertThrows(ReportException.class, () -> exec("PLAY \"1c\""));
    }

    @Test
    void invalidNoteNameThrowsBeforePlaybackStarts() {
      assertThrows(ReportException.class, () -> exec("PLAY \"z\""));
    }

    @Test
    void upToThreeChannelsAreAccepted() {
      exec("PLAY \"1c\", \"1e\", \"1g\"");
    }
  }

  @Nested
  class Aplay {
    @Test
    void doesNotBlock() {
      // Returns immediately (the whole point of APLAY) rather than waiting for the tune --
      // measured,
      // not just "eventually completes": a 9c note is 2 real seconds long, so if executeAplayStmt
      // were actually waiting on it, this would take ~2000ms instead of a few.
      final long start = System.nanoTime();
      exec("APLAY \"9c\"");
      final long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      assertTrue(elapsedMs < 100, "executeAplayStmt took " + elapsedMs + "ms, expected < 100ms");
    }

    @Test
    void invalidNoteNameThrowsBeforePlaybackStarts() {
      assertThrows(ReportException.class, () -> exec("APLAY \"z\""));
    }

    @Test
    void aSecondAplayReplacesTheFirstWithoutThrowing() {
      exec("APLAY \"9c\"");
      exec("APLAY \"9d\"");
    }

    @Test
    void dashPlaceholdersTargetAPartialUpdateWithoutThrowing() {
      exec("APLAY \"9c\", \"9d\"");
      exec("APLAY \"-\", \"-\", \"1c\""); // touches channel C only, leaving A/B running
    }

    @Test
    void dashHaltIdiomStopsBackgroundMusicWithoutThrowing() {
      exec("APLAY \"9c\", \"9d\"");
      exec("APLAY \"H\", \"H\", \"1c\""); // the "game over" idiom: stop A/B, play one last effect
    }

    @Test
    void backgroundThreadStaysAliveAfterItsContentFinishesSoALaterUpdateIsNotLost()
        throws InterruptedException {
      // Regression test for a real intermittent bug: the background thread used to exit as soon as
      // it ran out of notes, racing executeAplayStmt's isAlive() check and silently dropping a
      // channel update that landed in the (narrow) window between "decided to exit" and "actually
      // terminated" -- observed as pong's paddle-touch click sometimes not playing at all. "1c" is
      // the shortest available note (0.125s at the default tempo); sleeping well past that proves
      // the thread doesn't self-terminate just because its content finished.
      exec("APLAY \"1c\"");
      Thread.sleep(400);
      assertTrue(
          executor.aplaySessionIsAlive(),
          "background APLAY thread exited after its content finished -- a later update could be "
              + "silently dropped");
    }

    /**
     * Counts total audio pushed to the speaker, how much of it was actually sounding, and how many
     * times {@code drainPlay()} fired -- the background loop's own authoritative "a sounding
     * session just went idle" signal (see {@code StatementExecutor.startNewAplaySession}), fired
     * exactly once per session and never spuriously mid-note.
     */
    private static final class RecordingSpeaker implements VirtualSpeaker {
      private final AtomicLong totalCalls = new AtomicLong();
      private final AtomicLong soundingCalls = new AtomicLong();
      private final AtomicLong drainCalls = new AtomicLong();

      @Override
      public void playFrame(VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds) {
        totalCalls.incrementAndGet();
        if (a.toneOn() || a.noiseOn() || b.toneOn() || b.noiseOn() || c.toneOn() || c.noiseOn()) {
          soundingCalls.incrementAndGet();
        }
      }

      @Override
      public void drainPlay() {
        drainCalls.incrementAndGet();
      }
    }

    private static long liveAplayThreads() {
      return Thread.getAllStackTraces().keySet().stream()
          .filter(t -> "bazlang-aplay".equals(t.getName()) && t.isAlive())
          .count();
    }

    @Test
    void repeatedTriggersReuseOneBackgroundThreadRatherThanAccumulating()
        throws InterruptedException {
      // A game like pong fires APLAY on every paddle hit, for the whole session. Each trigger must
      // reuse the one live background session, never leave another thread behind: accumulated
      // threads would each keep polling and pushing their own audio, so sounds would pile up and
      // fire when nothing had triggered them.
      final long before = liveAplayThreads();
      for (int i = 0; i < 40; i++) {
        exec("APLAY \"T240V11O6N1e\"");
        Thread.sleep(5);
      }
      Thread.sleep(200);
      assertEquals(
          before + 1,
          liveAplayThreads(),
          "background APLAY threads accumulated across repeated triggers");
    }

    // Polls `condition` until it is true, or fails the test outright once `timeoutMillis` has
    // elapsed without that happening -- so a genuine regression is reported (eventually) rather
    // than hanging. Takes java.util.function.BooleanSupplier by full name rather than importing
    // it -- this file already sits at PMD's ExcessiveImports threshold (30).
    private static void waitUntilTrue(
        java.util.function.BooleanSupplier condition, long timeoutMillis)
        throws InterruptedException {
      final long deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L;
      while (!condition.getAsBoolean()) {
        if (System.nanoTime() > deadlineNanos) {
          // org.junit.jupiter.api.Assertions.fail by full name rather than importing it -- this
          // file already sits at PMD's ExcessiveImports threshold (30).
          org.junit.jupiter.api.Assertions.fail(
              "condition still false after " + timeoutMillis + "ms");
        }
        Thread.sleep(10L);
      }
    }

    @Test
    void anIdleAplaySessionStopsPushingAudioEntirelyRatherThanStreamingSilence()
        throws InterruptedException {
      // The invariant that makes low-latency playback possible at all, and the direct regression
      // test for the root cause of the reported "sounds are late / sometimes missing" bug: an
      // audio line is a FIFO whose write() only blocks once full, so anything that keeps pushing
      // idle silence stays a whole buffer ahead of the speaker and every later note lands behind
      // that backlog. An idle APLAY session must therefore push *nothing at all*, not silence.
      final var recordingSpeaker = new RecordingSpeaker();
      final var recordingExecutor = new StatementExecutor(state, screen, screen, recordingSpeaker);
      for (final var stmt :
          AstLowering.lowerStatements(PARSER.parseStatementsContext("APLAY \"1c\""), 10)) {
        recordingExecutor.execute(stmt);
      }
      // Waits for drainPlay() -- the background loop's own authoritative "just went idle" signal,
      // fired exactly once when a sounding session finishes (see ADR-0007) -- rather than
      // inferring idleness from a quiet gap in playFrame calls. A quiet-gap heuristic is unsound
      // here: each real chunk is already paced ~20ms apart, so a single GC pause on a loaded CI
      // runner can space two still-legitimate mid-note chunks further apart than a short "gone
      // idle" threshold would assume, making it declare idle before the note actually finished
      // (this is exactly what an earlier version of this test did, and it was still flaky).
      waitUntilTrue(() -> recordingSpeaker.drainCalls.get() > 0, 5_000);
      final long callsOnceIdle = recordingSpeaker.totalCalls.get();
      assertTrue(recordingSpeaker.soundingCalls.get() > 0, "the note never sounded at all");
      // A generous fixed margin is safe to use here, unlike above: it is only ever used to catch a
      // regression (more calls arriving), never to prove idleness, so CI slowness cannot make it
      // flake -- it can only make a genuine regression take longer to report.
      Thread.sleep(500);
      assertEquals(
          callsOnceIdle,
          recordingSpeaker.totalCalls.get(),
          "an idle APLAY session kept pushing audio (silence) to the speaker -- that backlog is "
              + "exactly what delays and swallows later notes");
    }

    /** Records every distinct frequency (rounded) ever seen sounding on channel A. */
    private static final class FrequencyRecordingSpeaker implements VirtualSpeaker {
      private final Set<Long> frequenciesSeen = ConcurrentHashMap.newKeySet();

      @Override
      public void playFrame(VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds) {
        if (a.toneOn()) {
          frequenciesSeen.add(Math.round(a.frequencyHz()));
        }
      }

      boolean saw(double frequencyHz) {
        return frequenciesSeen.contains(Math.round(frequencyHz));
      }
    }

    @Test
    void aChannelReplaceWakesTheBackgroundThreadPromptlyRatherThanWaitingOutItsCurrentSleep()
        throws InterruptedException {
      // What's directly, deterministically testable here is latency, not loss: the background
      // thread already polls at least every PLAY_CHUNK_SECONDS (~20ms) regardless, since
      // PlaySequencer.next() caps every pull at that value even for a long note -- so a *single*
      // replace would eventually be discovered within ~20ms even without this fix, and a test
      // that waits substantially longer than that before checking can't tell the two apart (an
      // earlier version of this test made exactly that mistake). What interrupt() provably does
      // is deliver a replaced channel near-instantly instead of waiting out that ~20ms window --
      // tested here directly by checking well *inside* that window, which only a prompt wake can
      // satisfy. (A true zero-gap back-to-back replace racing the interrupt itself is a separate,
      // harder-to-eliminate scenario a unit test can't reliably force either way; see the
      // response given alongside this fix for what a fully race-proof design would need instead.)
      final var recordingSpeaker = new FrequencyRecordingSpeaker();
      final var recordingExecutor = new StatementExecutor(state, screen, screen, recordingSpeaker);
      for (final var stmt :
          AstLowering.lowerStatements(PARSER.parseStatementsContext("APLAY \"9c\""), 10)) {
        recordingExecutor.execute(stmt);
      }
      Thread.sleep(5); // background thread is now mid-way through its first ~20ms sleep
      for (final var stmt :
          AstLowering.lowerStatements(PARSER.parseStatementsContext("APLAY \"9d\""), 10)) {
        recordingExecutor.execute(stmt);
      }
      // Checking at ~10ms total, well before that first sleep would naturally complete at ~20ms:
      // only a prompt interrupt-driven wake -- not the natural polling cadence -- can satisfy this.
      Thread.sleep(5);
      assertTrue(
          recordingSpeaker.saw(Pitch.hzFromSemitonesAboveMiddleC(2)),
          "replaced note 'd' wasn't delivered promptly -- the background thread waited out its "
              + "current sleep instead of waking immediately");
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
