# Architecture

This document explains how the BazLang interpreter is built - its grammar, Java structure, execution
model, and performance-load-bearing patterns. It is one of the three members
[`SPECIFICATION.md`](../../SPECIFICATION.md) indexes, and the only one of the three actually about
the Java code - `SPECIFICATION.md` itself holds none of this directly. It is written for
**interpreter implementers** (including LLM agents modifying the code). Language-level behaviour is
documented in [language.md](language.md) and the deliberately-preserved eccentric behaviours in
[quirks.md](../quirks.md) - nothing listed there may be changed by a refactoring.

## Grammar

BazLang uses ANTLR 4 to generate its lexer and parser from a declarative grammar file
(`app-bazlang/src/main/antlr/BazLang.g4`) - why ANTLR over a hand-written parser is
[ADR-0006](../adr/0006-use-antlr-for-the-grammar.md). This section covers the grammar's key patterns;
the grammar file itself is the machine-readable source of truth for syntax - see `DOC-MAP.md`
"Machine-readable and generated parts" - so nothing here may restate a production, only explain it.

### Key grammar patterns

#### Expression precedence

The resulting precedence order, as a plain reference table for BazLang programmers, is in
[language.md](language.md#math) - this section covers the mechanism, not the outcome; don't let the
two drift apart.

ANTLR handles operator precedence by ordering - earlier alternatives bind tighter:

```antlr
numExpr
    : NUM_LITERAL                                           # NumLiteralExpr
    | NUM_IDENTIFIER                                        # NumVarExpr
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'         # NumArrayExpr
    | '(' numExpr ')'                                       # NumParenExpr
    | numFunc                                               # NumFuncCallExpr
    | <assoc=right> numExpr ('**' | '^') numExpr            # NumPowerExpr
    | '-' numExpr                                           # NumUnaryMinusExpr
    | numExpr ('*' | '/') numExpr                           # NumMulDivExpr
    | numExpr ('+' | '-') numExpr                           # NumAddSubExpr
    | numExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') numExpr # NumCompExpr
    | strTerm ('<' | '<=' | '>' | '>=' | '=' | '<>') strTerm # StrCompExpr
    | NOT numExpr                                           # NumNotExpr
    | numExpr AND numExpr                                   # NumAndExpr
    | numExpr OR numExpr                                    # NumOrExpr
    ;
```

Note: `<assoc=right>` makes `**` and `^` right-associative, so `2^3^4` = `2^(3^4)`.

#### Case insensitivity

The grammar uses ANTLR's `caseInsensitive` option for keywords and identifiers:

```antlr
options { caseInsensitive=true; }

PRINT : 'PRINT';  // Matches PRINT, print, Print, etc.
```

This allows `PRINT`, `print`, and `Print` to all match the same token. Variable names are normalised
to uppercase when building the AST, so `myVar`, `MYVAR`, and `MyVar` all refer to the same variable.

String literal *contents* remain case-sensitive since they're captured as-is between quotes.

#### Numeric vs string identifiers

The grammar distinguishes numeric and string variables at the lexer level:

```antlr
STR_IDENTIFIER : [A-Z][A-Z0-9_]*'$' ;
NUM_IDENTIFIER : [A-Z][A-Z0-9_]* ;
```

This ensures `a` is always a numeric variable and `a$` is always a string variable, without
ambiguity. (The pattern uses `[A-Z]` but matches case-insensitively due to the grammar option.)

#### Function binding

Functions bind tightly to their arguments (atoms), not full expressions:

```antlr
numFunc
    : SIN numAtom
    | COS numAtom
    | PLOTMODE
    // ...
    ;

numAtom
    : NUM_LITERAL
    | NUM_IDENTIFIER
    | '(' numExpr ')'
    | numFunc
    ;
```

This means `SIN PI/2` parses as `SIN(PI)/2`, not `SIN(PI/2)`.

Multi-argument functions require explicit parentheses and comma-separated full expressions (not just
atoms), consistent with ZX Spectrum BASIC functions like `ATTR` and `SCREEN$`:

```antlr
numFunc
    : UCNEXT '(' strExpr ',' numExpr ')'
    | XATTR '(' numExpr ',' numExpr ',' numExpr ')'
    // ...
    ;
```

This means `UCNEXT(a$, i+1)` works as expected.

#### String subscripts and slicing

String subscripts use a unified rule that allows indices and optional slicing:

```antlr
strSubscript
    : numExpr (',' numExpr)*                        // indices only
    | numExpr (',' numExpr)* ',' numExpr? TO numExpr?  // indices + slice
    | numExpr? TO numExpr?                          // slice only
    ;
```

This supports: `a$(1)`, `a$(1,2)`, `a$(1 TO 5)`, `a$(TO 5)`, `a$(1 TO)`, `a$(TO)`,
`a$(1, 2 TO 5)`, etc.

### Statements vs. REPL commands

BazLang strictly separates program execution logic (statements) from interactive IDE/environment
actions (REPL commands).

```antlr
replLine
    : NUM_LITERAL statements? EOF                          # NumberedLine
    | replCommand EOF                                      # ReplCommandLine
    | statements EOF                                       # ImmediateLine
    ;
```

As defined in the `replLine` root parsing rule:

- **Statements** (`PRINT`, `LET`, `IF`, etc.) can be placed inside numbered program lines, or
  chained together with colons in immediate execution mode (e.g., `PRINT 1 : PRINT 2`).
- **REPL commands** (`RENUM`, `REFORMAT`, `EDIT`, `DELETE`) modify the program or interact with the
  editor. They **cannot** be placed inside numbered program lines, they **cannot** be combined with
  other statements using a colon, and they **must** be the only instruction entered on the line.

### Adding new features

To add a new operator (e.g., modulo `%`):

1. Add to grammar: `| numExpr '%' numExpr  # NumModExpr`
2. Add a case to `AstLowering.lowerNum` producing the AST node (a new `Op` enum value plus a
   `NumBinaryOp` case, or a new `NumExpr` record if it doesn't fit the existing binary-op shape),
   and a matching `case` in `ExpressionEvaluator.evalNum`/`evalNumBinaryOp` (for expressions) or the
   equivalent `Stmt`/`StatementExecutor` pair (for statements)
3. Write tests

The grammar serves as both the implementation and the documentation of the language syntax.

## Code structure

The interpreter executes from a typed AST - `Stmt`/`NumExpr`/`StrExpr` - walked via Java `switch`
pattern matching, not from the ANTLR parse tree directly. Each `ProgramLine`'s source text is parsed
once and lowered once, lazily, on first execution.

### Package layout

Under `com.davidconneely.bazlang`:

- **root**: entry point and REPL wiring (`MainClass`, `InterpreterReplHandler`) plus the shared
  primitives used by every package (`BStr`, `Limits`, `ReportCode`, `ReportException`).
- **`exec`**: the execution engine - `Interpreter`, `StatementExecutor`, `ExpressionEvaluator`,
  `EvalState`, `Program`, `ProgramLine`, `ProgramStorage`, and the small value types `SliceBounds`,
  `StyleState`.
- **`exec.ast`**: the typed AST and the lowering pass - `Stmt`, `Expr`/`NumExpr`/`StrExpr`, `Op`,
  `NumFuncKind`/`StrFuncKind`, `AssignTarget`, `StyleItem`, `PrintElement`, `LineRange`,
  `StrSubscript`, and `AstLowering` (the ANTLR-parse-tree-to-AST lowering pass).
- **`edit`**: program-editing commands (`ProgramEditor`, `ReformatVisitor`) - these operate directly
  on freshly parsed ANTLR trees or source text, not the AST; see "Parse tree vs. AST" below.
- **`antlr`**: the parser facade (`AntlrParser`) and the generated lexer/parser.
- **`io`**: screens and input (see the I/O section below).
- **`play`**: `PLAY`/`APLAY`'s note-string DSL parser and multi-channel scheduler (`PlayParser`,
  `PlayToken`, `PlayChannelState`, `SharedRegisters`, `PlaySequencer`) - see the I/O section below.
- **`debug`**: `DebugEngine`, the protocol-agnostic debugging core used by the MCP server.
- **`mcp`**: `McpServer`, the agent-oriented MCP (Model Context Protocol) debugger entry point.

### Main components

- **ANTLR grammar (`BazLang.g4`)**: Defines the lexer and parser rules declaratively. ANTLR
  generates `BazLangLexer` and `BazLangParser` from this grammar.
- **`AntlrParser`**: A facade that wraps the ANTLR parser, providing `parseProgramLines()`,
  `parseReplLine()`, `parseStatementsContext()`, `parseNumExpr()`, and `parseStrExpr()` entry
  points. ANTLR syntax errors are converted to `ReportException` (`C Nonsense in BASIC`) by a custom
  error listener.
- **`AstLowering`**: A pure, `EvalState`-free set of functions that lower an ANTLR parse tree
  (`StatementsContext`/`NumExprContext`/`StrExprContext`/...) to the typed AST. Resolves literals
  and operators once, at lowering time; variable/array references stay unresolved until first
  evaluation (see "Variable reference caching" below). `AstLowering.lowerStatements` also performs
  the flattening described in "Statement addressing and flattening" - folding what used to be a
  separate `ProgramLine.flatten()` pass into the same walk that produces the AST.
- **`ExpressionEvaluator`**: Walks the typed `NumExpr`/`StrExpr` AST via `switch` pattern matching
  and returns `double`/`BStr` directly from `evalNum`/`evalStr`.
- **`StatementExecutor`**: Walks the typed `Stmt` AST via `switch` pattern matching, executing
  variable assignment, I/O operations, state mutation, and the flow-control statements (`CONT`,
  `FOR`, `GO SUB`, `GO TO`, `NEXT`, `RETURN`, `RUN`). The `switch` in `execute(Stmt)` has no
  `default` arm - it is exhaustive over the sealed `Stmt`, so a new statement kind is a compile
  error here until handled, not a silent gap. A 3-argument convenience constructor builds the
  default `ProgramStorage`/`ExpressionEvaluator` collaborators; the full constructor takes them
  (and the `AntlrParser`) injected.
- **`Interpreter`**: Manages the overall flow. It coordinates the executor and evaluator, decides
  which line to run next, handles jumps, and loops until the program stops.
- **`EvalState`**: The program's memory. It stores variables (scalars and arrays), custom functions,
  the state of any active `FOR` loops, the `GOSUB` return stack, the `DATA` pointer, the
  current/pending execution position (a `StatementAddress`), the last report, the random generator,
  the graphics cursor, and the default style attributes (a `StyleState`). `FnDefinition.body` is a
  lowered `Expr`, not a parse-tree node.
- **`Program`**: Encapsulates the line storage (a `TreeMap<Integer, ProgramLine>`), ensuring the
  underlying map is protected. Owns the program-order navigation scans `findFirstData` (the
  `RESTORE`/`READ` pointer) and `findMatchingNext` (the `FOR` skip-scan), matching `Stmt.DataStmt`/
  `Stmt.NextStmt` via pattern matching.
- **`ProgramLine`**: Stores the source text of each line and lazily lowers it to a flat `Stmt` list
  on first execution, caching that list to avoid re-lowering on subsequent calls.
  `getStatements(parser)` is a separate accessor that always re-parses the source text fresh into a
  raw ANTLR tree - used only by `ProgramEditor`/`ReformatVisitor` and parser-level tests, never
  execution, and deliberately shares no state with the cached AST (see "Parse tree vs. AST" below).
- **`BStr`**: The immutable byte-string value type used for all BazLang string values (see
  [language.md](language.md) for its byte semantics).
- **`InterpreterReplHandler`**: Routes each REPL line - numbered entry (store/delete), REPL-only
  command (`DELETE`/`EDIT`/`RENUM`/`REFORMAT`, delegated to `ProgramEditor`), or immediate
  execution - and records the last-report state consumed by `CONT` and shown in the status bar.
- **`ProgramEditor` / `ReformatVisitor`**: Program-editing commands; `RENUM` also rewrites
  `GO TO`/`GO SUB`/`RESTORE`/`RUN` targets.
- **`ProgramStorage`**: `SAVE` (plain text, one numbered line per file line, line 0 skipped) and
  `LOAD` (from a file, or from the classpath when the name starts with `resource:`).
- **`ReportCode` / `ReportException` / `Limits`**: The ZX-style report codes (`0`-`R`), the carrier
  exception (code, line label, statement index, detail), and interpreter limits.
- **`debug.DebugEngine`**: The protocol-agnostic debugging core - owns the interpreter,
  `BreakpointEngine` (breakpoint store and `CSC`/`ELAPSE`/`?expr`/`EVERY` condition evaluation),
  `ScreenText` (screen search and the `bazlang_screen` grid dump), and `MockScreen`. `McpServer`
  (`com.davidconneely.bazlang.mcp`) is the sole adapter over it, translating JSON-RPC `tools/call`
  requests into `DebugEngine` calls - see [mcp.md](mcp.md) for the tool reference.

### Debugger execution model

Why this is synchronous rather than a command-thread design is recorded in
[ADR-0001](../adr/0001-synchronous-debugger.md); this section covers the mechanics.

**Synchronous blocking execution, no reentrant command loop.** `DebugEngine.run`/`gotoLine`/`go`/
`stepInto`/`stepOver` each drive `Interpreter.resume()` on the caller's own thread until the
programme next breaks, elapses, steps, hits its wall-clock safety timeout, or stops, then return -
there is no blocking wait for a "next command" inside the engine itself. A breakpoint pauses
execution by having `Interpreter.setExecutionListener`'s callback set `EvalState.setRunning(false)`
before the triggering statement executes, which unwinds `Interpreter.resume()` straight back to the
caller; a later `go()` call resumes at the exact same location, guarded so the same breakpoint does
not immediately re-fire. `stepInto`/`stepOver` reuse that same resume guard to let the current
statement execute, then either pause unconditionally on the next one (`stepInto`) or let a deeper
`GOSUB` call run free - while still honouring a breakpoint inside it - until the return-stack depth
is back at or below where stepping started (`stepOver`; see `EvalState.returnStackDepth()`). Every
run-control call also arms a per-call wall-clock safety timeout (default 30s, overridable), pausing
with a `Limit` reason if nothing else fires first - a mitigation for the lack of true `tools/call`
cancellation (see `docs/spec/mcp.md` "Known limitations"), not a substitute for it.

### Class coupling notes

Facts an implementer needs before restructuring anything:

- `AntlrParser` is injected everywhere it is used at runtime; the global singleton
  `AntlrParser.INSTANCE` is named only at composition roots (`MainClass`, `McpServer`) and in
  constructor defaults.
- AST nodes carry per-`EvalState` reference caches (see below), so a `ProgramLine`'s cached `Stmt`
  list must never be shared between two interpreter instances.

### Parse tree vs. AST

Two representations of a line's source text coexist deliberately, for different purposes:

- **`ProgramLine.getFlattenedStatements(parser)`** - the typed AST (`List<Stmt>`), lowered once and
  cached. This is what execution walks: `Interpreter`, `StatementExecutor`, `ExpressionEvaluator`,
  and `Program`'s scans all operate on it exclusively.
- **`ProgramLine.getStatements(parser)`** - a raw ANTLR parse tree, re-parsed fresh on every call,
  sharing no state with the cached AST. `ProgramEditor.executeReformat` and `ReformatVisitor` use
  this (`REFORMAT` needs to walk grammar structure and regenerate source text, not execute), as do
  parser-level tests that assert on parse-tree shape directly. Because it is always freshly parsed,
  nothing here observes or mutates the execution AST's variable-reference caches or vice versa.

`ProgramEditor`'s other commands (`RENUM`, `DELETE`) work from source text and token streams
directly, never touching either representation - see `ProgramEditor.updateLineTargets`.
`BreakpointEngine`'s `?expr` condition and `DebugEngine`'s `bazlang_eval` tool parse-and-lower a
fresh `NumExpr`/`StrExpr`/`Stmt` on every check/call (the same "parse fresh every time" shape `VAL`/
`INPUT` use - see below), rather than reading any cached AST.

## Execution model

### Statement addressing and flattening

Every executable position is a pair **(line label, statement index)**, where the statement index is
**1-based** and counts positions in the line's **flattened** statement list.
`ProgramLine.getFlattenedStatements()` lists a line's statements in source order with the bodies of
`IF ... THEN` statements inlined recursively after the `IF` itself: `10 IF x THEN PRINT "A":
PRINT "B"` flattens to `[IfStmt, PrintStmt("A"), PrintStmt("B")]` with indices 1-3.

Flat indices are the shared currency of the interpreter loop, `CONT`, `GOSUB` return addresses, the
`DATA` pointer, the `FOR` skip-scan, and `BreakpointEngine` breakpoints (`<line>:<stmt>`).

### The fetch-execute loop

`Interpreter.resume()` loops while the state is running:

1. If a **pending jump** (label + statement index) is set in `EvalState`, consume it; a negative
   label ends the run. Otherwise, advance to the next higher line number; none left means a normal
   end. If the current line label is 0 (immediate mode) and no jump is pending, the run ends.
2. Poll `VirtualInput.pollForBreak()`; a pending break raises `L BREAK into program`.
3. Fetch and flatten the line. The valid start-index range is `1 .. size + 1` (`size + 1` means
   "start past the end", i.e. fall through); anything else raises `N Statement lost`.
4. Visit statements from the start index, stopping early when a statement sets a pending jump or
   stops the run.

All control transfers are expressed as pending jumps recorded in `EvalState`; visitor methods never
call back into the interpreter. `GO TO`/`GO SUB` resolve targets with `ceilingKey`
(jumping past the last line is a clean stop). `RUN` implies `CLEAR`; `GO TO` does not. `CONT`
resumes at the last-report location (for reports `9 STOP statement` and `L BREAK into program`, at
the *following* statement).

### Sentinel values

- **Line 0 is the immediate-mode line.** `Interpreter.executeImmediate()` temporarily inserts the
  immediate statements at key 0 and removes them in a `finally`. Line 0 is excluded from `GO TO`
  targeting, `SAVE`, and `LIST`; a `0 ...` REPL line executes immediately (ZX81-style). A false
  `IF` in immediate mode sets a pending jump to statement index `Integer.MAX_VALUE`, which the
  loop's bounds check reports as `N Statement lost, 0:1` - intentional, see
  [quirks.md](../quirks.md).
- **`DATA` pointer**: components of `-1` mean "not yet initialised"; a line label of
  `Integer.MAX_VALUE` means "exhausted" (`E Out of DATA` on the next `READ`).

### State lifecycle

`EvalState.clear()` blanks the *contents* of the variable reference objects (`NumVarRef`,
`NumArrayRef`, `StrVarRef`, `FnDefRef`) but keeps the objects themselves alive. This is what keeps
references cached on AST nodes valid across `CLEAR`/`RUN`. Editing program lines preserves all
runtime state (hot-patching, see [quirks.md](../quirks.md)); only `NEW` and `CLEAR`
reset it.

## I/O system (the `io` package)

Input and output are handled by a set of classes that share a common `VirtualScreen` interface
(which extends the base `ReplReader` and `AutoCloseable` interfaces), a `VirtualInput` interface,
and a `VirtualSpeaker` interface (for `BEEP`/`PLAY`/`APLAY`), isolating the interpreter from the
specific device. `VirtualSpeaker` is deliberately its own interface rather than another
`VirtualScreen` method - see [ADR-0002](../adr/0002-virtualspeaker-separate-interface.md) for why. Every
`VirtualSpeaker` method defaults to a no-op, so every implementation except `TerminalScreen` gets
silent `BEEP`/`PLAY`/`APLAY` for free, the same way `setFastMode` already works. Frames are pushed to
the speaker rather than pulled from it - see [ADR-0003](../adr/0003-push-based-audio-frames.md).

`PLAY`/`APLAY`'s DSL parsing and multi-channel scheduling live entirely in their own
`com.davidconneely.bazlang.play` package (`PlayParser`, `PlayToken`, `PlayChannelState`,
`SharedRegisters`, `PlaySequencer`), not in `io` - `VirtualSpeaker.playFrame` only ever sees
already-resolved `VoiceFrame`s (frequency/amplitude/tone-or-noise), never a "note" or a "channel
string". `StatementExecutor` pulls from the `play` package's `PlaySequencer` (via `PlaySource`) in
~20ms slices and hands each resolved slice to `speaker.playFrame`, which renders exactly that much
audio and no more - this keeps all DSL/BREAK logic on the `StatementExecutor` side, so
`PLAY`/`APLAY` are headless-testable for free, just like `BEEP`.

**Nothing ever queues idle silence, and this is load-bearing rather than incidental.** An audio
line is a FIFO whose `write` blocks only once the buffer is full, so any component that keeps
writing silence while nothing is playing runs a whole buffer ahead of the speaker permanently, and
every later-triggered note lands *behind* that backlog - constant latency equal to the buffer
depth. An earlier design did exactly that (a persistent render thread continuously synthesising
whatever voices were last pushed to it) and produced a real, user-visible bug: game sound effects
arriving noticeably late or seeming to go missing entirely, with short notes clipped by the fixed
sampling granularity such a loop requires. Writing only real, requested audio also means the
line's own backpressure paces playback for free, so the pull loops need no cadence mechanism of
their own beyond a fallback sleep for the no-device (headless) case.
`APLAY` runs the same pull loop as `PLAY`, just on its own background thread - see
[language.md](language.md#input--output) for the blocking/non-blocking contract itself.

- **`TerminalScreen`**: The standard version for interactive use. It provides a TUI (Text User
  Interface) with distinct window regions: an interpreter output area at the top, an input area with
  prompt, and a status bar. Uses the `TerminalEngine` class (which wraps JLine) for terminal
  control, escape sequences, and raw input. Supports command history, cursor movement, and handles
  terminal window resizes gracefully. The only `VirtualSpeaker` implementation that plays real
  audio. `beep()` starts a square-wave `SourceDataLine` write on its own daemon thread and returns
  immediately, so the interpreter thread stays free to run `PAUSE`-style chunked BREAK-polling
  (`StatementExecutor.executeBeepStmt`) instead of blocking inside the audio write for the tone's
  whole duration; `stopBeep()` cuts a tone short on BREAK. `playFrame()` synthesises exactly the
  requested duration of up to 3 mixed voices and writes it straight to a second, entirely
  independent persistent `SourceDataLine` (opened lazily, reused for the session) on whichever
  thread called it - no render thread of its own, per the no-idle-silence rule above (see
  [ADR-0007](../adr/0007-synchronous-per-call-play-rendering.md)); `stopPlay()`
  flushes audio already queued but not yet heard, so a note cut short by BREAK or a replacement
  stops promptly, while `drainPlay()` instead lets a naturally-finished note's queued tail play out
  before parking the line, called only when a sound reaches its natural end rather than being cut
  short. Keeping `PLAY`/`APLAY` on their own line means a `BEEP` sound effect can layer
  over music without either interfering with the other, matching real hardware's independent
  beeper/AY circuits. `LineUnavailableException` (no audio device - e.g. a headless/SSH session) is
  caught and silently swallowed for both, matching the no-op fallback the interface already
  provides elsewhere.
- **`StreamScreen`**: A simpler version used for pipes or non-interactive environments. It uses
  standard Java `System.in` and `System.out`. Graphics (`PLOT` and related draw calls)
  are no-ops. `MainClass` also falls back to this screen silently if `TerminalScreen` cannot be
  initialised (intentional - see [quirks.md](../quirks.md)).
- **`MockScreen`**: An in-memory screen with a scripted input queue, used by the program tests and
  by `DebugEngine`.
- **`AbstractCellBufferedScreen`**: The base class for screens backed by a lib-cell `CellBuffer`;
  tracks the cursor and the active attribute set.

The `VirtualScreen` interface defines methods for screen output (`print`, `println`, `cls`),
graphics (`plot`, `point`, `setPlotMode`), attributes (`setInk` ... `setOver`), introspection
(`getScreenCodepoint`, `getScreenAttributes`,
`getXAttributes`), and status updates (`setStatus`). `VirtualInput` defines `readln` (with different
modes for REPL vs `INPUT`), non-blocking `inkey()`/`uinkey()`, break polling, and input prefill.
Most graphics and attribute methods have no-op defaults so that simple screens stay simple.

## Statement execution notes

Mechanism only - the observable behaviour these implement is in [language.md](language.md); don't
restate it here, link to it.

- **Graphics & rendering**: The screen uses a `CellBuffer` designed with a Structure-of-Arrays (SoA)
  layout for high performance, supporting 24-bit RGB colours and styles. `PLOT` and `UNPLOT` operate
  on this buffer with dynamic sizing (see [language.md](language.md#input--output) for `PLOT`'s
  coordinate behaviour). Rendering resolution is pluggable via `PixelMode` (e.g., `QuadrantMode` for
  2x2 blocks, `SextantMode` for 2x3 blocks, or `BrailleMode` for 2x4 patterns). Text output (`PRINT`)
  and graphics share the same buffer seamlessly.
- **Styles**: Standalone style statements (`INK 2`) set both the session default (stored in
  `EvalState`) and the screen's active attribute. Style items embedded in `PRINT`/`PLOT`/`DRAW`
  (`PRINT INK 2; ...`) apply temporarily: the executor snapshots the defaults, applies the items,
  and restores the defaults afterwards (`withRestoredStyles`).
- **Error handling**: A `ReportException` carries the code, line label, and statement index; the
  REPL handler formats and shows it in the status bar. See [language.md](language.md#errors) for the
  report format itself.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step, and return
  location) in `EvalState`. The `NEXT` statement increments the variable and checks if the loop
  should continue. If the initial value is already past the limit, a flat skip-scan finds the
  matching `NEXT` (see [quirks.md](../quirks.md) for the scan's deliberate eccentricities).
- **`DATA`/`READ`/`RESTORE`**: `DATA` is a no-op at execution time; a three-part pointer in
  `EvalState` (line label, statement index, expression index) tracks the next value, advancing
  within a statement, then to the next `DATA` statement in the line, then to the next line with
  `DATA`.
- **`INPUT`**: This uses the `VirtualInput` to read a full line of text from the user; for a numeric
  target the input is evaluated as a full numeric expression (`VAL` semantics). See
  [language.md](language.md#input--output) for what the user sees on a syntax error or `STOP`.

## Performance & memory optimisations

To ensure high execution performance and minimise garbage collector pressure on the JVM, the
interpreter implements several key patterns. These are load-bearing; refactorings must keep their
effect:

- **Direct primitive returns, no boxing**: `ExpressionEvaluator.evalNum`/`evalStr` return `double`/
  `BStr` directly from a `switch` expression over the sealed `NumExpr`/`StrExpr` - no boxed
  `Double` wrapper objects, and no `numResult`/`strResult` side fields for the visitor to stash
  into (the ANTLR-visitor predecessor this replaced at the parse-tree-to-AST migration had to use
  side fields, since a single visitor type parameter can't cleanly return `double` for one rule
  family and `BStr` for another without boxing; ordinary method returns don't have that
  restriction).
- **Variable reference caching**: Variables are normally looked up in the
  [EvalState](../../app-bazlang/src/main/java/com/davidconneely/bazlang/exec/EvalState.java)
  maps by their name strings. To avoid continuous hash map lookups during execution (especially in
  tight loops), the AST's variable/array/subscript nodes (`NumExpr.NumVarExpr`, `NumExpr.NumArrayExpr`,
  `StrExpr.StrVarExpr`, `StrExpr.StrSubscriptExpr`, and the `AssignTarget` variants) are small
  mutable classes, not plain records: each carries a nullable, typed `ref` field (e.g.
  `EvalState.NumVarRef`) that is resolved once on first evaluation and reused thereafter. (This is
  why cleared variables keep their ref objects, and why a `ProgramLine`'s cached `Stmt` list is
  bound to one `EvalState` - see "State lifecycle" above.)
- **Literal and operator resolution at lowering time**: `AstLowering` resolves numeric, binary, and
  string literals, and arithmetic/comparison operators (`Op`), once when lowering a parse tree to
  AST - every other node in the AST is an immutable record - so evaluation never reparses literal
  text or re-derives an operator from token text.
- **Lazy lowering**: `ProgramLine` defers parsing-and-lowering to first execution and caches the
  resulting flat `Stmt` list.
- **Index scratch stack**: `ExpressionEvaluator` evaluates array subscripts into a shared
  `int[256]` stack (with a stack pointer restored in `finally`) instead of allocating an index array
  per access.
- **Single-byte string cache**: `BStr.fromByte` returns interned single-byte instances for all 256
  byte values (used heavily by `CHR$` and `INKEY$`).

## Language quirks

Deliberately preserved eccentric behaviours (stale `FOR` variables, the flat skip-scan, `DATA`
visibility inside `IF` bodies, byte-oriented string arrays, immediate-mode line-0 reports, and more)
are documented in [quirks.md](../quirks.md). Treat that page as a contract: behaviour listed there must
survive any change to this codebase.
