---
status: "accepted"
date: 2026-10-29
decision-makers: David Conneely
---

# 2. How is audio output structured and rendered?

## Context and Problem Statement

`BEEP`, and later `PLAY`/`APLAY`, needed an output abstraction that headless screens could ignore,
while `PLAY`/`APLAY` also needed to wait for a multi-channel tune, or run it in the background,
while staying interruptible by BREAK. `VirtualScreen` already defines around 30 methods for output,
graphics and attributes, so audio needed somewhere to land.

The first implementation made two choices that had to be revisited. The speaker pulled frames
through a `PlaySource`/`isPlaying()` pair, and `TerminalScreen` rendered them on a persistent
background thread. The pull model broke headless tests: a no-op speaker never reports "playing", so
BREAK could never fire and `PLAY` returned instantly. The persistent thread then produced a real
latency bug: a `SourceDataLine` is a FIFO, so a loop that keeps writing silence runs a buffer ahead,
and newly triggered notes land behind the backlog. Short notes were clipped and game sound effects
arrived late or seemed to go missing.

## Considered Options

- Put `beep`/`playFrame`/`stopBeep`/`stopPlay` on `VirtualScreen`.
- Define `VirtualSpeaker` as its own interface, implemented alongside `VirtualScreen`.
- Pull-based frames: hand the speaker a `PlaySource`; `StatementExecutor` polls `isPlaying()`.
- Push-based frames: `StatementExecutor` pulls fixed ~20ms slices from `PlaySequencer` and pushes
  each resolved slice to `speaker.playFrame(...)`/`stopPlay()`, with no duration or liveness concept
  on the speaker side.
- Keep the persistent render thread and fix the backlog by writing silence more sparingly, or by
  tracking idle explicitly.
- Keep the persistent render thread, but have `playFrame` block until the thread consumes the pushed
  voices.
- Drop the persistent render thread: `playFrame` synthesises exactly the audio it is asked for and
  writes it synchronously on the calling thread.

## Decision Outcome

Chosen options, which together are the current audio architecture:

1. **A separate `VirtualSpeaker` interface.** Audio is not a screen concern, and folding it into
   `VirtualScreen` would entangle two things that vary independently on an interface that is already
   large. Every `VirtualSpeaker` method defaults to a no-op, so every implementation except
   `TerminalScreen` gets silent `BEEP`/`PLAY`/`APLAY` for free.
2. **Push-based frames.** The pull model silently broke headless testability. All DSL parsing,
   timing and BREAK handling stays in `StatementExecutor`, mirroring `executeBeepStmt`'s
   chunked-sleep/BREAK-poll shape. The push decision's predicted cost, a persistent background
   render thread, was rejected by decision 3.
3. **Synchronous per-call rendering.** The persistent thread caused the latency bug above.
   `playFrame(..., durationSeconds)` writes for exactly that duration on the calling thread; the
   line's own backpressure paces playback, with a fallback sleep only where there is no device.
   `drainPlay()` was added alongside `stopPlay()`: `stopPlay()` flushes queued audio immediately,
   while `drainPlay()` lets the tail finish at a natural end.

The push decision is about who initiates each frame, not about buffering. The separate "nothing ever
queues idle silence" rule in [`docs/spec/architecture.md`](../spec/architecture.md) ("I/O system")
governs buffering and still holds under the push model.

### Consequences

- Good, because screens and audio evolve independently: a new headless or test screen needs zero
  audio-specific code, and `VirtualScreen` does not grow further.
- Good, because `PLAY`/`APLAY` are headless-testable for free, exactly like `BEEP` - nothing about
  correctness depends on a real audio device reporting its own state.
- Good, because the backlog and latency bug is gone by construction: nothing is written that was not
  explicitly requested, so there is no buffer of silence to land behind.
- Good, because the design is simpler than predicted - no second thread, no cross-thread handoff,
  and no synchronisation beyond the lock already needed for the shared line and render position.
- Bad, because two interfaces have to be threaded through constructors (for example
  `StatementExecutor`'s ~21 call sites) instead of one.
- Bad, because `playFrame` blocks the calling thread for roughly `durationSeconds` once the buffer
  is saturated; callers must keep individual calls short enough to stay responsive to BREAK.
- Neutral: `VirtualSpeaker` implementations get a third overridable method (`drainPlay`, default
  no-op), matching the existing default-no-op pattern for headless implementations.
