---
status: "accepted (refined by ADR-0007)"
date: 2026-08-22
decision-makers: David Conneely
---

# 3. Push resolved audio frames to `VirtualSpeaker`, rather than a pull/`isPlaying()` model

## Context and Problem Statement

`PLAY`/`APLAY` need `StatementExecutor` to wait for a multi-channel tune to finish (or, for `APLAY`,
to run in the background) while staying interruptible by BREAK, exactly like `BEEP`/`PAUSE`. The
first implementation gave `VirtualSpeaker` a `play(PlaySource)`/`isPlaying()` pair, with
`StatementExecutor`'s wait loop polling `isPlaying()` to decide when to stop waiting.

## Considered Options

* Pull-based: hand the speaker a `PlaySource`; `StatementExecutor` polls `isPlaying()` for
  completion. (Original design; rejected.)
* Push-based: `StatementExecutor` itself pulls fixed ~20ms slices from `PlaySequencer` and pushes
  each resolved slice to `speaker.playFrame(VoiceFrame, VoiceFrame, VoiceFrame)`/`stopPlay()`, with
  no duration or liveness concept on the speaker side at all.

## Decision Outcome

Chosen option: push-based `playFrame`/`stopPlay`, because the pull-based design silently broke
headless testability. A no-op `VirtualSpeaker` — every test double — never reports "playing", so
`BREAK` could never fire and `PLAY` returned instantly in tests, unlike `BEEP`/`PAUSE`. Inverting the
design keeps all DSL parsing, timing, and BREAK-handling logic inside `StatementExecutor`, mirroring
`executeBeepStmt`'s chunked-sleep/BREAK-poll shape exactly.

### Consequences

* Good, because `PLAY`/`APLAY` are headless-testable for free, exactly like `BEEP` — nothing about
  correctness depends on a real audio device reporting its own state back.
* Bad, because `TerminalScreen`'s real playback needs its own persistent background render thread
  (started lazily, decoupled from the push cadence) rather than simply blocking inside `play()`.
* Neutral: this decision is about who *initiates* each audio frame, not about buffering — the
  separate "nothing ever queues idle silence" rule in
  [`docs/spec/architecture.md`](../spec/architecture.md) ("I/O system") governs buffering and still
  holds under the push model.

<!-- Extracted from the gitignored localonly-BAZLANG-IMPROVEMENTS.md (2026-08-19 pass) during the
     doc-kit migration (see docs/tasks/adopt-doc-kit.md). -->
