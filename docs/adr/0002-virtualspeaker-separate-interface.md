---
status: "accepted"
date: 2026-08-17
decision-makers: David Conneely
---

# 2. Keep `VirtualSpeaker` separate from `VirtualScreen`

## Context and Problem Statement

Adding `BEEP` (and later `PLAY`/`APLAY`) needed a device abstraction so headless screens
(`StreamScreen`, `MockScreen`) get silent audio for free, the same way graphics methods already have
no-op defaults. `VirtualScreen` already defines around 30 methods for output, graphics and
attributes; audio needed to land somewhere.

## Considered Options

- Add `beep`/`playFrame`/`stopBeep`/`stopPlay` methods directly onto `VirtualScreen`.
- Define `VirtualSpeaker` as its own interface, implemented alongside `VirtualScreen` by screens
  that support audio.

## Decision Outcome

Chosen option: a separate `VirtualSpeaker` interface, because audio is not a screen concern at all -
folding it into `VirtualScreen` would entangle two things that vary independently, on an interface
that is already large. Every `VirtualSpeaker` method defaults to a no-op, so every implementation
except `TerminalScreen` gets silent `BEEP`/`PLAY`/`APLAY` for free, the same way `setFastMode`
already works.

### Consequences

- Good, because screens and audio evolve independently: a new headless or test screen needs zero
  audio-specific code, and `VirtualScreen` does not grow further.
- Bad, because two interfaces have to be threaded through constructors (e.g. `StatementExecutor`'s
  ~21 call sites) instead of one.
- Neutral: matches the no-op-default pattern `VirtualScreen`'s own graphics/attribute methods
  already use.

<!-- Extracted from docs/implementation.md, I/O system section ("VirtualSpeaker is deliberately its
     own interface...") during the doc-kit migration (see docs/tasks/adopt-doc-kit.md); a pointer
     was left there. -->
