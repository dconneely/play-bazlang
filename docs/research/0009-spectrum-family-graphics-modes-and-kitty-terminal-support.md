# What graphics modes do ZX Spectrum-family hardware variants add beyond the base machine's attribute clash, and which Windows-usable terminals support the Kitty graphics protocol's Unicode Placeholder addressing?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** medium overall - hardware resolution/colour-depth numbers are corroborated by at
least two independent sources for every machine below and verified directly against a primary or
near-primary technical reference for most of them (see per-machine notes and Evidence); the ZX
Spectrum Next figures rely on the community-maintained SpecNext wiki rather than the Next's own
formal Core Spec document, which was searched for but not located as a single fetchable file (see
Dead ends). Pentagon's two independent sources disagree with each other on two specific numbers
(colour count, monochrome-mode resolution - see Open questions); ATM Turbo and the Scorpion GMX
board remain single-Wikipedia-article-sourced, lower confidence than the rest of this section.
Terminal-support claims are each verified directly against the named project's own GitHub issue
tracker, pull requests, or documentation as of 2026-09-20, so are high confidence for that date, but
this is exactly the kind of fact that goes stale fastest in this whole document - re-check before
relying on it for a design decision made much later.

## Purpose

BazLang's `PLOTMODE` statement currently chooses between `PixelMode` implementations (`lib-cell`
module: `QuadrantMode`, `SextantMode`, `BrailleMode`, `HalfCellMode`) that all preserve the real
48K/128K Spectrum's "attribute clash" - full sub-cell pixel resolution, but only one ink/paper pair
per whole terminal cell - rendered with plain ANSI SGR. A prospective new mode would instead use the
Kitty Graphics Protocol's Unicode Placeholder scheme, where a cell holds a placeholder codepoint
(foreground colour encodes an image ID, combining diacritics encode row/column) that references real
bitmap pixel data, potentially breaking the attribute-clash constraint entirely. This note gathers
the two pieces of sourced background needed before any design work: what real Spectrum-family
hardware actually did about attribute clash beyond the base machine, and which terminals BazLang
might realistically run in today support the protocol this new mode would need. It does not propose
a design, and no Java code or `PLAN.md` entry accompanies it.

## Finding: ZX Spectrum-family graphics modes and attribute-clash behaviour

### Baseline: 48K/128K/+2/+3 (what everything else is compared against)

256x192 pixels, addressed as 32x24 attribute cells of 8x8 pixels each; every cell has exactly one
ink colour, one paper colour, and one bright/flash pair, chosen from the 8 base colours (16 with
bright). This is the baseline BazLang already targets and the one every entry below is measured
against. Well-established and not separately re-verified against a primary manual here.

### Chronology across this survey

Release dates matter here and are easy to get backwards, so stated plainly up front (see Evidence
for each): **Timex TC2068/TS2068 (November 1983) is the earliest machine in this entire survey**,
predating SAM Coupe by six years - but, as detailed below, neither of its alternate modes achieves
genuine per-pixel colour. SAM Coupe (December 1989, slipped from an originally planned April 1989)
and Pentagon (1989, exact month not established here, so relative order against SAM Coupe within
that year is unknown) are the earliest machines confirmed to have genuine multi-colour per-pixel
addressing, arrived at independently of each other. ATM Turbo (1991) and the Scorpion ZS-256
(also 1991) are later still - though Scorpion's own base machine has no independent graphics
capability at all; its distinct mode comes from a separate expansion board released at an unknown
later date. ZX Spectrum Next is a modern, ongoing successor project, decades after all of the above.

### Timex Sinclair TC2068/TS2068 (November 1983) - two ways of avoiding standard clash, neither achieving genuine per-pixel colour

