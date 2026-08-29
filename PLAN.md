# Plan

Single ranked backlog, most important first. Entries are **deleted** when done, never annotated -
a plan that accumulates completed items stops being read.

## Fix `monster.bas`'s maze-view rendering

**Type:** bug - **Importance:** high - **Effort:** medium

The 3D Monster Maze example (`app-bazlang/src/example/bas/monster.bas`, 446 lines) is incomplete and
has a rendering bug in the maze view - confirmed 2026-08-22, not just a stale status note. See
`docs/research/0001-3d-monster-maze-reference.md` for the reference mechanics (maze generation, Rex
AI, the six-depth-segment rendering scheme) and two previously-tried rendering approaches that didn't
work, plus an untried PET-port-inspired simplification (single-character vanishing point, buffer the
frame as a string before printing) worth considering as a starting point instead of the original's
exact `DISTCOL`/`DISTWALL` segment tables.

## Baseline output tests for the interactive example games

**Type:** debt - **Importance:** high - **Effort:** medium

No automated comparison exists between a scripted playthrough's final screen state and a stored
snapshot, for games like `lander.bas` or `monster.bas` (see `docs/testing.md` "What is deliberately
not covered"). Snapshots should be text grids of the cell buffer, not images - the screen is a
character-cell buffer, text snapshots diff cleanly in review, and `MockScreen` already provides most
of the machinery.

## `WHILE...WEND` / `REPEAT...UNTIL`

**Type:** feature - **Importance:** high - **Effort:** medium

Structured loops for variable-length iteration, avoiding line-number-dependent loop structures. The
`FOR`/`NEXT` skip-scan (a flat linear pass over flattened statements) is the proven pattern for
locating matching terminators, so this fits the current execution model without structural change.

## `IF...THEN...ELSE`

**Type:** feature - **Importance:** high - **Effort:** medium (single-line `ELSE`), large
(multi-line blocks)

Single-line `ELSE` first - it fits the existing statement model. Multi-line block `IF` is a larger
step: execution flow is line-label based, so block terminators need the same flat-scan treatment as
loops, and unterminated blocks need well-defined runtime errors.

## `DEF PROC` & local scoping

**Type:** feature - **Importance:** high - **Effort:** large

Multi-line procedures with parameters passed by value or reference, and local variable namespaces
(using `LOCAL`). Shifts BazLang from a flat line-number-based execution flow toward a modern,
block-structured language. The largest language item: needs a call stack with local frames layered
over the current global variable model, and interacts with `GOSUB`, `CLEAR`, and the variable
reference caching described in `docs/spec/architecture.md`.

## Consistent error attribution in `ExpressionEvaluator`

**Type:** bug - **Importance:** medium - **Effort:** small

`StatementExecutor.codedException` records `(code, currentLineLabel, currentStatementIndex, msg)`,
but `ExpressionEvaluator.codedException` records only `(code, currentLineLabel, msg)` - every
expression-level error loses the statement index and reports an incomplete location. Thread
`state.currentStatementIndex()` through for parity.

## `TL$` / `UTL$` (string tail)

**Type:** feature - **Importance:** medium - **Effort:** small

Native support for substring operations that return everything except the first byte/character,
simplifying recursive string manipulation. Small, self-contained grammar and evaluator change.

## `PLAY`/`APLAY` tied notes (`_`)

**Type:** feature - **Importance:** low - **Effort:** small

`PlayParser.parse` already reserves `_` and rejects it with a clear "not yet supported" error
(`PlayParser.java:164`) rather than silently misparsing it - `PlayParser`'s own class doc calls this
out as "Phase 2, not yet implemented", so it belongs on this list rather than only living as a
parse-error message. A tie needs one new thing at the sequencer level: `PlaySequencer` inserts
`ARTICULATION_GAP_TICKS` of silence after every note so consecutive same-pitch notes don't merge
into one drone (see the class doc there); a tied pair needs to suppress exactly that gap between the
two notes while still summing their durations, rather than the note-and-rest-based rhythm model
skipping straight to `parseNoteOrDuration` in `PlayParser` needing any deeper change. Triplet
duration codes (`10`-`12`) are the DSL's other "Phase 2" parse-error placeholder in the same spot -
worth doing alongside this one if either comes up, but each is independently small enough not to
block on the other.

## Unify read/write subscript-and-slice resolution

**Type:** debt - **Importance:** medium - **Effort:** medium

Both sides now read the same `StrSubscript`/`StrSlice` AST type, but the bounds-resolution
*algorithm* is still duplicated: `ExpressionEvaluator.evalStrSubscriptCore` versus
`StatementExecutor.assignStrScalarTarget`/`assignStrArrayTarget` both distinguish a single byte index
from a slice, call `SliceBounds.resolve`, and compute the array element offset the same way. Extract
a shared resolver so the read and write paths cannot drift apart.

## Model control flow as returned signals

**Type:** debt - **Importance:** medium - **Effort:** medium

Statements currently signal `GO TO`/`GO SUB`/`RETURN`/`NEXT` and the `IF`-skip by mutating
`EvalState.pendingJump`/`running`, which `Interpreter.resume()` reads after each `execute`; `STOP`
and `BREAK` are thrown as `ReportException`s and pattern-matched in `InterpreterReplHandler`. Both
couple control flow to shared mutable state (or the exception channel): a new statement that forgets
the post-`execute` `hasPendingJump()` check mis-sequences silently, and a normal pause/stop is
indistinguishable, at the type level, from a genuine error. A sealed `ControlFlow` result
(`Continue`, `Jump(address)`, `Stop`, `Return`, ...) returned from statement execution would make the
loop explicit. `StatementExecutor.execute(Stmt)` already returns normally rather than being a
`Void`-returning ANTLR visitor method, so this is now a smaller change than it once was - best
sequenced alongside decomposing `EvalState` below, since both touch the same execution seam.

## Decompose `EvalState`

**Type:** debt - **Importance:** medium - **Effort:** medium

`EvalState` (~420 lines) is a single mutable blackboard: the program, four variable namespaces, FOR
loops, the return stack, the RNG, the DATA pointer, the program counter, the pending jump, the last
report, default styles, and the graphics cursor. Centralisation keeps `NEW`/`CLEAR`/`RUN` tractable,
but there is no encapsulation and no testable seam. Extracting cohesive collaborators -
`VariableStore`, `ReturnStack`, `ProgramCounter`, `DataCursor` - behind a thin `EvalState` facade
would clarify exactly what each of `NEW`/`CLEAR`/`RUN` resets, and give the item below something to
drive against.

## Programmatic component tests

**Type:** debt - **Importance:** medium - **Effort:** medium

Tests that drive `ExpressionEvaluator.evalNum()` and `StatementExecutor.visit()` directly with
parsed fragments, rather than executing complete programs through the interpreter. ANTLR has no
builder API for constructing parse trees programmatically, so the practical route is parsing minimal
source snippets and feeding the resulting contexts to the component under test -
`ExpressionEvaluatorTest`/`StatementExecutorTest` already use this approach, but cover only a
handful of cases against a much larger statement and expression surface. Ongoing, not a one-shot.

## `TerminalScreenGraphicsTest.testInverseOverRendering` failed once on `windows-latest`

**Type:** debt - **Importance:** medium - **Effort:** medium

Confirmed 2026-08-22, one GitHub Actions `Build` run: `TerminalScreenGraphicsTest.java:184` failed
on `windows-latest` only (ubuntu/macos passed); did not reproduce locally. Cause not yet diagnosed -
`StatementExecutorTest.Aplay.anIdleAplaySessionStopsPushingAudioEntirelyRatherThanStreamingSilence`
failed the same way on `macos-latest` twice, in two separate runs, and needed two attempts to fix
properly: a first pass (polling until the recorded call count went quiet) was still flaky, because a
GC pause on a loaded runner can space two still-legitimate mid-note chunks further apart than a short
quiet-window would assume. The working fix waits for `drainPlay()` instead - `StatementExecutor`'s
own single-fire, unambiguous "just went idle" signal (see
[ADR-0007](docs/adr/0007-synchronous-per-call-play-rendering.md)) - rather than inferring idleness
from any timing heuristic. Worth auditing any other test using `Thread.sleep` for synchronization the
same way, but confirm this Windows failure actually recurs before spending more effort on it.

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

## `RAND` entropy source is unreliable

**Type:** bug - **Importance:** low - **Effort:** small

`visitRandStmt` seeds the no-argument/zero case from
`System.nanoTime() ^ ((long) new Object().hashCode() << 32 | new Object().hashCode())`. Identity hash
codes are not a guaranteed entropy source (some JVMs hand out near-sequential values), and allocating
two throwaway `Object`s to read them is wasteful. `System.nanoTime()` alone - optionally mixed with
`ThreadLocalRandom.current().nextLong()` - is simpler and stronger. Keep the XorShift mixing that
follows.

## `PLAY`/`APLAY` volume is linear, not the AY chip's logarithmic curve

**Type:** bug - **Importance:** low - **Effort:** small

`PlaySequencer.nextFrame` resolves a channel's non-envelope amplitude as flat
`volume[channelIndex] / 15.0` (`PlaySequencer.java:184`). Real AY-3-8912 hardware (and both
`joric`/`AY38912PSG.java` and `JSpeccy`/`AY8912.java`, compared directly against this code) use a
measured ~logarithmic 16-level table instead (roughly x1.5 amplitude per step, not equal
increments), so relative loudness between two `V` volume settings doesn't match real hardware -
e.g. volume 8 sounds louder relative to volume 15 here than on a real chip. Self-contained fix:
replace the linear divide with either a small lookup table (JSpeccy's `volumeRate` array is a
ready-made source of the measured values) or a simple cubic approximation
(`amplitude = (volume / 15.0)^3`, per softspectrum48's AY emulation notes, "Part 4: Improving
Dynamics" - the same series also points at the CPC Wiki's PSG page and the AY-3-8910 manual for the
underlying measured curve if a closer match than the cubic is wanted later).

## `PLAY`/`APLAY` tone edges are not band-limited

**Type:** debt - **Importance:** low - **Effort:** small

`voiceSample`/`fillPlayMix` (`TerminalScreen.java:787`, `:814`) generate each channel's square wave
as a hard `sampleIndex % samplesPerCycle < samplesPerCycle / 2` flip, so a transition always lands
on a whole-sample boundary - a naive, non-band-limited oscillator that aliases on higher notes.
`JSpeccy`/`AY8912.java`'s `updateAY` softens this by weighting the partial sample at each tone
transition (its `percent` calculation); `joric`/`AY38912PSG.java` does not do this either, so this
is a step behind one of the two references, not both. Worth doing alongside the volume-table item
above since both touch the same mixing code.

## Accessor naming consistency

**Type:** debt - **Importance:** low - **Effort:** small

Accessors mix bare and `get`-prefixed styles: `screen()`, `program()`, `input()` versus
`getExprEvaluator()`, `getVariablesSnapshot()`, `getStringVariablesSnapshot()`. Settle on the bare
record-style convention already dominant in `exec` and rename the `get`-prefixed outliers.

## Resolve the threading model; remove the `DecimalFormat` `ThreadLocal`s if single-threaded

**Type:** debt - **Importance:** low - **Effort:** small

`ExpressionEvaluator` still holds `ThreadLocal<DecimalFormat> SCI_FORMAT`/`DEC_FORMAT` for number
formatting, implying concurrent execution is supported - but the AST's mutable ref-cache fields
(`NumVarExpr.ref` etc.) already mean a `ProgramLine`'s cached `Stmt` list is not safe to execute
concurrently. If execution is genuinely single-threaded (it appears to be), a plain field replaces
those two `ThreadLocal`s.

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

## 64-bit explicit integers (`%` suffix)

**Type:** feature - **Importance:** low - **Effort:** large

Support for variables (e.g. `count%`, `grid%(10)`) with exact 64-bit semantics and fast bitwise
operations. The value is semantic correctness (exact integer arithmetic, well-defined bit ops), not
performance - in the current AST-walking interpreter, memory-footprint and speed gains would be
minimal until the bytecode tier exists, so the two items should not be justified by each other. Large
because a second numeric type touches the whole expression evaluator, assignment targets, arrays,
and `DEF FN`.

## Virtual machine / bytecode tier

**Type:** feature - **Importance:** low - **Effort:** large (the largest item on this list)

Transition the interpreter from AST-`switch` execution to compiling into a custom, compact bytecode
run on a lightweight VM stack. The most speculative item: AST execution is already comfortably fast
for 50 Hz-era game workloads, so this should wait for profiling evidence of need. Its precondition -
a typed AST to compile from - is already satisfied. `PEEK`/`POKE` support is really a separate
feature (a simulated memory map with defined layout) that does not require a bytecode VM and should
be planned independently.
