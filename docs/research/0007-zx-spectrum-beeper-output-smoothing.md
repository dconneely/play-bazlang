# Why does a mathematically pure square wave not reproduce the real ZX Spectrum beeper's sound, and what do real emulators do instead?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** medium overall. Two independent sources (one contemporary hobbyist/hardware
reference, one peer-reviewed academic paper) converge on "the beeper's physical output stage
smooths/rounds a mathematically instantaneous bit-toggle," but disagree on exactly why (electrical
filtering at the EAR/MIC socket vs. mechanical inertia of the internal driver), and neither was
checked against a schematic or an oscilloscope trace of real hardware - see "Evidence conflict"
below. Separately, **high** confidence for what real emulators actually implement in their own
source: Fuse's filter constants and design comments were read directly from its current source code,
not summarised second-hand. Fuse's own listening-test methodology is corroborated by its `ChangeLog`
(medium confidence - the entry itself was read directly, but not the underlying commit diff or any
write-up of the test process). The other emulators surveyed (Spiffy, JSSpeccy 3, ZEsarUX) are
low-confidence/anecdotal by comparison - see Evidence and Dead ends. **High** confidence for what
BazLang's own code currently does: confirmed directly against `JavaSoundSpeaker.java`'s source that
`fillSquareWave` (the `BEEP` path) never calls `squareWaveAverage`, so `BEEP` has neither
anti-aliasing nor edge-rounding today, unlike `PLAY`/`APLAY`'s `voiceSample`.

## Finding

