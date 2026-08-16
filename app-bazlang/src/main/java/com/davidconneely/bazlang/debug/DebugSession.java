package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One interactive AgentDebugger session: a thin stdin/stdout text-protocol adapter over a shared
 * {@link DebugEngine}. Parses {@code >}/{@code /}/{@code ?}/{@code !} command lines and formats
 * {@code +}/{@code -} responses; the protocol itself is documented on {@link AgentDebugger}. All
 * actual interpreter/breakpoint/screen state lives in {@link DebugEngine}, which the MCP server
 * ({@code com.davidconneely.bazlang.mcp}) adapts the same way for JSON-RPC.
 */
final class DebugSession {

  private final AntlrParser parser;
  private final DebugEngine engine;
  private final BufferedReader inputReader =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
  private boolean sessionStopped = false;

  DebugSession(AntlrParser parser) {
    this.parser = parser;
    this.engine = new DebugEngine(parser);
  }

  /** Runs the session until the agent stops it or stdin reaches EOF. */
  void run(Path initialFilePath) {
    if (initialFilePath != null) {
      try {
        engine.state().setProgram(parser.parseProgramLines(Files.readString(initialFilePath)));
      } catch (IOException e) {
        System.err.printf(
            "Could not load BASIC file from '%s': %s%n",
            initialFilePath.toAbsolutePath(), e.getMessage());
        return;
      }
    }
    System.out.println("+READY");
    System.out.flush();
    while (!sessionStopped) {
      String line;
      try {
        line = inputReader.readLine();
      } catch (IOException e) {
        break;
      }
      if (line == null) {
        break; // EOF behaves as /STOP
      }
      handleCommand(line.trim());
      System.out.flush();
    }
  }

  private void handleCommand(String cmd) {
    if (cmd.startsWith(">")) {
      handleReplCommand(cmd.substring(1).trim());
    } else if (cmd.startsWith("/")) {
      handleSlashCommand(cmd.substring(1));
    } else if (cmd.startsWith("?")) {
      handleEval(cmd.substring(1).trim());
    } else if (cmd.startsWith("!")) {
      handleLet(cmd.substring(1).trim());
    } else {
      System.out.println("-UNKNOWN COMMAND. Commands start with /, ?, !, or >");
    }
  }

  private void handleSlashCommand(String cmd) {
    if (cmd.isEmpty()) {
      System.out.println("-Command expected after /");
      return;
    }
    String upper = cmd.toUpperCase();
    if (upper.equals("GO")) {
      handleGo();
    } else if (upper.equals("STOP")) {
      System.out.println("+");
      System.out.flush();
      engine.stop();
      sessionStopped = true;
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
  }

  private void handleReplCommand(String cmd) {
    if (cmd.isEmpty()) {
      System.out.println("-REPL command expected after >");
      return;
    }
    String upper = cmd.toUpperCase();

    // Special cases that can't be delegated directly:
    if (upper.equals("LIST")) {
      System.out.println("+" + QuotedArg.format(engine.listProgram()));
      return;
    }
    if (upper.equals("RUN")) {
      if (engine.state().program().isEmpty()) {
        System.out.println("-no programme loaded; use >n stmt or >LOAD \"path\", then >RUN");
        return;
      }
      printPauseResult(engine.run());
      return;
    }
    if (upper.equals("GOTO") || upper.startsWith("GOTO ") || upper.startsWith("GOTO\t")) {
      String rest = cmd.length() > 4 ? cmd.substring(4).trim() : "";
      if (rest.isEmpty()) {
        System.out.println("-GOTO requires a line number");
        return;
      }
      int n;
      try {
        n = Integer.parseInt(rest);
      } catch (NumberFormatException e) {
        System.out.println("-GOTO requires a valid line number");
        return;
      }
      printPauseResult(engine.gotoLine(n));
      return;
    }

    // All other commands (NEW, LOAD, numbered lines, REFORMAT, etc) are delegated to the engine.
    try {
      engine.applyReplCommand(cmd);
      System.out.println("+");
    } catch (DebugEngineException e) {
      System.out.println("-" + e.getMessage());
    }
  }

  private void handleGo() {
    if (!engine.isPaused()) {
      System.out.println("-/GO is only valid when paused at a breakpoint; use >RUN");
      return;
    }
    printPauseResult(engine.go());
  }

  private void printPauseResult(DebugEngine.PauseResult result) {
    String reason =
        switch (result) {
          case DebugEngine.PauseResult.Break(int line, int stmt) -> "BREAK AT " + line + ":" + stmt;
          case DebugEngine.PauseResult.Elapse ignored -> "ELAPSE";
          case DebugEngine.PauseResult.Stopped(ReportException report) -> "STOP " + report.format();
        };
    System.out.println("+" + reason);
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
    String grid =
        ScreenText.buildScreenString(engine.screen(), rStart, rEnd, cStart, cEnd, showAttr);
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
      engine.screen().queueInkey(BStr.fromByte(b & 0xFF));
    }
    decoded
        .codePoints()
        .forEach(
            cp ->
                engine
                    .screen()
                    .queueUinkey(BStr.fromJavaString(new String(Character.toChars(cp)))));
    engine.screen().queueInput(decoded);
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
    engine.breakpoints().add(brk);
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
      engine.breakpoints().clearPersistent();
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
    engine.breakpoints().clearAt(line, stmt);
    System.out.println("+");
  }

  private void handleEval(String expr) {
    if (expr.isEmpty()) {
      System.out.println("-? requires an expression");
      return;
    }
    try {
      DebugEngine.EvalResult result = engine.evalExpression(expr);
      switch (result) {
        case DebugEngine.EvalResult.Num(double value) ->
            System.out.printf("+%s%n", ExpressionEvaluator.formatNum(value));
        case DebugEngine.EvalResult.Str(String value) ->
            System.out.printf("+%s%n", QuotedArg.format(value));
      }
    } catch (ReportException e) {
      System.out.printf("-%s%n", e.getMessage());
    }
  }

  private void handleLet(String args) {
    if (args.isEmpty()) {
      System.out.println("-! requires an assignment: ! <var> = <expr>");
      return;
    }
    try {
      engine.executeAssignment(args);
      System.out.println("+");
    } catch (DebugEngineException | ReportException e) {
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
      engine.screen().resize(newRows, newCols);
    } catch (NumberFormatException e) {
      System.out.println("-Invalid values for /SSD");
      return;
    }
    System.out.println("+");
  }
}
