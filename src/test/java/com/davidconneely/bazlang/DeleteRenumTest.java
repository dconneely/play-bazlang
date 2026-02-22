package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.*;

import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for parsed LIST, DELETE and RENUM statements. */
class DeleteRenumTest {

  private EvalState state;
  private MockDisplay display;
  private BazLangExecutor executor;
  private AntlrParser parser;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    display = new MockDisplay();
    executor = new BazLangExecutor(state, display);
    parser = new AntlrParser();
    // Set up a simple program
    state.program().put(10, new ProgramLine(10, "PRINT \"HELLO\""));
    state.program().put(20, new ProgramLine(20, "GOTO 40"));
    state.program().put(30, new ProgramLine(30, "PRINT \"WORLD\""));
    state.program().put(40, new ProgramLine(40, "STOP"));
  }

  private void execute(String statement) {
    var parsed = parser.parseReplLine(statement);
    if (parsed instanceof AntlrParser.ParsedLine.Immediate(var stmt)) {
      executor.visit(stmt);
    }
  }

  // ==================== LIST tests ====================

  @Test
  void testListAll() {
    // LIST - lists all lines
    execute("LIST");
    String output = display.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListToOnly() {
    // LIST TO - lists all lines (same as LIST)
    execute("LIST TO");
    String output = display.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListFromN() {
    // LIST n - lists from n to end
    execute("LIST 30");
    String output = display.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertFalse(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListFromNTo() {
    // LIST n TO - lists from n to end
    execute("LIST 30 TO");
    String output = display.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertFalse(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertTrue(output.contains("40 STOP"));
  }

  @Test
  void testListToM() {
    // LIST TO m - lists from MIN to m
    execute("LIST TO 20");
    String output = display.getOutput();
    assertTrue(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertFalse(output.contains("30 PRINT"));
    assertFalse(output.contains("40 STOP"));
  }

  @Test
  void testListNToM() {
    // LIST n TO m - lists from n to m
    execute("LIST 20 TO 30");
    String output = display.getOutput();
    assertFalse(output.contains("10 PRINT"));
    assertTrue(output.contains("20 GOTO"));
    assertTrue(output.contains("30 PRINT"));
    assertFalse(output.contains("40 STOP"));
  }

  // ==================== DELETE tests ====================

  @Test
  void testDeleteSingleLine() {
    // DELETE n - deletes only line n
    execute("DELETE 20");
    assertFalse(state.program().containsKey(20));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
    assertEquals(3, state.program().size());
  }

  @Test
  void testDeleteRange() {
    // DELETE n TO m - deletes lines n through m
    execute("DELETE 20 TO 30");
    assertFalse(state.program().containsKey(20));
    assertFalse(state.program().containsKey(30));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteToEnd() {
    // DELETE n TO - deletes from line n to end
    execute("DELETE 30 TO");
    assertFalse(state.program().containsKey(30));
    assertFalse(state.program().containsKey(40));
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteFromStart() {
    // DELETE TO m - deletes from MIN to line m
    execute("DELETE TO 20");
    assertFalse(state.program().containsKey(10));
    assertFalse(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
    assertEquals(2, state.program().size());
  }

  @Test
  void testDeleteWithoutNumberThrowsError() {
    // DELETE and DELETE TO should throw error
    var ex1 = assertThrows(ReportException.class, () -> execute("DELETE"));
    assertTrue(ex1.getMessage().contains("requires at least one line number"));

    var ex2 = assertThrows(ReportException.class, () -> execute("DELETE TO"));
    assertTrue(ex2.getMessage().contains("requires at least one line number"));
  }

  // ==================== RENUM tests ====================

  @Test
  void testRenumBasic() {
    execute("RENUM");
    // Default: start=10, step=10
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));
    assertTrue(state.program().containsKey(40));
  }

  @Test
  void testRenumWithStart() {
    execute("RENUM 100");
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(120));
    assertTrue(state.program().containsKey(130));
    assertFalse(state.program().containsKey(10));
  }

  @Test
  void testRenumWithStep() {
    execute("RENUM 100 STEP 5");
    assertTrue(state.program().containsKey(100));
    assertTrue(state.program().containsKey(105));
    assertTrue(state.program().containsKey(110));
    assertTrue(state.program().containsKey(115));
  }

  @Test
  void testRenumUpdatesGotoTargets() {
    execute("RENUM 100 STEP 10");
    // Original line 20 had "GOTO 40", should now be "GOTO 130"
    ProgramLine line = state.program().get(110);
    assertNotNull(line);
    assertEquals("GOTO 130", line.sourceText());
  }

  @Test
  void testRenumStepOnly() {
    execute("RENUM STEP 5");
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(15));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(25));
    assertEquals(4, state.program().size());
  }

  @Test
  void testRenumNonExistentGotoTarget() {
    // Set up program with gap: 1000, 1200, 1300 where 1300 has GOTO 1100
    state.program().clear();
    state.program().put(1000, new ProgramLine(1000, "PRINT \"HELLO\""));
    state.program().put(1200, new ProgramLine(1200, "PRINT \"THERE\""));
    state.program().put(1300, new ProgramLine(1300, "GOTO 1100"));

    execute("RENUM");

    // Should renumber to 10, 20, 30
    assertEquals(3, state.program().size());
    assertTrue(state.program().containsKey(10));
    assertTrue(state.program().containsKey(20));
    assertTrue(state.program().containsKey(30));

    // GOTO 1100 should become GOTO 20 (ceiling of 1100 is 1200, which becomes 20)
    ProgramLine line30 = state.program().get(30);
    assertNotNull(line30);
    assertEquals("GOTO 20", line30.sourceText());
  }
}