The 48K Spectrum's `BEEP`/beeper output is driven by the Z80 CPU (via the ULA) toggling a single bit
(bit 4 of I/O port `&FE`) on and off; there is no dedicated sound chip. This part is settled and
uncontested across every source below. What is _not_ captured by treating that as an ideal digital
square wave is the physical output stage between that bit-toggle and the sound a listener actually
hears: real hardware's driver cannot change state instantaneously, so the edges of the signal are
rounded/smoothed rather than sharp. Composers exploited this rounding deliberately: driving the
beeper with a rapid train of narrow pulses at varying duty cycle (pulse-width modulation) lets the
smoothed output approximate a continuous analogue voltage, which is how a device with only one
binary output bit produced convincing multi-channel music and non-square timbres (a hand-written
beeper routine published in a contemporary magazine being the most-cited example - see Evidence
below). BazLang's own `PLAY`/`APLAY` model (AY-3-8912-style multi-voice tone/noise mixing, see
`VoiceFrame`) already covers the 128K machines' dedicated 3-channel sound chip; see
[`docs/research/0008`](0008-ay-3-8912-onboard-circuit-characteristics.md) for that chip's own
on-board circuit, which turns out to raise a different concern (amplifier bias/clipping distortion
on affected board revisions) rather than the beeper's edge-rounding one. This finding is
specifically about the 48K beeper path (`BEEP`, and any future single-bit-style effect): BazLang's
current `JavaSoundSpeaker.fillSquareWave` (used by `beep()`/`playBeep`) renders a plain, naive,
point-sampled square wave, with no smoothing/inertia stage modelling the real output hardware and no
anti-aliasing either - `squareWaveAverage`'s sample-interval band-limiting is only ever called from
`voiceSample`, i.e. only for `PLAY`/`APLAY`'s AY-modelled tone channels, not for `BEEP` at all
(confirmed directly against `JavaSoundSpeaker.java`'s source, not assumed).

**No source found settles which physical mechanism is responsible** (see "Evidence conflict"
below) - but real, actively-maintained emulators do not wait on that answer, and neither needs to
BazLang. **Fuse** implements three separate post-processing filters for three distinct output
destinations, current as of its `master` branch, each read directly from source:

- **`ula_filter`** - applied to the signal at the **MIC socket**. An _asymmetric_ one-pole filter
  with separate rise/fall time constants - `ULA_FILTER_RISE_TAU` = 36.53558495933805 microseconds,
  `ULA_FILTER_FALL_TAU` = 68.86073172982108 microseconds - a shape no single symmetric RC low-pass
  can produce. Its own source comments call it "a perceptual approximation of behaviour measured at
  the MIC socket," with the constants marked "Frozen listening-test candidate parameters; do not
  retune" - i.e. **empirically fitted to the sound of real hardware recordings, not derived from a
  measured or datasheet RC value**. This is, in effect, Fuse's own answer to the "which mechanism"
  question below: its authors did not resolve it either, they fitted a filter to the _sound_ rather
  than to a claimed circuit or mechanical model.
- **`speaker_filter`** - applied only to the **internal speaker** signal, never external outputs. A
  _second-order resonant high-pass_ (not low-pass): `SPEAKER_FILTER_FREQUENCY` = 750.0 Hz,
  `SPEAKER_FILTER_Q` = 0.70710678118654752440 (1/sqrt(2), a standard Butterworth Q). Its comment
  calls it "the minimal acoustic model for the Spectrum's built-in moving-coil speaker" - implicitly
  taking a side in the moving-coil-vs-piezo conflict below, as a design assumption rather than a
  verified fact - and explicitly says the values "are not measurements of any individual Spectrum
  speaker." A small speaker's inability to reproduce bass is a high-pass characteristic, which is
  why this filter is shaped so differently from `ula_filter`: it models the driver's own frequency
  response, not the toggle's rise/fall shape at a measurement point.
- **`tv_filter`** - applied when the emulated machine has no internal beeper (128K models,
  generally, are assumed to reach a TV/consumer amplifier). A cascaded pair of first-order filters:
  `TV_FILTER_HIGH_PASS_FREQUENCY` = 100.0 Hz, `TV_FILTER_LOW_PASS_FREQUENCY` = 10000.0 Hz, justified
  as "no single physical response to model... a mild, representative 100 Hz-10 kHz response" rather
  than fidelity to one specific device.
- A fourth, `dc_filter` (plain first-order DC-blocking, no acoustic claim) runs even in Fuse's
  "Unfiltered" mode - "unfiltered" there still means "DC removed," not "the raw synthesised
  waveform."

Fuse's own `ChangeLog` (read directly) confirms this is deliberate, recent work: "Improve ZX
Spectrum ULA beeper emulation with separate MIC and speaker paths and improved speaker-response
modelling" - credited in the entry to a named project contributor, with thanks recorded to named
community testers - alongside "Default to automatically selecting the appropriate speaker response
for each emulated machine." Fuse picks a filter automatically per emulated machine model rather than
applying one universal filter everywhere, and "experiment participants" corroborates the
listening-test methodology its source comments claim.

A rough independent cross-check: treating the one concrete timing figure found elsewhere (see the
Bumbershoot Software evidence below - a ~50-microsecond full-travel time, offered by analogy to a PC
speaker) as a first-order system's 10%-90% rise time, and using rise time ≈ 2.2 x RC, gives RC ≈
22.7 microseconds and a cutoff f_c = 1/(2 x pi x RC) ≈ 7 kHz. Converting Fuse's own rise/fall taus
the same way gives ≈ 4.4 kHz and ≈ 2.3 kHz respectively - different methods (an analogy-based
physical estimate vs. ear-tuning against recordings) landing within the same rough order of
magnitude, which is mild corroboration that both are in a plausible range, though neither is an
authoritative measurement of real hardware.

Other emulators surveyed add less than Fuse. **Spiffy** applies a low-pass filter for anti-aliasing
during downsampling (its own README: "to prevent aliasing," nicknamed "the drainpipe-o-matic" for
the resulting bassy/muffled sound), with a `BW` (bandwidth) parameter and documented starting values
(38/52/76 for beeper engines, 128 for AY use). This solves a sampling-theory problem, not a
physical-modelling one, and should not be read as corroborating an edge-rounding/hardware-fidelity
filter the way Fuse's `ula_filter`/`speaker_filter` do. BazLang's own `squareWaveAverage`
sample-interval averaging already solves this same aliasing concern for `PLAY`/`APLAY`'s tone
channels (`voiceSample`), but not for `BEEP`'s own `fillSquareWave` (see Finding above) - `BEEP`
currently has neither anti-aliasing nor edge-rounding, an independent gap from the edge-rounding
question this note otherwise focuses on. **JSSpeccy 3**'s own technical-notes document, read
directly, does not discuss audio filtering, post-processing, or square-wave accuracy at all - one
data point against assuming every credible emulator necessarily implements acoustic-model filtering.
**ZEsarUX** was searched but not read in enough depth to say anything concrete about its own
approach either way.

## Evidence

- <https://forgottencomputer.com/retro/sound/> (ZX Spectrum section) - states the ULA's signal "goes
  straight to a transistor" for the internal speaker, but that the EAR/MIC socket path has
  "capacitors and resistors on the way which degrade the rectangular signal," and that composers
  treat this filtering as something to exploit rather than a defect to ignore. Also notes real
  hardware's clock rate varies by model (3.5MHz vs. 3.5469MHz), so the same BASIC program's `BEEP`
  pitch/timing shifts slightly across real Spectrum variants - a separate, second-order authenticity
  point from the filtering question this note is otherwise about.
- <https://www.gamejournal.it/the-sound-of-1-bit-technical-constraint-as-a-driver-for-musical-creativity-on-the-48k-sinclair-zx-spectrum/>
  (peer-reviewed, _G|A|M|E_ journal) - describes the 48K's audio hardware as "a motherboard-mounted
  22mm, 40 Ohm beeper speaker, which provided just a single channel of 1-bit playback," driven
  directly by the Z80/ULA. States explicitly that "a speaker cone cannot change its state discretely
  and instantaneously. When driven, it takes a short but finite time to reach maximum displacement
  and must move through all its intermediate states between fully off and fully on" - attributing
  the smoothing to the driver's own mechanical inertia, not to an electrical RC filter - and that
  composers exploited this by "modulating the width of the signals... to simulate the effect of a
  continuous analogue voltage" (pulse-width modulation).
- <http://www.breakintoprogram.co.uk/computers/zx-spectrum/sound> - confirms the beeper is
  "controlled by rapidly toggling bit 4 of port &FE," describes the driver itself as "a small
  piezoelectric buzzer" (conflicting with gamejournal.it's "speaker cone" - see Evidence conflict
  below), and documents the AY-3-8912's register map for 128K machines (already fully covered by
  BazLang's existing `VoiceFrame`/`PlaySequencer` model). Also hosts a working example of a
  reader-submitted beeper routine (originally published as a hex listing in _Your Sinclair_ #20,
  "Star Tip #2," credited by name on that page to its original composer) that plays "two or three
  channels of music" from the single beeper bit through timing alone.
