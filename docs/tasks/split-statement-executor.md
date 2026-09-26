# Split `StatementExecutor` into focused collaborators

## Context

`app-bazlang/src/main/java/com/davidconneely/bazlang/exec/StatementExecutor.java` is about 1,150
lines and does several unrelated jobs besides dispatching statements:

- **Background audio session management** - the `AplaySession` record, its daemon thread, the paced
  frame-render loop (`renderNextFramePaced`), live channel replacement (`updateLiveAplaySession`)
  and `stopBackgroundAudio`. This is thread lifecycle code, not statement semantics.
- **Raster algorithms** - Bresenham line (`drawLine`) and midpoint circle (`drawCircle`,
  `plotCircleOctants`).
- **`PRINT` layout** - tab stops, comma/apostrophe separators, `AT`/`TAB` handling and the
  newline-suppression rule in `executePrintStmt`.
- **String assignment** - `assignStrTarget` and its scalar/array helpers, which duplicate the
  read-side logic in `ExpressionEvaluator.evalStrSubscriptCore` (already tracked as "Unify
  read/write subscript-and-slice resolution" in `PLAN.md`).
- **Two identical chunked BREAK-polling wait loops** in `executePauseStmt` and `executeBeepStmt`.

The class also sits at PMD's `ExcessiveImports` threshold, and `listLine` uses fully-qualified class
names purely to stay under it - a sign that the class needs splitting, not that the rule needs
dodging.

The intended outcome is a `StatementExecutor` that dispatches statements and delegates to small,
separately testable collaborators, with no change in behaviour: every program-level test and every
entry in `docs/quirks.md` must pass unchanged.

## Candidate extractions

In rough order of value versus risk:

1. **APLAY session controller** - move `AplaySession`, the render loop and the stop/replace logic
   into a class in the `play` package (for example `BackgroundPlayer`), owning its thread and taking
   a `VirtualSpeaker`. `StatementExecutor` keeps only the statement-level decisions (evaluate the
   channel strings, decide between "update live session" and "start new one").
   `Interpreter.resume()`'s BREAK path already calls `executor.stopBackgroundAudio()`; that call
   would delegate. Watch the race documented at the `frame.finished()` branch - the session thread
   deliberately never exits on its own.
2. **Breakable wait** - one helper for the "sleep in 20 ms chunks, poll `pollForBreak()`, raise
   `L BREAK into program`" loop shared by `PAUSE` and `BEEP` (and conceptually by `PLAY`'s loop).
3. **Raster helper** - `drawLine`/`drawCircle` as pure functions over an `IntIntConsumer`-style plot
   callback, so they can be unit-tested without a screen. Keep this in `app-bazlang`, not
   `lib-cell`, unless a second consumer appears.
4. **`PRINT` layout** - a small class holding `tabPos`/`suppressNewline` state across one `PRINT`
   statement's items.
5. **String assignment** - do this together with the existing "Unify read/write subscript-and-slice
   resolution" plan item rather than separately.

## Constraints

- Keep the exhaustive `switch (stmt)` with no `default` arm in `execute`.
- Preserve `withRestoredStyles` semantics for `PLOT`/`DRAW`/`CIRCLE`/`PRINT`/`LIST`.
- Do not change `VirtualSpeaker`'s push-based, no-idle-silence contract (ADR-0003, ADR-0007).
- `docs/spec/architecture.md` describes `StatementExecutor`'s responsibilities and the audio
  threading; update it in the same change as each extraction.

## Status

Not started.
