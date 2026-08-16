package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.InterpreterReplHandler;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.ProgramStorage;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.io.MockScreen;
import java.nio.file.Path;
import java.util.List;

/**
 * Protocol-agnostic BazLang debugging engine: owns the interpreter, breakpoints, and mock screen
 * for one debugging session, and exposes plain Java methods (no I/O, no {@code println}) for
 * program management, run control, and live expression evaluation. Both {@link DebugSession} (the
 * {@code AgentDebugger} text protocol) and the MCP server ({@code com.davidconneely.bazlang.mcp})
 * are thin adapters over one shared {@code DebugEngine} instance.
 *
 * <p>Run control ({@link #run}, {@link #gotoLine}, {@link #go}) is synchronous: each call drives
 * execution until the programme next breaks, elapses, or stops, then returns — there is no blocking
 * wait for a "next command" inside the engine itself. A breakpoint pauses execution by having the
 * {@link Interpreter.ExecutionListener} set {@link EvalState#setRunning} to {@code false} before
 * the triggering statement executes, which unwinds {@link Interpreter#resume()} back to the caller;
 * a later {@link #go()} call resumes at the exact same location, guarded so the same breakpoint
 * does not immediately re-fire.
 */
public final class DebugEngine {

  /** The outcome of {@link #run}, {@link #gotoLine}, or {@link #go}: where execution stopped. */
  public sealed interface PauseResult {
    /** A location or condition breakpoint fired before executing this statement. */
    record Break(int line, int stmt) implements PauseResult {}

    /** An {@code ELAPSE} condition fired. */
    record Elapse() implements PauseResult {}

    /** The programme ended: normally, via a {@code STOP} statement, or with a runtime error. */
    record Stopped(ReportException report) implements PauseResult {}
  }

  /** The outcome of {@link #evalExpression}: a numeric or string result. */
  public sealed interface EvalResult {
    record Num(double value) implements EvalResult {}

    record Str(String value) implements EvalResult {}
  }

  private final AntlrParser parser;
  private final EvalState state = new EvalState();
  private final BreakpointEngine breaks;
  private final MockScreen mockScreen =
      new MockScreen() {
        @Override
        public void systemPrintln(String text) {
          // Both front-ends format their own responses; suppress REPL echo here.
        }
      };
  private final StatementExecutor executor;
  private final Interpreter interpreter;
  private final InterpreterReplHandler replHandler;

  private boolean paused = false;
  private String firedPauseReason;
  private int resumeGuardLine = -1;
  private int resumeGuardStmt = -1;

  public DebugEngine(AntlrParser parser) {
    this.parser = parser;
    this.breaks = new BreakpointEngine(parser);
    ProgramStorage storage =
        new ProgramStorage(state, parser) {
          @Override
          public void load(String filename) {
            Path p = AgentDebugger.resolveBasPath(filename);
            if (p == null) {
              throw new ReportException(
                  ReportCode.INVALID_FILE_NAME,
                  state.currentLineLabel(),
                  "File not found: " + filename);
            }
            super.load(p.toString());
          }
        };
    ExpressionEvaluator exprEvaluator =
        new ExpressionEvaluator(state, mockScreen, mockScreen, parser);
    this.executor =
        new StatementExecutor(state, mockScreen, mockScreen, storage, exprEvaluator, parser);
    this.interpreter = new Interpreter(state, executor);
    ProgramEditor programEditor = new ProgramEditor(state, mockScreen, parser, executor::evalNum);
    this.replHandler =
        new InterpreterReplHandler(
            mockScreen, mockScreen, parser, state, executor, programEditor, interpreter);
    this.interpreter.setExecutionListener(this::onBeforeStatement);
  }

  private void onBeforeStatement(int line, int stmt) {
    if (line == resumeGuardLine && stmt == resumeGuardStmt) {
      // We just resumed exactly here after a previous pause: don't re-fire the same breakpoint.
      resumeGuardLine = -1;
      resumeGuardStmt = -1;
      return;
    }
    BreakpointEngine.BreakCondition fired =
        breaks.checkFired(line, stmt, mockScreen, executor.getExprEvaluator());
    if (fired != null) {
      firedPauseReason = fired.type() == BreakpointEngine.ConditionType.ELAPSE ? "ELAPSE" : "BREAK";
      state.setRunning(false);
    }
  }

  // ---- accessors shared with both adapters ----

  public MockScreen screen() {
    return mockScreen;
  }

  public BreakpointEngine breakpoints() {
    return breaks;
  }

  public EvalState state() {
    return state;
  }

  public boolean isPaused() {
    return paused;
  }

  // ---- program management ----

