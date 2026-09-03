# What are the Sinclair-heritage and Sinclair-adjacent BASIC dialects this project keeps comparing against, and how do they relate to each other?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high - genealogy and "does it extend X" answers are sourced from primary manual text
for nearly every dialect below; exact syntax for any given feature lives in the topic-specific
research notes this document exists to be linked from, not here.

## Purpose

`0002`-`0005` each independently introduced and re-described the same handful of dialects every time
they came up. This note exists so they (and any future topic-based research note comparing BazLang's
design against Sinclair-heritage BASICs) can link here instead of repeating the genealogy - who's
actually descended from what, who merely shares a platform, and which "dialects" in a
plausible-looking list turn out to be tooling rather than a language at all. Each entry below says
what's confirmed and points at the topic note that has the syntax detail, rather than repeating it.

It also exists to make the _next_ feature-across-dialects research note (this one covered
`DEF PROC`, `DEF FN`, `WHILE`/`REPEAT`, `IF`/`ELSE` - the next might be `FOR`/`NEXT` variations,
arrays, error handling, `PRINT`/output formatting, or anything else) faster to start: "Primary
sources by dialect" below is a working URL and a fetch method per dialect, not just a description,
and "Fetch technique notes" records reusable gotchas so they don't have to be rediscovered.

## Finding

### The core lineage BazLang is directly based on

- **ZX80 BASIC / ZX81 BASIC / ZX Spectrum BASIC** (48K, 128K, +2, +3) - one evolving family, not
  several unrelated dialects. ZX81 BASIC (integer-focused, no floating point without an add-on) is
  Sinclair's own direct ancestor of Spectrum 48K BASIC; the 128K/+2/+3 ROMs add editor and menu
  features but keep the same core language - none of them ever gained `DEF PROC`, `WHILE`, or a
  multi-line/`ELSE`-bearing `IF` natively. This is the baseline every other entry below is compared
  against, and the one BazLang itself models.

### Direct structured-programming extensions of Spectrum BASIC

These all start from the ROM above and bolt on procedures and/or block control flow, without
changing the underlying tokenizer/interpreter model:

