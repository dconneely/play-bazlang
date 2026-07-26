package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvalStateTest {

  private EvalState state;

  @BeforeEach
  void setUp() {
    state = new EvalState();
  }

  @Test
  void testClear() {
    state.setNumVar("X", 5.5);
    state.setStrVar("A$", new EvalState.StrVar.Scalar(BStr.fromJavaString("HI")));
    state.seedRandom(12_345);
    state.clear();
    assertFalse(state.hasNumVar("X"));
    assertFalse(state.hasStrVar("A$"));
    assertEquals(ReportCode.OK, state.lastReportCode());
    assertEquals(0, state.lastReportLabel());
  }

  @Test
  void testForLoops() {
    assertFalse(state.hasForLoop("I"));
    final var data = new EvalState.ForLoopData(10.0, 1.0, 100, 2);
    state.setForLoop("I", data);
    assertTrue(state.hasForLoop("I"));
    assertEquals(data, state.forLoop("I"));
  }

  @Test
  void testFunctionDefinitions() {
    assertFalse(state.hasFn("FNA"));
    final var def = new EvalState.FnDefinition("FNA", List.of("X"), null);
    state.setFn("FNA", def);
    assertTrue(state.hasFn("FNA"));
    assertEquals(def, state.fn("FNA"));
  }

  @Test
  void testJumpLocations() {
    assertFalse(state.hasPendingJump());
    assertNull(state.pendingJumpLabel());
    assertNull(state.pendingJumpStatementIndex());

    state.setPendingJumpLocation(100, 3);
    assertTrue(state.hasPendingJump());
    assertEquals(100, state.pendingJumpLabel());
    assertEquals(3, state.pendingJumpStatementIndex());

    state.clearPendingJump();
    assertFalse(state.hasPendingJump());
  }

  @Test
  void testNumArrays() {
    assertFalse(state.hasNumArray("A"));
    final var arr = new EvalState.NumArray(new int[] {5}, new double[5]);
    state.setNumArray("A", arr);
    assertTrue(state.hasNumArray("A"));
    assertEquals(arr, state.numArray("A"));
  }

  @Test
  void testNumScalars() {
    assertFalse(state.hasNumVar("X"));
    assertThrows(IllegalArgumentException.class, () -> state.numVar("X"));
    state.setNumVar("X", 42.0);
    assertTrue(state.hasNumVar("X"));
    assertEquals(42.0, state.numVar("X"));
    state.removeNumVar("X");
    assertFalse(state.hasNumVar("X"));
  }

  @Test
  void testProgramLines() {
    final var line = new ProgramLine(10, "PRINT \"HELLO\"");
    state.setProgram(Map.of(10, line));
    assertEquals(1, state.program().size());
    assertEquals(line, state.program().get(10));
  }

  @Test
  void testReturnStack() {
    assertTrue(state.isReturnStackEmpty());
    final var loc1 = new EvalState.StatementAddress(10, 1);
    final var loc2 = new EvalState.StatementAddress(20, 2);

    state.pushReturn(loc1);
    assertFalse(state.isReturnStackEmpty());
    state.pushReturn(loc2);

    assertEquals(loc2, state.popReturn());
    assertEquals(loc1, state.popReturn());
    assertTrue(state.isReturnStackEmpty());
  }

  @Test
  void testRunningState() {
    assertTrue(state.isRunning());
    state.setRunning(false);
    assertFalse(state.isRunning());
  }

  @Test
  void testSeedRandom() {
    state.seedRandom(42L);
    final double r1 = state.nextRandom();
    state.seedRandom(42L);
    final double r2 = state.nextRandom();
    assertEquals(r1, r2);
  }

  @Test
  void testStrVars() {
    assertFalse(state.hasStrVar("S$"));
    final var scalar = new EvalState.StrVar.Scalar(BStr.fromJavaString("TEST"));
    state.setStrVar("S$", scalar);
    assertTrue(state.hasStrVar("S$"));
    assertEquals(scalar, state.strVar("S$"));
    state.removeStrVar("S$");
    assertFalse(state.hasStrVar("S$"));
  }
}
