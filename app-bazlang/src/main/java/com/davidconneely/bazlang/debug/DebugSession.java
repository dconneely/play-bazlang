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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One interactive AgentDebugger session: owns the interpreter state, the mock screen, the
 * breakpoint engine, and the stdin/stdout command loop. The protocol itself is documented on {@link
 * AgentDebugger}.
 */
final class DebugSession {

  private final AntlrParser parser;
  private final EvalState state = new EvalState();
  private final BufferedReader inputReader =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
  private final BreakpointEngine breaks;
  private final MockScreen mockScreen =
      new MockScreen() {
        @Override
        public void systemPrintln(String text) {
          // AgentDebugger suppresses REPL echoing to preserve the protocol
        }
      };
  private final StatementExecutor executor;
  private final Interpreter interpreter;
  private final InterpreterReplHandler replHandler;

  private boolean runRequested = false;
  private boolean gotoRequested = false;
  private int gotoTarget = -1;
  private boolean inBreakpoint = false;
  private boolean sessionStopped = false;

  DebugSession(AntlrParser parser) {
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
    this.interpreter.setExecutionListener(
        (line, stmt) -> {
          final var firedBreak =
              breaks.checkFired(line, stmt, mockScreen, executor.getExprEvaluator());
          if (firedBreak != null) {
            String reason =
                firedBreak.type() == BreakpointEngine.ConditionType.ELAPSE
                    ? "ELAPSE"
                    : "BREAK AT " + line + ":" + stmt;
            blockAndListen(reason);
          }
        });
  }

  /** Runs the session until the agent stops it or stdin reaches EOF. */
  void run(Path initialFilePath) {
    if (initialFilePath != null) {
      try {
        state.setProgram(parser.parseProgramLines(Files.readString(initialFilePath)));
      } catch (IOException e) {
        System.err.printf(
            "Could not load BASIC file from '%s': %s%n",
            initialFilePath.toAbsolutePath(), e.getMessage());
        return;
      }
    }

    // Outer execution loop: idle at +READY (or after each run), then execute when >RUN/>GOTO.
    String idleReason = "READY";
    while (true) {
      if (idleReason != null) {
        blockAndListen(idleReason);
      }
      idleReason = null;

      if (!state.isRunning() && !runRequested && !gotoRequested) {
        break; // STOP or EOF with no pending run
      }

      if (!runRequested && !gotoRequested) {
        // Should not happen — CONT from idle is rejected in handleCommand
        idleReason = "READY";
        continue;
      }

      boolean doRun = runRequested;
      int target = gotoTarget;
      runRequested = false;
      gotoRequested = false;
      sessionStopped = false;

      if (doRun) {
        state.clear();
        if (state.program().isEmpty()) {
          // Nothing to run — report an immediate stop and wait for more REPL commands
          idleReason =
              "STOP "
                  + new ReportException(ReportCode.OK, 0, 1, ReportCode.OK.getMessage()).format();
          continue;
        }
        state.setPendingJumpLocation(state.program().firstKey(), 1);
      } else {
        state.setPendingJumpLocation(target, 1);
      }

      String exitReason;
      try {
        interpreter.resume();
        exitReason =
            "STOP "
                + new ReportException(
                        ReportCode.OK,
                        state.currentLineLabel(),
                        state.currentStatementIndex(),
                        ReportCode.OK.getMessage())
                    .format();
      } catch (ReportException e) {
        exitReason = "STOP " + e.format();
      }

      // If >RUN/GOTO was sent mid-execution (from a breakpoint), restart without a stop report.
      if (runRequested || gotoRequested) {
        continue;
      }

      // If STOP was sent during execution, the agent already got '+' — no further output needed.
      if (sessionStopped) {
        break;
      }

      idleReason = exitReason;
    }
  }

