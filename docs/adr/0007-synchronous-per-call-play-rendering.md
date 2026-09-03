---
status: "accepted"
date: 2026-08-22
decision-makers: David Conneely
---

# 7. Render `PLAY`/`APLAY` audio synchronously per call; no persistent render thread

## Context and Problem Statement

[ADR-0003](0003-push-based-audio-frames.md) chose to push resolved audio frames to
`VirtualSpeaker.playFrame` rather than pull them, and predicted, as a stated "Bad" consequence, that
`TerminalScreen`'s real playback would need "its own persistent background render thread (started
lazily, decoupled from the push cadence) rather than simply blocking inside play()." A first
`TerminalScreen` implementation built exactly that: a persistent thread continuously synthesising
whatever voices were last pushed to it, so a caller's `playFrame` call only had to update shared
state.

That design produced a real, user-visible bug. A `SourceDataLine` is a FIFO whose `write` blocks
only once the buffer is full, so a render loop that keeps writing silence while nothing is playing
runs a whole buffer ahead of the speaker at all times. Every newly-triggered note then lands
_behind_ that backlog - constant latency equal to the buffer depth - and short notes were easily
clipped by the loop's own fixed sampling granularity. Game sound effects (e.g. pong's paddle click)
arrived noticeably late or seemed to go missing entirely.

## Considered Options

- Keep the persistent render thread, and fix the backlog by having it write silence more sparingly,
  or track "idle" explicitly so it stops writing when there is nothing to play.
- Keep the persistent render thread, but have `playFrame` block until the thread has actually
  consumed the pushed voices, restoring synchronous pacing at the cost of reintroducing a handoff
  between two threads.
- Drop the persistent render thread entirely: `playFrame` synthesises exactly the audio it is asked
  for and writes it straight to the line, synchronously, on whichever thread called it (the
  interpreter thread for `PLAY`, the background `APLAY` thread otherwise) - never queuing anything
  the caller did not explicitly ask for.

## Decision Outcome

Chosen option: drop the render thread.
`playFrame(VoiceFrame, VoiceFrame, VoiceFrame, durationSeconds)` now writes synchronously, for
exactly `durationSeconds`, on the calling thread - no separate thread, no idle silence ever queued.
The line's own backpressure (`write()` blocking once the buffer is full) then paces playback for
free: `StatementExecutor`'s ~20ms pull loop needs no cadence mechanism of its own beyond a fallback
sleep for the no-device (headless/test) case, where `write()` returns instantly.

This also required two API changes ADR-0003 did not anticipate:

- `playFrame` gained a `durationSeconds` parameter. ADR-0003 explicitly said the speaker side would
  have "no duration ... concept ... at all"; that turned out to be necessary so the fallback sleep
  (used only when there is no real device) can advance a tune in something like real time.
- `drainPlay()` was added alongside `stopPlay()`. `stopPlay()` flushes queued-but-unheard audio
  immediately (BREAK, or a replacing call) but was also being used, unconditionally, at the natural
  end of every `PLAY`/`APLAY` - which clipped the last note's tail, and left the line running empty
  and starved between sounds (some backends replay stale buffer contents in that state, heard as
  spurious repeats). `drainPlay()` lets the queued tail finish, then parks the line, and is called
  only when a sound reaches its natural end.

The core choice in ADR-0003 - push rather than pull - still stands and is not reconsidered here;
this record only replaces the implementation detail it got wrong.

### Consequences

- Good, because the backlog/latency bug is gone by construction: nothing is ever written that was
  not explicitly requested, so there is no buffer of silence to land behind.
- Good, because the design is simpler than predicted - no second thread, no cross-thread handoff, no
  synchronization beyond the lock already needed for the shared line/render-position state.
- Bad, because `playFrame` now blocks the calling thread for roughly `durationSeconds` once the
  buffer is saturated - callers must keep individual calls short enough to stay responsive to BREAK;
  this was already true of the pull loop's ~20ms cadence, so nothing new had to be built for it.
- Neutral: `VirtualSpeaker` implementations get a third overridable method (`drainPlay`, default
  no-op), matching the existing default-no-op pattern for headless implementations.

<!-- Supersedes the "persistent background render thread" consequence of ADR-0003; that decision's
     own core choice (push over pull) is unaffected and not revisited here. -->