The earliest machine in this whole survey by release date (see Chronology above and Evidence), but
its two alternate modes both fall short of genuine per-pixel colour, unlike every other machine
covered below. `0006-related-basic-dialects-overview.md` already covers this machine's keyword
additions (`SOUND`, `ON ERR`, `FREE`, `DELETE`, `RESET`, `STICK`) and namechecks "BASIC-64 for the
TC2068's extra graphics/text modes" without detail; that mention is not wrong, just under-detailed -
this note supplies the detail `0006` omits. Confirmed directly against the TS2068 Technical
Reference Manual (Corcoran and Branigin, Timex Computer Corporation, 1984/1986 second edition - see
Evidence), which documents all three modes as controlled entirely through port 0xFF (`OUT 255,n`)
and ordinary memory writes (`POKE`) - both directly available from any Sinclair-BASIC-compatible
dialect on this hardware, BASIC-64 included, with no dedicated command needed:

- **Dual Screen Mode** (port 0xFF, bit 0 set) - two complete, independent, ordinary Spectrum-format
  screens (256x192, 32x24 attribute cells of 8x8, same clash as stock Spectrum) at different memory
  addresses (`0x4000`/`0x6000`). No attribute-granularity change by itself - just a second bitmap to
  flip between.
- **High Resolution Graphics Mode** - "also called Extended Color Mode" in the manual's own words
  (port 0xFF, bit 1 set) - reuses the second screen's data area as an attribute plane for the first,
  giving 2 colours per 8x1-pixel cell: the same "finer-only-vertically" trick SAM Coupe's MODE 2
  uses below, arrived at independently via a completely different mechanism (borrowing a second
  screen's bitmap as attribute memory, rather than a wider addressable attribute RAM) - six years
  before SAM Coupe existed.
- **64 Column Mode** (port 0xFF, bits 1 and 2 both set) - 512x192 pixels, 1 bit per pixel, built by
  taking display columns alternately from the two screens' data areas (neither screen's attribute
  area is used at all). Colour is **one single ink/paper pair for the entire screen**, chosen from 8
  fixed combinations via 3 more port-0xFF bits; the manual states plainly that "the Bright and Flash
  attributes are fixed at 0", and the border colour matches the paper colour. This avoids attribute
  clash only in the trivial sense that there is no attribute grid to clash within - the whole screen
  is one colour pair, not real per-pixel colour the way SAM Coupe MODE 3/4 or Spectrum Next Layer 2
  are (below).
- **BASIC accessibility** - the manual documents all of the above as machine-code-oriented
  techniques, and a separate secondary Timex reference site's BASIC-64 instructions page only shows
  its `SCREEN$` command reaching the standard-resolution screen (`SCREEN$ 0`) and 64 Column Mode
  (`SCREEN$ 1`) as named presets - but since every mode above is just a value written to port 0xFF
  plus ordinary pixel/attribute bytes POKEd into RAM, any Sinclair-BASIC-compatible dialect on this
  hardware (BASIC-64 included) can reach all three directly via `OUT`/`POKE`, with or without a
  dedicated command wrapping them.

### SAM Coupe (December 1989, MGT) - genuine multi-colour, per-pixel addressing via a CLUT

Confirmed directly against the SAM Coupe Technical Manual (version 3.0, 1990) and its release date
(see Evidence for both). Four screen MODEs, selected in BASIC via `MODE n`:

