# Plan

Single ranked backlog, most important first. Entries are **deleted** when done, never annotated - a
plan that accumulates completed items stops being read. **One paragraph each** - see
[DOC-MAP.md](DOC-MAP.md).

## Baseline output tests for the interactive example games

**Type:** debt - **Importance:** high - **Effort:** medium

No automated comparison exists between a scripted playthrough's final screen state and a stored
snapshot, for games like `lander.bas` or `monster.bas` (see `docs/testing.md` "What is deliberately
not covered"). Snapshots should be text grids of the cell buffer, not images - the screen is a
character-cell buffer, text snapshots diff cleanly in review, and `MockScreen` already provides most
of the machinery.

## Simplify `LIST`/`DELETE`/`REFORMAT`/`RENUM` line-range syntax

**Type:** feature - **Importance:** medium - **Effort:** small

`FOR` is the only place real Sinclair BASIC uses `TO`/`STEP`; `LIST`/`DELETE`/`REFORMAT`'s
line-range arguments and `RENUM`'s are BazLang's own invention, with no Sinclair dialect having
typed line-range or renumber syntax at all. Replace the `TO`/`STEP` keyword syntax with
comma-positional arguments in all four (e.g. `DELETE 10, 100`, `RENUMBER 100, 10, 50, 80`), and
rename `RENUM` to `RENUMBER` to match the real "Renumber" spelling (confirmed 2026-08-30 against the
ZX Spectrum +3 manual, though real hardware's Renumber is a fixed-parameter menu option - a
separate, deeper divergence this item doesn't attempt to close), keeping `RENUM` as a short alias.

## Slash-prefix REPL-only commands (`/delete`, `/edit`, `/renumber`, `/reformat`, `/exit`)

**Type:** feature - **Importance:** medium - **Effort:** medium

`DELETE`, `EDIT`, `RENUM`/`RENUMBER`, and `REFORMAT` are BazLang's only REPL-only commands
(`replCommand` in `BazLang.g4`), and none exist in any real Sinclair dialect - unlike `LIST`, which
stays a real keyword because `10 LIST` is authentically valid inside a program. Prefix them with `/`
(`/delete 10,100`, `/renumber 100,10`), resolving the command name by text at the dispatch layer
instead of as dedicated keyword tokens, freeing `delete`/`edit`/`renum`/`renumber`/`reformat` for
use as ordinary variable/array names. Add a new `/exit`, and let `STOP` drop its own undocumented
REPL-exit special case: `InterpreterReplHandler` currently ends the whole REPL loop when `STOP` is
typed at line label 0, which no real hardware does and which `Repl.loop`'s existing EOF handling
already makes redundant.

## Tab-completion for statement/REPL-command keywords (JLine)

**Type:** feature - **Importance:** medium - **Effort:** medium

A `lib-repl` enhancement. JLine is already the terminal engine (`RobustLineReaderImpl` extends
`LineReaderImpl` directly), but no `Completer` is wired in, so a partial keyword or `/`-command gets
no suggestions. `lib-repl` depends only on JLine and knows nothing of BazLang or ANTLR, and
`TerminalEngine`'s own Javadoc says its job is to isolate the application from the terminal
library - so this needs a small neutral interface `lib-repl` wires into JLine internally, with
`app-bazlang` supplying candidates derived from `BazLangLexer`'s generated keyword vocabulary rather
than a hand-maintained list. Scope to the first token of a statement for v1; completing
`GOTO`/`GOSUB` targets or `LOAD`/`SAVE` filenames is a separate, larger follow-on.

## Syntax highlighting in the REPL (JLine)

**Type:** feature - **Importance:** medium - **Effort:** medium

A `lib-repl` enhancement, related to but independent of the tab-completion item above - same
`RobustLineReaderImpl` extension point (no `Highlighter` set), same module-boundary constraint:
`lib-repl` must not gain an ANTLR/BazLang dependency, `app-bazlang` must not gain a direct
`org.jline.*` one. `lib-repl` should expose a small neutral tokenizing interface it wires into
JLine's `Highlighter` internally; `app-bazlang` supplies an implementation built on the real
`BazLangLexer` - the same lexer used for actual parsing, not a hand-maintained regex scheme that
could drift from the grammar.

## Unify read/write subscript-and-slice resolution

**Type:** debt - **Importance:** medium - **Effort:** medium

Both sides now read the same `StrSubscript`/`StrSlice` AST type, but the bounds-resolution
_algorithm_ is still duplicated: `ExpressionEvaluator.evalStrSubscriptCore` versus
`StatementExecutor.assignStrScalarTarget`/`assignStrArrayTarget` both distinguish a single byte
index from a slice, call `SliceBounds.resolve`, and compute the array element offset the same way.
Extract a shared resolver so the read and write paths cannot drift apart.

## Programmatic component tests

**Type:** debt - **Importance:** medium - **Effort:** medium

Tests that drive `ExpressionEvaluator.evalNum()` and `StatementExecutor.visit()` directly with
parsed fragments, rather than executing complete programs through the interpreter. ANTLR has no
builder API for constructing parse trees programmatically, so the practical route is parsing minimal
source snippets and feeding the resulting contexts to the component under test -
`ExpressionEvaluatorTest`/`StatementExecutorTest` already use this approach, but cover only a
handful of cases against a much larger statement and expression surface. Ongoing, not a one-shot.

## Make the AST strictly immutable (Decouple reference caches from AST nodes)

**Type:** debt - **Importance:** medium - **Effort:** medium

AST nodes (`NumVarExpr`, `StrVarExpr`, etc.) currently carry mutable `ref` fields (e.g.
`EvalState.NumVarRef`) to cache variable lookups. This permanently ties a lowered `ProgramLine`'s
cached `Stmt` list to a single `EvalState` instance, preventing the sharing of a parsed program
across multiple interpreter sessions. Moving these caches out of the AST nodes and into the
`EvalState` (e.g. by assigning each variable reference a unique integer ID at lowering time and
having `EvalState` hold a flat array of references indexed by that ID) would make the AST strictly
immutable and thread-safe. This supersedes the "Resolve the threading model" item by enabling true
concurrent execution of the same AST.

## Agent-friendly formatting and line-numbering maintenance

**Type:** feature - **Importance:** medium - **Effort:** medium

Currently, maintaining consistent BASIC formatting and preserving logical line-number blocks (like
`1000`, `2000`) from outside the interactive REPL is difficult. Agents currently rely on an external
Python script (`block_renumber.py`) to orchestrate REPL commands for this. We should explore ways to
natively help LLM agents and external tools maintain consistent code style and line numbering in
`.bas` files, without pre-determining the architectural solution (e.g., via MCP tools, batch CLI
flags, or IDE extensions).

## `TerminalScreenGraphicsTest.testInverseOverRendering` failed once on `windows-latest`

**Type:** debt - **Importance:** medium - **Effort:** medium

Confirmed 2026-08-22, one GitHub Actions `Build` run: `TerminalScreenGraphicsTest.java:184` failed
on `windows-latest` only (ubuntu/macos passed); did not reproduce locally. Cause not yet diagnosed -
`StatementExecutorTest.Aplay.anIdleAplaySessionStopsPushingAudioEntirelyRatherThanStreamingSilence`
failed the same way on `macos-latest` twice, in two separate runs, and needed two attempts to fix
properly: a first pass (polling until the recorded call count went quiet) was still flaky, because a
GC pause on a loaded runner can space two still-legitimate mid-note chunks further apart than a
short quiet-window would assume. The working fix waits for `drainPlay()` instead -
`StatementExecutor`'s own single-fire, unambiguous "just went idle" signal (see
[ADR-0007](docs/adr/0007-synchronous-per-call-play-rendering.md)) - rather than inferring idleness
from any timing heuristic. Worth auditing any other test using `Thread.sleep` for synchronization
the same way, but confirm this Windows failure actually recurs before spending more effort on it.

## MCP: true `tools/call` cancellation

**Type:** feature - **Importance:** medium - **Effort:** large

`bazlang_step(run/goto/go/step_into/step_over)` blocks the calling thread until the programme next
pauses; `notifications/cancelled` is accepted but has no effect (see `docs/spec/mcp.md` "Known
limitations"). The `timeoutMs` safety cap already guarantees a programme with no breakpoint of its
own can't block a call forever, but an agent still can't interrupt one early on demand. A full fix
would run `Interpreter.resume()` on a worker thread so a cancellation notification arriving on stdin
could interrupt it mid-run - reintroducing the shared-state concurrency
[ADR-0001](docs/adr/0001-synchronous-debugger.md) deliberately avoided. Only worth doing if the
safety cap turns out to be insufficient in practice.

## External render sidecars

**Type:** feature - **Importance:** medium - **Effort:** large

Execute the interpreter headless while routing a streaming frame buffer (via TCP or WebSockets) to a
graphical display sidecar (e.g. a native canvas using OpenGL or WebAssembly). The `VirtualScreen`/
`VirtualInput` interface split is the enabling precondition and is already in place. Streaming
cell-buffer diffs over WebSocket to a browser canvas is the most practical first target.

## Fix `monster.bas`'s maze-view rendering

**Type:** bug - **Importance:** low - **Effort:** medium

The 3D Monster Maze example (`app-bazlang/src/example/bas/monster.bas`, 446 lines) is incomplete and
has a rendering bug in the maze view - confirmed 2026-08-22, not just a stale status note. See
`docs/research/0001-3d-monster-maze-reference.md` for the reference mechanics (maze generation, Rex
AI, the six-depth-segment rendering scheme) and two previously-tried rendering approaches that
didn't work, plus an untried PET-port-inspired simplification (single-character vanishing point,
buffer the frame as a string before printing) worth considering as a starting point instead of the
original's exact `DISTCOL`/`DISTWALL` segment tables. Downgraded from high 2026-08-29: an attempted
fix here made the rendering visibly worse rather than better, and needed a human to catch it -
parked at low priority until LLM-assisted work on this kind of pixel/character-level visual layout
is more reliable.

## `WHILE...WEND` / `REPEAT...UNTIL`

**Type:** feature - **Importance:** low - **Effort:** medium

Structured loops for variable-length iteration, avoiding line-number-dependent loop structures. The
`FOR`/`NEXT` skip-scan (a flat linear pass over flattened statements) is the proven pattern for
locating matching terminators, so this fits the current execution model without structural change.
Downgraded to low 2026-08-30 (previously demoted only to medium 2026-08-29): neither `WHILE`/`WEND`
nor `REPEAT`/`UNTIL` are part of ZX81 or ZX Spectrum BASIC, the dialects BazLang is based on - the
same non-authentic-extension reasoning that put `DEF PROC` below at low, on reflection, applies here
regardless of this item's smaller size; authenticity, not effort, is the reason for ranking below
core-fidelity work. See `docs/research/0004-while-repeat-loops-across-sinclair-basics.md`: `WEND`
itself doesn't appear in any Sinclair-heritage dialect surveyed there (it's the GW-BASIC-family
spelling) - the same seven dialects split three ways between `DO`/`LOOP` with an `UNTIL`/`WHILE`
clause on either end (SAM Coupé, Beta BASIC, Boriel), `REPEAT`/`REPEAT UNTIL` with `WHILE` as a
guard clause anywhere in the body (NextBASIC), and a single unified `REPeat`/`END REPeat` with no
dedicated condition keywords at all (QL SuperBASIC) - only BBC BASIC and COMAL use the separate
`WHILE...ENDWHILE` / `REPEAT...UNTIL` pair this item's name assumes.

