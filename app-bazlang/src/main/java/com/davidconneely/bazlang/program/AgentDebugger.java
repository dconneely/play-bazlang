package com.davidconneely.bazlang.program;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockScreen;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.antlr.v4.runtime.tree.ParseTree;

/// Interactive debugger for BazLang programmes, designed for use by LLM agents over stdin/stdout
/// pipes.
///
/// The process loads the target programme and immediately blocks with reason `LOADED` before
/// executing a single statement. This gives the agent a chance to set breakpoints and pre-queue
/// input before the programme starts. After that initial block, execution proceeds and the
/// debugger blocks again at each breakpoint and on programme termination. Each time it blocks it
/// prints the break reason, `SIZE <rows> <cols>`, and `READY` on separate lines. The agent then
/// sends commands until it sends `CONTINUE` or `EXIT`.
///
/// ### Commands (uppercase; all except `CONTINUE` and `EXIT` respond with output then `READY`)
///
/// **`VIEW [r1 r2 [c1 c2]] [ATTR]`**
/// Dump the screen buffer. Row and column indices are 0-based. Prints a bordered grid with runs
/// of spaces compressed to `{N}`. `ATTR` adds `[fg,bg]` colour annotations after each cell.
/// With two integer arguments, limits output to that row range; with four, limits to that row and
/// column window.
///
/// **`VAR [<name> ...]`**
/// Print initialised variables. Without arguments, prints all: `NAME: value` for numeric
/// scalars, `NAME$: "value"` for string scalars. With one or more names, prints only the named
/// variables in the order given. String variables require the `$` suffix (e.g. `VAR STAT$`).
///
/// **`SEND "<text>"`**
/// Queue `<text>` for keyboard and input reads. The argument must be a double-quoted string;
/// `\"` represents a literal double-quote and `\\` represents a literal backslash. Multiple
/// characters may be queued at once: e.g. `SEND "oo  o o  oo"`. INKEY$ receives one byte per
/// UTF-8 byte; UINKEY$ receives one BStr per Unicode codepoint; INPUT receives the full text as
/// one line.
///
/// **`BREAK AT <line>:<stmt>`**
/// Set a persistent location breakpoint (statement indices are 1-based). Fires before the named
/// statement executes. Persists until removed with `CLEAR AT` or `CLEAR`.
///
/// **`BREAK AT <line>:<stmt> IF <cond>(<params>)`**
/// Location breakpoint with a condition guard: only fires when both the location and condition
/// are satisfied. Persists until cleared.
///
/// **`BREAK IF <cond>(<params>)`**
/// Condition-only wait with no location filter. Checked on every statement. Single-shot:
/// clears automatically after firing.
///
/// **`CLEAR AT <line>:<stmt>`**
/// Remove all breakpoints registered at that exact location.
///
/// **`CLEAR`**
/// Remove all persistent breakpoints.
///
/// **`CONTINUE`**
/// Resume execution. Resets the `ELAPSE` timer. Does not print `READY`.
///
/// **`SIZE [<rows> <cols>]`**
/// With no arguments, reports the current screen dimensions. With two arguments, resizes the
/// virtual screen buffer. Always responds with `SIZE <rows> <cols>` reflecting the current
/// dimensions after the command.
///
/// **`EXIT`**
/// Terminate the programme immediately. Does not print `READY`.
///
/// ### Break Conditions
///
/// All conditions use the form `<NAME>(<parameters>)`:
///
/// - `VAR(<varName> <op> <value>)` — numeric variable satisfies the relation; operators are
///   `=`, `<`, `>`, `<=`, `>=`, `<>`.
/// - `VIEW("<text>")` — screen buffer contains the given text (case-insensitive). The text is a
///   double-quoted string with the same `\"` and `\\` escapes as `SEND`.
/// - `ELAPSE(<ms>)` — at least `<ms>` wall-clock milliseconds have elapsed since the last
///   `CONTINUE` (or since the programme started if `CONTINUE` has not yet been sent).
///
/// ### Response Format
///
/// Each time the debugger blocks it prints, on separate lines:
///
/// ```
/// <reason>
/// SIZE <rows> <cols>
/// READY
/// ```
///
/// `<reason>` is one of:
/// - `LOADED` — programme loaded; no statement has executed yet (always the first block)
/// - `BREAK AT <line>:<stmt>` — a location breakpoint (with or without a condition guard) fired
/// - `ELAPSE` — a condition-only `ELAPSE` wait fired
/// - `TERMINATED` — the programme ended normally (including via `STOP`)
/// - `ERROR <code> <msg>, <line>:<stmt>` — the programme ended with a runtime error
public final class AgentDebugger {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private enum ConditionType {
    NONE,
    VAR,
    SEE,
    ELAPSE
  }

