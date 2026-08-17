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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    engine.add(
        new BreakCondition(-1, -1, ConditionType.EVERY, null, 0, true, 3, new AtomicInteger()));
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
    engine.add(new BreakCondition(-1, -1, ConditionType.EXPR, "x=3", 0, true, 0, null));
    assertNull(engine.checkFired(10, 1, screen, eval));
    state.getOrAddNumVar("X").value = 3.0;
    assertNotNull(engine.checkFired(10, 1, screen, eval));
  }

  @Test
  void testViewCondition() {
    engine.add(new BreakCondition(-1, -1, ConditionType.VIEW, "target", 0, true, 0, null));
    assertNull(engine.checkFired(10, 1, screen, eval));
    screen.print("the TARGET text");
    assertNotNull(engine.checkFired(10, 1, screen, eval), "CSC is case-insensitive");
  }

  @Test
  void testList() {
    assertEquals(List.of(), engine.list());
    var b1 = new BreakCondition(10, 1, ConditionType.NONE, null, 0, true, 0, null);
    var b2 = new BreakCondition(20, 1, ConditionType.NONE, null, 0, false, 0, null);
    engine.add(b1);
    engine.add(b2);
    assertEquals(List.of(b1, b2), engine.list());
    engine.clearAt(10, 1);
    assertEquals(List.of(b2), engine.list());
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