- **Beta BASIC** (1983, Dr Andrew Wright/BetaSoft) - the earliest and most thorough of these, and
  the one every other Sinclair-heritage extension in this list either directly credits (SAM Coupé)
  or echoes. Its 3.0 manual confirms `DEF PROC name param1 param2` (**no parentheses** around the
  parameter list - a real outlier), `END PROC` (two words), `REF` for reference parameters, a
  standalone `DEFAULT name=expr` statement, and the _original_ `diamant` recursion example SAM
  Coupé's manual credits it for. Two genuine surprises: **no evidence `DEF FN` was ever extended**
  (absent from the manual's own keyword index entirely, unlike every other BB03 addition), and **no
  real block `IF` at all** - what reads as "multiline `IF THEN ELSE`" in secondary sources is a
  single colon-joined program line that Beta BASIC's own `LIST FORMAT 2` editor mode displays
  indented across several screen lines. Full detail: `0002-def-proc-across-sinclair-basics.md`,
  `0003-multiline-def-fn-across-sinclair-basics.md`,
  `0004-while-repeat-loops-across-sinclair-basics.md`,
  `0005-if-then-else-across-sinclair-basics.md`.
- **SAM Coupé BASIC** (1989, MGT, for the SAM Coupé - a Spectrum-compatible successor machine, not
  Spectrum hardware itself) - its own manual explicitly credits at least one worked example to the
  Beta BASIC manual (see `0002`), and shares Beta BASIC's `DO`/`LOOP`/`UNTIL`/`WHILE` shape - the
  clearest case of one dialect directly building on another in this whole survey, not just
  convergent design.
- **YS MegaBasic** (1984, Mike Leaman, sold via _Your Spectrum_ magazine) - procedures exist but are
  named starting with an `@` sign, can't be typed as ordinary commands, and **have no local
  variables at all** - a deliberately weaker mechanism than Beta BASIC's. Its one new loop construct
  is a plain `REPEAT UNTIL`, no `DO`/`LOOP`, no separate `WHILE`. No `IF`/`ELSE` extension found.
  See `0004`.
- **Laser BASIC** (1986, Kevin Hambleton/Ocean Software) - primarily a sprite/graphics command pack
  (138 added commands) rather than a structured-programming tool. Its own shipped instructions
  confirm **no new looping or conditional structures at all**: only classic `FOR`/`NEXT` for loops
  and unmodified single-line `IF`/`THEN`/`ELSE`. Its "procedures" are the most unusual naming choice
  in this entire survey - built by **hijacking the existing `DEF FN`/`FN` tokens** rather than
  adding new `PROC`-style keywords: `DEF FN NAME#(params) ... .RETN`, called with
  `.PROCFN NAME#(args)`, up to 52 of them, single-letter (optionally `$`-suffixed) names only - the
  same naming constraint classic Sinclair `DEF FN` itself has. The "Laser Compiler" bundled with it
  isn't a compiler in the normal sense - it reuses the same tokens as ordinary Spectrum BASIC.
- **NextBASIC** (SpecNext, ongoing) - covered in full in `0002`-`0005`; extends Spectrum 128 BASIC
  with `DEFPROC`, `REPEAT`/`WHILE`, and both short- and long-form `IF`/`ELSE`, in a style that reads
  as consciously modelled on SAM Coupé's (near-identical manual wording in places - see `0005`).
- **SpecBAS** (2011-ongoing, Paul Dunn/"ZXDunny") - a from-scratch modern reimplementation (not a
  ROM extension - it runs on PC, not Spectrum hardware) explicitly modelled on Sinclair BASIC's
  syntax and extended well past it: `DEF PROC name[(params)] ... END PROC` with `REF` parameters and
  `LOCAL`, `DO`/`LOOP` with `WHILE`/`UNTIL` on either end, and single-line `IF...THEN...ELSE` with
  an optional `ENDIF` and a genuinely unusual `ELSE ELSE` idiom for a nested nested `IF` that needs
  to skip past its parent's own `ELSE` (see `0002`/`0004`/`0005`). Its function story is the most
  novel finding in this whole survey: classic single-line `DEF FN`/`FN` is kept entirely unchanged,
  and a _new_, differently-named construct, `CALL`, provides "a `PROC` that returns a value" - not
  via a `RETURN` statement like every other extended dialect surveyed, but via an **implicit
  `result`/`result$` variable** auto-created on entry and read back on exit. See `0003`.
- **BASin** (Paul Dunn, same author as SpecBAS) - **not a dialect at all**: it's a Windows
  Spectrum-BASIC development environment/emulator targeting genuine, unextended Sinclair BASIC. Its
  relevance here is biographical, not linguistic - the same person who built a Sinclair-BASIC tool
  first went on to design an actual extended dialect in SpecBAS.
- **Boriel ZX BASIC** - covered in full in `0002`/`0003`/`0004`/`0005`; the one dialect here that
  abandons the `DEF PROC`/`DEF FN` surface entirely in favour of a typed `SUB`/`FUNCTION` design.
  **Both Boriel and SpecBAS are modern, actively-developed projects, but they target opposite
  ends**: Boriel is a _compiler_ that emits real Z80 machine code (`.tap`/`.tzx` etc.) to run on
  actual Spectrum hardware or a cycle-accurate emulator of it - a modern toolchain for old hardware.
  SpecBAS is an _interpreter_ that runs natively on a modern PC (Windows/Linux, x86) and never
  touches Z80 code at all - a modern application merely inspired by old BASIC's syntax. Both are
  "modern" in being actively written by present-day authors, but only Boriel actually produces
  something that runs on - or as if on - the original machine.
- **Pascalated ZX BASIC** (1987 original "Pascalated BASIC" by ZarSoft, Portugal's _MicroSe7e_
  magazine; revived 2021 as a JS converter, then 2023 as "Pascalated Boriel") is **less a dialect
  than a renaming**: a handful of plain C-preprocessor-style `#define` macros feeding Boriel's own
  compiler, not a new language layer with its own semantics. The macros: `#define REPEAT DO`,
  `#define UNTIL LOOP UNTIL`, `#define PROCEDURE SUB`, `#define VAR DIM`, `#define TYPE AS`,
  `#define INTEGER LONG`, `#define REAL FLOAT`, `#define PROGRAM REM` - so Pascalated's
  `REPEAT...UNTIL condition` _is_, after macro expansion, literally Boriel's own
  `DO...LOOP UNTIL condition` (see `0004`), not a separate top-tested/bottom-tested pair the way BBC
  BASIC's or COMAL's genuinely is. `IF`/`ELSEIF`/`ELSE`/`END IF`, `WHILE`/`END WHILE`, and
  `FUNCTION`/`RETURN`/`END FUNCTION` have **no macro at all** - a comment in a second lesson's
  source notes explicitly `'#define WHILE WHILE ' already defined` and
  `'#define FUNCTION FUNCTION ' already defined` - meaning these are passed straight through as
  literal Boriel syntax, already documented in `0003`/`0005`. One incidental find: a worked example
  uses `ELSE IF` as **two words** with a single `END IF` closing the whole chain (not two, which
  real nesting would need) - meaning Boriel's compiler accepts `ELSE IF` as equivalent to its
  documented one-word `ELSEIF`, a spacing nuance its own docs don't mention (see `0005`).

### Extended, but not with control-flow/procedure features

- **Timex Sinclair TS1000/TS2068** - the TS1000 is essentially a US-market ZX81 with cosmetic
  differences; the TS2068 (T/S 2068) adds a handful of new keywords (`SOUND`, `ON ERR`, `FREE`,
  `DELETE`, `RESET`, `STICK`) for its own sound chip and joystick hardware, but none of them touch
  procedures, loops, or conditionals - it stays exactly as un-structured as stock Spectrum BASIC in
  every way this project cares about.

### Not descended from Sinclair BASIC at all, despite running on Sinclair-branded hardware

- **Locomotive BASIC** is the Amstrad CPC's own native ROM BASIC (Locomotive Software, 1984) - it is
  _not_ what shipped on the ZX Spectrum +3, despite the two often being mentioned in the same breath
  (Amstrad acquired the Sinclair brand and manufactured the +3, and the +3's ordinary BASIC mode is
  still Sinclair Spectrum BASIC, unchanged, for compatibility).
- **Mallard BASIC** is the one that actually did ship with the +3 - as the bundled BASIC for its
  optional CP/M Plus mode, a wholly separate boot path from ordinary Spectrum BASIC. It's a
  Locomotive Software product built for CP/M generally (also used on other CP/M machines), not
  derived from either Locomotive BASIC or Sinclair BASIC's codebase. Its manual confirms:
  `IF...THEN...ELSE` is nestable but **single-line only, no block form** and no `ENDIF`/`END IF` at
  all ("the whole structure... must be on a single program line," in the manual's own words); a
  genuine `WHILE...WEND` (the actual source, in this whole combined survey, of the "`WEND`" spelling
  `PLAN.md`'s item is named after - see `0004`); no `REPEAT` anywhere in the full text; no
  `DEF PROC`/`SUB`-style procedure mechanism either - only plain `GOSUB`/`RETURN`; and classic
  single-line `DEF FN function-name(params) = expr`, unextended. So the one dialect in this entire
  combined survey that spells the loop terminator `WEND` is also the one dialect that isn't
  Sinclair-heritage at all, and is the weakest of any surveyed on structured procedures.

### Acorn lineage - contemporary, not descended, but the direct ancestor of the `PROC`/`FN` convention nearly everything else here copies

- **Acorn Atom BASIC** (1980) - Acorn's own earlier, more primitive dialect, predating BBC BASIC.
  Its language manual, _Atomic Theory and Practice_ (not to be confused with a same-vintage-looking
  "Technical Manual" that turns out to be a hardware/construction guide - see Dead ends), documents:
  `IF...THEN GOTO`/label-based jumps only, no `ELSE`, no floating point without an add-on ROM, no
  procedures or user-defined functions at all - but **a genuine `DO...UNTIL` loop already exists**,
  confirmed with several worked examples (`10 I=0` / `30 I=I+1` / `40 PRINT "!"` / `50 UNTIL I=256`,
  and both an inline `DO stmt; UNTIL cond` form and a bare `DO; UNTIL cond` form for an
  unconditional-first-pass loop). Bottom-tested only, no `WHILE` variant, no `LOOP` keyword - the
  manual's own section header calls it "`DO...UNTIL` Loops". This makes Atom BASIC the direct
  ancestor of **two** different shapes in this survey at once, not zero: BBC BASIC's later
  `REPEAT...UNTIL` reads as a straight rename of Atom's own `DO...UNTIL` (see `0004`), while Beta
  BASIC's/SAM Coupé's/SpecBAS's/Boriel's `DO`/`LOOP` keeps Atom's `DO` keyword but adds the `LOOP`
  terminator and a `WHILE` counterpart Atom never had. BBC BASIC's structured-programming model
  (`DEF PROC`/`ENDPROC`, `DEF FN`, `WHILE`/`ENDWHILE`, block-`IF...ENDIF`) is still a genuine leap
  past Atom BASIC - procedures, functions, `WHILE`, and block `IF` are all new at BBC BASIC - but
  the loop story specifically starts one machine earlier.
- **BBC BASIC** - the actual origin, confirmed via primary manual text in `0002`-`0005`, of the
  `PROC`/`ENDPROC`/`FN`/`LOCAL`/`REPEAT...UNTIL`/`WHILE...ENDWHILE`/block-`IF...ENDIF` shape that
  Beta BASIC, SAM Coupé, NextBASIC, QL SuperBASIC, and SpecBAS all recognisably descend from or
  echo, despite none of them running on Acorn hardware or sharing Acorn's codebase. Not
  Sinclair-heritage by platform, but the single most-copied design in this entire survey.

### Sinclair's own, but a separate codebase from Spectrum BASIC entirely

- **Sinclair QL SuperBASIC** - covered in full in `0002`-`0005`; Sinclair's own product, but for a
  different machine (the QL, 1984) with its own from-scratch interpreter, not derived from Spectrum
  ROM BASIC. The official _QL User Guide_ (distinct from the fan-maintained readthedocs manual
  mirror used throughout `0002`-`0005`) settles a question that mirror's own pages left open:
  `IF`/`ELSE` is documented as strictly two-way, with three-or-more-way decisions explicitly routed
  to a separate `SELect ON`/`END SELect` construct instead of a chained `ELSEIF` - the same "add a
  `CASE`-style construct rather than extend `IF`" choice COMAL makes below.
- **"SBasic"** turns out to name the same language, not a separate one: it's SMSQ/E's (the QL
  operating system's modern continuation) own built-in SuperBASIC-compatible interpreter, documented
  by the very same _SBASIC/SuperBASIC Reference Manual_ already cited throughout `0002`-`0005`.

### Related by comparison, not lineage

- **COMAL** - covered in full in `0002`-`0005`; a Danish-designed, Pascal-influenced structured
  BASIC-family language (1970s) with no platform or codebase connection to Sinclair, Acorn, or
  Amstrad hardware at all - included throughout this project's research purely because its
  `PROC`/`FUNC`/`WHILE`/`REPEAT`/`IF` design is a useful, independently-arrived-at comparison point
  for the same problems these Sinclair-heritage dialects solved. Not one implementation but a small
  family of its own: the 1984 Christensen "standard" reference guide and a 1983 Lindsay handbook for
  Commodore's COMAL 80 (versions 0.11-1.02) between them show the language mid-evolution - `EXEC`
  (optionally omittable) as the call keyword, and a `LOOP`/`ENDLOOP`/`EXIT`/`EXITIF` construct for a
  middle-of-body loop condition that's real but postdates the 1984 standard, added in version 1.02
  "as this Handbook was going to press" per its own text (see `0002`/`0004`).

### Further ROMs, tools, and ports surveyed - confirmed to add no `PROC`/`FN`/`WHILE`/`REPEAT`/`IF`-`ELSE` extensions

A second, wider name-list turned up nothing new for this project's purposes - worth recording _that_
clearly, rather than leaving the impression there's untapped syntax data here. Every one of these
falls into one of a few buckets, none of which touch control flow:

- **Editor/tokenizer/bugfix ROMs**, not language extensions: **Gosh Wonderful** (Geoff Wearmouth) -
  its headline feature is character-by-character BASIC entry instead of Sinclair's keyword-token
  entry, plus `RENUMBER`/`DELETE` editor commands and ROM bug fixes; **Looking Glass ROM**, a
  development of Gosh Wonderful; **Sea Change ROM**, an experimental Interface-1-oriented ROM
  (streams/Microdrive-focused). None add procedures, loops, or `ELSE`.
- **Hardware-support command additions**, not control-flow extensions: **OpenSE BASIC**/**SE Basic**
  (`EDIT`/`DELETE`/`RENUM`/`ON ERR` editor commands, AY sound, ULAplus, direct machine-code calls,
  hex/octal literals - a faster, more capable Sinclair BASIC, not a structurally different one); the
  whole **Timex Extended BASIC** family (`BASIC-64` for the TC2068's extra graphics/text modes,
  `TEC` - Timex Extended Commands - on the Timex Computer 3256 for its AY chip/RS-232/hi-res mode) -
  all add hardware-access keywords, none add `PROC`/`WHILE`/`ELSE`.
- **Compilers of unmodified Sinclair BASIC syntax**, not new dialects: **HiSoft COLT Compiler**
  (1985, the fourth generation in a lineage from ZX-GT through Mcoder 1/2) compiles ordinary
  Sinclair BASIC to machine code without changing its syntax - the same pattern already found in
  `0002` for the unrelated "Laser Compiler" bundled with Laser BASIC.
- **Ports, emulation layers, and reimplementations aiming for fidelity, not extension**: **ROMU6**
  (Cesar and Juan Hernandez, 1994) emulates an unmodified Sinclair ZX Spectrum 48K (BASIC included)
  on MSX hardware - a compatibility layer, not a dialect; **Sinbas** (Pavel Napravnik) is a
  Sinclair-BASIC-compatible interpreter for DOS; **CheckBasic** (Philip Kendall, also the maintainer
  of the Spectrum ROM-image collection cited earlier in this note) is a Spectrum-BASIC tool for
  Unix; **BINSIC** (Adrian McMenamin) is a Java/Groovy reimplementation explicitly "closely modelled
  on ZX81 BASIC" - and ZX81 BASIC itself has no `PROC`/`WHILE`/`ELSE` to inherit; **Sparky eSinclair
  BASIC** (Richard Kelsh) is described as an operating system "loosely based on" Sinclair BASIC for
  the 24-bit Zilog eZ80 - a port to different hardware, not a documented language extension.
- **Not found at all**: no source located for "ZebraOS" under that name in connection with Sinclair
  or Spectrum BASIC - flagged as unconfirmed/possibly a naming mismatch rather than dropped silently
  (see Open questions).

## Primary sources by dialect, and how to fetch them

A working URL plus fetch method for each dialect's primary manual, so the next feature-comparison
note can go straight to primary text. "Direct" means `WebFetch` reads it as-is; anything else names
the workaround.

| Dialect                                                         | Primary source                                                                                                                                                                                                                                        | Fetch method                                                                                                                                                                                                                                                                                          |
| --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| BBC BASIC                                                       | <https://www.bbcbasic.co.uk/bbcwin/tutorial/> - numbered chapter pages (e.g. `chapter09.html` IF, `chapter12.html` REPEAT/WHILE, `chapter16.html` PROC, `chapter17.html` FN)                                                                          | Direct                                                                                                                                                                                                                                                                                                |
| Beta BASIC                                                      | <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> (3.0 manual, Slovak translation)                                                                                                                                                      | The `_en.htm` landing page doesn't contain the manual itself - read its raw HTML for the real download link (see technique notes)                                                                                                                                                                     |
| Beta BASIC (4.0 128K supplement only)                           | <https://worldofspectrum.net/pub/sinclair/games-info/b/BetaBasicV4.0.pdf>                                                                                                                                                                             | `curl -A "Mozilla/5.0"` past the 403, then `pypdf`                                                                                                                                                                                                                                                    |
| SAM Coupé BASIC                                                 | <https://sam.speccy.cz/basic/sam-basic_complete_guide.pdf>                                                                                                                                                                                            | Download + `pypdf`                                                                                                                                                                                                                                                                                    |
| NextBASIC / SpecNext                                            | <https://element.zxfiles.net/DOCS/OTHER/NEXTBAS.PDF>                                                                                                                                                                                                  | Download + `pypdf`                                                                                                                                                                                                                                                                                    |
| QL SuperBASIC / SBasic                                          | <https://superbasic-manual.readthedocs.io/en/latest/> - per-letter/keyword pages (e.g. `D/define--procedure.html`, `D/define--function.html`, `I/if.html`, `R/repeat.html`) - a fan-maintained mirror                                                 | Direct                                                                                                                                                                                                                                                                                                |
| QL SuperBASIC (official)                                        | <https://archive.org/details/SinclairQLHomepage> - the official _QL User Guide_'s own SuperBASIC-programming chapters, `docs/manuals/program.zip` (plain text, one file per chapter)                                                                  | Direct download of the zip, no `_djvu.txt` needed - the source archive already ships as plain text                                                                                                                                                                                                    |
| COMAL (1984 standard)                                           | <https://archive.org/details/COMAL_Reference_Guide> (Christensen, 1984)                                                                                                                                                                               | Archive.org `_djvu.txt` sibling (see technique notes)                                                                                                                                                                                                                                                 |
| COMAL (1983, Commodore, versions 0.11-1.02)                     | <https://archive.org/details/COMAL_Handbook_1983_Reston_Publishing> (Lindsay) - a second, independently useful edition; documents `LOOP`/`ENDLOOP` (added in v1.02, absent from the 1984 standard) and confirms `EXEC` as the (optional) call keyword | Archive.org `_djvu.txt` sibling                                                                                                                                                                                                                                                                       |
| Boriel ZX BASIC                                                 | <https://github.com/boriel/zxbasic/blob/main/docs/> - one file per keyword (`sub.md`, `function.md`, `while.md`, `do.md`, `if.md`, ...)                                                                                                               | Fetch the `raw.githubusercontent.com/boriel/zxbasic/main/docs/<name>.md` form directly                                                                                                                                                                                                                |
| SpecBAS                                                         | <https://sites.google.com/site/pauldunn/home/manual>                                                                                                                                                                                                  | Direct - one long page, use a targeted prompt per feature                                                                                                                                                                                                                                             |
| Mallard BASIC                                                   | <https://archive.org/details/Mallard-BASIC>                                                                                                                                                                                                           | Archive.org `_djvu.txt` sibling - **filename drops the hyphen** the display title has (`MallardBASIC_djvu.txt`); check `archive.org/metadata/Mallard-BASIC` (JSON) rather than guessing                                                                                                               |
| Laser BASIC                                                     | <https://spectrumcomputing.co.uk/pub/sinclair/games-info/l/LaserBasic.txt>                                                                                                                                                                            | Direct - plain text, no PDF needed                                                                                                                                                                                                                                                                    |
| YS MegaBasic, Laser BASIC, Beta BASIC (secondary corroboration) | <https://www.crashonline.org.uk/25/basics.htm> ("Battle of the Basics", 1985) and <https://www.crashonline.org.uk/43/betabasic.htm>                                                                                                                   | Direct                                                                                                                                                                                                                                                                                                |
| Acorn Atom BASIC                                                | <https://www.theoddys.com/acorn/acorn_system_computers/atom/Atomic%20Theory%20and%20Practice.pdf> (227pp, the real language manual)                                                                                                                   | Download + `pypdf`. **Do not confuse with** `chrisacorns.computinghistory.org.uk/docs/Acorn/Manuals/Acorn_AtomTechnicalManual.pdf`, a same-vintage-looking but different document that turns out to be a hardware/construction guide with no language content                                         |
| Pascalated ZX BASIC                                             | <https://arcalusitana.org/MuseuZX/Pascalated_ZXBASIC/>                                                                                                                                                                                                | The 34 worked lesson programs are real Pascalated source, but embedded in per-lesson `<textarea>` blocks generated by inline JS `onclick` handlers, not plain `href`s - `curl` the raw HTML and read the `<textarea>` content directly; `WebFetch`'s markdown conversion of the same page misses them |

### Fetch technique notes

- **`WebFetch` cannot read PDF text at all** - its markdown conversion returns the raw
  binary/compressed stream structure. It does still save the binary to a local file even when it
  can't convert it (check the tool result for the saved path), or download directly with `curl`.
  Either way, `pip install pypdf` (pure Python, no system dependencies) plus a few lines of Python
  extracts the text cleanly. This project's `Read` tool has its own PDF path, but it shells out to
  `pdftoppm` (`poppler-utils`), which isn't installed in this environment - don't rely on it for
  PDFs here; use `pypdf` instead.
- **A `WebFetch` 403 doesn't mean the file is unreachable** - `worldofspectrum.net` is a repeat
  offender. Plain `curl -sL -A "Mozilla/5.0" -o file.pdf <url>` gets past it; a browser `User-Agent`
  is usually all a 403 like this is checking for.
- **Internet Archive's `_djvu.txt` full-text sibling is usually the fastest path to real text** -
  `https://archive.org/download/<item>/<item>_djvu.txt` - skipping PDF extraction entirely. But the
  actual filename can differ from the item's display title (a hyphen present in the title but absent
  from the file, for instance) - if a guessed URL 404s, check `https://archive.org/metadata/<item>`
  (JSON) for the real `files[].name` list rather than concluding the text isn't available.
- **spectrumcomputing.co.uk often hosts a plain `.txt` instructions file alongside a PDF** for the
  same title - check an entry page's download table for one before reaching for PDF extraction.
- **A manual's own landing/index page may not contain the manual** - `softhouse.speccy.cz`'s
  `_en.htm` page for the Beta BASIC 3.0 manual is a case in point: `WebFetch`'s markdown conversion
  of it reaches only page metadata, not the manual. If a fetched "manual" page reads suspiciously
  thin, read the raw HTML (`curl`) for the actual download links.
- **A page whose real content is generated by inline JavaScript won't show useful `href`s** -
  Pascalated ZX BASIC's lesson table builds each program's source into a `<textarea>` via an
  `onclick="view_program(...)"` handler, not a link. `curl` the raw HTML and read the `<textarea>`
  blocks (or other embedded content) directly rather than searching for download links that don't
  exist as such.
- **Archive.org often holds several independent editions of the same language/manual, not just
  one** - a plain `site:archive.org <language name>` search can turn up a second edition, a
  different platform's manual, or a later version that documents a feature the first one predates
  entirely. COMAL's `LOOP`/`ENDLOOP` (real, just added after the 1984 standard guide - see
  `0002`/`0004`) was found this way.
- **Not every archive.org item is freely downloadable** - some are lending-library ("borrow") items;
  their `_djvu.txt` (and other download routes) return a 401 Unauthorized page rather than the
  actual text. There's no reliable way to tell from the item's details page alone - try the
  `_djvu.txt` route and check whether the response is real text or an HTML "401 Authorization
  Required" page before concluding it worked.
- **A `.zip` of plain-text chapter files, where one exists, beats even a `_djvu.txt` sibling** - the
  official _Sinclair QL User Guide_'s SuperBASIC-programming chapters are distributed as one
  `program.zip` (30-115KB per manual section, no OCR noise at all, unlike a scanned book's
  `_djvu.txt`) - check an archive.org item's own `docs/manuals/index.html`-style listing (if the
  item has one) for this kind of native plain-text release before falling back to full-book OCR
  text.
- **Search-engine-summarized "quotes" are not evidence on their own.** A search result once
  fabricated a full "grade calculator" COMAL example around a keyword (`ELIF`) that did turn out to
  be genuine - the keyword being right didn't make the example real. Verify anything a Finding
  depends on against a primary source before treating it as settled, and say clearly in Evidence
  when something hasn't been.
- **A found PDF matching a machine's name isn't necessarily the language manual** - a same-vintage
  Acorn Atom "Technical Manual" turned out to be a hardware/construction guide with no BASIC content
  at all, distinct from the real language manual (_Atomic Theory and Practice_). Check that a found
  document actually contains what's needed before citing it.

## Evidence

Sources for dialects not already fully cited in `0002`-`0005` (which remain the primary citation for
BBC BASIC, Beta BASIC, SAM Coupé BASIC, NextBASIC, QL SuperBASIC, COMAL, and Boriel ZX BASIC):

- <https://archive.org/details/COMAL_Handbook_1983_Reston_Publishing> (Lindsay, 1983) - a second
  primary COMAL source. Source for the `EXEC` call-syntax finding and the `LOOP`/`ENDLOOP`
  resolution in `0002`/`0004`.
- <https://archive.org/details/SinclairQLHomepage> - the official _Sinclair QL User Guide_'s
  SuperBASIC-programming chapters (`docs/manuals/program.zip`). Source for the `SELect ON` finding
  in `0005`.
- <https://sites.google.com/site/pauldunn/home/manual> - the SpecBAS Reference Manual, Paul Dunn's
  own site. Source for all SpecBAS claims above.
- <https://github.com/ZXDunny> and <https://news.ycombinator.com/item?id=27085592> - confirm Paul
  Dunn ("ZXDunny") as author of both SpecBAS and BASin (and the ZXSpin emulator).
- <https://www.crashonline.org.uk/25/basics.htm> - _CRASH_ issue 25 (1985), "Battle of the Basics",
  a contemporary comparative review covering Beta BASIC, YS MegaBasic ("Mega BASIC" in the article),
  and Laser BASIC side by side. Source for the YS MegaBasic claims above, and independent
  corroboration of Beta BASIC's "exit from any point" loop characterisation.
- <https://spectrumcomputing.co.uk/pub/sinclair/games-info/l/LaserBasic.txt> - Laser BASIC's own
  shipped instructions. Source for the `DEF FN`/`.PROCFN`/`.RETN` procedure mechanism, and
  confirmation - from the product itself, not just a contemporary review - that only classic
  `FOR`/`NEXT` and unmodified `IF`/`THEN`/`ELSE` exist.
- <https://en.wikipedia.org/wiki/Mallard_BASIC> and <https://en.wikipedia.org/wiki/Locomotive_BASIC>
  - source for the Locomotive-vs-Mallard distinction.
- <https://archive.org/details/Mallard-BASIC> - the manual's full text (the item's actual filename
  is `MallardBASIC_djvu.txt`, no hyphen, unlike the display title `Mallard-BASIC` - found via
  `archive.org/metadata/`). Source for Mallard's confirmed `IF...THEN...ELSE`/`WHILE...WEND`/
  `DEF FN` syntax, detailed in `0004`/`0005`.
- <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> - the Beta BASIC 3.0 manual
  (Slovak translation), source for the corrected Beta BASIC entry above; see the technique notes
  above for how its download link was found (the landing page itself isn't the manual).
- <https://arcalusitana.org/MuseuZX/Pascalated_ZXBASIC/> - the page itself, plus (via its raw HTML,
  see technique notes above) Lesson 0's complete worked example program and its `#define` macro
  table. Source for the Pascalated ZX BASIC entry above.
- Search-engine results over Timex/Sinclair reference material at timexsinclair.com - source for the
  TS2068's added-keywords list.
- <https://en.wikipedia.org/wiki/Acorn_System_BASIC> - source for Atom BASIC's relationship to BBC
  BASIC generally.
- <https://www.theoddys.com/acorn/acorn_system_computers/atom/Atomic%20Theory%20and%20Practice.pdf>.
  The Acorn Atom BASIC language manual (227pp). Source for the Atom BASIC entry above, including the
  `DO...UNTIL` finding.
- Search-engine results (none independently re-fetched as primary text, all consistent enough across
  multiple hits to report as found) for: Gosh Wonderful/Looking Glass ROM, Sea Change ROM, OpenSE
  BASIC/SE Basic, HiSoft COLT Compiler, ROMU6, Sinbas, CheckBasic, BINSIC, and Sparky eSinclair
  BASIC - source for the "further surveyed" list above. "ZebraOS" returned no relevant results under
  any search phrasing tried.

## Dead ends

- **The first "Acorn Atom Technical Manual" PDF found (from `chrisacorns.computinghistory.org.uk`)
  is the wrong document** - a hardware construction/assembly manual, not a BASIC language reference;
  its only hit for "`PROC`" is "CONSTRUCTION PROCEDURE" in the kit-assembly instructions. The real
  manual is _Atomic Theory and Practice_ (see Primary sources table) - searching for that exact
  title finds it directly, rather than generic terms like "Acorn Atom manual". Two official-sounding
  manual titles for the same machine can be genuinely different documents: check a found PDF
  actually contains what's needed before citing it.

## Open questions

- What is "ZebraOS"? No source was found connecting that name to Sinclair or Spectrum BASIC under
  several search phrasings - it may be misremembered, a very obscure/local product, or indexed under
  a different name entirely.
- Do any of the "further surveyed" tools above turn out, on closer reading of primary documentation,
  to have a `PROC`/`WHILE`/`ELSE`-equivalent this survey's search-engine-only pass missed? Treated
  as unlikely given how consistently each one's own stated purpose (editor speed, hardware access,
  compilation, portability) points away from language-surface changes, but none of them were read in
  primary-source depth the way `0002`-`0005`'s core dialects were.
- Did any ROM revision of Acorn Atom BASIC itself gain `PROC`/`FN` or a `WHILE`-equivalent before
  BBC BASIC's release, beyond the `DO...UNTIL` loop confirmed to exist? _Atomic Theory and
  Practice_'s 227 pages never mention either in the sections `grep`-checked here, which is
  reasonably strong evidence for "no" given how thorough that manual is elsewhere - but the whole
  text wasn't read page by page, so this stays open rather than closed with certainty.
- Does Laser BASIC's final 1986 commercial release differ from _CRASH_ 25's 1985 pre-release
  description in any other respect? Its own shipped instructions file settles the control-flow
  question specifically (no, both agree); two contemporary reviews found via search but not read
  (_Sinclair User_ 49, _CRASH_ 26) might still hold other differences, but nothing points at a need
  to check them for BazLang's purposes specifically.
