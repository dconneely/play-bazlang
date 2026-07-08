# AgentDebugger

`AgentDebugger` is an interactive debugger for BazLang programmes, designed to be driven by an LLM
agent over stdin/stdout pipes. The programme under test runs inside the debugger process; execution
blocks at each breakpoint and on programme termination. The agent then inspects state and issues
commands before resuming.

The debugger has two modes:

- **File mode** — a `.bas` file is supplied as an argument; the programme is pre-loaded so the
  agent sees `+READY` with the programme ready to run via `>RUN`.
- **Blank mode** — no argument; the agent starts with an empty programme and uses `>` REPL
  commands to build or load a programme before running it. This is the primary mode for
  LLM-driven testing of new language features.

## Running the debugger

```bash
# File mode — loads programme.bas before the first +READY
./gradlew :app-bazlang:runAgentDebugger -Pargs=path/to/programme.bas

# Blank mode — starts empty; use >LOAD or >n stmt then >RUN
./gradlew :app-bazlang:runAgentDebugger
```

The file argument may be a file path or a bare name (with or without `.bas`). Bare names are
resolved first from the working directory, then from `app-bazlang/src/example/bas/`.

## Protocol overview

### Startup

When the debugger starts it immediately prints one line to stdout:

```
+READY
```

Every server success line starts with `+` and every error line starts with `-`. `+READY` signals
that the debugger is ready for commands. In file mode the programme is pre-loaded; in blank mode
the programme is empty and must be populated with `>` REPL commands before running. The debugger
prints a human-readable info message to stderr before `+READY`; agents should ignore stderr.

### Command/response model

Each command is a single line. Every command produces **exactly one response line** — either
`+[data]` (success) or `-<message>` (error). There is no prompt between commands.

The agent may batch multiple commands in a single write; the debugger responds in order, one line
per command, as each is processed.

### Resuming and starting execution

`/GO` and the `>RUN` / `>GOTO n` REPL commands all produce a **deferred response**: the response
arrives only when the programme next blocks.

`/GO` is only valid when the programme is paused at a breakpoint. `>RUN` and `>GOTO n` are used
to start (or restart) execution and are valid both at `+READY` and after a `+STOP` response.

`>RUN` and `/GO` (when used as the last line of a message) must be the **last command** in any
client message — any command sent after them would be consumed in the *next* break context.

The deferred response arrives when the programme next blocks:

| Event                                  | Response                                     |
|:---------------------------------------|:---------------------------------------------|
| Programme loaded (startup)             | `+READY`                                     |
| Location or condition breakpoint fired | `+BREAK AT <line>:<stmt>`                    |
| `ELAPSE` condition fired               | `+ELAPSE`                                    |
| Programme ended normally               | `+STOP 0 OK, <line>:<stmt>`                  |
| Programme ended via `STOP` statement   | `+STOP 9 STOP statement, <line>:<stmt>`      |
| Programme ended with a runtime error   | `+STOP <code> <msg>, <line>:<stmt>`          |

Report code `0` always means a clean end; any other code can be treated as an error. The full
list of codes is defined in `ReportCode.java`.

A client message that ends with `/GO` or `>RUN` therefore produces N responses immediately (one
per command before it) plus one final response when execution next blocks.

`/STOP` also ends the command phase; it responds `+` immediately and terminates the programme.
If stdin reaches EOF the debugger behaves as if `/STOP` was sent.

---

## Commands

All commands are case-insensitive.

### `/RSC <rowTop> <colLeft> <rowBottom> <colRight> [ATTR]`

