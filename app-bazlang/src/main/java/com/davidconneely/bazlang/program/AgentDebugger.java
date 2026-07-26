package com.davidconneely.bazlang.program;

import com.davidconneely.bazlang.AstAnnotator;
import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ExpressionEvaluator;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.StatementExecutor;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.io.MockScreen;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.antlr.v4.runtime.tree.ParseTree;

/// Interactive debugger for BazLang programmes, designed for use by LLM agents over stdin/stdout
/// pipes.
///
/// Without an argument the debugger starts in a blank REPL state — no programme is loaded and the
/// agent uses `>` REPL commands to load or enter code before running it. With a file argument the
/// named programme is pre-loaded so the agent sees `+READY` with the programme already in place.
///
/// ### Protocol
///
/// After `+READY`, the agent sends commands (one per line). Each command produces **exactly one**
/// response line — either `+[data]` (success) or `-<message>` (error) — with no prompt between
/// commands. Multiple commands may be batched in a single write; responses arrive in the same
// order.
///
/// `/GO` and `>RUN`/`>GOTO` produce a **deferred response** — the response arrives only when the
/// programme next blocks. They must be the **last command** in any client message; commands sent
/// after them in the same message would be consumed in the *next* break context. `/STOP` responds
/// `+` immediately.
///
/// Deferred responses — `+<reason>` — where `<reason>` is one of:
/// - `READY` — no statement has yet executed (always the first block)
/// - `BREAK AT <line>:<stmt>` — a location breakpoint (with or without a condition guard) fired
/// - `ELAPSE` — an `ELAPSE` condition fired
/// - `STOP <code> <msg>, <line>:<stmt>` — the programme ended; code `0` means normal end,
///   code `9` means a `STOP` statement, any other code is a runtime error
///
/// ### Slash commands (prefixed with `/`)
///
/// **`/GO`**
/// Resume execution from a breakpoint. Only valid when paused at a breakpoint — use `>RUN` to
/// start or restart. Resets the `ELAPSE` timer. Responds `+<reason>` when the programme next
/// blocks.
///
/// **`/STOP`**
/// Terminate the programme immediately. Responds `+`.
///
/// **`/SPB <line>:<stmt>`**
/// Set a **persistent** location breakpoint (statement indices are 1-based). Fires before the
/// named statement executes. Persists until removed with `/CPB`. Responds `+`.
///
/// **`/SPB <line>:<stmt> <cond>`**
/// Persistent location breakpoint with a condition guard: only fires when both the location and
/// condition are satisfied. Persists until cleared. Responds `+`.
///
/// **`/SPB <cond>`**
/// Persistent condition-only breakpoint with no location filter. Checked on every statement.
/// Persists until removed with `/CPB`. Responds `+`.
///
/// **`/S1B <line>:<stmt>`**
/// Set a **one-time** location breakpoint. Fires once, then removes itself. Responds `+`.
///
/// **`/S1B <line>:<stmt> <cond>`**
/// One-time location breakpoint with a condition guard. Fires once when both the location and
/// condition are satisfied, then removes itself. Responds `+`.
///
/// **`/S1B <cond>`**
/// One-time condition-only breakpoint with no location filter. Checked on every statement.
/// Single-shot: clears automatically after firing. Responds `+`.
///
/// **`/CPB <line>:<stmt>`**
/// Clear all persistent breakpoints registered at that exact location. Responds `+`.
///
/// **`/CPB`**
/// Clear all persistent breakpoints. Responds `+`.
///
/// **`/RSC <rowTop> <colLeft> <rowBottom> <colRight> [ATTR]`**
/// Read screen content: dump the given rectangle of the screen buffer as a QuotedArg string. All
/// indices are 0-based. Rows are separated by `\n`; runs of spaces are compressed to `{N}`.
/// `ATTR` adds `[fg,bg]` colour annotations.
/// Responds `+"<grid>"`.
///
/// **`/PIQ "<text>"`**
/// Post to input queue: enqueue `<text>` for keyboard and input reads. The argument must be a
/// QuotedArg string. `INKEY$` receives one byte per UTF-8 byte; `UINKEY$` receives one BStr per
/// Unicode codepoint; `INPUT` receives the full decoded text as one line.
/// Responds `+`.
///
/// **`/SSD <rows> <cols>`**
/// Set screen dimensions: resize the virtual screen buffer. To read current dimensions without
/// resizing, use `?TEXTH` and `?TEXTW`. Responds `+`.
///
/// ### Expression and assignment commands
///
/// **`?<expression>`**
/// Evaluate a single BazLang expression in the live programme context. Numeric results are
/// formatted like BazLang's `PRINT`; string results are returned as QuotedArg. Array elements,
/// functions, and arithmetic are all supported. Side effects (`INKEY$`, `RND`, etc.) apply.
/// Responds `+<value>`. Send one `?` per expression.
///
/// **`!<assignmentTarget> = <expression>`**
/// Execute a single `LET` statement to mutate programme state. Only a bare assignment is
/// accepted — no colon-separated multi-statement sequences. Responds `+`.
///
/// ### REPL commands (prefixed with `>`)
///
/// **`>n [stmt]`**
/// Add or replace line `n` with `stmt`. If `stmt` is omitted, the line is deleted.
/// Responds `+`.
///
/// **`>NEW`**
/// Clear the programme and all runtime state (variables, stacks, etc.). Responds `+`.
///
/// **`>LOAD "path"`**
/// Load a programme from a file, replacing the current programme. Accepts bare filenames (with or
/// without `.bas`) resolved against the example directory. Responds `+` on success, `-` on error.
///
/// **`>LIST`**
/// Return the current programme as a QuotedArg string, with lines separated by `\n`. Responds
/// `+"<listing>"`.
///
/// **`>RUN`**
/// Clear runtime state (variables, stacks) and run the programme from its first line. Produces a
/// deferred response. Also valid when paused at a breakpoint — aborts the current run and
/// restarts. Responds `+<stop-reason>` when the programme next blocks.
///
/// **`>GOTO n`**
/// Run the programme from line `n` without clearing variables. Produces a deferred response.
/// Also valid when paused at a breakpoint — jumps to line `n` and continues.
///
/// ### Break conditions
///
/// - `CSC "<text>"` — screen buffer contains the given text (case-insensitive). QuotedArg.
/// - `ELAPSE <ms>` — at least `<ms>` wall-clock milliseconds have elapsed since the last `/GO`
///   (or since the programme started if `/GO` has not yet been sent).
/// - `?<expression>` — BazLang expression is truthy: non-zero for numeric results, non-empty for
///   string results. Prefer `/SPB <line>:<stmt> ?<expr>` over `/SPB ?<expr>`; condition-only
///   breaks evaluate before every BASIC statement.
/// - `EVERY <n>` — fires every `n`th time the condition is checked. The counter is per-breakpoint
///   and starts at zero when the breakpoint is registered.
///
/// ### QuotedArg String Format
///
/// Double-quoted strings used as command arguments and returned in responses. Escape sequences:
///
/// | Escape | Meaning |
/// |:---|:---|
/// | `\"` | Double-quote character (chr 34) |
/// | `\\` | Backslash character (chr 92) |
/// | `\n` | LF (chr 10) |
/// | `\r` | CR (chr 13) |
/// | `\e` | ESC (chr 27) |
public final class AgentDebugger {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private enum ConditionType {
    NONE,
    VIEW,
    ELAPSE,
    EXPR,
    EVERY
  }