  private record BreakCondition(
      int line,
      int stmt,
      ConditionType type,
      String varName,
      String varOp,
      double varValue,
      String seeText,
      long timeoutMs,
      boolean persistent) {}

  private static BreakCondition locationBreak(int line, int stmt) {
    return new BreakCondition(line, stmt, ConditionType.NONE, null, null, 0, null, 0, true);
  }

  private static BreakCondition varBreak(
      int line, int stmt, String varName, String op, double value) {
    return new BreakCondition(
        line, stmt, ConditionType.VAR, varName, op, value, null, 0, line != -1);
  }

  private static BreakCondition seeBreak(int line, int stmt, String text) {
    return new BreakCondition(line, stmt, ConditionType.SEE, null, null, 0, text, 0, line != -1);
  }

  private static BreakCondition elapseBreak(int line, int stmt, long ms) {
    return new BreakCondition(
        line, stmt, ConditionType.ELAPSE, null, null, 0, null, ms, line != -1);
  }

  private static boolean evalVarCondition(BreakCondition brk, EvalState state) {
    if (!state.hasNumVar(brk.varName())) {
      return false;
    }
    double actual = state.numVar(brk.varName());
    return switch (brk.varOp()) {
      case "=" -> actual == brk.varValue();
      case "<" -> actual < brk.varValue();
      case ">" -> actual > brk.varValue();
      case "<=" -> actual <= brk.varValue();
      case ">=" -> actual >= brk.varValue();
      case "<>" -> actual != brk.varValue();
      default -> false;
    };
  }

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

