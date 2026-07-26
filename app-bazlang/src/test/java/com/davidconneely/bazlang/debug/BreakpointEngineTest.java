package com.davidconneely.bazlang.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.debug.BreakpointEngine.BreakCondition;
import com.davidconneely.bazlang.debug.BreakpointEngine.ConditionType;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import com.davidconneely.bazlang.io.MockScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BreakpointEngineTest {

  private BreakpointEngine engine;
  private EvalState state;
  private MockScreen screen;
  private ExpressionEvaluator eval;

  @BeforeEach
  void setUp() {
    engine = new BreakpointEngine(AntlrParser.INSTANCE);
    state = new EvalState();
    screen = new MockScreen();
    eval = new ExpressionEvaluator(state, screen, screen, AntlrParser.INSTANCE);
  }

  @Test
  void testParseConditionVariants() {
    assertEquals(
        ConditionType.VIEW, BreakpointEngine.parseCondition(-1, -1, "CSC \"x\"", true).type());
    assertEquals(
        ConditionType.ELAPSE, BreakpointEngine.parseCondition(-1, -1, "ELAPSE 100", true).type());
    assertEquals(ConditionType.EXPR, BreakpointEngine.parseCondition(-1, -1, "?a=1", true).type());
    assertEquals(
        ConditionType.EVERY, BreakpointEngine.parseCondition(-1, -1, "EVERY 3", true).type());
    assertNull(BreakpointEngine.parseCondition(-1, -1, "CSC unquoted", true));
    assertNull(BreakpointEngine.parseCondition(-1, -1, "ELAPSE", true));
    assertNull(BreakpointEngine.parseCondition(-1, -1, "?", true));
    assertNull(BreakpointEngine.parseCondition(-1, -1, "EVERY 0", true));
    assertNull(BreakpointEngine.parseCondition(-1, -1, "nonsense", true));
  }

  @Test
  void testLocationMatchAndPersistence() {
    engine.add(new BreakCondition(10, 1, ConditionType.NONE, null, 0, true, 0, null));
    assertNull(engine.checkFired(20, 1, screen, eval), "wrong line must not fire");
    assertNotNull(engine.checkFired(10, 1, screen, eval));
    assertNotNull(engine.checkFired(10, 1, screen, eval), "persistent break fires again");
  }

  @Test
  void testOneShotRemovesItself() {
    engine.add(new BreakCondition(10, 1, ConditionType.NONE, null, 0, false, 0, null));
    assertNotNull(engine.checkFired(10, 1, screen, eval));
    assertNull(engine.checkFired(10, 1, screen, eval), "one-shot break must not fire twice");
  }

  @Test
  void testEveryCounter() {
    engine.add(BreakpointEngine.parseCondition(-1, -1, "EVERY 3", true));
    assertNull(engine.checkFired(10, 1, screen, eval));
    assertNull(engine.checkFired(10, 1, screen, eval));
    assertNotNull(engine.checkFired(10, 1, screen, eval), "fires on the 3rd check");
    assertNull(engine.checkFired(10, 1, screen, eval));
    assertNull(engine.checkFired(10, 1, screen, eval));
    assertNotNull(engine.checkFired(10, 1, screen, eval), "fires on the 6th check");
  }

  @Test
  void testExprCondition() {
    state.getOrAddNumVar("X").value = 0.0;
    state.getOrAddNumVar("X").initialised = true;
    engine.add(BreakpointEngine.parseCondition(-1, -1, "?x=3", true));
    assertNull(engine.checkFired(10, 1, screen, eval));
    state.getOrAddNumVar("X").value = 3.0;
    assertNotNull(engine.checkFired(10, 1, screen, eval));
  }

  @Test
  void testViewCondition() {
    engine.add(BreakpointEngine.parseCondition(-1, -1, "CSC \"target\"", true));
    assertNull(engine.checkFired(10, 1, screen, eval));
    screen.print("the TARGET text");
    assertNotNull(engine.checkFired(10, 1, screen, eval), "CSC is case-insensitive");
  }

  @Test
  void testClearPersistentAndClearAt() {
    engine.add(new BreakCondition(10, 1, ConditionType.NONE, null, 0, true, 0, null));
    engine.add(new BreakCondition(20, 1, ConditionType.NONE, null, 0, true, 0, null));
    engine.clearAt(10, 1);
    assertNull(engine.checkFired(10, 1, screen, eval));
    assertNotNull(engine.checkFired(20, 1, screen, eval));
    engine.clearPersistent();
    assertNull(engine.checkFired(20, 1, screen, eval));
  }
}
