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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * BazLang debugging engine: owns the interpreter, breakpoints, and mock screen for one debugging
 * session, and exposes plain Java methods (no I/O, no {@code println}) for program management, run
 * control, and live expression evaluation. The MCP server ({@code com.davidconneely.bazlang.mcp})
 * is a thin adapter over one shared {@code DebugEngine} instance.
 *
 * <p>Run control ({@link #run}, {@link #gotoLine}, {@link #go}, {@link #stepInto}, {@link
 * #stepOver}) is synchronous: each call drives execution until the programme next breaks, elapses,
 * steps, hits its safety timeout, or stops, then returns - there is no blocking wait for a "next
 * command" inside the engine itself. A breakpoint pauses execution by having the {@link
 * Interpreter.ExecutionListener} set {@link EvalState#setRunning} to {@code false} before the
 * triggering statement executes, which unwinds {@link Interpreter#resume(int, int)} back to the
 * caller; a later {@link #go()} call resumes at the exact same location, guarded so the same
 * breakpoint does not immediately re-fire. Every run-control call also arms a wall-clock deadline
 * (default {@link #DEFAULT_STEP_TIMEOUT_MS}, overridable per call) as a safety net against a
 * runaway programme with no breakpoint of its own - there is no cancel-while-running mechanism (see
 * docs/spec/mcp.md "Known limitations"), so an unconditionally blocking call would otherwise hang
 * the caller (and, for the MCP server, the whole single-threaded session) forever.
 */
public final class DebugEngine {

  /**
   * Default wall-clock safety cap for {@link #run}/{@link #gotoLine}/{@link #go}/{@link
   * #stepInto}/{@link #stepOver} when the caller doesn't specify one: guards against a programme
   * with an accidental infinite loop and no breakpoint of its own hanging the call forever.
   */
  public static final long DEFAULT_STEP_TIMEOUT_MS = 30_000;

  /**
   * Resolves a bare programme name or file path to an actual {@link Path}.
   *
   * <p>Tries the argument verbatim, then with {@code .bas} appended, then under the canonical
   * example directory. Returns {@code null} when no existing file is found, or when {@code
   * inputPath} isn't a syntactically valid path on this platform at all ({@link Path#of} throws
   * {@link InvalidPathException} for e.g. a bare {@code :} on Windows) - the caller already treats
   * {@code null} as "not found", so an unparseable path is just another way not to find one. Used
   * by this class's own {@code LOAD} handling below, so {@code bazlang_program(load_file)} resolves
   * bare names consistently.
   */
  private static Path resolveBasPath(String inputPath) {
    try {
      Path p = Path.of(inputPath);
      if (Files.exists(p)) {
        return p;
      }
      String name = inputPath.endsWith(".bas") ? inputPath : inputPath + ".bas";
      p = Path.of("src", "example", "bas", name);
      if (Files.exists(p)) {
        return p;
      }
      p = Path.of("app-bazlang", "src", "example", "bas", name);
      if (Files.exists(p)) {
        return p;
      }
      return null;
    } catch (InvalidPathException e) {
      return null;
    }
  }

  /**
   * The outcome of {@link #run}, {@link #gotoLine}, {@link #go}, {@link #stepInto}, or {@link
   * #stepOver}: where execution stopped.
   */
  public sealed interface PauseResult {
    /**
     * A location or condition breakpoint fired before executing this statement.
     *
     * @param line the line number about to execute.
     * @param stmt the flat statement index about to execute.
     */
    record Break(int line, int stmt) implements PauseResult {}

    /** An {@code ELAPSE} condition fired. */
    record Elapse() implements PauseResult {}

    /**
     * {@link #stepInto}/{@link #stepOver} completed one step; this is where it landed.
     *
     * @param line the line number stepped to.
     * @param stmt the flat statement index stepped to.
     */
    record Step(int line, int stmt) implements PauseResult {}

    /**
     * The per-call wall-clock safety deadline was exceeded with no other pause reason.
     *
     * @param line the line number about to execute.
     * @param stmt the flat statement index about to execute.
     */
    record Limit(int line, int stmt) implements PauseResult {}

    /**
     * The programme ended: normally, via a {@code STOP} statement, or with a runtime error.
     *
     * @param report the reason execution stopped; {@link ReportCode#OK} for a normal end or {@code
     *     STOP}, a real error code otherwise.
     */
    record Stopped(ReportException report) implements PauseResult {}
  }

  /** The outcome of {@link #evalExpression}: a numeric or string result. */
  public sealed interface EvalResult {
    /**
     * A numeric result.
     *
     * @param value the result value.
     */
    record Num(double value) implements EvalResult {}

    /**
     * A string result.
     *
     * @param value the result value.
     */
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
  private long stepDeadlineMs = Long.MAX_VALUE;
  private boolean stepArmed = false;
  private boolean stepOver = false;
  private int stepOverBaseDepth = -1;

  /**
   * Creates a debugging session with its own interpreter, breakpoint store, and mock screen.
   *
   * @param parser the parser to use for programs, expressions, and REPL commands.
   */
  public DebugEngine(AntlrParser parser) {
    this.parser = parser;
    this.breaks = new BreakpointEngine(parser);
    ProgramStorage storage =
        new ProgramStorage(state, parser) {
          @Override
          public void load(String filename) {
            Path p = resolveBasPath(filename);
            if (p == null) {
              throw new ReportException(
                  ReportCode.INVALID_FILE_NAME,
                  state.currentLineLabel(),
                  state.currentStatementIndex(),
                  "File not found: " + filename);
            }
            super.load(p.toString());
          }
        };
    ExpressionEvaluator exprEvaluator =
        new ExpressionEvaluator(state, mockScreen, mockScreen, parser);
    this.executor =
        new StatementExecutor(
            state, mockScreen, mockScreen, mockScreen, storage, exprEvaluator, parser);
    this.interpreter = new Interpreter(state, executor);
    ProgramEditor programEditor = new ProgramEditor(state, mockScreen, parser, executor::evalNum);
    this.replHandler =
        new InterpreterReplHandler(
            mockScreen, mockScreen, parser, state, executor, programEditor, interpreter);
    this.interpreter.setExecutionListener(this::onBeforeStatement);
  }

  private void onBeforeStatement(int line, int stmt) {
    if (line == 0) {
      // Line 0 is the Interpreter.executeImmediate sentinel used for REPL/immediate-mode
      // commands (LOAD, NEW, a numbered-line edit, an assignment via applyReplCommand /
      // executeAssignment) - never a real programme line. Breakpoints must not intercept these:
      // an unconditional persistent breakpoint (an ELAPSE one especially, since real wall-clock
      // time keeps advancing between calls) would otherwise fire here, set running=false *before*
      // Interpreter.resume() reaches executor.execute(stmt), and silently cancel the command -
      // e.g. a `LOAD "x"` that never actually loads anything, while still reporting success,
      // because the REPL handler has no way to distinguish "cancelled by a breakpoint" from
      // "ran fine". See the 2026-08-16 entry in localonly-BAZLANG-IMPROVEMENTS.md for how this
      // was found.
      return;
    }
    if (line == resumeGuardLine && stmt == resumeGuardStmt) {
      // We just resumed exactly here after a previous pause: don't re-fire the same breakpoint, and
      // let this statement execute even if a step is armed - stepInto/stepOver's "one step" starts
      // counting from the statement *after* the one we resumed at, exactly like go()'s resume
      // point.
      resumeGuardLine = -1;
      resumeGuardStmt = -1;
      return;
    }
    if (stepArmed && !(stepOver && state.returnStackDepth() > stepOverBaseDepth)) {
      // stepInto always pauses on the next statement; stepOver only pauses once the GOSUB depth is
      // back down to (or shallower than) where stepping started - a statement inside a call it
      // stepped over runs unimpeded (falling through to the breakpoint check below, so a breakpoint
      // inside that call can still interrupt it; see the branch's condition).
      firedPauseReason = "STEP";
      state.setRunning(false);
      disarmStep();
      return;
    }
    if (System.currentTimeMillis() >= stepDeadlineMs) {
      // Safety net: no breakpoint of the programme's own fired within the deadline. Without this, a
      // runaway loop would block the calling tools/call - and, since the MCP server processes one
      // request at a time with no cancel-while-running mechanism, the whole session - forever.
      firedPauseReason = "LIMIT";
      state.setRunning(false);
      disarmStep();
      return;
    }
    BreakpointEngine.BreakCondition fired =
        breaks.checkFired(line, stmt, mockScreen, executor.exprEvaluator());
    if (fired != null) {
      firedPauseReason = fired.type() == BreakpointEngine.ConditionType.ELAPSE ? "ELAPSE" : "BREAK";
      state.setRunning(false);
      disarmStep();
    }
  }

  private void disarmStep() {
    stepArmed = false;
    stepOver = false;
  }

  // ---- accessors shared with both adapters ----

  /**
   * The session's mock screen.
   *
   * @return the mock screen.
   */
  public MockScreen screen() {
    return mockScreen;
  }

  /**
   * The session's breakpoint store.
   *
   * @return the breakpoint engine.
   */
  public BreakpointEngine breakpoints() {
    return breaks;
  }

  /**
   * The session's interpreter state.
   *
   * @return the eval state.
   */
  public EvalState state() {
    return state;
  }

  /**
   * Whether the session is currently paused at a breakpoint.
   *
   * @return {@code true} if paused.
   */
  public boolean isPaused() {
    return paused;
  }

  // ---- program management ----

  /**
   * Applies one REPL command line (a numbered line, {@code NEW}, {@code LOAD "path"}, {@code
   * DELETE}, {@code RENUM}, {@code REFORMAT}, {@code EDIT}, ...) exactly as the interactive REPL
   * would. Throws {@link DebugEngineException} (carrying the formatted status text) on failure.
   *
   * @param cmd the REPL command line to apply.
   */
  public void applyReplCommand(String cmd) {
    replHandler.handleReplInput(cmd);
    if (state.lastReport().code() != ReportCode.OK) {
      throw new DebugEngineException(mockScreen.getStatus());
    }
    if (replacesWholeProgram(cmd)) {
      // NEW and LOAD replace the whole programme; a plain numbered-line edit does not. Flushing
      // here - rather than requiring every caller to remember to - is what actually prevents input
      // queued for one programme from being silently consumed by a different one that happens to
      // read a different input primitive - INKEY$, UINKEY$, and INPUT all share one MockScreen for
      // the lifetime of this engine, which can span many programme loads. See docs/spec/mcp.md's
      // "Input queue" section for the incident that motivated this - two different games' stale
      // queued input each broke a later one.
      mockScreen.clearInputQueues();
    }
  }

  /**
   * True for {@code NEW} and {@code LOAD "..."} - the two REPL commands that replace the whole
   * programme, as opposed to editing a single line of the current one (or {@code MERGE}, which
   * overlays lines onto the existing programme rather than replacing it).
   */
  private static boolean replacesWholeProgram(String cmd) {
    String upper = cmd.trim().toUpperCase();
    return upper.equals("NEW")
        || upper.equals("LOAD")
        || upper.startsWith("LOAD ")
        || upper.startsWith("LOAD\t");
  }

  /**
   * Replaces the whole programme with {@code source} (one BASIC line per {@code \n}-separated
   * entry).
   *
   * @param source the programme source, one line per {@code \n}-separated entry.
   */
  public void loadSource(String source) {
    applyReplCommand("NEW");
    for (String ln : source.split("\n", -1)) {
      if (!ln.isBlank()) {
        applyReplCommand(ln);
      }
    }
  }

  /**
   * Returns the current programme as {@code "<n> <stmt>"} lines joined with {@code \n}.
   *
   * @return the programme listing.
   */
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

  /**
   * Clears runtime state and runs the programme from its first line, with the default timeout.
   *
   * @return where execution stopped.
   */
  public PauseResult run() {
    return run(DEFAULT_STEP_TIMEOUT_MS);
  }

  /**
   * Clears runtime state and runs the programme from its first line. Pauses with {@link
   * PauseResult.Limit} if no other pause reason fires within {@code timeoutMs} (non-positive means
   * "use the default", not "disable" - see {@link #DEFAULT_STEP_TIMEOUT_MS}).
   *
   * @param timeoutMs the wall-clock safety cap, in milliseconds; non-positive to use the default.
   * @return where execution stopped.
   */
  public PauseResult run(long timeoutMs) {
    state.clear();
    if (state.program().isEmpty()) {
      throw new DebugEngineException("no programme loaded");
    }
    disarmStep();
    breaks.resetTimer();
    armDeadline(timeoutMs);
    return driveUntilPause(state.program().firstKey(), 1);
  }

  /**
   * Runs from line {@code lineNumber} without clearing variables, with the default timeout.
   *
   * @param lineNumber the line to start execution at.
   * @return where execution stopped.
   */
  public PauseResult gotoLine(int lineNumber) {
    return gotoLine(lineNumber, DEFAULT_STEP_TIMEOUT_MS);
  }

  /**
   * Runs from line {@code lineNumber} without clearing variables. See {@link #run(long)}.
   *
   * @param lineNumber the line to start execution at.
   * @param timeoutMs the wall-clock safety cap, in milliseconds; non-positive to use the default.
   * @return where execution stopped.
   */
  public PauseResult gotoLine(int lineNumber, long timeoutMs) {
    disarmStep();
    breaks.resetTimer();
    armDeadline(timeoutMs);
    return driveUntilPause(lineNumber, 1);
  }

  /**
   * Resumes execution from a breakpoint, with the default timeout. Only valid when paused.
   *
   * @return where execution stopped.
   */
  public PauseResult go() {
    return go(DEFAULT_STEP_TIMEOUT_MS);
  }

  /**
   * Resumes execution from a breakpoint. See {@link #run(long)}. Only valid when paused.
   *
   * @param timeoutMs the wall-clock safety cap, in milliseconds; non-positive to use the default.
   * @return where execution stopped.
   */
  public PauseResult go(long timeoutMs) {
    requirePaused();
    armResumeGuard();
    disarmStep();
    breaks.resetTimer();
    armDeadline(timeoutMs);
    return driveUntilPause(resumeGuardLine, resumeGuardStmt);
  }

  /**
   * Executes exactly one statement and pauses, entering any {@code GOSUB} it calls. Only valid when
   * paused. With the default timeout.
   *
   * @return where execution stopped.
   */
  public PauseResult stepInto() {
    return stepInto(DEFAULT_STEP_TIMEOUT_MS);
  }

  /**
   * {@link #stepInto()} with an explicit timeout. See {@link #run(long)}.
   *
   * @param timeoutMs the wall-clock safety cap, in milliseconds; non-positive to use the default.
   * @return where execution stopped.
   */
  public PauseResult stepInto(long timeoutMs) {
    requirePaused();
    armResumeGuard();
    stepArmed = true;
    stepOver = false;
    breaks.resetTimer();
    armDeadline(timeoutMs);
    return driveUntilPause(resumeGuardLine, resumeGuardStmt);
  }

  /**
   * Executes exactly one statement and pauses, running any {@code GOSUB} it calls to completion
   * instead of pausing inside it (a breakpoint inside the call can still interrupt it). Only valid
   * when paused. With the default timeout.
   *
   * @return where execution stopped.
   */
  public PauseResult stepOver() {
    return stepOver(DEFAULT_STEP_TIMEOUT_MS);
  }

  /**
   * {@link #stepOver()} with an explicit timeout. See {@link #run(long)}.
   *
   * @param timeoutMs the wall-clock safety cap, in milliseconds; non-positive to use the default.
   * @return where execution stopped.
   */
  public PauseResult stepOver(long timeoutMs) {
    requirePaused();
    armResumeGuard();
    stepArmed = true;
    stepOver = true;
    stepOverBaseDepth = state.returnStackDepth();
    breaks.resetTimer();
    armDeadline(timeoutMs);
    return driveUntilPause(resumeGuardLine, resumeGuardStmt);
  }

  /** Terminates the currently running/paused programme immediately. */
  public void stop() {
    paused = false;
    state.setRunning(false);
    disarmStep();
  }

  private void requirePaused() {
    if (!paused) {
      throw new DebugEngineException("not paused at a breakpoint");
    }
  }

  /** Arms the resume guard at the current pause location, for {@link #driveUntilPause} to use. */
  private void armResumeGuard() {
    resumeGuardLine = state.currentLineLabel();
    resumeGuardStmt = state.currentStatementIndex();
  }

  private void armDeadline(long timeoutMs) {
    long effectiveMs = timeoutMs > 0 ? timeoutMs : DEFAULT_STEP_TIMEOUT_MS;
    stepDeadlineMs = System.currentTimeMillis() + effectiveMs;
  }

  private PauseResult driveUntilPause(int label, int statementIndex) {
    firedPauseReason = null;
    try {
      interpreter.resume(label, statementIndex);
    } catch (ReportException e) {
      paused = false;
      disarmStep();
      return new PauseResult.Stopped(e);
    }
    if (firedPauseReason != null) {
      paused = true;
      return switch (firedPauseReason) {
        case "ELAPSE" -> new PauseResult.Elapse();
        case "STEP" ->
            new PauseResult.Step(state.currentLineLabel(), state.currentStatementIndex());
        case "LIMIT" ->
            new PauseResult.Limit(state.currentLineLabel(), state.currentStatementIndex());
        default -> new PauseResult.Break(state.currentLineLabel(), state.currentStatementIndex());
      };
    }
    paused = false;
    disarmStep();
    return new PauseResult.Stopped(
        new ReportException(
            ReportCode.OK,
            state.currentLineLabel(),
            state.currentStatementIndex(),
            ReportCode.OK.getMessage()));
  }

  // ---- expression evaluation ----

  /**
   * Evaluates a single BazLang expression in the live programme context.
   *
   * @param expr the expression source to parse and evaluate.
   * @return the numeric or string result.
   */
  public EvalResult evalExpression(String expr) {
    ExpressionEvaluator eval = executor.exprEvaluator();
    BazLangParser.NumExprContext numCtx = null;
    try {
      numCtx = parser.parseNumExpr(expr);
    } catch (ReportException ignored) {
      // numeric parse failed - fall through to a string-expression attempt below
    }
    if (numCtx != null) {
      return new EvalResult.Num(eval.evalNum(AstLowering.lowerNum(numCtx, 0)));
    }
    var strCtx = parser.parseStrExpr(expr);
    BStr val = eval.evalStr(AstLowering.lowerStr(strCtx, 0));
    return new EvalResult.Str(val.toJavaString());
  }

  /**
   * Executes a single {@code LET}-equivalent assignment to mutate programme state.
   *
   * @param assignment the assignment source, e.g. {@code x = 1}.
   */
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
