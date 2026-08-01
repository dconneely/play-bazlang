# Implementation details

This document explains how the BazLang interpreter is built using Java. It is written for
**interpreter implementers** (including LLM agents modifying the code). Language-level behaviour
is documented in [language_features.md](language_features.md) and the deliberately-preserved
eccentric behaviours in [quirks.md](quirks.md) — nothing listed there may be changed by a
refactoring.

## Code structure

The interpreter executes directly from ANTLR's parse tree using a visitor pattern.

### Package layout

Under `com.davidconneely.bazlang`:

- **root**: entry point and REPL wiring (`MainClass`, `InterpreterReplHandler`) plus the shared
  primitives used by every package (`BStr`, `Limits`, `ReportCode`, `ReportException`).
- **`exec`**: the execution engine — `Interpreter`, `StatementExecutor`, `ExpressionEvaluator`,
  `AstAnnotator`, `EvalState`, `Program`, `ProgramLine`, `ProgramStorage`, and the small value
  types `Ops`, `SliceBounds`, `StyleState`.
- **`edit`**: program-editing commands (`ProgramEditor`, `ReformatVisitor`).
- **`antlr`**: the parser facade (`AntlrParser`) and the generated lexer/parser.
- **`io`**: screens and input (see the I/O section below).
- **`debug`**: `AgentDebugger`, the agent-oriented debugger main class.

### Main components

- **ANTLR grammar (`BazLang.g4`)**: Defines the lexer and parser rules declaratively. ANTLR
  generates `BazLangLexer` and `BazLangParser` from this grammar.
- **`AntlrParser`**: A facade that wraps the ANTLR parser, providing `parseProgramLines()`,
  `parseReplLine()`, `parseStatementsContext()`, `parseNumExpr()`, and `parseStrExpr()` entry
  points. ANTLR syntax errors are converted to `ReportException` (`C Nonsense in BASIC`) by a
  custom error listener.
- **`ExpressionEvaluator`**: A visitor that evaluates numeric and string expressions from the
  parse tree.
- **`StatementExecutor`**: A visitor that executes statements — variable assignment, I/O
  operations, state mutation, and the flow-control statements (`CONT`, `FOR`, `GO SUB`, `GO TO`,
  `NEXT`, `RETURN`, `RUN`). A 3-argument convenience constructor builds the default
  `ProgramStorage`/`ExpressionEvaluator` collaborators; the full constructor takes them (and the
  `AntlrParser`) injected.
- **`Interpreter`**: Manages the overall flow. It coordinates the executor and evaluator,
  decides which line to run next, handles jumps, and loops until the program stops.
- **`AstAnnotator`**: A one-time pass over each freshly parsed tree that caches parsed literal
  values (`cachedNum`, `cachedStr`) into fields declared as grammar `locals`.
- **`EvalState`**: The program's memory. It stores variables (scalars and arrays), custom
  functions, the state of any active `FOR` loops, the `GOSUB` return stack, the `DATA` pointer,
  the current/pending execution position (a `StatementAddress`), the last report, the random
  generator, the graphics cursor, and the default style attributes (a `StyleState`).
- **`Program`**: Encapsulates the line storage (a `TreeMap<Integer, ProgramLine>`), ensuring the
  underlying map is protected. Owns the program-order navigation scans `findFirstData` (the
  `RESTORE`/`READ` pointer) and `findMatchingNext` (the `FOR` skip-scan).
- **`ProgramLine`**: Stores the source text of each line, lazily parses to a parse tree on first
  execution, and caches a flattened statement list to avoid rebuilding it on subsequent calls.
- **`BStr`**: The immutable byte-string value type used for all BazLang string values (see
  [language_features.md](language_features.md) for its byte semantics).
- **`InterpreterReplHandler`**: Routes each REPL line — numbered entry (store/delete), REPL-only
  command (`DELETE`/`EDIT`/`RENUM`/`REFORMAT`, delegated to `ProgramEditor`), or immediate
  execution — and records the last-report state consumed by `CONT` and shown in the status bar.
