package com.davidconneely.bazlang.repl;

import static org.junit.jupiter.api.Assertions.*;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.MockDisplay;
import com.davidconneely.bazlang.ProgramLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplCommandsTest {

  private EvalState state;
  private MockDisplay display;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    display = new MockDisplay();
    // Set up a simple program
    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));
  }

  @Test
  void testEditReturnsWithoutExecuting() {
    // EDIT should not execute anything, just pre-fill input
    assertTrue(ReplCommands.tryHandle("EDIT 10", state, display));
    // Program should be unchanged
    assertEquals(4, state.program().size());
  }

  @Test
  void testEditPrefillsInput() {
    ReplCommands.tryHandle("EDIT 20", state, display);
    assertEquals("20 GOTO 40", display.getPrefillText());
  }

  @Test
  void testEditNonExistentLine() {
    ReplCommands.tryHandle("EDIT 100", state, display);
    assertEquals("100 ", display.getPrefillText());
  }

  @Test
  void testNonReplCommandReturnsFalse() {
    assertFalse(ReplCommands.tryHandle("PRINT \"HELLO\"", state, display));
    assertFalse(ReplCommands.tryHandle("10 PRINT \"HELLO\"", state, display));
    assertFalse(ReplCommands.tryHandle("RUN", state, display));
    assertFalse(ReplCommands.tryHandle("DELETE 10", state, display)); // Now parsed
    assertFalse(ReplCommands.tryHandle("RENUM", state, display)); // Now parsed
  }
}
