package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockScreen;
import org.junit.jupiter.api.Test;

/**
 * Proves a compiled {@link Program} - its {@link ProgramLine}s, their lowered/cached {@code Stmt}
 * lists, and the variable ids baked into those lists' AST nodes - can be reused by a second {@link
 * EvalState} without re-lowering, while each session's own variable values stay independent. See
 * {@code docs/spec/architecture.md}'s "Variable id indexing" section and {@code
 * docs/tasks/cross-session-ast-sharing.md} for the design this exercises.
 */
class CrossSessionSharingTest {

  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private static Interpreter newInterpreter(EvalState state) {
    var screen = new MockScreen();
    return new Interpreter(state, new StatementExecutor(state, screen, screen, screen));
  }

  @Test
  void secondSessionReusesTheFirstsLoweredAstWithIndependentValues() {
    var program = new Program();
    var state1 = new EvalState(program);
    newInterpreter(state1).execute(PARSER.parseProgramLines("10 LET X=42\n"));

    // First session set its own X.
    assertEquals(42.0, state1.numVar("X"));

    // A second session over the *same* Program starts with no value for X of its own - the shared
    // Program only carries the compiled lines/ids, never session values.
    var state2 = new EvalState(program);
    assertFalse(state2.hasNumVar("X"));

    // The line's cached Stmt list is the identical object for both sessions - proving the second
    // session's execution below reuses the first's lowering rather than re-parsing/re-lowering.
    var line = program.get(10);
    assertSame(
        line.getFlattenedStatements(PARSER, state1), line.getFlattenedStatements(PARSER, state2));

    // Resume directly (not via execute(), which would re-populate the Program's lines from a fresh
    // Map and defeat the point) - runs the same cached Stmt list against state2's own storage.
    newInterpreter(state2).resume(program.firstKey(), 1);
    assertEquals(42.0, state2.numVar("X"));

    // Both sessions computed the same result from the same ids, but into separate storage: mutating
    // one doesn't affect the other.
    state1.setNumVar("X", 99.0);
    assertEquals(99.0, state1.numVar("X"));
    assertEquals(42.0, state2.numVar("X"));
  }

  @Test
  void idsAssignedToTheSameNameAgreeAcrossSessionsSharingAProgram() {
    var program = new Program();
    var state1 = new EvalState(program);
    int idFromSession1 = state1.numVarId("SCORE");

    var state2 = new EvalState(program);
    int idFromSession2 = state2.numVarId("SCORE");

    assertEquals(idFromSession1, idFromSession2);
    assertNotSame(state1.numVarRefById(idFromSession1), state2.numVarRefById(idFromSession2));
  }
}