- **`ProgramEditor` / `ReformatVisitor`**: Program-editing commands; `RENUM` also rewrites
  `GO TO`/`GO SUB`/`RESTORE`/`RUN` targets.
- **`ProgramStorage`**: `SAVE` (plain text, one numbered line per file line, line 0 skipped) and
  `LOAD` (from a file, or from the classpath when the name starts with `resource:`).
- **`ReportCode` / `ReportException` / `Limits`**: The ZX-style report codes (`0`–`R`), the
  carrier exception (code, line label, statement index, detail), and interpreter limits.
- **`debug.AgentDebugger`**: A separate main class that runs the interpreter under a
  stdin/stdout protocol for LLM agents (see [language_debugger.md](language_debugger.md)),
  using `MockScreen`. It is split into `DebugSession` (the command loop and session wiring),
  `BreakpointEngine` (breakpoint store and `CSC`/`ELAPSE`/`?expr`/`EVERY` condition evaluation),
  `QuotedArg` (the protocol string codec), and `ScreenText` (screen search and the `/RSC` grid
  dump); `AgentDebugger` itself is only the documented entry point. The protocol transcript is
  pinned end-to-end by `AgentDebuggerProtocolTest` — a change to those transcripts is a protocol
  change and must be reflected in `language_debugger.md`.

### Debugger architecture decision

**Synchronous blocking execution.** The debugger blocks inside the main execution thread.
`DebugSession` runs its command loop (`blockAndListen`) re-entrantly from within the statement
`visit` override (via `Interpreter.setExecutionListener`), meaning the loop runs inside statement
execution. This strictly synchronous design entirely avoids concurrency — a genuine
simplification — but it is the least conventional part of the architecture. A command thread
with a handoff queue is the standard alternative for debuggers, but the trade-off (introducing
shared-state concurrency, lock management, and thread safety across the entire `EvalState`)
is significant. The current single-threaded approach is a documented decision: it limits
the debugger's ability to interrupt an infinite loop (since it only gains control at statement
boundaries) but keeps the execution model simple and deterministically testable.

### Class coupling notes

Facts an implementer needs before restructuring anything:

- `AntlrParser` is injected everywhere it is used at runtime; the global singleton
  `AntlrParser.INSTANCE` is named only at composition roots (`MainClass`, `AgentDebugger`) and
  in constructor defaults.
- Parse trees are annotated with per-`EvalState` caches (see below), so a `ProgramLine`'s cached
  tree must never be shared between two interpreter instances.

## Execution model

### Statement addressing and flattening

Every executable position is a pair **(line label, statement index)**, where the statement index
is **1-based** and counts positions in the line's **flattened** statement list.
`ProgramLine.getFlattenedStatements()` lists a line's statements in source order with the bodies
of `IF ... THEN` statements inlined recursively after the `IF` itself: `10 IF x THEN PRINT "A":
PRINT "B"` flattens to `[IfStmt, PrintStmt("A"), PrintStmt("B")]` with indices 1–3.

Flat indices are the shared currency of the interpreter loop, `CONT`, `GOSUB` return addresses,
the `DATA` pointer, the `FOR` skip-scan, and `AgentDebugger` breakpoints (`<line>:<stmt>`).

### The fetch–execute loop

`Interpreter.resume()` loops while the state is running:

1. If a **pending jump** (label + statement index) is set in `EvalState`, consume it; a negative
   label ends the run. Otherwise advance to the next higher line number; none left means a normal
   end. If the current line label is 0 (immediate mode) and no jump is pending, the run ends.
2. Poll `VirtualInput.pollForBreak()`; a pending break raises `L BREAK into program`.
3. Fetch and flatten the line. The valid start-index range is `1 .. size + 1` (`size + 1` means
   "start past the end", i.e. fall through); anything else raises `N Statement lost`.