## `IF...THEN...ELSE`

**Type:** feature - **Importance:** low - **Effort:** medium (single-line `ELSE`), large (multi-line
blocks)

Single-line `ELSE` first - it fits the existing statement model. Multi-line block `IF` is a larger
step: execution flow is line-label based, so block terminators need the same flat-scan treatment as
loops, and unterminated blocks need well-defined runtime errors. Downgraded to low 2026-08-30
(previously demoted only to medium 2026-08-29): Sinclair BASIC's `IF`/`THEN` has no `ELSE` clause at
all - same non-authentic-extension reasoning as `WHILE`/`WEND` above and `DEF PROC` below. See
`docs/research/0005-if-then-else-across-sinclair-basics.md`: every surveyed dialect's block form
uses a `THEN`-presence-or-position rule to tell single-line from block `IF` apart (matching this
item's own two-tier plan), but terminator spelling is split four ways (`ENDIF`/`END IF`/`ELIF` all
appear) and two dialects' `ELSE IF` chaining is a flat same-line scan, not real nesting - worth
picking the terminator/nesting shape deliberately when this is implemented, not by default.

## `DEF PROC` & local scoping

**Type:** feature - **Importance:** low - **Effort:** large

Multi-line procedures with parameters passed by value or reference, and local variable namespaces
(using `LOCAL`). Shifts BazLang from a flat line-number-based execution flow toward a modern,
block-structured language. The largest language item: needs a call stack with local frames layered
over the current global variable model, and interacts with `GOSUB`, `CLEAR`, and the variable
reference caching described in `docs/spec/architecture.md`. Downgraded from high 2026-08-30:
`DEF PROC`, multi-line `DEF FN`, and `LOCAL` are not ZX81 or ZX Spectrum BASIC - Sinclair `DEF FN`
is a single-expression construct, and there is no `DEF PROC`/`LOCAL` at all. Same
non-authentic-extension reasoning as `WHILE`/`WEND` and `IF`/`ELSE` above, taken further given how
large this item is. Still wanted eventually, just not ahead of core-fidelity work. See
`docs/research/0002-def-proc-across-sinclair-basics.md` for how BBC BASIC, Beta BASIC, SAM Coupé
BASIC, NextBASIC, QL SuperBASIC, Boriel ZX BASIC, and COMAL each handle parameter passing and
scoping - a `DEF FN`-shaped design (value-only params shadowing globals, an optional `LOCAL` reusing
that same shadow/restore mechanism, no reference parameters) would sit outside all of them, smaller
than any surveyed precedent. See also
`docs/research/0003-multiline-def-fn-across-sinclair-basics.md` on multi-line `DEF FN` specifically:
only QL SuperBASIC, COMAL, and Boriel actually extend `FN` past a single expression, and all three
do it by making `FN` just `PROC` with a mandatory return value rather than a separate mechanism -
SAM Coupé's manual instead states outright that it keeps `DEFFN` single-expression and composes
multiple `FN`s together for anything longer, the one precedent for keeping `FN` and `PROC` as
separate mechanisms the way BazLang's own design already does.