  private static void printScreen(
      MockScreen mockScreen, int rStart, int rEnd, int cStart, int cEnd, boolean showAttr) {
    int maxRows = mockScreen.printHeight();
    int maxCols = mockScreen.printWidth();
    int r1 = Math.clamp(rStart, 0, maxRows - 1);
    int r2 = Math.clamp(rEnd, 0, maxRows - 1);
    int c1 = Math.clamp(cStart, 0, maxCols - 1);
    int c2 = Math.clamp(cEnd, 0, maxCols - 1);
    System.out.println("┌" + "─".repeat(c2 - c1 + 1) + "┐");
    for (int r = r1; r <= r2; r++) {
      StringBuilder row = new StringBuilder();
      row.append('│');
      int c = c1;
      while (c <= c2) {
        int cp = mockScreen.getScreenCodepoint(r, c);
        if (cp == ' ' && !showAttr) {
          int start = c;
          while (c <= c2 && mockScreen.getScreenCodepoint(r, c) == ' ') {
            c++;
          }
          int count = c - start;
          if (count > 3) {
            row.append('{').append(count).append('}');
          } else {
            row.append(" ".repeat(count));
          }
        } else {
          row.appendCodePoint(cp);
          if (showAttr) {
            int attr = mockScreen.getScreenAttributes(r, c);
            row.append('[').append(attr & 7).append(',').append((attr >> 3) & 7).append(']');
          }
          c++;
        }
      }
      row.append('│');
      System.out.println(row.toString());
    }
    System.out.println("└" + "─".repeat(c2 - c1 + 1) + "┘");
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

  private static BreakCondition parseCondition(int line, int stmt, String cond) {
    String upper = cond.toUpperCase();
    if (upper.startsWith("VAR(") && cond.endsWith(")")) {
      String expr = cond.substring(4, cond.length() - 1).trim();
      String[] parts = expr.split("\\s+");
      if (parts.length == 3) {
        try {
          double val = Double.parseDouble(parts[2]);
          return varBreak(line, stmt, parts[0].toUpperCase(), parts[1], val);
        } catch (NumberFormatException e) {
          return null;
        }
      }
      return null;
    }
    if (upper.startsWith("VIEW(") && cond.endsWith(")")) {
      String quotedArg = cond.substring(5, cond.length() - 1).trim();
      String text = parseQuotedArg(quotedArg);
      if (text == null) {
        return null;
      }
      return seeBreak(line, stmt, text);
    }
    if (upper.startsWith("ELAPSE(") && cond.endsWith(")")) {
      try {
        long ms = Long.parseLong(cond.substring(7, cond.length() - 1).trim());
        return elapseBreak(line, stmt, ms);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("PMD.NcssCount")
  private static void runInteractive(String source) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();
    final var inputReader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    final List<BreakCondition> activeBreaks = new ArrayList<>();
    final AtomicLong continueStartMs = new AtomicLong(System.currentTimeMillis());
    final AtomicBoolean exitRequested = new AtomicBoolean(false);

    final var mockScreen =
        new MockScreen() {
          void blockAndListen(String reason) {
            System.out.println(reason);
            System.out.printf("SIZE %d %d%n", printHeight(), printWidth());
            System.out.println("READY");
            System.out.flush();
            boolean resumed = false;
            while (!resumed) {
              try {
                String line = inputReader.readLine();
                if (line == null) {
                  exitRequested.set(true);
                  state.setRunning(false);
                  resumed = true;
                } else {
                  resumed = handleCommand(line.trim());
                  if (!resumed) {
                    System.out.println("READY");
                    System.out.flush();
                  }
                }
              } catch (IOException e) {
                exitRequested.set(true);
                state.setRunning(false);
                resumed = true;
              }
            }
            if (state.isRunning()) {
              continueStartMs.set(System.currentTimeMillis());
            }
          }

          private boolean handleCommand(String cmd) {
            String upper = cmd.toUpperCase();
            if (upper.equals("CONTINUE")) {
              return true;
            } else if (upper.equals("EXIT")) {
              exitRequested.set(true);
              state.setRunning(false);
              return true;
            } else if (upper.equals("VAR") || upper.startsWith("VAR ")) {
              handleVar(cmd.substring(3).trim());
            } else if (upper.startsWith("VIEW")) {
              handleView(cmd);
            } else if (upper.startsWith("SEND ")) {
              handleSend(cmd.substring(5).trim());
            } else if (upper.startsWith("BREAK")) {
              handleBreak(cmd.substring(5).trim());
            } else if (upper.startsWith("CLEAR")) {
              handleClear(cmd.substring(5).trim());
            } else if (upper.equals("SIZE") || upper.startsWith("SIZE ")) {
              handleSize(cmd.substring(4).trim());
            } else {
              System.err.println(
                  "UNKNOWN COMMAND. Allowed:"
                      + " VAR, VIEW, SEND, BREAK, CLEAR, CONTINUE, SIZE, EXIT");
            }
            return false;
          }

          private void handleVar(String args) {
            System.out.println("VARIABLES:");
            Map<String, Double> nums = state.getVariablesSnapshot();
            Map<String, String> strs = state.getStringVariablesSnapshot();
            if (args.isEmpty()) {
              for (Map.Entry<String, Double> e : nums.entrySet()) {
                System.out.printf("%s: %s%n", e.getKey(), e.getValue());
              }
              for (Map.Entry<String, String> e : strs.entrySet()) {
                System.out.printf("%s: \"%s\"%n", e.getKey(), e.getValue());
              }
            } else {
              List<String> requested = new ArrayList<>();
              for (String token : args.split("\\s+")) {
                requested.add(token.toUpperCase());
              }
              for (String name : requested) {
                if (nums.containsKey(name)) {
                  System.out.printf("%s: %s%n", name, nums.get(name));
                } else if (strs.containsKey(name)) {
                  System.out.printf("%s: \"%s\"%n", name, strs.get(name));
                }
              }
            }
          }

          private void handleView(String cmd) {
            String args = cmd.substring(4).trim();
            String argsUp = args.toUpperCase();
            boolean showAttr = argsUp.endsWith("ATTR");
            String numPart = args.replaceAll("(?i)\\bATTR\\b", "").trim();
            String[] parts = numPart.isEmpty() ? new String[0] : numPart.split("\\s+");
            int rStart = 0;
            int rEnd = printHeight() - 1;
            int cStart = 0;
            int cEnd = printWidth() - 1;
            try {
              if (parts.length >= 4) {
                rStart = Integer.parseInt(parts[0]);
                rEnd = Integer.parseInt(parts[1]);
                cStart = Integer.parseInt(parts[2]);
                cEnd = Integer.parseInt(parts[3]);
              } else if (parts.length >= 2) {
                rStart = Integer.parseInt(parts[0]);
                rEnd = Integer.parseInt(parts[1]);
              }
            } catch (NumberFormatException e) {
              System.err.println("Invalid coordinates for VIEW");
            }
            printScreen(this, rStart, rEnd, cStart, cEnd, showAttr);
          }

          private void handleSend(String text) {
            String decoded = text.startsWith("\"") ? parseQuotedArg(text) : text;
            if (decoded == null) {
              System.err.println("Invalid quoted string for SEND — use SEND \"text\"");
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
            System.out.println("QUEUED");
          }

          private void handleBreak(String args) {
            String argsUp = args.toUpperCase();
            if (argsUp.startsWith("AT ")) {
              handleBreakAt(args.substring(3).trim());
            } else if (argsUp.startsWith("IF ")) {
              handleBreakIf(-1, -1, args.substring(3).trim());
            } else {
              System.err.println(
                  "BREAK requires AT <line>:<stmt> [IF <cond>(<params>)]"
                      + " or IF <cond>(<params>)");
            }
          }

          private void handleBreakAt(String args) {
            String argsUp = args.toUpperCase();
            int ifIdx = argsUp.indexOf(" IF ");
            String locStr = ifIdx >= 0 ? args.substring(0, ifIdx).trim() : args;
            String condStr = ifIdx >= 0 ? args.substring(ifIdx + 4).trim() : null;
            int colonIdx = locStr.indexOf(':');
            if (colonIdx < 0) {
              System.err.println("BREAK AT requires <line>:<stmt>");
              return;
            }
            int line;
            int stmt;
            try {
              line = Integer.parseInt(locStr.substring(0, colonIdx));
              stmt = Integer.parseInt(locStr.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
              System.err.println("Invalid line:stmt in BREAK AT");
              return;
            }
            handleBreakIf(line, stmt, condStr);
          }

          private void handleBreakIf(int line, int stmt, String condStr) {
            BreakCondition brk;
            if (condStr == null) {
              brk = locationBreak(line, stmt);
            } else {
              brk = parseCondition(line, stmt, condStr);
              if (brk == null) {
                System.err.println(
                    "Invalid condition — expected VAR(<var> <op> <val>),"
                        + " VIEW(\"<text>\"), or ELAPSE(<ms>)");
                return;
              }
            }
            activeBreaks.add(brk);
            System.out.println("BREAKPOINT SET");
          }

          private void handleClear(String args) {
            if (args.isEmpty()) {
              activeBreaks.removeIf(BreakCondition::persistent);
              System.out.println("BREAKPOINTS CLEARED");
              return;
            }
            String argsUp = args.toUpperCase();
            if (!argsUp.startsWith("AT ")) {
              System.err.println("CLEAR requires AT <line>:<stmt> or no arguments");
              return;
            }
            String locStr = args.substring(3).trim();
            int colonIdx = locStr.indexOf(':');
            if (colonIdx < 0) {
              System.err.println("CLEAR AT requires <line>:<stmt>");
              return;
            }
            int line;
            int stmt;
            try {
              line = Integer.parseInt(locStr.substring(0, colonIdx));
              stmt = Integer.parseInt(locStr.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
              System.err.println("Invalid line:stmt in CLEAR AT");
              return;
            }
            final int finalLine = line;
            final int finalStmt = stmt;
            activeBreaks.removeIf(b -> b.line() == finalLine && b.stmt() == finalStmt);
            System.out.println("BREAKPOINTS CLEARED");
          }

          private void handleSize(String args) {
            if (!args.isEmpty()) {
              String[] parts = args.split("\\s+");
              if (parts.length < 2) {
                System.err.println("SIZE requires <rows> <cols> or no arguments");
                return;
              }
              try {
                int newRows = Integer.parseInt(parts[0]);
                int newCols = Integer.parseInt(parts[1]);
                resize(newRows, newCols);
              } catch (NumberFormatException e) {
                System.err.println("Invalid values for SIZE");
                return;
              }
            }
            System.out.printf("SIZE %d %d%n", printHeight(), printWidth());
          }
        };

    final var executor =
        new ProgramManager(state, mockScreen) {
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
                    case VAR -> evalVarCondition(brk, state);
                    case SEE -> screenContainsText(mockScreen, brk.seeText());
                    case ELAPSE ->
                        (System.currentTimeMillis() - continueStartMs.get()) >= brk.timeoutMs();
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
            return super.visit(tree);
          }
        };

    mockScreen.blockAndListen("LOADED");
    if (exitRequested.get()) {
      return;
    }
    final var interpreter = new Interpreter(state, executor);
    String exitReason = "TERMINATED";
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        exitReason = "ERROR " + e.format();
      }
    }
    if (!exitRequested.get()) {
      mockScreen.blockAndListen(exitReason);
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: java AgentDebugger <file.bas | programme_name>");
      return;
    }
    String inputPath = args[0];
    Path p = Path.of(inputPath);
    if (!Files.exists(p)) {
      String name = inputPath.endsWith(".bas") ? inputPath : inputPath + ".bas";
      p = Path.of("src", "example", "bas", name);
      if (!Files.exists(p)) {
        p = Path.of("app-bazlang", "src", "example", "bas", name);
      }
    }
    String source;
    try {
      source = Files.readString(p);
    } catch (IOException e) {
      System.err.printf(
          "Could not load BASIC file from '%s': %s%n", p.toAbsolutePath(), e.getMessage());
      return;
    }
    Path fileNamePath = p.getFileName();
    String fileName = fileNamePath != null ? fileNamePath.toString() : "PROGRAMME";
    System.out.printf("Running Agent Debugger for programme: %s%n", fileName);
    runInteractive(source);
  }
}