4. Visit statements from the start index, stopping early when a statement sets a pending jump or
   stops the run.

All control transfers are expressed as pending jumps recorded in `EvalState`; visitor methods
never call back into the interpreter. `GO TO`/`GO SUB` resolve targets with `ceilingKey`
(jumping past the last line is a clean stop). `RUN` implies `CLEAR`; `GO TO` does not. `CONT`
resumes at the last-report location (for reports `9 STOP statement` and `L BREAK into program`,
at the *following* statement).

### Sentinel values

- **Line 0 is the immediate-mode line.** `Interpreter.executeImmediate()` temporarily inserts the
  immediate statements at key 0 and removes them in a `finally`. Line 0 is excluded from `GO TO`
  targeting, `SAVE`, and `LIST`; a `0 ...` REPL line executes immediately (ZX81-style). A false
  `IF` in immediate mode sets a pending jump to statement index `Integer.MAX_VALUE`, which the
  loop's bounds check reports as `N Statement lost, 0:1` — intentional, see
  [quirks.md](quirks.md).
- **`DATA` pointer**: components of `-1` mean "not yet initialised"; a line label of
  `Integer.MAX_VALUE` means "exhausted" (`E Out of DATA` on the next `READ`).

### State lifecycle

`EvalState.clear()` blanks the *contents* of the variable reference objects (`NumVarRef`,
`NumArrayRef`, `StrVarRef`, `FnDefRef`) but keeps the objects themselves alive. This is what
keeps references cached in parse trees valid across `CLEAR`/`RUN`. Editing program lines
preserves all runtime state (hot-patching, see [quirks.md](quirks.md)); only `NEW` and `CLEAR`
reset it.

## I/O system (the `io` package)

Input and output are handled by a set of classes that share a common `VirtualScreen` interface
(which extends the base `ReplReader` and `AutoCloseable` interfaces) plus a `VirtualInput`
interface, isolating the interpreter from the specific device.

- **`TerminalScreen`**: The standard version for interactive use. It provides a TUI
  (Text User Interface) with distinct window regions: an interpreter output area at the top,
  an input area with prompt, and a status bar. Uses the `TerminalEngine` class (which wraps
  JLine) for terminal control, escape sequences, and raw input. Supports command history,
  cursor movement, and handles terminal window resizes gracefully.
- **`StreamScreen`**: A simpler version used for pipes or non-interactive environments.
  It uses standard Java `System.in` and `System.out`. Graphics (`PLOT` and related draw calls)
  are no-ops. `MainClass` also falls back to this screen silently if `TerminalScreen` cannot be
  initialised (intentional — see [quirks.md](quirks.md)).
- **`MockScreen`**: An in-memory screen with a scripted input queue, used by the program tests
  and by `AgentDebugger`.
- **`AbstractCellBufferedScreen`**: The base class for screens backed by a lib-cell `CellBuffer`;
  tracks the cursor and the active attribute set.

The `VirtualScreen` interface defines methods for screen output (`print`, `println`, `cls`),
graphics (`plot`, `point`, `setPlotMode`), attributes
(`setInk` … `setOver`), introspection (`getScreenCodepoint`, `getScreenAttributes`,
`getXAttributes`), and status updates (`setStatus`). `VirtualInput` defines `readln` (with
different modes for REPL vs `INPUT`), non-blocking `inkey()`/`uinkey()`, break polling, and
input prefill. Most graphics and attribute methods have no-op defaults so that simple screens
stay simple.

## Specific logic

- **Graphics & rendering**: The screen uses a `CellBuffer` designed with a Structure-of-Arrays
  (SoA) layout for high performance, supporting 24-bit RGB colours and styles. `PLOT` and
  `UNPLOT` operate on this buffer with dynamic sizing. Coordinates (0,0) start in the bottom-left
  corner. Rendering resolution is pluggable via `PixelMode` (e.g., `QuadrantMode` for 2x2 blocks,
  `SextantMode` for 2x3 blocks, or `BrailleMode` for 2x4 patterns). Text output (`PRINT`) and
  graphics share the same buffer seamlessly.