Read screen content: dumps the given rectangle of the virtual screen buffer as a single-line
QuotedArg string (see [QuotedArg format](#quotedarg-format) below). All indices are 0-based.
Rows are separated by `\n` escapes. Runs of five or more spaces are compressed to `{N}`.

`ATTR` prepends a `[fg,bg]` colour tag at the start of each run of cells that share the same
colour. The tag is omitted when the colour is unchanged from the previous cell. In ATTR mode,
`{N}` means N spaces all sharing the current colour.

```
/RSC 0 0 2 79
+"{20}\n{8}SCORE: 42{10}\n{20}"

/RSC 0 0 2 79 ATTR
+"[7,0]{20}\n{8}[7,1]SCORE: 42[7,0]{10}\n{20}"
```

Use `?TEXTH` and `?TEXTW` to learn screen dimensions before choosing the rectangle.

Responds `+"<grid>"`.

### `/PIQ "<text>"`

Post to input queue: queues `<text>` for the programme to consume. The argument must be a
QuotedArg string. Multiple characters may be queued in a single command.

- `INKEY$` receives one byte per UTF-8 byte of the decoded text.
- `UINKEY$` receives one BStr per Unicode codepoint.
- `INPUT` receives the full decoded text as a single line.

```
/PIQ " "          — queue a space character
/PIQ "oo  o o"    — queue a multi-character sequence
/PIQ "\""         — queue a double-quote character
/PIQ "\e[A"       — queue ESC followed by [ and A (e.g. cursor-up key sequence)
```

Responds `+`.

### `?<expression>`

Evaluates a single BazLang expression in the live programme context. Numeric results are formatted
like BazLang's `PRINT`; string results are returned as a QuotedArg string. Send one `?` per
expression.

Array elements, built-in functions, and arithmetic are all supported. Side-effecting functions
such as `INKEY$` and `RND` do take effect. Numeric values use BazLang's canonical number format
(e.g. `42` not `42.0`, `3.14159`); string values are returned as QuotedArg.

```
?SCORE
+42

?TEXTH
+25

?SQR(VX*VX+VY*VY)
+1.41421356

?A$(3)
+"hello"
```

Responses are in the same order as the commands, so the client can correlate by position.
Responds `+<value>`.

**Timing note:** user-defined variables are not accessible until the programme has executed the
statements that assign them. At `+READY` (before any `/GO`) only built-in zero-argument
functions (`TEXTH`, `PLOTW`, `RND`, etc.) are available. Evaluating an unassigned variable
responds `-Undefined variable: <NAME>`. Set a breakpoint inside the programme and issue `/GO`
first, then read variables with `?`.

### `!<assignmentTarget> = <expression>`

Executes a single assignment to mutate programme state. Only one statement is accepted per
command; use multiple `!` lines for multiple assignments.

```
!SCORE=0
+

!PX=80
+
```

Responds `+`.

### `/SPB <line>:<stmt>`

Sets a **persistent** location breakpoint. Fires before statement `<stmt>` on line `<line>`
executes. Persists until removed with `/CPB`. Statement indices are 1-based.

Responds `+`.

### `/SPB <line>:<stmt> <cond>`

Sets a persistent location breakpoint with a condition guard. Only fires when both the location
matches and the condition is satisfied. See [Break conditions](#break-conditions) below.

Responds `+`.

### `/SPB <cond>`

Sets a persistent condition-only breakpoint with no location filter. Checked before every
statement. Persists until removed with `/CPB`. See [Break conditions](#break-conditions) below.

Responds `+`.

### `/S1B <line>:<stmt>`

Sets a **one-time** location breakpoint. Fires once before that statement, then removes itself
automatically.

Responds `+`.

### `/S1B <line>:<stmt> <cond>`

Sets a one-time location breakpoint with a condition guard. Fires once when both the location and
condition are satisfied, then removes itself. See [Break conditions](#break-conditions) below.

Responds `+`.

### `/S1B <cond>`

Sets a one-time condition-only breakpoint with no location filter. Checked before every statement.
Single-shot: clears automatically after firing. See [Break conditions](#break-conditions) below.

Responds `+`.

### `/CPB <line>:<stmt>`

Removes all persistent breakpoints registered at that exact location.

Responds `+`.

### `/CPB`

Removes all persistent breakpoints.

Responds `+`.

### `/SSD <rows> <cols>`

Resizes the virtual screen buffer. To read current dimensions without resizing, use `?TEXTH`
and `?TEXTW`.

Responds `+`.

### `/GO`

Resumes programme execution from a breakpoint. Only valid when paused at a breakpoint — rejected
with `-` at `+READY` or after `+STOP`; use `>RUN` or `>GOTO n` instead. Resets the `ELAPSE`
timer. Responds with `+<reason>` when the programme next blocks — see
[Protocol overview](#protocol-overview).

### `/STOP`

Terminates the programme immediately. Responds `+`.

---

## REPL commands

All REPL commands begin with `>`. They can be sent at any point (at `+READY`, after `+STOP`, or
while paused at a breakpoint). Each produces exactly one immediate response, except `>RUN` and
`>GOTO n` which are deferred like `/GO`.

Breakpoints are preserved across `>RUN` and `>GOTO n` — only runtime state (variables, stacks)
is reset by `>RUN`; breakpoints survive until explicitly cleared with `/CPB`.

### `>n [stmt]`

Adds or replaces line `n` in the current programme with `stmt`. If `stmt` is omitted the line is
deleted. Line numbers must be positive integers.

```
>10 LET x=5
+

>20 PRINT x
+

>10
+            (line 10 deleted)
```

Responds `+`.

### `>NEW`

Clears the entire programme and all runtime state (variables, stacks, colour attributes).
Equivalent to the BazLang `NEW` statement.

```
>NEW
+
```

Responds `+`.

### `>LOAD "path"`

Loads a programme from a file, replacing any current programme. The path may be quoted or
unquoted. Bare filenames (with or without `.bas`) are resolved against the example directory.

```
>LOAD "pong"
+

>LOAD "app-bazlang/src/example/bas/monster.bas"
+
```

Responds `+` on success, `-` on error.

### `>LIST`

Returns the current programme as a QuotedArg string with lines separated by `\n`. Returns `+""`
if the programme is empty.

```
>LIST
+"10 LET x=5\n20 PRINT x\n30 STOP"
```

Responds `+"<listing>"`.

### `>RUN`

Clears runtime state (variables, stacks, data pointer) and runs the programme from its first
line. Produces a deferred response like `/GO`. Also valid when paused at a breakpoint — aborts
the current execution and restarts from the beginning.

```
>RUN
+BREAK AT 20:1      (deferred — arrives when break fires)
```

### `>GOTO n`

Runs the programme from line `n` without clearing variables. Produces a deferred response like
`/GO`. When sent from a breakpoint, jumps to line `n` and continues executing immediately.

```
>GOTO 100
+STOP 0 OK, 100:1      (deferred — arrives when programme ends or breaks)
```

Responds with `+<reason>` when execution next blocks — same format as `/GO`.

## Break conditions

### `CSC "<text>"`

Fires when the virtual screen buffer contains `<text>` (case-insensitive). The argument is a
QuotedArg string.

```
/SPB CSC "Game Over"
/S1B CSC "score: 100"
```

### `ELAPSE <ms>`

Fires when at least `<ms>` wall-clock milliseconds have elapsed since the last `/GO` (or since
the programme started, if `/GO` has not yet been sent). The timer resets on every `/GO`.

```
/S1B ELAPSE 5000
```

### `?<expression>`

Fires when the BazLang expression is truthy: non-zero for numeric results, non-empty for string
results. Supports array access, functions, and any expression the language can evaluate.

```
/SPB 3010:1 ?FUEL < 50
/S1B ?SCREEN$(10,5) = "X"
```

**Performance note:** prefer `/SPB <line>:<stmt> ?<expr>` over `/SPB ?<expr>`. Condition-only
`?<expr>` evaluates the expression before every single BASIC statement.

### `EVERY <n>`

Fires every `n`th time the condition is checked. With a location, that means every `n`th visit to
the named statement; without a location, it counts every statement executed. The counter starts at
zero when the breakpoint is registered.

```
/SPB 1035:1 EVERY 4   — fire every 4th visit to line 1035, statement 1
/S1B EVERY 100         — fire after 100 statements then auto-remove (single-shot step)
```

## QuotedArg format

Double-quoted strings used as command arguments and in responses. Backslash escape sequences:

| Escape | Meaning |
|:---|:---|
| `\"` | Double-quote character (chr 34) |
| `\\` | Backslash character (chr 92) |
| `\n` | Line feed (chr 10) |
| `\r` | Carriage return (chr 13) |
| `\e` | Escape (chr 27) |

Used in: `/PIQ` arguments, `CSC` condition text, `/RSC` command output, `?` string output.

## Example sessions

`←` is server output; `→` is agent input. Blank lines separate client messages for readability
only — they are not part of the protocol.

### File mode

```
← Running Agent Debugger for programme: pong.bas
← +READY

→ /SPB 100:1
→ ?TEXTH
→ >RUN
← +
← +25
← +BREAK AT 100:1

→ ?SCORE
→ /RSC 0 0 4 79
→ /GO
← +0
← +"{80}\n{35}PONG{41}\n{80}\n{80}\n{80}"
← +BREAK AT 100:1
→ /STOP
← +
```

### Blank mode — inline programme

```
← Running Agent Debugger (blank state — use >LOAD or >n stmt, then >RUN)
← +READY

→ >10 LET x=5
→ >20 PRINT x
→ >30 STOP
← +
← +
← +

→ /SPB 20:1
→ >RUN
← +
← +BREAK AT 20:1

→ ?x
→ /GO
← +5
← +STOP 9 STOP statement, 30:1

→ !x=99
→ >RUN
← +
← +BREAK AT 20:1

→ ?x
→ /STOP
← +5
← +
```

## Notes

- Statement indices (`<stmt>`) are 1-based and reset to 1 at the start of each new line.
- `ELAPSE` breakpoints measure wall-clock time, not CPU time or BASIC frame ticks (`FRAMES`).
- `FAST` mode (see [language_features.md](language_features.md)) suppresses terminal re-rendering;
  the virtual screen buffer is always up to date regardless of fast mode.
- `MockScreen` (used internally by the debugger) silently ignores `FAST`/`SLOW` — `/RSC` always
  shows the current buffer state.
