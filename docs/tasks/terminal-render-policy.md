# Define which operations force a terminal render

## Context

Found 2026-09-28 while pacing `torus.bas` to 120 CPS with `FAST`/`SLOW` and `PAUSE`. The language
reference describes `FAST` as suppressing re-rendering "after each output operation" and `SLOW` as
flushing pending changes, but it doesn't say which operations render anyway. In practice several do,
including one that bypasses `FAST` entirely, so a programme that double-buffers a frame between
`FAST` and `SLOW` can't be sure a half-drawn frame never reaches the screen. The key-polling path is
also slower than it needs to be.

The intended outcome is a short, documented rule for when the terminal renders, stated in
`docs/spec/language.md` next to `FAST`/`SLOW`, with `TerminalScreen` implementing exactly that rule,
and an `INKEY$`/`UINKEY$` poll that costs close to nothing when no key is waiting.

## Current behaviour

Every path in `app-bazlang/src/main/java/com/davidconneely/bazlang/io/TerminalScreen.java` that
renders, as of 2026-09-28. "Throttled" means it renders only when the screen is dirty and at least
the named interval has passed since the last render.

| Trigger                                            | Renders in `SLOW`                             | Renders in `FAST` |
| :------------------------------------------------- | :-------------------------------------------- | :---------------- |
| `SLOW` (`setFastMode(false)`)                      | if dirty                                      | n/a               |
| `CLS` (`cls()`)                                    | always                                        | no                |
| `PRINT` newline, `SCROLL`, `PLOT`/`DRAW`/`CIRCLE`  | throttled (`FRAME_RENDER_INTERVAL_MS`, 20ms)  | no                |
| end of every `PRINT` statement (`flush()`)         | throttled (`FLUSH_RENDER_INTERVAL_MS`, 100ms) | yes, throttled    |
| `INKEY$`/`UINKEY$` (`renderIfDue(true)`)           | throttled (20ms)                              | yes, throttled    |
| `INPUT` (`readln`), before and after the prompt    | always                                        | yes, always       |
| end-of-programme "Press any key" (`waitForKey`)    | if dirty                                      | yes, if dirty     |
| construction, input-height change, terminal resize | always                                        | yes, always       |

Separately, `inkey()`/`uinkey()` call `engine.readKey(1L)`, so each poll with nothing waiting blocks
for up to 1ms - about an eighth of a frame at 120 CPS.

## Questions to settle

- Should `FAST` guarantee that nothing a programme does renders until `SLOW` - so `INKEY$` in a
  `FAST` block reads keys without rendering - or should `INKEY$` keep rendering so that a game that
  never calls `SLOW` still updates? The second is presumably why `renderIfDue(true)` exists; check
  the example games before changing it.
- Is `INPUT` rendering inside `FAST` correct? It needs the screen current to show the prompt, so it
  probably stays, but the specification should say so.
- `PRINT` ends with `screen.flush()` (in `StatementExecutor.executePrintStmt`) so that
  semicolon-terminated output appears. Should that respect `FAST`? As it stands, a frame that takes
  longer than 100ms can be shown half-drawn by its own status-line `PRINT`.
- Can a poll check for buffered input and return at once when there is none, without reintroducing
  the Windows zero-byte-read problem documented in `RobustLineReaderImpl`?

## Status

Not started. `torus.bas` currently works around both issues by calling `INKEY$` after `SLOW` and
inside its paced wait, so the poll's cost comes out of the frame's idle time.