- <https://bumbershootsoft.wordpress.com/2026/09/05/zx-spectrum-experimenting-with-1-bit-sound/> - a
  detailed hobbyist technical blog, not peer-reviewed but specific and closely reasoned. States that
  a raw beeper toggle is "1-bit PCM, which we should expect to sound pretty bad... the kind of
  distortion that you'd get when blowing a speaker out with too much amplification," and that "PWM
  is more analog than PCM, despite being more aggressively 1-bit" precisely because pulse-width
  variation exploits the driver's inertia. Gives the one concrete timing figure found in this
  research: "assuming the Spectrum's speaker is roughly equivalent to the PC's, it takes it 50
  microseconds... to achieve full travel" - by analogy to the PC speaker rather than measured on
  Spectrum hardware directly (see the cross-check in Finding above). Also independently confirms
  Fuse "implements one of three possible lowpass filters over the [beeper] output."
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/sound/ula_filter.c> and
  `.../sound/ula_filter.h` - read directly. Source of `ULA_FILTER_RISE_TAU`/`ULA_FILTER_FALL_TAU`,
  "a perceptual approximation of behaviour measured at the MIC socket," and "Frozen listening-test
  candidate parameters; do not retune."
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/sound/speaker_filter.c> - read
  directly. Source of `SPEAKER_FILTER_FREQUENCY`/`SPEAKER_FILTER_Q`, "the minimal acoustic model for
  the Spectrum's built-in moving-coil speaker," and "not measurements of any individual Spectrum
  speaker." (`speaker_filter.h` itself, also fetched, held only licence text and structure
  declarations - the design comments live in the `.c` file.)
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/sound/tv_filter.c> and
  `.../sound/tv_filter.h` - read directly. Source of `TV_FILTER_HIGH_PASS_FREQUENCY`/
  `TV_FILTER_LOW_PASS_FREQUENCY` and "no single physical response to model... a mild, representative
  100 Hz-10 kHz response."
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/sound/dc_filter.h> - confirms a
  first-order DC-blocking filter exists and runs even on the "unfiltered" path.
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/sound/output_mixer.c> and
  `.../sound.c` - confirm these four filters are real, wired-in code (`speaker_filter_apply`,
  `tv_filter_apply`, `ula_filter_apply`, `dc_filter_apply`), not dead/experimental code, though the
  exact routing logic (which filter feeds which final output under which configuration) was not
  traced in full.
