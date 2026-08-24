package com.davidconneely.bazlang.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Component-level tests for {@link DebugEngine} - the debugging core the MCP server adapts.
 * Exercised directly, with no subprocess and no protocol framing, so these cover the engine layer
 * itself rather than duplicating the same scenarios via JSON-RPC framing. See the 2026-08-16/17
 * entries in localonly-BAZLANG-IMPROVEMENTS.md: two real bugs (the ELAPSE clock not resetting on
 * {@link DebugEngine#run}/{@link DebugEngine#gotoLine}, and breakpoints intercepting immediate-mode
 * REPL commands) lived at exactly this layer and were caught by neither the engine's nor the MCP
 * server's own test suite until live use surfaced them - {@link
 * #breakpointsDoNotInterceptReplCommands} and {@link #elapseBreakpointResetsOnRun} are the
 * permanent regression guards for those.
 */
class DebugEngineTest {

  private DebugEngine engine;

  @BeforeEach
  void setUp() {
    engine = new DebugEngine(AntlrParser.INSTANCE);
  }

  @Test
  void loadSourceAndListProgramRoundTrip() {
    engine.loadSource("10 LET X = 1\n20 PRINT X");
    assertEquals("10 LET X = 1\n20 PRINT X", engine.listProgram());
  }

  @Test
  void newClearsTheProgram() {
    engine.loadSource("10 LET X = 1");
    engine.applyReplCommand("NEW");
    assertEquals("", engine.listProgram());
  }

  @Test
  void editLineAddsReplacesAndDeletes() {
    engine.applyReplCommand("10 LET X = 1");
    assertEquals("10 LET X = 1", engine.listProgram());
    engine.applyReplCommand("10 LET X = 2");
    assertEquals("10 LET X = 2", engine.listProgram());
    engine.applyReplCommand("10");
    assertEquals("", engine.listProgram());
  }

  @Test
  void evalExpressionReadsNumericAndStringResults() {
    engine.executeAssignment("LET X = 42");
    engine.executeAssignment("LET A$ = \"hello\"");
    assertEquals(42.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("X")).value(), 0.0001);
    assertEquals("hello", ((DebugEngine.EvalResult.Str) engine.evalExpression("A$")).value());
  }

  @Test
  void runWithoutAProgrammeThrows() {
    assertThrows(DebugEngineException.class, engine::run);
  }

  @Test
  void goWithoutBeingPausedThrows() {
    assertThrows(DebugEngineException.class, engine::go);
  }

  @Test
  void breakpointFiresOnEveryVisitAndGoResumesWithoutReFiring() {
    engine.loadSource("10 LET N = 0\n20 LET N = N + 1\n30 GO TO 20");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                20, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));

    DebugEngine.PauseResult first = engine.run();
    assertTrue(
        first instanceof DebugEngine.PauseResult.Break(int line, int stmt)
            && line == 20
            && stmt == 1);
    // Paused *before* line 20 executes, so N is still 0 - proves go() below isn't a no-op re-fire.
    assertEquals(0.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("N")).value(), 0.0001);

    DebugEngine.PauseResult second = engine.go();
    assertTrue(
        second instanceof DebugEngine.PauseResult.Break(int line, int stmt)
            && line == 20
            && stmt == 1);
    // N incremented once (20 executed, looped back via 30, and 20 was visited again) before this
    // second pause - proves go() actually resumed execution rather than re-firing immediately.
    assertEquals(1.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("N")).value(), 0.0001);
  }

  @Test
  void runEndsNormallyWhenProgrammeCompletes() {
    engine.loadSource("10 LET X = 1\n20 STOP");
    DebugEngine.PauseResult result = engine.run();
    assertTrue(result instanceof DebugEngine.PauseResult.Stopped);
    var stopped = (DebugEngine.PauseResult.Stopped) result;
    assertEquals('9', stopped.report().reportCode().getCode()); // STOP_STATEMENT
  }

  @Test
  void breakpointsDoNotInterceptReplCommands() {
    // Regression test (see class Javadoc): an unconditional breakpoint (ConditionType.NONE, always
    // "fires") used to intercept Interpreter.executeImmediate's line-0 dispatch too, silently
    // cancelling any REPL command - LOAD, NEW, a numbered-line edit, an assignment - issued while
    // it was armed, without any error, since InterpreterReplHandler still reported success.
    engine.loadSource("10 LET X = 1");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                -1, -1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));

    engine.applyReplCommand("20 LET X = 2"); // must not be silently cancelled
    assertEquals("10 LET X = 1\n20 LET X = 2", engine.listProgram());

    engine.executeAssignment("LET X = 99"); // must not be silently cancelled either
    assertEquals(99.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("X")).value(), 0.0001);
  }

  @Test
  void elapseBreakpointResetsOnRun() throws InterruptedException {
    // Regression test (see class Javadoc): BreakpointEngine's ELAPSE clock started ticking at
    // construction time; run()/gotoLine() must reset it, or a DebugEngine that has been alive for a
    // while sees an already-"overdue" breakpoint fire on the very first statement of the next run,
    // before the programme has done anything. Simulate "alive for a while" with a sleep before the
    // programme is even loaded, well past the breakpoint's own threshold.
    Thread.sleep(300);
    engine.loadSource("10 LET N = 0\n20 LET N = N + 1\n30 GO TO 20");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                -1, -1, BreakpointEngine.ConditionType.ELAPSE, null, 50, true, 0, null));

    DebugEngine.PauseResult result = engine.run();
    assertTrue(result instanceof DebugEngine.PauseResult.Elapse);
    // If the clock had measured from construction, the 50ms threshold was already exceeded by the
    // 300ms sleep above, and this would fire before line 20 ever ran, leaving N at 0.
    assertTrue(((DebugEngine.EvalResult.Num) engine.evalExpression("N")).value() > 0);
  }

  @Test
  void loadFileResolvesABareExampleName() {
    engine.applyReplCommand("LOAD \"pong\"");
    assertFalse(engine.listProgram().isBlank());
    assertTrue(engine.listProgram().startsWith("1000 REM ### Classic Pong ###"));
  }

  @Test
  void loadWithASyntacticallyInvalidPathFailsCleanlyRatherThanCrashing() {
    // resolveBasPath's Path.of(inputPath) throws InvalidPathException (a RuntimeException, not a
    // ReportException) for a ':' anywhere but a Windows drive prefix - must be treated as "not
    // found" rather than propagating uncaught past applyReplCommand.
    assertThrows(DebugEngineException.class, () -> engine.applyReplCommand("LOAD \"bad:name\""));
  }

  @Test
  void newFlushesQueuedInput() {
    // Found via live use: switching programmes on one long-lived engine left stale queued input
    // (queued for a program using one input primitive) to be silently consumed by the next
    // programme if it happened to use a different one - see docs/spec/mcp.md "Input queue".
    engine.screen().queueInkey(BStr.fromJavaString("x"));
    engine.screen().queueUinkey(BStr.fromJavaString("x"));
    engine.screen().queueInput("x");
    engine.applyReplCommand("NEW");
    assertEquals(BStr.EMPTY, engine.screen().inkey());
    assertEquals(BStr.EMPTY, engine.screen().uinkey());
    assertEquals("", engine.screen().readln((String) null));
  }

  @Test
  void loadFlushesQueuedInput() {
    engine.screen().queueInkey(BStr.fromJavaString("x"));
    engine.applyReplCommand("LOAD \"pong\"");
    assertEquals(BStr.EMPTY, engine.screen().inkey());
  }

  @Test
  void loadSourceFlushesQueuedInput() {
    engine.screen().queueUinkey(BStr.fromJavaString("x"));
    engine.loadSource("10 LET X = 1");
    assertEquals(BStr.EMPTY, engine.screen().uinkey());
  }

  @Test
  void limitPausesARunawayLoopWithNoBreakpoint() {
    // Safety net: no breakpoint of the programme's own would ever fire here, so without the
    // timeout this call would block forever - and, for the MCP server, hang the whole session
    // (see docs/spec/mcp.md "Known limitations": there is no cancel-while-running mechanism).
    engine.loadSource("10 GO TO 10");
    DebugEngine.PauseResult result = engine.run(50);
    assertTrue(
        result instanceof DebugEngine.PauseResult.Limit(int line, int stmt)
            && line == 10
            && stmt == 1);
  }

  @Test
  void stepIntoRequiresBeingPaused() {
    assertThrows(DebugEngineException.class, engine::stepInto);
  }

  @Test
  void stepOverRequiresBeingPaused() {
    assertThrows(DebugEngineException.class, engine::stepOver);
  }

  @Test
  void stepIntoExecutesExactlyOneStatementAtATime() {
    engine.loadSource("10 LET X = 1\n20 LET X = 2\n30 STOP");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                10, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));
    DebugEngine.PauseResult atBreak = engine.run();
    assertTrue(
        atBreak instanceof DebugEngine.PauseResult.Break(int line, int stmt)
            && line == 10
            && stmt == 1);

    DebugEngine.PauseResult afterFirst = engine.stepInto();
    assertTrue(
        afterFirst instanceof DebugEngine.PauseResult.Step(int line, int stmt)
            && line == 20
            && stmt == 1);
    assertEquals(1.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("X")).value(), 0.0001);

    DebugEngine.PauseResult afterSecond = engine.stepInto();
    assertTrue(
        afterSecond instanceof DebugEngine.PauseResult.Step(int line, int stmt)
            && line == 30
            && stmt == 1);
    assertEquals(2.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("X")).value(), 0.0001);

    DebugEngine.PauseResult afterStop = engine.stepInto();
    assertTrue(afterStop instanceof DebugEngine.PauseResult.Stopped);
  }

  @Test
  void stepIntoEntersAGosubCall() {
    engine.loadSource("10 GOSUB 100\n20 STOP\n100 LET X = 1\n110 RETURN");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                10, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));
    engine.run();

    DebugEngine.PauseResult afterStep = engine.stepInto();
    // stepInto follows the GOSUB rather than treating it as one atomic statement.
    assertTrue(
        afterStep instanceof DebugEngine.PauseResult.Step(int line, int stmt)
            && line == 100
            && stmt == 1);
  }

  @Test
  void stepOverRunsAGosubCallToCompletionWithoutPausingInsideIt() {
    engine.loadSource("10 GOSUB 100\n20 STOP\n100 LET X = 1\n110 RETURN");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                10, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));
    engine.run();

    DebugEngine.PauseResult afterStep = engine.stepOver();
    // Unlike stepInto (see stepIntoEntersAGosubCall), stepOver treats the whole GOSUB call as one
    // step and lands back on line 20, having run 100/110 to completion.
    assertTrue(
        afterStep instanceof DebugEngine.PauseResult.Step(int line, int stmt)
            && line == 20
            && stmt == 1);
    assertEquals(1.0, ((DebugEngine.EvalResult.Num) engine.evalExpression("X")).value(), 0.0001);
  }

  @Test
  void stepOverStillStopsForABreakpointInsideTheCalledSubroutine() {
    // A breakpoint takes priority over "run the call to completion" - otherwise stepOver would be
    // an easy way to accidentally skip straight past a breakpoint an agent placed inside a call.
    engine.loadSource("10 GOSUB 100\n20 STOP\n100 LET X = 1\n110 RETURN");
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                10, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));
    engine.run();
    engine
        .breakpoints()
        .add(
            new BreakpointEngine.BreakCondition(
                100, 1, BreakpointEngine.ConditionType.NONE, null, 0, true, 0, null));

    DebugEngine.PauseResult afterStep = engine.stepOver();
    assertTrue(
        afterStep instanceof DebugEngine.PauseResult.Break(int line, int stmt)
            && line == 100
            && stmt == 1);
  }

  @Test
  void editingALineDoesNotFlushQueuedInput() {
    // Deliberately scoped: a plain numbered-line edit is a much smaller mutation than replacing
    // the whole programme, and a workflow actively editing-and-testing one programme in a loop
    // plausibly wants its queued input to survive a line tweak.
    engine.applyReplCommand("10 LET X = 1");
    engine.screen().queueInkey(BStr.fromJavaString("x"));
    engine.applyReplCommand("20 LET X = 2");
    assertEquals("x", engine.screen().inkey().toJavaString());
  }
}