## Resolve the threading model; remove the `DecimalFormat` `ThreadLocal`s if single-threaded

**Type:** debt - **Importance:** low - **Effort:** small

`ExpressionEvaluator` still holds `ThreadLocal<DecimalFormat> SCI_FORMAT`/`DEC_FORMAT` for number
formatting, implying concurrent execution is supported - but the AST's mutable ref-cache fields
(`NumVarExpr.ref` etc.) already mean a `ProgramLine`'s cached `Stmt` list is not safe to execute
concurrently. If execution is genuinely single-threaded (it appears to be), a plain field replaces
those two `ThreadLocal`s.

## `AstLowering`'s over-long `BIN` literal error reports statement 1

**Type:** bug - **Importance:** low - **Effort:** small

`AstLowering.parseBinLiteral`'s "Binary literal exceeds 64 digits" error hardcodes statement index 1
rather than the literal's real statement, the one gap left after a pass that fixed every other
`ReportException` call site defaulting to statement 1. Not a mechanical thread-through like
`lineNumber`: the real index is the statement's position in the _flattened_ list `Interpreter` walks
(`flattenInto` splices each `IfStmt`'s body in right after it), which isn't resolved until after
every statement on the line - including nested `IF` bodies - has already been lowered, so a naive
parameter can't carry a value that doesn't exist yet at the point `parseBinLiteral` runs. Fixing it
means computing final flat positions structurally on the parse tree first (mirroring `flattenInto`'s
walk, but over `StatementContext`/`IfStmtContext` before lowering), then lowering each statement
with both `lineNumber` and `statementIndex` in hand.

## `VirtualScreen` is still wide (~30 methods)

**Type:** debt - **Importance:** low - **Effort:** small

Default methods keep individual implementations small, so this isn't urgent. If the external render
sidecar item above proceeds, consider splitting the graphics surface (`plot`, `point`,
`setPlotMode`) from text output at that point - not before.

## Immediate mode mutates the program

**Type:** debt - **Importance:** low - **Effort:** medium

`Interpreter.executeImmediate` inserts the statement at line 0, resumes, then removes line 0. The
line-0 attribution itself is intentional ZX BASIC fidelity (see `docs/quirks.md`) and correct as-is;
the only observation is that immediate execution temporarily mutates the program map. An alternative
would execute the statement without touching storage while still attributing results to line 0, but
the current approach is well-contained. Optional.

## Pin JaCoCo's `toolVersion` explicitly

**Type:** debt - **Importance:** low - **Effort:** small

`build.gradle.kts`'s shared `subprojects` block applies `jacoco` but never sets
`JacocoPluginExtension.toolVersion`, so the build floats on whatever version ships with the current
Gradle release rather than a pinned one - unlike `checkstyleVersion`/`pmdVersion`/
`spotbugsToolVersion`, which are all declared in `gradle/libs.versions.toml`. identigon/identigon's
own `jacocoTool` catalog entry is the pattern to copy: add a `jacoco` version to the catalog and
reference it from the `JacocoPluginExtension` configuration, so a Gradle upgrade can't silently
change coverage-report behaviour underneath the pinned per-module INSTRUCTION-coverage minimums.

## Trial `find-sec-bugs` on SpotBugs

**Type:** debt - **Importance:** low - **Effort:** small

SpotBugs runs in CI (`build.gradle.kts`'s `subprojects` block) without the `find-sec-bugs` plugin
identigon/identigon adds to its own SpotBugs configuration. Bazlang isn't obviously
security-sensitive, but it does run an ANTLR-generated parser over user-supplied `.bas` source and
exposes an MCP server that reads commands from stdin - both plausible categories the generic
SpotBugs ruleset doesn't specifically target. Add the plugin once and review what it actually flags
before deciding whether to keep it permanently; a clean run is useful information too.

## Javadoc/doclint enforcement (`Xdoclint:all` + `Xwerror`)

**Type:** debt - **Importance:** low - **Effort:** medium

identigon/identigon enforces full doclint (a missing `@param`/`@return`/`@throws` fails the build)
on every subproject; this repo has no such gate. `lib-cell` and `lib-repl` are consumed across
module boundaries, and `app-bazlang`'s MCP server (`McpServer`) is a programmatic surface other
tools call into, so undocumented public API is a real cost here, not just style. Sizeable one-time
debt to pay off first, though: existing public classes/methods would need a documentation pass
before the `Xwerror` gate could be turned on without breaking the build immediately.

## 64-bit explicit integers (`%` suffix)

**Type:** feature - **Importance:** low - **Effort:** large

Support for variables (e.g. `count%`, `grid%(10)`) with exact 64-bit semantics and fast bitwise
operations. The value is semantic correctness (exact integer arithmetic, well-defined bit ops), not
performance - in the current AST-walking interpreter, memory-footprint and speed gains would be
minimal until the bytecode tier exists, so the two items should not be justified by each other.
Large because a second numeric type touches the whole expression evaluator, assignment targets,
arrays, and `DEF FN`.

## Virtual machine / bytecode tier

**Type:** feature - **Importance:** low - **Effort:** large (the largest item on this list)

Transition the interpreter from AST-`switch` execution to compiling into a custom, compact bytecode
run on a lightweight VM stack. The most speculative item: AST execution is already comfortably fast
for 50 Hz-era game workloads, so this should wait for profiling evidence of need. Its precondition -
a typed AST to compile from - is already satisfied. `PEEK`/`POKE` support is really a separate
feature (a simulated memory map with defined layout) that does not require a bytecode VM and should
be planned independently.
