package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.nio.file.Files;
import java.nio.file.Path;

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

  /**
   * Resolves a bare programme name or file path to an actual {@link Path}.
   *
   * <p>Tries the argument verbatim, then with {@code .bas} appended, then under the canonical
   * example directory. Returns {@code null} when no existing file is found.
   */
  static Path resolveBasPath(String inputPath) {
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

  private AgentDebugger() {}

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
    new DebugSession(AntlrParser.INSTANCE).run(initialPath);
  }
}