  private void blockAndListen(String reason) {
    inBreakpoint = !reason.equals("READY") && !reason.startsWith("STOP ");
    System.out.println("+" + reason);
    System.out.flush();
    boolean resumed = false;
    while (!resumed) {
      try {
        String line = inputReader.readLine();
        if (line == null) {
          state.setRunning(false);
          resumed = true;
        } else {
          resumed = handleCommand(line.trim());
          if (!resumed) {
            System.out.flush();
          }
        }
      } catch (IOException e) {
        state.setRunning(false);
        resumed = true;
      }
    }
    inBreakpoint = false;
    if (state.isRunning()) {
      breaks.resetTimer();
    }
  }

  private boolean handleCommand(String cmd) {
    if (cmd.startsWith(">")) {
      return handleReplCommand(cmd.substring(1).trim());
    } else if (cmd.startsWith("/")) {
      return handleSlashCommand(cmd.substring(1));
    } else if (cmd.startsWith("?")) {
      handleEval(cmd.substring(1).trim());
    } else if (cmd.startsWith("!")) {
      handleLet(cmd.substring(1).trim());
    } else {
      System.out.println("-UNKNOWN COMMAND. Commands start with /, ?, !, or >");
    }
    return false;
  }

  private boolean handleSlashCommand(String cmd) {
    if (cmd.isEmpty()) {
      System.out.println("-Command expected after /");
      return false;
    }
    String upper = cmd.toUpperCase();
    if (upper.equals("GO")) {
      if (!inBreakpoint) {
        System.out.println("-/GO is only valid when paused at a breakpoint; use >RUN");
        return false;
      }
      return true;
    } else if (upper.equals("STOP")) {
      System.out.println("+");
      System.out.flush();
      state.setRunning(false);
      sessionStopped = true;
      return true;
    } else if (upper.equals("SPB") || upper.startsWith("SPB ") || upper.startsWith("SPB\t")) {
      handleSpb(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else if (upper.equals("S1B") || upper.startsWith("S1B ") || upper.startsWith("S1B\t")) {
      handleS1b(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else if (upper.equals("CPB") || upper.startsWith("CPB ") || upper.startsWith("CPB\t")) {
      handleCpb(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else if (upper.equals("RSC") || upper.startsWith("RSC ") || upper.startsWith("RSC\t")) {
      handleRsc(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else if (upper.equals("PIQ") || upper.startsWith("PIQ ") || upper.startsWith("PIQ\t")) {
      handlePiq(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else if (upper.equals("SSD") || upper.startsWith("SSD ") || upper.startsWith("SSD\t")) {
      handleSsd(cmd.length() > 3 ? cmd.substring(3).trim() : "");
    } else {
      System.out.println(
          "-UNKNOWN / COMMAND. Allowed: /GO, /STOP, /SPB, /S1B, /CPB, /RSC, /PIQ, /SSD");
    }
    return false;
  }

  private boolean handleReplCommand(String cmd) {
    if (cmd.isEmpty()) {
      System.out.println("-REPL command expected after >");
      return false;
    }
    String upper = cmd.toUpperCase();

    // Special cases that can't be delegated directly:
    if (upper.equals("LIST")) {
      System.out.println("+" + QuotedArg.format(buildProgramListing()));
      return false;
    }
    if (upper.equals("RUN")) {
      if (state.program().isEmpty()) {
        System.out.println("-no programme loaded; use >n stmt or >LOAD \"path\", then >RUN");
        return false;
      }
      runRequested = true;
      if (inBreakpoint) {
        state.setRunning(false);
      }
      return true;
    }
    if (upper.equals("GOTO") || upper.startsWith("GOTO ") || upper.startsWith("GOTO\t")) {
      String rest = cmd.length() > 4 ? cmd.substring(4).trim() : "";
      if (rest.isEmpty()) {
        System.out.println("-GOTO requires a line number");
        return false;
      }
      int n;
      try {
        n = Integer.parseInt(rest);
      } catch (NumberFormatException e) {
        System.out.println("-GOTO requires a valid line number");
        return false;
      }
      if (inBreakpoint) {
        state.setPendingJumpLocation(n, 1);
      } else {
        gotoTarget = n;
        gotoRequested = true;
      }
      return true;
    }

    // All other commands (NEW, LOAD, numbered lines, REFORMAT, etc) are delegated to the handler.
    replHandler.handleReplInput(cmd);
    if (state.lastReport().code() == ReportCode.OK) {
      System.out.println("+");
    } else {
      System.out.println("-" + mockScreen.getStatus());
    }
    return false;
  }

  private String buildProgramListing() {
    var sb = new StringBuilder();
    boolean first = true;
    for (var entry : state.program().entrySet()) {
      if (entry.getKey() <= 0) {
        continue;
      }
      if (!first) {
        sb.append('\n');
      }
      sb.append(entry.getKey()).append(' ').append(entry.getValue().sourceText());
      first = false;
    }
    return sb.toString();
  }

  private void handleRsc(String args) {
    boolean showAttr = args.toUpperCase().endsWith("ATTR");
    String numPart = args.replaceAll("(?i)\\bATTR\\b", "").trim();
    String[] parts = numPart.split("\\s+");
    if (parts.length != 4) {
      System.out.println("-/RSC requires rowTop colLeft rowBottom colRight [ATTR]");
      return;
    }
    int rStart;
    int cStart;
    int rEnd;
    int cEnd;
    try {
      rStart = Integer.parseInt(parts[0]);
      cStart = Integer.parseInt(parts[1]);
      rEnd = Integer.parseInt(parts[2]);
      cEnd = Integer.parseInt(parts[3]);
    } catch (NumberFormatException e) {
      System.out.println("-Invalid coordinates for /RSC");
      return;
    }
    String grid = ScreenText.buildScreenString(mockScreen, rStart, rEnd, cStart, cEnd, showAttr);
    System.out.println("+" + QuotedArg.format(grid));
  }

  private void handlePiq(String text) {
    if (!text.startsWith("\"")) {
      System.out.println("-/PIQ argument must be a quoted string: /PIQ \"text\"");
      return;
    }
    String decoded = QuotedArg.parse(text);
    if (decoded == null) {
      System.out.println("-Invalid quoted string for /PIQ");
      return;
    }
    byte[] bytes = decoded.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      mockScreen.queueInkey(BStr.fromByte(b & 0xFF));
    }
    decoded
        .codePoints()
        .forEach(
            cp -> mockScreen.queueUinkey(BStr.fromJavaString(new String(Character.toChars(cp)))));
    mockScreen.queueInput(decoded);
    System.out.println("+");
  }

  private void parseBrkArgs(String args, boolean persistent) {
    if (args.isEmpty()) {
      System.out.println(
          "-requires <line>:<stmt> [<cond>] or <cond>"
              + " where <cond> is CSC \"<text>\", ELAPSE <ms>, ?<expr>, or EVERY <n>");
      return;
    }
    int line = -1;
    int stmt = -1;
    String condStr;
    if (Character.isDigit(args.charAt(0))) {
      int spaceIdx = args.indexOf(' ');
      String locStr = spaceIdx >= 0 ? args.substring(0, spaceIdx) : args;
      condStr = spaceIdx >= 0 ? args.substring(spaceIdx).trim() : null;
      int colonIdx = locStr.indexOf(':');
      if (colonIdx < 0) {
        System.out.println("-requires <line>:<stmt>");
        return;
      }
      try {
        line = Integer.parseInt(locStr.substring(0, colonIdx));
        stmt = Integer.parseInt(locStr.substring(colonIdx + 1));
      } catch (NumberFormatException e) {
        System.out.println("-Invalid line:stmt");
        return;
      }
    } else {
      condStr = args;
    }
    BreakpointEngine.BreakCondition brk =
        (condStr == null || condStr.isEmpty())
            ? new BreakpointEngine.BreakCondition(
                line, stmt, BreakpointEngine.ConditionType.NONE, null, 0, persistent, 0, null)
            : BreakpointEngine.parseCondition(line, stmt, condStr, persistent);
    if (brk == null) {
      System.out.println(
          "-Invalid condition — expected CSC \"<text>\","
              + " ELAPSE <ms>, ?<expression>, or EVERY <n>");
      return;
    }
    breaks.add(brk);
    System.out.println("+");
  }

  private void handleSpb(String args) {
    parseBrkArgs(args, true);
  }

  private void handleS1b(String args) {
    parseBrkArgs(args, false);
  }

  private void handleCpb(String args) {
    if (args.isEmpty()) {
      breaks.clearPersistent();
      System.out.println("+");
      return;
    }
    int colonIdx = args.indexOf(':');
    if (colonIdx < 0) {
      System.out.println("-/CPB requires <line>:<stmt> or no arguments");
      return;
    }
    int line;
    int stmt;
    try {
      line = Integer.parseInt(args.substring(0, colonIdx).trim());
      stmt = Integer.parseInt(args.substring(colonIdx + 1).trim());
    } catch (NumberFormatException e) {
      System.out.println("-Invalid line:stmt in /CPB");
      return;
    }
    breaks.clearAt(line, stmt);
    System.out.println("+");
  }

  private void handleEval(String expr) {
    if (expr.isEmpty()) {
      System.out.println("-? requires an expression");
      return;
    }
    ExpressionEvaluator eval = executor.getExprEvaluator();
    // Parse numeric first; only fall through to string if the *parse* fails.
    // If the parse succeeds but evaluation fails (e.g. undefined variable),
    // report that error directly rather than showing a misleading string-parse error.
    BazLangParser.NumExprContext numCtx = null;
    try {
      numCtx = parser.parseNumExpr(expr);
    } catch (ReportException ignored) {
      // numeric parse failed — will attempt string expression below
    }
    if (numCtx != null) {
      try {
        double val = eval.evalNum(AstLowering.lowerNum(numCtx, 0));
        System.out.printf("+%s%n", ExpressionEvaluator.formatNum(val));
      } catch (ReportException e) {
        System.out.printf("-%s%n", e.getMessage());
      }
      return;
    }
    try {
      var strCtx = parser.parseStrExpr(expr);
      BStr val = eval.evalStr(AstLowering.lowerStr(strCtx, 0));
      System.out.printf("+%s%n", QuotedArg.format(val.toJavaString()));
    } catch (ReportException e) {
      System.out.printf("-%s%n", e.getMessage());
    }
  }

  private void handleLet(String args) {
    if (args.isEmpty()) {
      System.out.println("-! requires an assignment: ! <var> = <expr>");
      return;
    }
    BazLangParser.StatementsContext stmts;
    try {
      stmts = parser.parseStatementsContext(args);
    } catch (ReportException e) {
      System.out.printf("-Parse error: %s%n", e.getMessage());
      return;
    }
    List<Stmt> lowered = AstLowering.lowerStatements(stmts, 0);
    if (lowered.size() != 1 || !(lowered.get(0) instanceof Stmt.LetStmt letStmt)) {
      System.out.println("-! requires exactly one assignment statement");
      return;
    }
    try {
      executor.execute(letStmt);
      System.out.println("+");
    } catch (ReportException e) {
      System.out.printf("-%s%n", e.getMessage());
    }
  }

  private void handleSsd(String args) {
    if (args.isEmpty()) {
      System.out.println("-/SSD requires <rows> <cols> — use ?TEXTH, ?TEXTW to read dimensions");
      return;
    }
    String[] parts = args.split("\\s+");
    if (parts.length < 2) {
      System.out.println("-/SSD requires <rows> <cols>");
      return;
    }
    try {
      int newRows = Integer.parseInt(parts[0]);
      int newCols = Integer.parseInt(parts[1]);
      mockScreen.resize(newRows, newCols);
    } catch (NumberFormatException e) {
      System.out.println("-Invalid values for /SSD");
      return;
    }
    System.out.println("+");
  }
}
