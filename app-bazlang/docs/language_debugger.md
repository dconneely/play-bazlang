# AgentDebugger

`AgentDebugger` is an interactive debugger for BazLang programmes, designed to be driven by an LLM
agent over stdin/stdout pipes. The programme under test runs inside the debugger process; execution
blocks at each breakpoint and on programme termination. The agent then inspects state and issues
commands before resuming.

## Running the debugger

Use the Gradle task from the repository root:

```bash
./gradlew :app-bazlang:runAgentDebugger -Pargs=path/to/programme.bas
```

The argument may be a file path or a bare name (with or without `.bas`). Bare names are resolved
first from the working directory, then from `app-bazlang/src/example/bas/`.

## Protocol overview

Each time execution blocks, the debugger prints three lines to stdout:

```
<reason>
SIZE <rows> <cols>
READY
```

`<reason>` is one of:

| Reason | Meaning |
|:---|:---|
| `LOADED` | Programme loaded; execution has not yet started. First block before any code runs. |
| `BREAK AT <line>:<stmt>` | A location breakpoint (with or without a condition guard) fired |
| `ELAPSE` | A condition-only `ELAPSE` wait fired |
| `TERMINATED` | The programme ended normally (including via `STOP`) |
| `ERROR <code> <msg>, <line>:<stmt>` | The programme ended with a runtime error |

`<line>` is the BASIC line number; `<stmt>` is the 1-based statement index within that line.
`SIZE` reports the current virtual screen dimensions (rows then cols).

After printing `READY`, the debugger reads commands from stdin, one per line, until it receives
`CONTINUE` or `EXIT`. Each command (except `CONTINUE` and `EXIT`) produces output followed by
another `READY` prompt.

If stdin is closed (EOF), the debugger behaves as if `EXIT` was sent.

## Commands

All commands are case-insensitive. Arguments are as written.

### `VIEW [r1 r2 [c1 c2]] [ATTR]`

Dumps the virtual screen buffer as a bordered grid. Row and column indices are 0-based.

- No arguments: dumps the full screen.
- Two integers (`r1 r2`): limits output to those rows (inclusive).
- Four integers (`r1 r2 c1 c2`): limits output to that row and column window.
- `ATTR`: appends `[fg,bg]` colour annotations after each cell character.

Runs of spaces are compressed to `{N}` (where N ≥ 4) to reduce token count. Example output:

```
┌────────────────────┐
│{20}                │
│{8}SCORE: 42{10}    │
│{20}                │
└────────────────────┘
```

Use `SIZE` first to learn the screen dimensions, then choose `r1 r2 c1 c2` values accordingly.

### `VAR [<name> ...]`

Prints initialised variables. Without arguments, prints all. With one or more names, prints only
those variables in the order given. Numeric scalars appear as `NAME: value`; string scalars as
`NAME$: "value"`. Arrays are not shown. String variable names must include the `$` suffix.

```
VAR
VARIABLES:
I: 7.0
SCORE: 42.0
NAME$: "Alice"

VAR SCORE NAME$
VARIABLES:
SCORE: 42.0
NAME$: "Alice"
```

### `SEND <text>`

Queues `<text>` for the programme to consume:

- `INKEY$` receives one byte per UTF-8 byte of `<text>`.
- `UINKEY$` receives one BStr per Unicode codepoint.
- `INPUT` receives the full `<text>` as a single line.

Responds with `QUEUED`.

### `BREAK AT <line>:<stmt>`

Sets a persistent location breakpoint. Fires before statement `<stmt>` on line `<line>` executes.
Persists until removed with `CLEAR AT` or `CLEAR`.

Responds with `BREAKPOINT SET`.

### `BREAK AT <line>:<stmt> IF <cond>(<params>)`

Sets a persistent location breakpoint with a condition guard. Only fires when both the location
matches and the condition is satisfied. See [Break conditions](#break-conditions) below.

Responds with `BREAKPOINT SET`.

### `BREAK IF <cond>(<params>)`

Sets a condition-only breakpoint with no location filter. Checked before every statement.
Single-shot: clears automatically after firing. See [Break conditions](#break-conditions) below.

Responds with `BREAKPOINT SET`.

### `CLEAR AT <line>:<stmt>`

Removes all persistent breakpoints registered at that exact location.

Responds with `BREAKPOINTS CLEARED`.

### `CLEAR`

Removes all persistent breakpoints.

Responds with `BREAKPOINTS CLEARED`.

### `SIZE [<rows> <cols>]`

Without arguments, reports the current screen dimensions:

```
SIZE 25 80
```

With two arguments, resizes the virtual screen buffer then reports the new dimensions:

```
SIZE 40 100
```

Useful for making `VIEW` coordinates meaningful before inspecting the screen.

### `CONTINUE`

Resumes programme execution. Resets the `ELAPSE` timer. Does not print `READY`.

### `EXIT`

Terminates the programme immediately. Does not print `READY`.

## Break conditions

All conditions use the form `<NAME>(<parameters>)`:

### `VAR(<varName> <op> <value>)`

Fires when the named numeric variable satisfies the relation. Operators: `=`, `<`, `>`, `<=`,
`>=`, `<>`. If the variable is not yet initialised the condition does not fire.

Examples:
```
BREAK IF VAR(SCORE >= 100)
BREAK AT 500:1 IF VAR(I <> 0)
```

### `VIEW("<text>")`

Fires when the virtual screen buffer contains `<text>` (case-insensitive). Single-shot.

Example:
```
BREAK IF VIEW("Game Over")
```

### `ELAPSE(<ms>)`

Fires when at least `<ms>` wall-clock milliseconds have elapsed since the last `CONTINUE` (or
since the programme started, if `CONTINUE` has not yet been sent). Single-shot; the timer resets
on every `CONTINUE`.

Example:
```
BREAK IF ELAPSE(5000)
```

## Example session

```
Running Agent Debugger for programme: pong.bas
LOADED
SIZE 25 80
READY
BREAK AT 100:1
BREAKPOINT SET
READY
CONTINUE
BREAK AT 100:1
SIZE 25 80
READY
VAR SCORE
VARIABLES:
SCORE: 0.0
READY
VIEW 0 4
┌────────────────────────────────────────────────────────────────────────────────┐
│{80}                                                                             │
│{35}PONG{41}                                                                     │
│{80}                                                                             │
│{80}                                                                             │
│{80}                                                                             │
└────────────────────────────────────────────────────────────────────────────────┘
READY
CONTINUE
```

## Notes

- Statement indices (`<stmt>`) are 1-based and reset to 1 at the start of each new line.
- `ELAPSE` breakpoints measure wall-clock time, not CPU time or BASIC frame ticks (`FRAMES`).
- `FAST` mode (see [language_features.md](language_features.md)) suppresses terminal re-rendering;
  the virtual screen buffer is always up to date regardless of fast mode.
- `MockScreen` (used internally by the debugger) silently ignores `FAST`/`SLOW` — `VIEW` always
  shows the current buffer state.