  private record BreakCondition(
      int line,
      int stmt,
      ConditionType type,
      String seeText,
      long timeoutMs,
      boolean persistent,
      int everyN,
      AtomicInteger counter) {}

  private static boolean screenContainsText(MockScreen mockScreen, String text) {
    String query = text.toLowerCase();
    int rows = mockScreen.printHeight();
    int cols = mockScreen.printWidth();
    for (int r = 0; r < rows; r++) {
      StringBuilder sb = new StringBuilder();
      for (int c = 0; c < cols; c++) {
        sb.appendCodePoint(mockScreen.getScreenCodepoint(r, c));
      }
      if (sb.toString().toLowerCase().contains(query)) {
        return true;
      }
    }
    return false;
  }

  private static String buildScreenString(
      MockScreen mockScreen, int rStart, int rEnd, int cStart, int cEnd, boolean showAttr) {
    int maxRows = mockScreen.printHeight();
    int maxCols = mockScreen.printWidth();
    int r1 = Math.clamp(rStart, 0, maxRows - 1);
    int r2 = Math.clamp(rEnd, 0, maxRows - 1);
    int c1 = Math.clamp(cStart, 0, maxCols - 1);
    int c2 = Math.clamp(cEnd, 0, maxCols - 1);
    StringBuilder output = new StringBuilder();
    int lastAttr = -1;
    for (int r = r1; r <= r2; r++) {
      if (r > r1) {
        output.append('\n');
      }
      int c = c1;
      while (c <= c2) {
        int cp = mockScreen.getScreenCodepoint(r, c);
        if (cp == ' ') {
          int spaceAttr = showAttr ? mockScreen.getScreenAttributes(r, c) : 0;
          int start = c;
          while (c <= c2
              && mockScreen.getScreenCodepoint(r, c) == ' '
              && (!showAttr || mockScreen.getScreenAttributes(r, c) == spaceAttr)) {
            c++;
          }
          int count = c - start;
          if (showAttr && spaceAttr != lastAttr) {
            output
                .append('[')
                .append(spaceAttr & 7)
                .append(',')
                .append((spaceAttr >> 3) & 7)
                .append(']');
            lastAttr = spaceAttr;
          }
          if (count > 4) {
            output.append('{').append(count).append('}');
          } else {
            output.append(" ".repeat(count));
          }
        } else {
          if (showAttr) {
            int attr = mockScreen.getScreenAttributes(r, c);
            if (attr != lastAttr) {
              output.append('[').append(attr & 7).append(',').append((attr >> 3) & 7).append(']');
              lastAttr = attr;
            }
          }
          output.appendCodePoint(cp);
          c++;
        }
      }
    }
    return output.toString();
  }

