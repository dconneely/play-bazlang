---
status: "accepted"
date: 2026-09-19
decision-makers: David Conneely
---

# 9. Band-limit and edge-round `BEEP`'s square wave

## Context and Problem Statement

`JavaSoundSpeaker.fillSquareWave` (the `BEEP` path) rendered a plain, naive, point-sampled square
wave - it never called `squareWaveAverage`, the sample-interval band-limiting technique `PLAY`/
`APLAY`'s `voiceSample` already used, so `BEEP` had neither anti-aliasing nor any modelling of real
hardware's inability to switch instantaneously.
[`docs/research/0007`](../research/0007-zx-spectrum-beeper-output-smoothing.md) found that real 48K
Spectrum hardware's physical output stage rounds the edges of what the ULA drives as an
instantaneous bit-toggle - independent sources agree this rounding is real and is what composers (a
hand-written beeper routine published in a contemporary magazine being the most-cited example)
exploited via pulse-width modulation - but no source settles _which_ physical mechanism is
responsible (electrical filtering vs. the driver's own mechanical inertia, and even conflicting
claims about whether the driver is a moving-coil speaker or a piezoelectric buzzer).

That research also found this does not need settling to act on it: **Fuse**, a long-established,
actively-maintained open-source Spectrum emulator, implements a `ula_filter` - read directly from
its current source - that sidesteps the mechanism question entirely. It is an _asymmetric_ one-pole
filter (separate rise/fall time constants, `ULA_FILTER_RISE_TAU` = 36.53558495933805 microseconds,
`ULA_FILTER_FALL_TAU` = 68.86073172982108 microseconds - a shape no single symmetric RC low-pass can
produce), documented in its own source as "a perceptual approximation of behaviour measured at the
MIC socket," with the constants marked "Frozen listening-test candidate parameters; do not retune."
Fuse's own `ChangeLog` confirms this was fitted via a real listening-test process, not derived from
a claimed circuit value. Fuse separately applies a different filter (`speaker_filter`, a 750 Hz
second-order high-pass) to its emulated internal speaker signal specifically, modelling a small
speaker's own bass rolloff rather than edge-rounding - a different concern from the one this record
is about. BazLang has one virtual speaker/output path, not a MIC-socket-vs-internal-speaker
distinction, so only the edge-rounding concern (`ula_filter`'s role) is in scope here.

This is scoped to `BEEP` only. `PLAY`/`APLAY`'s AY-3-8912-style tone/noise mixing (`VoiceFrame`,
`JavaSoundSpeaker.fillPlayMix`) models the 128K machines' dedicated sound chip.
[`docs/research/0008`](../research/0008-ay-3-8912-onboard-circuit-characteristics.md) looked at that
chip's own on-board circuit specifically and found a different, unrelated concern: the 128K/+2A/+3
board's own amplifier stage has a well-documented bias/clipping bug on certain PCB revisions (fixed
in a later revision via resistor changes), not a deliberate frequency-shaping filter analogous to
the beeper's edge-rounding - so that research gives no reason to add an equivalent filter to `PLAY`/
`APLAY`'s output, and this record does not revisit `fillPlayMix`.

## Considered Options

- Leave `BEEP` as it was: a plain point-sampled square wave, with neither anti-aliasing nor
  edge-rounding. Simplest, but measurably harsher/more sterile than anything real 48K hardware ever
  produced, and out of step with Fuse's own documented judgement that leaving the beeper unfiltered
  is "less accurate."
- Add only the sample-interval band-limiting `PLAY`/`APLAY` already has (reusing `squareWaveAverage`
  for `BEEP` too), without an edge-rounding filter. Fixes the aliasing gap but leaves the
  instantaneous-edge problem `docs/research/0007` is centrally about untouched.
- A simple symmetric one-pole low-pass on top of band-limiting, with a cutoff picked from scratch.
  Workable, but re-derives (from a much thinner evidence base) a shape Fuse's own authors already
  tried and moved past - `docs/research/0007`'s independent order-of-magnitude cross-check (≈7 kHz,
  from a PC-speaker-analogy timing figure) is at least broadly consistent with Fuse's asymmetric
  filter's rise/fall corner frequencies (≈4.4 kHz / ≈2.3 kHz), suggesting a plain symmetric filter
  would land in a plausible range but without the rise-vs-fall asymmetry real listening tests
  apparently found necessary.
- **Chosen:** band-limit via `squareWaveAverage` (matching `PLAY`/`APLAY`) _and_ add an asymmetric
  one-pole filter matching Fuse's `ula_filter` shape, seeded with its own frozen rise/fall time
  constants.

## Decision Outcome

Chosen option: band-limiting plus the asymmetric one-pole filter, implemented in
`JavaSoundSpeaker.fillSquareWave`/`applyBeepEdgeFilter`. Band-limiting reuses the existing,
already-tested `squareWaveAverage` rather than re-solving a problem `PLAY`/`APLAY` had already
solved. The edge-rounding filter reuses Fuse's own published rise/fall time constants as a starting
point rather than deriving new ones from scratch, because it is the best-evidenced choice found - a
real, shipped implementation from an actively-maintained emulator, arrived at via an actual
listening-test process against real-hardware recordings. Filter state resets to silence
(`filterState = 0.0`) at the start of every `beep()` call rather than carrying over between separate
`BEEP` statements, matching how the existing sample-phase counter (`samplesWritten`) already resets
per call.

Not settled by this decision:

- Whether Fuse's constants transfer as-is or need retuning once heard against BazLang's own output
  pipeline (`BEEP_AMPLITUDE`, `LINE_BUFFER_BYTES`) - this cannot be closed by automated testing
  alone (see Consequences) and is left for by-ear comparison.
- Whether `PLAY`/`APLAY`'s AY-modelled tone channels need any equivalent treatment -
  `docs/research/0008` found no reason they would, and this change does not touch `fillPlayMix`.
- Whether the DSL ever gains a raw single-bit output primitive (e.g. an `OUT`-style port write) that
  would let a BazLang program attempt PWM tricks directly - that would reopen the _pulse-timing_
  question `docs/research/0007` also touches on, not just the _output-smoothing_ question this
  record settles.

### Consequences

- Good, because `BEEP`'s output moves closer to how real 48K hardware actually sounds, using a
  filter shape and starting parameters with real precedent rather than an unfounded guess, and gains
  the same anti-aliasing `PLAY`/`APLAY` already had.
- Good, because the change is confined to `JavaSoundSpeaker`'s internals - `VirtualSpeaker`'s
  interface contract (`beep`/`stopBeep`) is unaffected, so headless implementations and existing
  tests are untouched.
- Good, because the new math (`applyBeepEdgeFilter`) is a small, pure, package-visible function unit
  tested the same way `squareWaveAverage`/`frequencyForPitch` already are (`JavaSoundSpeakerTest`) -
  convergence, stability, and the rise-faster-than-fall asymmetry are all directly assertable
  without an audio device.
- Bad, because even Fuse's own constants are "nominal," not a verified real-hardware measurement -
  whether they sound right for BazLang's own output pipeline needs a subjective by-ear comparison
  against reference recordings, which no automated test can perform; only the filter's mathematical
  behaviour (convergence, asymmetry) is assertable, not whether it actually sounds more authentic.
- Neutral: this decision is deliberately silent on `PLAY`/`APLAY`'s tone channels, which keep their
  current sample-averaged-only rendering, and on the AY/128K amplifier bug `docs/research/0008`
  documents, which this record treats as a hardware defect rather than something to reproduce.