- **Styles**: Standalone style statements (`INK 2`) set both the session default (stored in
  `EvalState`) and the screen's active attribute. Style items embedded in `PRINT`/`PLOT`/`DRAW`
  (`PRINT INK 2; ...`) apply temporarily: the executor snapshots the defaults, applies the items,
  and restores the defaults afterwards (`withRestoredStyles`).
- **Error handling**: If something goes wrong (e.g. dividing by zero), the interpreter stops
  execution, throws a `ReportException`, and reports a Sinclair ZX BASIC-style code in the
  status bar. The format is `<Code> <Message>, <Line>:<Statement> (Optional details)`,
  for example: `6 Number too big, 100:1 (Arithmetic overflow)`.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step, and return
  location) in `EvalState`. The `NEXT` statement increments the variable and checks if the loop
  should continue. If the initial value is already past the limit, a flat skip-scan finds the
  matching `NEXT` (see [quirks.md](quirks.md) for the scan's deliberate eccentricities).
- **`DATA`/`READ`/`RESTORE`**: `DATA` is a no-op at execution time; a three-part pointer in
  `EvalState` (line label, statement index, expression index) tracks the next value, advancing
  within a statement, then to the next `DATA` statement in the line, then to the next line with
  `DATA`.
- **`INPUT`**: This uses the `VirtualInput` to read a full line of text from the user. For a
  numeric target the input is evaluated as a full numeric expression (`VAL` semantics); on a
  syntax error with an interactive screen, the user is re-prompted with the bad text prefilled.
  Typing `STOP` raises `H STOP in INPUT`.

## Performance & memory optimisations

To ensure high execution performance and minimise garbage collector pressure on the JVM, the
interpreter implements several key patterns. These are load-bearing; refactorings must keep
their effect:

- **Visitor as calculator**: Rather than returning boxed `Double` wrapper objects from parsing
  visitor methods (which would cause massive boxing and unboxing overhead during arithmetic
  evaluation), the evaluation visitor
  [ExpressionEvaluator](../src/main/java/com/davidconneely/bazlang/ExpressionEvaluator.java)
  returns `Void` and stores primitive double results directly in a class field.
- **Variable reference caching (`ctx.varRef`)**: Variables are normally looked up in the
  [EvalState](../src/main/java/com/davidconneely/bazlang/EvalState.java) maps by their name
  strings. To avoid continuous hash map lookups during execution (especially in tight loops),
  the parser context objects (`ctx`) cache their resolved reference objects (such as `NumVarRef`)
  after the first lookup. Subsequent evaluations retrieve the cached reference directly.
  (This is why cleared variables keep their ref objects, and why parse trees are bound to one
  `EvalState` — see "State lifecycle" above.)
- **Literal caching**: `AstAnnotator` pre-parses numeric, binary, and string literals into
  `cachedNum`/`cachedStr` context fields once per tree, so evaluation never re-parses text.
- **Lazy parse and flatten**: `ProgramLine` defers parsing to first execution and caches both the
  parse tree and the flattened statement list.
- **Index scratch stack**: `ExpressionEvaluator` evaluates array subscripts into a shared
  `int[256]` stack (with a stack pointer restored in `finally`) instead of allocating an index
  array per access.
- **Single-byte string cache**: `BStr.fromByte` returns interned single-byte instances for all
  256 byte values (used heavily by `CHR$` and `INKEY$`).

## Language quirks

Deliberately preserved eccentric behaviours (stale `FOR` variables, the flat skip-scan, `DATA`
visibility inside `IF` bodies, byte-oriented string arrays, immediate-mode line-0 reports, and
more) are documented in [quirks.md](quirks.md). Treat that page as a contract: behaviour listed
there must survive any change to this codebase.