- **MODE 1** - 256x192 pixels as 32x24 cells of 8x8 pixels (`Spectrum-attribute compatible`, the
  manual's own words); each cell has 2 colours chosen from a 16-colour subset of a 128-colour
  palette. Same clash granularity as stock Spectrum, wider per-cell colour choice.
- **MODE 2** - 256x192 pixels, but attribute cells are only 8x1 pixels (32x192 cells): each single
  pixel row gets its own 2-colour attribute pair. Horizontal clash within a row is unchanged (still
  only 2 colours across any 8-pixel-wide run in a given row), but the vertical smearing that MODE 1/
  stock Spectrum have across 8 pixel rows at once is eliminated.
- **MODE 3** - 512x192 pixels, 2 bits per pixel addressing a colour lookup table (CLUT) directly -
  genuine per-pixel colour, no attribute cell at all. Only 4 of the CLUT's 16 entries are reachable
  without extra tricks (2 extra address bits come from the High Memory Page Register, HMPR, bits
  5-6); the CLUT itself is drawn from the 128-colour hardware palette and can be redefined at each
  scanline via the LINE INTERRUPT system, so a whole frame can use more than 4 colours in total even
  though any single instant is limited to 4.
- **MODE 4** - 256x192 pixels, 4 bits per pixel addressing the same CLUT directly - 16 simultaneous
  colours, again genuine per-pixel colour with no attribute cell, same per-scanline palette
  redefinition available as MODE 3.

The manual's own ATTRIBUTES read port description corroborates this split precisely: it "enables the
programmer to read the attributes of the currently displayed character cell in modes 1 and 2, **and
the third byte in every four displayed in modes 3 and 4**" - i.e. modes 3/4 have no separate
attribute byte at all; what gets read back is literal pixel data, because the colour information
_is_ the pixel data. MODE 3/4 are a real, decades-early precedent for what ZX Spectrum Next's Layer
2 does below - true per-pixel colour addressing, breaking attribute clash entirely - just with a far
smaller simultaneous palette (4 or 16 colours vs Layer 2's 256) unless the programmer also drives
the line-interrupt system to swap the CLUT scanline by scanline.

### Further Spectrum-compatible clones with a genuinely different graphics capability

Two Soviet-era clone families, both later than SAM Coupe (see Chronology above), add real hardware
or firmware tricks beyond stock Spectrum graphics, confirmed via their own Wikipedia articles
(treated here as adequate corroboration, not a primary technical manual - see Evidence and Open
questions for the caveat this implies):

- **Pentagon (1989)** - two independent Wikipedia articles (`Pentagon (computer)` and
  `ZX Spectrum graphic modes` - see Evidence) agree Pentagon has a genuine attribute-free colour
  mode at its base 256x192 resolution: per-pixel colour rather than 8x8 blocks, eliminating clash at
  that resolution specifically - a different route to the same "no clash" property SAM Coupe MODE 4
  and Spectrum Next Layer 2 reach, on hardware otherwise built to be Spectrum-compatible rather than
  a fresh design. The two articles disagree on its exact colour count - "256x192x15" versus a
  separately-named "16c mode" described as "each pixel can have one of 16 colours" - and on its
  monochrome high-resolution mode's dimensions (512x192 versus 512x384); both discrepancies are
  unresolved without a primary source (see Open questions). Pentagon also supports the same
  8x1-pixel attribute mode Timex's High Resolution Graphics Mode above independently arrived at, per
  `ZX Spectrum graphic modes` - a third mode alongside the attribute-free and monochrome ones, not a
  replacement for either. A separate 384x304 mode supports 16 colours per that same source. Pentagon
  is also dated 1989 like SAM Coupe; no source found here establishes which of the two actually
  shipped first within that year.
- **ATM Turbo (1991)** - three modes: stock 256x192 (unchanged 8x8-block clash); 640x200 with 2 of
  16 colours per **8x1** cell (the same vertical-only fix as SAM Coupe MODE 2/Timex hi-colour mode,
  a third independent arrival at that same idea); and a 320x200 "chunky" mode offering 16 colours
  **per pixel** (explicitly noted as chunky rather than planar, unlike EGA) - again genuine
  per-pixel colour, no clash at all. All ATM Turbo modes draw from a 64-colour (6-bit RGB) hardware
  palette.
- **Scorpion ZS-256 (1991)** - the base machine is plain Spectrum-compatible (256x192, unchanged
  clash), not independently different from Pentagon. Its own distinct capability arrives later, via
  a separate "GMX" (Graphic Memory eXpander) expansion board (exact release date not established
  here): a 640x200, 16-colour mode plus an 80x25 text mode. Two independent sources agree GMX exists
  and give the same resolution/colour numbers, but neither states whether GMX's 16 colours are
  addressed per pixel (like SAM Coupe MODE 3/4) or via a finer attribute grid (like SAM Coupe MODE
  2/ATM Turbo's 640x200 mode) - see Open questions.

None of these three were confirmed against a primary hardware manual or register reference the way
SAM Coupe and Timex were. Pentagon has two independent Wikipedia articles in agreement on its core
claims (stronger than single-source, but still not primary); ATM Turbo and the Scorpion GMX board
remain sourced from a single Wikipedia article plus one further hobbyist reference site each.
Flagged accordingly in Open questions rather than stated at the same confidence as the rest of this
section.

### ZX Spectrum Next (SpecNext, ongoing) - genuine per-pixel colour via a dedicated display layer

Confirmed against the community-maintained SpecNext wiki's own Layer 2 page, corroborated by a
second independent summary agreeing on the same numbers (see Evidence); the Next's own formal "Core
Spec" document was searched for but not found as a single fetchable primary source - see Dead ends.
Layer 2 is an additional display layer, independent of the ULA-compatible screen, offering three
selectable resolutions:

- **256x192** and **320x256**, both at 8 bits per pixel: **256 simultaneous colours, addressed per
  pixel** into a (typically RGB332-format) palette - the wiki's own description is "a pixel mapped
  display without colour clash" and "every pixel is individually coloured". 256x192 needs 48KiB of
  Layer 2 memory; 320x256 needs 80KiB.
- **640x256** at 4 bits per pixel: 16 simultaneous colours, same per-pixel addressing, same 80KiB.

Layer 2 genuinely has no attribute clash at any of its three resolutions, because colour is stored
per pixel rather than per block, the same principle SAM Coupe's MODE 3/4 already used - Layer 2
simply has a much larger simultaneous palette (256 colours at its two finer resolutions) than the
SAM Coupe ever offered without scanline tricks. Layer 2 can be composited above, below, or in place
of the ULA-compatible/Tilemap layers, so a Next-targeting program is not forced to choose only one
graphics model.

### Summary: how each mode breaks (or keeps) attribute clash

| Machine / mode                     | Released   | Resolution    | Colours                     | Clash cell (blank = none - per-pixel) |
| ---------------------------------- | ---------- | ------------- | --------------------------- | ------------------------------------- |
| Stock 48K/128K/+2/+3               | 1982-1987  | 256x192       | 2/cell of 16                | 8x8                                   |
| Timex hi-colour                    | Nov 1983   | 256x192       | 2/cell of 8                 | 8x1                                   |
| Timex hi-res                       | Nov 1983   | 512x192       | 2 (whole screen)            | whole screen (uniform, not per-cell)  |
| SAM Coupe MODE 1                   | Dec 1989   | 256x192       | 2/cell of 16                | 8x8                                   |
| SAM Coupe MODE 2                   | Dec 1989   | 256x192       | 2/cell of 16                | 8x1                                   |
| SAM Coupe MODE 3                   | Dec 1989   | 512x192       | 4 (of 128 total)            | (per pixel)                           |
| SAM Coupe MODE 4                   | Dec 1989   | 256x192       | 16 (of 128 total)           | (per pixel)                           |
| Pentagon attribute-free ("16c")    | 1989       | 256x192       | 15 or 16 (sources disagree) | (per pixel)                           |
| Pentagon 8x1 attribute mode        | 1989       | 256x192       | 2/cell of 16                | 8x1                                   |
| Pentagon 384x304                   | 1989       | 384x304       | 16                          | 8x8 (unconfirmed exact grid)          |
| ATM Turbo 640x200                  | 1991       | 640x200       | 2/cell of 16                | 8x1                                   |
| ATM Turbo 320x200 chunky           | 1991       | 320x200       | 16                          | (per pixel)                           |
| Scorpion GMX 640x200               | after 1991 | 640x200       | 16                          | unconfirmed - see Open questions      |
| SpecNext Layer 2 (256x192/320x256) | ongoing    | up to 320x256 | 256                         | (per pixel)                           |
| SpecNext Layer 2 (640x256)         | ongoing    | 640x256       | 16                          | (per pixel)                           |

## Finding: Kitty graphics protocol / Unicode Placeholder support among Windows-usable terminals

Each entry below is checked directly against that project's own repository (issue tracker, merged
pull requests, or shipped documentation) rather than the Kitty protocol's own implementations list,
since that list names implementers but does not itself confirm current status, and this is a
fast-moving area.

- **Windows Terminal (`microsoft/terminal`)** - **not implemented, but under active exploration as
  of August 2026**. Two tracking issues exist: #8389 (opened 2020, still open, labelled
  `Priority-3`/`Backlog`) and #17309 (opened 2024, closed as a duplicate of #8389). A Windows
  Terminal maintainer (`lhecker`) commented on #8389 on 2026-08-01: "As we're experimenting with
  implementing support for this, I have to at least once, somewhere, express my dissatisfaction with
  this protocol" - confirming active, if reluctant, exploratory work rather than either a shipped
  feature or a declined one - followed by a detailed, specifically-named complaint about the Unicode
  Placeholder mechanism itself ("_virtual_ image placements using Private-Use Area Unicode
  characters... the image ID is specified using the RGB foreground colour of the Unicode placeholder
  character (???)... the row and column are specified using diacritic combining marks (?????)"). A
  second maintainer (`DHowett`) pushed back on 2026-08-31 against prioritising the work ("No.") in
  response to a request tied to Neovim 0.13's new image API. Windows Terminal does already support
  Sixel (shipped in the 1.22 release, per Microsoft's own devblog) - Kitty graphics is a separate,
  still-unimplemented protocol, not a superset of what already works.
- **WezTerm** - **base Kitty graphics protocol shipped; Unicode Placeholder support specifically is
  not yet merged**. Its own tracking issue (#986) lists transmission parsing, placement, and
  animation as complete (checked), enabled in end-user config via `enable_kitty_graphics = true`,
  but explicitly lists placeholder support as an open checklist item, pointing at pull request #7924
  ("feat(term): support kitty graphics protocol unicode placeholders"). That PR, opened 2026-07-11,
  is still open and unmerged as of this note (last updated 2026-09-15) - so a WezTerm user today
  gets ordinary Kitty image placement, not the Unicode Placeholder addressing scheme this project's
  prospective `PLOTMODE` design depends on. The same tracking issue also lists Windows-specific
  shared-memory transmission as a separate open item, though that is only one of several
  transmission media the protocol offers (file/temp-file transmission is not gated on it).
- **mintty** (relevant here because it is Git Bash/Cygwin/MSYS2's terminal, and this project's own
  agent environment runs Git Bash) - supports Sixel (since 2.6.0) and the iTerm2 inline-image
  protocol, but **does not support the Kitty graphics protocol** in any form, placeholder or
  otherwise.
- **Alacritty** - supports **neither Sixel nor the Kitty graphics protocol** in upstream releases;
  third-party forks/patches implementing both exist but are not part of the mainline project.
- **Ghostty** (Mitchell Hashimoto) - implements the Kitty graphics protocol including its Unicode
  Placeholder extension, one of the most complete implementations besides Kitty itself, but **has no
  official Windows build**; Windows support is explicitly planned for after the project's 1.0
  release, with only unofficial community-built Windows ports (not affiliated with or endorsed by
  the upstream project) available as of 2026. Not currently usable on Windows in any form BazLang
  could rely on.
- **ConEmu / Cmder** - ConEmu supports Sixel; no evidence found of Kitty graphics protocol support
  in either ConEmu or Cmder (Cmder itself is a distribution around ConEmu, not a separate rendering
  engine).
- **ConPTY passthrough** - no ConPTY-specific nuance was found that would block a
  Kitty-graphics-aware terminal from receiving these escape sequences intact when hosting a process
  through the pseudo console; the blocking factor across every terminal surveyed here is the
  terminal's own renderer not implementing the protocol (or its Unicode Placeholder extension) at
  all, not ConPTY altering or filtering the sequences in transit.

### Summary: Windows-usable terminals and Kitty graphics protocol status (2026-09-20)

| Terminal         | Base Kitty graphics | Unicode Placeholder | Notes                                   |
| ---------------- | ------------------- | ------------------- | --------------------------------------- |
| Windows Terminal | No (exploratory)    | No                  | Sixel already shipped; Kitty still open |
| WezTerm          | Yes                 | No (open PR #7924)  | `enable_kitty_graphics = true`          |
| mintty           | No                  | No                  | Has Sixel and iTerm2 instead            |
| Alacritty        | No                  | No                  | No Sixel either, upstream               |
| Ghostty          | Yes (upstream)      | Yes (upstream)      | No official Windows build yet           |
| ConEmu / Cmder   | No                  | No                  | Has Sixel (ConEmu)                      |

**None of the terminals with an official, current Windows build support the Unicode Placeholder
addressing scheme today.** WezTerm is the closest - it already has the base protocol and an open,
unmerged pull request for exactly the missing piece - which makes it the most plausible target to
re-check periodically for this project's purposes, rather than Windows Terminal itself (still at the
"maintainer is grudgingly experimenting" stage) or Ghostty (protocol-complete but Windows-absent).

## Evidence

- SAM Coupe Technical Manual, version 3.0 (1990), Miles Gordon Technology plc - fetched via
  <https://raw.githubusercontent.com/stefandrissen/sam-coupe-technical-manual/main/techmanual.md>, a
  plain-text transcription of the same PDF `0006`'s source table already cites
  (<https://sam.speccy.cz/systech/sam-coupe_tech-man_v3-0.pdf>) hosted for easier text search.
  Source for every MODE 1-4 resolution/colour/CLUT claim above, including the ATTRIBUTES-port
  wording quoted directly from the manual's own Read Ports section.
- _TS2068 Technical Reference Manual_ (V. C. Corcoran and M. H. Branigin, Timex Computer
  Corporation, Waterbury CT, May 1984; second edition printing, Time Designs Magazine Co.,
  January 1986) - fetched via
  <https://archive.org/download/TimexSinclair2068Manuals/Timex%20Sinclair%202068%20Technical%20Manual%20%28best%29_djvu.txt>,
  the full-text sibling of the scan hosted at <https://archive.org/details/TimexSinclair2068Manuals>
  (see also the archive item's own `_djvu.txt` naming quirk: the file on disk is titled "best",
  distinct from a separate, shorter "original" edition scan in the same item). Primary source for
  all three port-0xFF video modes above (section 5.2.1-5.2.3 of the manual), their exact names
  ("Dual Screen Mode", "High Resolution Graphics Mode"/"Extended Color Mode", "64 Column Mode"), the
  "Bright and Flash attributes are fixed at 0" wording quoted directly, and the OUT/POKE-based BASIC
  accessibility finding.
- <https://github.com/z88dk/z88dk/issues/1069> ("(classic) TS2068 mode switching") - a z88dk
  maintainer's independent technical description of the same port 0xFF bits, corroborating the
  Technical Reference Manual above.
- <https://loadzx.com/timexcomputerworld/tc2068/> and
  <https://loadzx.com/timexcomputerworld/sw-timex-basic-64/> - a dedicated Timex/Sinclair reference
  site; corroborates the TC2068's mode list and confirms BASIC-64's `SCREEN$` command exposes named
  presets for the standard-resolution screen and 64 Column Mode specifically, not a general port/
  POKE mechanism - consistent with, not contradicted by, the Technical Reference Manual's own
  OUT/POKE-based description.
- <https://wiki.specnext.dev/Layer_2> and <https://wiki.specnext.dev/Specifications> - the
  community-maintained SpecNext wiki's own Layer 2 page, source for all three Layer 2 resolutions,
  their bit depths, and the "no colour clash"/per-pixel-colour framing quoted above.
- <https://en.wikipedia.org/wiki/ZX_Spectrum_Next> - independent secondary corroboration of the
  Layer 2 resolution/colour-depth numbers (256x192/320x256/640x256, up to 256 simultaneous colours).
- <https://en.wikipedia.org/wiki/Pentagon_(computer)> and
  <https://en.wikipedia.org/wiki/ATM_(computer)> - source for the Pentagon "256x192x15
  attribute-free" mode, its 512x192 monochrome and 384x304 modes, and the three ATM Turbo modes
  (including the 8x1-cell 640x200 mode and the per-pixel 320x200 chunky mode) described above.
- <https://en.wikipedia.org/wiki/ZX_Spectrum_graphic_modes> - independent second Wikipedia source
  for Pentagon's attribute-free mode (named "16c mode" here, "each pixel can have one of 16 colours"
  quoted directly), confirms Pentagon also supports an 8x1-pixel hardware attribute mode (shared
  with Timex, eLeMeNt ZX, MB03+ Ultimate, and ZX Spectrum Next), gives 384x304 a colour count (16)
  the Pentagon article alone did not, and states a 512x384x2 monochrome mode - a resolution
  disagreement with the Pentagon article's own 512x192 monochrome mode, unresolved without a primary
  source (see Open questions).
- <https://en.wikipedia.org/wiki/Scorpion_ZS-256> and
  <http://www.interface1.net/zx/clones/scorpion.html>
  - two independent sources agreeing the Scorpion ZS-256's base machine is plain Spectrum-compatible
    and its GMX expansion board adds a 640x200, 16-colour mode plus an 80x25 text mode; source for
    the 1991 release year and the "can emulate Pentagon 128" framing above. Neither source documents
    GMX's attribute mechanism (per-pixel vs a finer grid).
- <https://github.com/microsoft/terminal/issues/8389> - Windows Terminal's own Kitty-graphics
  feature request; source for the `lhecker` 2026-08-01 "experimenting with implementing support"
  comment and the `DHowett` 2026-08-31 "No." response, fetched via
  `gh api repos/microsoft/terminal/issues/8389/comments`.
- <https://github.com/microsoft/terminal/issues/17309> - confirmed closed as a duplicate of #8389,
  via `gh api repos/microsoft/terminal/issues/17309`.
- <https://github.com/microsoft/terminal/issues/20588> - confirms Windows Terminal already supports
  Sixel (the issue is a bug report against that existing support, not a feature request for it),
  consistent with Microsoft's own devblog announcement of Sixel support shipping in the 1.22
  release.
- <https://github.com/wezterm/wezterm/issues/986> ("[Tracking Issue] Kitty Image Protocol
  Support") - fetched via `gh api repos/wezterm/wezterm/issues/986`; source for WezTerm's checklist
  of implemented-vs-open Kitty graphics protocol features, including the still-unchecked placeholder
  item and its link to PR #7924.
- <https://github.com/wezterm/wezterm/pull/7924> - fetched via
  `gh api repos/wezterm/wezterm/pulls/7924`; confirmed `state: open`, `merged: false`,
  `updated_at: 2026-09-15`, i.e. still unmerged as of this note.
- <https://wezterm.org/features.html> - WezTerm's own features page, listing Kitty graphics support
  as a shipped feature (consistent with the tracking issue's checked items).
- <https://ghostty.org/docs/about> and <https://x.com/mitchellh/status/1818696111999299976> -
  Ghostty is created by Mitchell Hashimoto (co-founder of HashiCorp).
  <https://x.com/mitchellh/status/2041253090205249584> - Hashimoto's own statement that Ghostty
  supports the Kitty graphics protocol's Unicode Placeholder extension specifically, describing it
  as one of the most complete implementations besides Kitty itself.
- Search-engine results only (not independently re-fetched against a primary source) for mintty's
  Sixel/iTerm2/no-Kitty status, Alacritty's lack of both Sixel and Kitty support, Ghostty's
  Windows-support timeline, and ConEmu/Cmder's Sixel-only status - each cross-checked against at
  least one other independent hit before being reported as a finding above, per this project's usual
  standard for search-only corroboration.

## Dead ends

- **No single fetchable "ZX Spectrum Next Core Spec" document is available** - only the TBBlue I/O
  port system page at specnext.com, the SpecNext wiki, and a separately-published "ZX Spectrum Next
  Assembler Developer Guide" PDF (a third-party developer guide, not the register-level Core Spec
  itself). The wiki is the best available community-maintained register/mode reference in its
  absence (see Confidence above). If a future note needs register-level precision (exact NextReg
  addresses, palette-format bit layout) rather than just resolution/colour-depth figures, that Core
  Spec document is worth locating properly rather than assuming it doesn't exist.
- **Windows Terminal's own Sixel support briefly looked like it might not exist yet** - one search
  result (from an unrelated blog summarising an older discussion) claimed "Windows Terminal Preview
  does not yet support the sixel format natively." This is stale: Microsoft's own devblog and a
  currently-open bug report against Sixel rendering (issue #20588, which presupposes working Sixel
  to file a bug against) both confirm Sixel shipped in the 1.22 release. Treat any single search
  result about a fast-moving terminal feature as provisional until cross-checked against the
  project's own tracker.
- **Two Russian-hosted retrocomputing sources are unreachable**: zxpress.ru (an English-language
  article on ATM Turbo/Pentagon/Profi history, plus a Spectrofon ezine article on port 0xFF
  implementation for Profi/Pentagon compatibility) and zx-pk.ru (a forum thread on ZX clone paging
  ports). Both sites reset the connection on every request regardless of User-Agent, headers, or
  retrieval method - behaviour consistent with actively blocking automated clients outright, not a
  client-specific or single-network-path issue (a control fetch of Wikipedia, Google, and a
  different `.ru` domain succeeded normally over the same path, ruling out a broader connectivity or
  `.ru`-wide block). The Wayback Machine is not a usable fallback either: `archive.org`/
  `web.archive.org` returned 429 Too Many Requests on every attempt, including its CDX API. The
  Pentagon/ATM Turbo entries above remain without a primary hardware manual or register reference as
  a result, though a second independent Wikipedia article (see Evidence) improved Pentagon's
  sourcing specifically.

## Open questions

- Are the Pentagon and ATM Turbo entries accurate in full technical detail? Neither is sourced from
  a primary hardware manual or register reference the way SAM Coupe and the TC2068/TS2068 are.
  Pentagon has two independent Wikipedia articles in agreement on its core attribute-free claim, but
  those two articles disagree with each other on its colour count (15 vs 16) and its monochrome
  mode's resolution (512x192 vs 512x384) - open questions a primary source would settle. ATM Turbo
  remains single-Wikipedia-article-sourced. Two Russian-hosted sources that looked likely to help
  (zxpress.ru, zx-pk.ru) are unreachable by every method tried (see Dead ends); a primary
  Pentagon/ATM Turbo technical reference, if one exists in a fetchable form elsewhere, would settle
  all of this.
- Does the Scorpion ZS-256's GMX expansion board's 640x200, 16-colour mode address colour per pixel
  (like SAM Coupe MODE 3/4) or via a finer attribute grid (like SAM Coupe MODE 2/ATM Turbo's 640x200
  mode)? Both sources confirming GMX's existence and resolution/colour numbers are silent on this
  specific point.
- When should this note's terminal-support section be re-checked? Windows Terminal's own maintainer
  comments describe active, ongoing exploratory work, and WezTerm's Unicode Placeholder PR is open
  and recently updated - both could change status well before any BazLang design work based on this
  note actually starts. Re-fetch the two tracking issues and the WezTerm PR/issue directly (URLs
  above) rather than trusting this note's snapshot once meaningful time has passed.