  /**
   * Applies one REPL command line (a numbered line, {@code NEW}, {@code LOAD "path"}, {@code
   * DELETE}, {@code RENUM}, {@code REFORMAT}, {@code EDIT}, ...) exactly as the interactive REPL
   * would. Throws {@link DebugEngineException} (carrying the formatted status text) on failure.
   */
  public void applyReplCommand(String cmd) {
    replHandler.handleReplInput(cmd);
    if (state.lastReport().code() != ReportCode.OK) {
      throw new DebugEngineException(mockScreen.getStatus());
    }
  }

  /**
   * Replaces the whole programme with {@code source} (one BASIC line per {@code \n}-separated
   * entry).
   */
  public void loadSource(String source) {
    applyReplCommand("NEW");
    for (String ln : source.split("\n", -1)) {
      if (!ln.isBlank()) {
        applyReplCommand(ln);
      }
    }
  }

  /** Returns the current programme as {@code "<n> <stmt>"} lines joined with {@code \n}. */
  public String listProgram() {
    var sb = new StringBuilder();
    boolean first = true;
    for (var entry : state.program().entrySet()) {
      if (entry.getKey() <= 0) {
        continue; // skip the immediate-mode line
      }
      if (!first) {
        sb.append('\n');
      }
      sb.append(entry.getKey()).append(' ').append(entry.getValue().sourceText());
      first = false;
    }
    return sb.toString();
  }

  // ---- run control ----

  /** Clears runtime state and runs the programme from its first line. */
  public PauseResult run() {
    state.clear();
    if (state.program().isEmpty()) {
      throw new DebugEngineException("no programme loaded");
    }
    state.setPendingJumpLocation(state.program().firstKey(), 1);
    return driveUntilPause();
  }

  /** Runs from line {@code lineNumber} without clearing variables. */
  public PauseResult gotoLine(int lineNumber) {
    state.setPendingJumpLocation(lineNumber, 1);
    return driveUntilPause();
  }

  /** Resumes execution from a breakpoint. Only valid when {@link #isPaused()}. */
  public PauseResult go() {
    if (!paused) {
      throw new DebugEngineException("not paused at a breakpoint");
    }
    resumeGuardLine = state.currentLineLabel();
    resumeGuardStmt = state.currentStatementIndex();
    state.setPendingJumpLocation(resumeGuardLine, resumeGuardStmt);
    breaks.resetTimer();
    return driveUntilPause();
  }

  /** Terminates the currently running/paused programme immediately. */
  public void stop() {
    paused = false;
    state.setRunning(false);
  }

  private PauseResult driveUntilPause() {
    firedPauseReason = null;
    try {
      interpreter.resume();
    } catch (ReportException e) {
      paused = false;
      return new PauseResult.Stopped(e);
    }
    if (firedPauseReason != null) {
      paused = true;
      return firedPauseReason.equals("ELAPSE")
          ? new PauseResult.Elapse()
          : new PauseResult.Break(state.currentLineLabel(), state.currentStatementIndex());
    }
    paused = false;
    return new PauseResult.Stopped(
        new ReportException(
            ReportCode.OK,
            state.currentLineLabel(),
            state.currentStatementIndex(),
            ReportCode.OK.getMessage()));
  }

  // ---- expression evaluation ----

  /** Evaluates a single BazLang expression in the live programme context. */
  public EvalResult evalExpression(String expr) {
    ExpressionEvaluator eval = executor.getExprEvaluator();
    BazLangParser.NumExprContext numCtx = null;
    try {
      numCtx = parser.parseNumExpr(expr);
    } catch (ReportException ignored) {
      // numeric parse failed — fall through to a string-expression attempt below
    }
    if (numCtx != null) {
      return new EvalResult.Num(eval.evalNum(AstLowering.lowerNum(numCtx, 0)));
    }
    var strCtx = parser.parseStrExpr(expr);
    BStr val = eval.evalStr(AstLowering.lowerStr(strCtx, 0));
    return new EvalResult.Str(val.toJavaString());
  }

  /** Executes a single {@code LET}-equivalent assignment to mutate programme state. */
  public void executeAssignment(String assignment) {
    BazLangParser.StatementsContext stmts;
    try {
      stmts = parser.parseStatementsContext(assignment);
    } catch (ReportException e) {
      throw new DebugEngineException("Parse error: " + e.getMessage());
    }
    List<Stmt> lowered = AstLowering.lowerStatements(stmts, 0);
    if (lowered.size() != 1 || !(lowered.get(0) instanceof Stmt.LetStmt letStmt)) {
      throw new DebugEngineException("! requires exactly one assignment statement");
    }
    executor.execute(letStmt);
  }
}