- <https://raw.githubusercontent.com/fuse-emulator/fuse/master/ChangeLog> - read directly (`grep`
  over the full text). Source of "separate MIC and speaker paths and improved speaker-response
  modelling" and "automatically selecting the appropriate speaker response" quoted above.
- <https://manpages.ubuntu.com/manpages/trusty/man1/fuse.1.html> and
  <https://fuse-emulator.sourceforge.net/> - corroborate the existence/purpose of Fuse's
  `--speaker-type` (TV/Beeper/Unfiltered) option at the documentation level, prior to reading the
  filter source itself. States plainly that "Unfiltered" gives "unmodified (but less accurate) sound
  output" - Fuse's own documentation treats leaving the beeper unfiltered as the _less_ accurate
  choice.
- <https://github.com/ec429/spiffy/blob/master/readme> - source of the Spiffy `BW`/anti-aliasing
  filter claim discussed in Finding above.
- <https://github.com/gasman/jsspeccy3/blob/main/tech_notes.md> - read directly; confirmed to
  contain no audio-filtering discussion at all.

### Evidence conflict

breakintoprogram.co.uk calls the 48K's internal driver "a small piezoelectric buzzer," while
gamejournal.it's peer-reviewed analysis calls it "a speaker cone" (a moving-coil driver) and bases
its whole mechanical-inertia argument on that being a speaker rather than a piezo element - the two
driver types behave differently. Fuse's own `speaker_filter` implicitly sides with "moving-coil"
(see Finding), but its own comments call this a nominal design assumption, not a verified
measurement. Neither original source, nor Fuse, was checked against a Spectrum schematic or teardown
photo to settle which is correct. This note treats the _effect_ (smoothing/rounding of an
ideally-instantaneous bit-toggle) as reasonably well established across independent sources
regardless of which physical mechanism is responsible, but does not treat any source's specific
mechanism claim as settled.

## Dead ends

- <https://worldofspectrum.org/archive/magazines/your-sinclair/20#56> - the archive's index/overview
  page for _Your Sinclair_ #20 does not itself contain the referenced "Star Tip #2" article text or
  scan; it only lists the issue's contents. breakintoprogram.co.uk's own page (cited above) already
  reproduces the relevant listing "with kind permission from the man himself," which served as the
  practical substitute here.