  /**
   * Parses a double-quoted string argument such as {@code "hello \"world\\"}.
   *
   * <p>Supports {@code \"} → {@code "} and {@code \\} → {@code \}. Returns {@code null} if the
   * argument does not start with {@code "}, is not properly closed, or ends with an unmatched
   * backslash.
   */
  private static String parseQuotedArg(String arg) {
    if (!arg.startsWith("\"")) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    int i = 1;
    while (i < arg.length()) {
      char c = arg.charAt(i);
      if (c == '"') {
        return i == arg.length() - 1 ? sb.toString() : null;
      } else if (c == '\\') {
        if (i + 1 >= arg.length()) {
          return null;
        }
        char next = arg.charAt(i + 1);
        if (next == '"') {
          sb.append('"');
        } else if (next == '\\') {
          sb.append('\\');
        } else if (next == 'n') {
          sb.append('\n');
        } else if (next == 'r') {
          sb.append('\r');
        } else if (next == 'e') {
          sb.append('\u001B');
        } else {
          sb.append('\\').append(next);
        }
        i += 2;
      } else {
        sb.append(c);
        i++;
      }
    }
    return null; // missing closing quote
  }

  /** Encodes a Java string as a QuotedArg for protocol output. */
  private static String formatQuotedArg(String value) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\u001B' -> sb.append("\\e");
        default -> sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static BreakCondition parseCondition(
      int line, int stmt, String cond, boolean persistent) {
    String upper = cond.toUpperCase();
    if (upper.startsWith("CSC")) {
      String quotedArg = cond.substring(3).trim();
      String text = parseQuotedArg(quotedArg);
      if (text == null) {
        return null;
      }
      return new BreakCondition(line, stmt, ConditionType.VIEW, text, 0, persistent, 0, null);
    }
    if (upper.startsWith("ELAPSE")) {
      String rest = cond.substring(6).trim();
      if (rest.isEmpty()) {
        return null;
      }
      try {
        long ms = Long.parseLong(rest);
        return new BreakCondition(line, stmt, ConditionType.ELAPSE, null, ms, persistent, 0, null);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    if (cond.startsWith("?")) {
      String exprSource = cond.substring(1).trim();
      if (exprSource.isEmpty()) {
        return null;
      }
      return new BreakCondition(line, stmt, ConditionType.EXPR, exprSource, 0, persistent, 0, null);
    }
    if (upper.startsWith("EVERY")) {
      String rest = cond.substring(5).trim();
      if (rest.isEmpty()) {
        return null;
      }
      try {
        int n = Integer.parseInt(rest);
        if (n <= 0) {
          return null;
        }
        return new BreakCondition(
            line, stmt, ConditionType.EVERY, null, 0, persistent, n, new AtomicInteger());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Resolves a bare programme name or file path to an actual {@link Path}.
   *
   * <p>Tries the argument verbatim, then with {@code .bas} appended, then under the canonical
   * example directory. Returns {@code null} when no existing file is found.
   */
  private static Path resolveBasPath(String inputPath) {
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
  }

  @SuppressWarnings("PMD.NcssCount")
  private static void runInteractive(Path initialFilePath) {
    final var state = new EvalState();
    final var inputReader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    final List<BreakCondition> activeBreaks = new ArrayList<>();
    final AtomicLong continueStartMs = new AtomicLong(System.currentTimeMillis());
    final AtomicReference<StatementExecutor> executorHolder = new AtomicReference<>();

    final var mockScreen =
        new MockScreen() {
          boolean runRequested = false;
          boolean gotoRequested = false;
          int gotoTarget = -1;
          boolean inBreakpoint = false;
          boolean sessionStopped = false;

          void blockAndListen(String reason) {
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
              continueStartMs.set(System.currentTimeMillis());
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
            } else if (upper.equals("SPB")
                || upper.startsWith("SPB ")
                || upper.startsWith("SPB\t")) {
              handleSpb(cmd.length() > 3 ? cmd.substring(3).trim() : "");
            } else if (upper.equals("S1B")
                || upper.startsWith("S1B ")
                || upper.startsWith("S1B\t")) {
              handleS1b(cmd.length() > 3 ? cmd.substring(3).trim() : "");
            } else if (upper.equals("CPB")
                || upper.startsWith("CPB ")
                || upper.startsWith("CPB\t")) {
              handleCpb(cmd.length() > 3 ? cmd.substring(3).trim() : "");
            } else if (upper.equals("RSC")
                || upper.startsWith("RSC ")
                || upper.startsWith("RSC\t")) {
              handleRsc(cmd.length() > 3 ? cmd.substring(3).trim() : "");
            } else if (upper.equals("PIQ")
                || upper.startsWith("PIQ ")
                || upper.startsWith("PIQ\t")) {
              handlePiq(cmd.length() > 3 ? cmd.substring(3).trim() : "");
            } else if (upper.equals("SSD")
                || upper.startsWith("SSD ")
                || upper.startsWith("SSD\t")) {
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
            if (Character.isDigit(cmd.charAt(0))) {
              handleReplLine(cmd);
              return false;
            }
            if (upper.equals("NEW")) {
              state.clear();
              state.program().clear();
              System.out.println("+");
              return false;
            }
            if (upper.equals("LIST")) {
              System.out.println("+" + formatQuotedArg(buildProgramListing()));
              return false;
            }
            if (upper.equals("RUN")) {
              if (state.program().isEmpty()) {
                System.out.println(
                    "-no programme loaded; use >n stmt or >LOAD \"path\", then >RUN");
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
            if (upper.equals("LOAD") || upper.startsWith("LOAD ") || upper.startsWith("LOAD\t")) {
              String rest = cmd.length() > 4 ? cmd.substring(4).trim() : "";
              if (rest.isEmpty()) {
                System.out.println("-LOAD requires a file path: >LOAD \"path\"");
                return false;
              }
              String pathStr = parseQuotedArg(rest);
              if (pathStr == null) {
                pathStr = rest;
              }
              Path p = resolveBasPath(pathStr);
              if (p == null) {
                System.out.printf("-File not found: %s%n", pathStr);
                return false;
              }
              try {
                state.setProgram(PARSER.parseProgramLines(Files.readString(p)));
                System.out.println("+");
              } catch (IOException e) {
                System.out.printf("-Failed to read file: %s%n", e.getMessage());
              }
              return false;
            }
            System.out.println(
                "-UNKNOWN REPL COMMAND."
                    + " Allowed: >n [stmt], >NEW, >LOAD \"path\", >LIST, >RUN, >GOTO n");
            return false;
          }

          private void handleReplLine(String cmd) {
            int i = 0;
            while (i < cmd.length() && Character.isDigit(cmd.charAt(i))) {
              i++;
            }
            int lineNum;
            try {
              lineNum = Integer.parseInt(cmd.substring(0, i));
            } catch (NumberFormatException e) {
              System.out.println("-Invalid line number");
              return;
            }
            if (lineNum <= 0) {
              System.out.println("-Line number must be a positive integer");
              return;
            }
            String rest = cmd.substring(i).trim();
            if (rest.isEmpty()) {
              state.program().remove(lineNum);
            } else {
              state.program().put(lineNum, new ProgramLine(lineNum, rest));
            }
            System.out.println("+");
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
            String grid = buildScreenString(this, rStart, rEnd, cStart, cEnd, showAttr);
            System.out.println("+" + formatQuotedArg(grid));
          }

          private void handlePiq(String text) {
            if (!text.startsWith("\"")) {
              System.out.println("-/PIQ argument must be a quoted string: /PIQ \"text\"");
              return;
            }
            String decoded = parseQuotedArg(text);
            if (decoded == null) {
              System.out.println("-Invalid quoted string for /PIQ");
              return;
            }
            byte[] bytes = decoded.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
              queueInkey(BStr.fromByte(b & 0xFF));
            }
            decoded
                .codePoints()
                .forEach(cp -> queueUinkey(BStr.fromJavaString(new String(Character.toChars(cp)))));
            queueInput(decoded);
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
            BreakCondition brk =
                (condStr == null || condStr.isEmpty())
                    ? new BreakCondition(
                        line, stmt, ConditionType.NONE, null, 0, persistent, 0, null)
                    : parseCondition(line, stmt, condStr, persistent);
            if (brk == null) {
              System.out.println(
                  "-Invalid condition — expected CSC \"<text>\","
                      + " ELAPSE <ms>, ?<expression>, or EVERY <n>");
              return;
            }
            activeBreaks.add(brk);
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
              activeBreaks.removeIf(BreakCondition::persistent);
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
            final int finalLine = line;
            final int finalStmt = stmt;
            activeBreaks.removeIf(b -> b.line() == finalLine && b.stmt() == finalStmt);
            System.out.println("+");
          }

          private void handleEval(String expr) {
            if (expr.isEmpty()) {
              System.out.println("-? requires an expression");
              return;
            }
            ExpressionEvaluator eval = executorHolder.get().getExprEvaluator();
            // Parse numeric first; only fall through to string if the *parse* fails.
            // If the parse succeeds but evaluation fails (e.g. undefined variable),
            // report that error directly rather than showing a misleading string-parse error.
            BazLangParser.NumExprContext numCtx = null;
            try {
              numCtx = PARSER.parseNumExpr(expr);
            } catch (ReportException ignored) {
              // numeric parse failed — will attempt string expression below
            }
            if (numCtx != null) {
              try {
                new AstAnnotator(0).visit(numCtx);
                double val = eval.evalNum(numCtx);
                System.out.printf("+%s%n", ExpressionEvaluator.formatNum(val));
              } catch (ReportException e) {
                System.out.printf("-%s%n", e.getMessage());
              }
              return;
            }
            try {
              var strCtx = PARSER.parseStrExpr(expr);
              new AstAnnotator(0).visit(strCtx);
              BStr val = eval.evalStr(strCtx);
              System.out.printf("+%s%n", formatQuotedArg(val.toJavaString()));
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
              stmts = PARSER.parseStatementsContext(args);
              new AstAnnotator(0).visit(stmts);
            } catch (ReportException e) {
              System.out.printf("-Parse error: %s%n", e.getMessage());
              return;
            }
            List<? extends BazLangParser.StatementContext> stmtList = stmts.statement();
            if (stmtList.size() != 1
                || !(stmtList.get(0) instanceof BazLangParser.LetStmtContext)) {
              System.out.println("-! requires exactly one assignment statement");
              return;
            }
            try {
              executorHolder.get().visitLetStmt((BazLangParser.LetStmtContext) stmtList.get(0));
              System.out.println("+");
            } catch (ReportException e) {
              System.out.printf("-%s%n", e.getMessage());
            }
          }

          private void handleSsd(String args) {
            if (args.isEmpty()) {
              System.out.println(
                  "-/SSD requires <rows> <cols> — use ?TEXTH, ?TEXTW to read dimensions");
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
              resize(newRows, newCols);
            } catch (NumberFormatException e) {
              System.out.println("-Invalid values for /SSD");
              return;
            }
            System.out.println("+");
          }
        };

    final var executor =
        new StatementExecutor(state, mockScreen, mockScreen) {
          @Override
          public Void visit(ParseTree tree) {
            int line = state.currentLineLabel();
            int stmt = state.currentStatementIndex();
            List<BreakCondition> toCheck = new ArrayList<>(activeBreaks);
            BreakCondition firedBreak = null;
            for (BreakCondition brk : toCheck) {
              if (firedBreak != null) {
                continue;
              }
              boolean locMatch =
                  (brk.line() == -1 || brk.line() == line)
                      && (brk.stmt() == -1 || brk.stmt() == stmt);
              if (!locMatch) {
                continue;
              }
              boolean condMet =
                  switch (brk.type()) {
                    case NONE -> true;
                    case VIEW -> screenContainsText(mockScreen, brk.seeText());
                    case ELAPSE ->
                        (System.currentTimeMillis() - continueStartMs.get()) >= brk.timeoutMs();
                    case EXPR -> {
                      try {
                        var numCtx = PARSER.parseNumExpr(brk.seeText());
                        new AstAnnotator(0).visit(numCtx);
                        yield getExprEvaluator().evalNum(numCtx) != 0.0;
                      } catch (ReportException e) {
                        try {
                          var strCtx = PARSER.parseStrExpr(brk.seeText());
                          new AstAnnotator(0).visit(strCtx);
                          yield !getExprEvaluator().evalStr(strCtx).isEmpty();
                        } catch (ReportException e2) {
                          yield false;
                        }
                      }
                    }
                    case EVERY -> brk.counter().incrementAndGet() % brk.everyN() == 0;
                  };
              if (condMet) {
                firedBreak = brk;
              }
            }
            if (firedBreak != null) {
              if (!firedBreak.persistent()) {
                activeBreaks.remove(firedBreak);
              }
              String reason =
                  firedBreak.type() == ConditionType.ELAPSE
                      ? "ELAPSE"
                      : "BREAK AT " + line + ":" + stmt;
              mockScreen.blockAndListen(reason);
            }
            if (!state.isRunning()) {
              return null;
            }
            return super.visit(tree);
          }
        };
    executorHolder.set(executor);

    if (initialFilePath != null) {
      try {
        state.setProgram(PARSER.parseProgramLines(Files.readString(initialFilePath)));
      } catch (IOException e) {
        System.err.printf(
            "Could not load BASIC file from '%s': %s%n",
            initialFilePath.toAbsolutePath(), e.getMessage());
        return;
      }
    }

    final var interpreter = new Interpreter(state, executor);

    // Outer execution loop: idle at +READY (or after each run), then execute when >RUN/>GOTO.
    String idleReason = "READY";
    while (true) {
      if (idleReason != null) {
        mockScreen.blockAndListen(idleReason);
      }
      idleReason = null;

      if (!state.isRunning() && !mockScreen.runRequested && !mockScreen.gotoRequested) {
        break; // STOP or EOF with no pending run
      }

      if (!mockScreen.runRequested && !mockScreen.gotoRequested) {
        // Should not happen — CONT from idle is rejected in handleCommand
        idleReason = "READY";
        continue;
      }

      boolean doRun = mockScreen.runRequested;
      int gotoTarget = mockScreen.gotoTarget;
      mockScreen.runRequested = false;
      mockScreen.gotoRequested = false;
      mockScreen.sessionStopped = false;

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
        state.setPendingJumpLocation(gotoTarget, 1);
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
      if (mockScreen.runRequested || mockScreen.gotoRequested) {
        continue;
      }

      // If STOP was sent during execution, the agent already got '+' — no further output needed.
      if (mockScreen.sessionStopped) {
        break;
      }

      idleReason = exitReason;
    }
  }

  public static void main(String[] args) {
    Path initialPath = null;
    if (args.length > 0) {
      String inputPath = args[0];
      initialPath = resolveBasPath(inputPath);
      if (initialPath == null) {
        System.err.printf("Could not find BASIC file '%s' — check the path or name%n", inputPath);
        return;
      }
      Path fileNamePath = initialPath.getFileName();
      String fileName = fileNamePath != null ? fileNamePath.toString() : inputPath;
      System.err.printf("Running Agent Debugger for programme: %s%n", fileName);
    } else {
      System.err.println("Running Agent Debugger (blank state — use >LOAD or >n stmt, then >RUN)");
    }
    runInteractive(initialPath);
  }
}