- A biographical page about the routine's original composer, and
  <https://github.com/utz82/ZX-Spectrum-1-Bit-Routines>, were both consulted but not cited as
  primary evidence above: the biographical page adds nothing about the electrical/mechanical
  question this note investigates, and the GitHub repository's routine names (`nanobeep`,
  `octodepwm`, `wtbeep`, etc.) corroborate that PWM/multi-channel/wavetable techniques are a real,
  well-populated genre of 1-bit beeper programming, but its README documents no underlying hardware
  characteristics - it is a code collection, not a hardware reference.
- <https://vtrd.in/book/ZXSWORLD.ZIP> (a sound reference linked from forgottencomputer.com) was not
  fetched - a `.zip` download rather than a directly readable page, and the evidence already
  gathered was judged sufficient without it.
- <http://www.zxdesign.info/soundbeeper.shtml> (ZX Spectrum Reverse Engineering and Clone Design
  blog, by the author of the published Harlequin ULA-replacement project) corroborates that "this
  ULA pin is connected directly to the analogue IO circuit" with no filtering component named
  between the ULA and the speaker coil on the _original_ machine - consistent with
  forgottencomputer.com's "goes straight to a transistor" - but the rest of that page's circuit
  detail (an emitter-follower driving a TV SCART input through a 100nF DC-blocking capacitor)
  describes the author's own Harlequin clone replacement design, not the original Spectrum's stock
  circuit, and should not be cited as evidence about original hardware.
- Fuse's `sound.c` and `sound/output_mixer.c` were fetched first, expecting to find the filter
  constants directly there - they only contain the _calls_ into the separate `*_filter.c`/`.h`
  files. The actual numbers are one directory-listing hop further in (`sound/<name>_filter.{c,h}`),
  a naming pattern worth remembering directly rather than re-searching if this project's own
  implementation needs revisiting later.
- A general web search for "ZEsarUX / JSSpeccy / SpectEmu beeper filter" returned mostly project
  landing pages and README summaries with no filter-specific detail - none of the three were read at
  source-code depth here (JSSpeccy 3's tech-notes document was read directly, but confirmed to say
  nothing on this topic rather than yielding a positive finding).

## Open questions

- Which physical mechanism actually dominates the smoothing on real 48K hardware? No source read
  here settles this - even Fuse's own authors sidestepped it by fitting `ula_filter` to recordings
  rather than to a claimed circuit or mechanical model (see Finding). Settling it for real would
  need an official schematic/service manual or a measured oscilloscope trace, neither examined here.
- Is the internal 48K driver actually a moving-coil speaker or a piezoelectric buzzer? Sources
  disagree (see "Evidence conflict"); BazLang's own scope (no raw port I/O, so only the _output
  stage's_ smoothing character is relevant, not the exact driver mechanism) may make this moot.
- Do the two documented Spectrum clock rates (3.5MHz vs. 3.5469MHz) affect anything BazLang already
  renders (`BEEP` pitch/timing), or is this purely a real-hardware-variant concern with no bearing
  on an emulated/synthesised interpreter with no CPU clock to run fast or slow? Not investigated
  further, orthogonal to the filtering question this note exists to answer.
- What was Fuse's actual listening-test methodology behind `ula_filter`'s frozen constants - how
  many participants, what recordings, what comparison protocol? The `ChangeLog`'s credited thanks to
  named community testers confirms _some_ structured process existed, but no write-up of it was
  found or read. BazLang's own `BEEP` filter ([ADR-0009](../adr/0009-beep-edge-rounding-filter.md))
  used Fuse's frozen constants as its starting point rather than tuning from nothing; whether they
  need retuning by ear against BazLang's own output pipeline is still open (see that ADR's
  Consequences).
- Was Fuse's `ula_filter` genuinely new (i.e. MIC and speaker were previously one unmodelled path),
  or did the cited `ChangeLog` entry only refine an existing, less accurate filter? The entry's own
  wording ("separate MIC and speaker paths") suggests the former, but the commit diff itself was not
  read to confirm.
- ZEsarUX's own audio-filtering approach (if any) remains unexamined - flagged rather than dropped
  silently.
