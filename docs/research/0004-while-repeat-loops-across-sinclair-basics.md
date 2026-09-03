# How do Sinclair-family BASICs (and COMAL) implement conditional loops - `WHILE`/`WEND`, `REPEAT`/`UNTIL`, and their relatives?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high - primary manual text was read directly for all dialects covered here except
Pascalated ZX BASIC, sourced from one real worked example program rather than a full manual. See
`0006-related-basic-dialects-overview.md` for the full dialect genealogy and working source URLs.

## Finding

**`WEND` does not appear in any Sinclair-heritage dialect surveyed - but it does have a real source
on Sinclair-branded hardware.** `PLAN.md`'s own `WHILE...WEND` item is named after the
Microsoft/GW-BASIC-family spelling, and this survey's one confirmed `WHILE...WEND` is **Mallard
BASIC** - the CP/M BASIC (from Locomotive Software, unrelated to Sinclair BASIC's own codebase)
bundled with the ZX Spectrum +3's CP/M Plus mode as a separate boot path from ordinary Spectrum
BASIC (see `0006`). So `WEND` genuinely shipped on a Sinclair-branded machine, just never as part of
Sinclair BASIC itself. Every dialect that's actually part of the Sinclair/Acorn/COMAL lineage spells
its top-tested-loop terminator differently: BBC BASIC and Boriel ZX BASIC use `ENDWHILE` (Boriel
also accepts `WEND` as an explicit alternative spelling - its own docs note plainly that `WHILE`
"does not exist in Sinclair Basic"), COMAL uses `ENDWHILE`, and most of the rest (SAM Coupé, Beta
BASIC, NextBASIC) don't have a freestanding `WHILE` loop construct at all - `WHILE` there is a
*condition clause* attached to a different loop keyword, not its own loop.

Four genuinely different loop-family shapes turned up, of varying richness:

1. **`DO`/`LOOP` with `UNTIL`/`WHILE` as an optional clause on either end** (SAM Coupé, Beta BASIC,
   Boriel ZX BASIC, and SpecBAS). All four let the condition sit at the top (`DO UNTIL cond` /
   `DO WHILE cond`, tested before the first iteration) or the bottom (`LOOP UNTIL cond` /
   `LOOP WHILE cond`, tested after - body always runs at least once), with a bare `DO...LOOP` as an
   infinite loop otherwise, plus an `EXIT`-style early-exit statement usable from anywhere in the
   body. SAM Coupé's manual credits this construct's own worked recursion example to the Beta BASIC
   manual (see `0002`), and Beta BASIC's own manual confirms the identical `DO`/`LOOP` shape
   directly, not just as a shared ancestor SAM Coupé's manual claims. SpecBAS (a modern
   reimplementation, not a ROM-era dialect - see `0006`) uses the identical shape again. Boriel ZX
   BASIC's is the same shape once more, presented as a BASIC-family-general feature rather than
   credited to any of these. The `DO` keyword itself traces back further still: **Acorn Atom BASIC
   (1980), BBC BASIC's own predecessor, already has a `DO...UNTIL` loop** - bottom-tested only, no
   `WHILE` variant, no `LOOP` terminator (the loop just runs from `DO` back to the matching
   `UNTIL`). So this shape's `DO` half is at least as old as Sinclair-heritage structured BASIC gets
   in this survey, predating even BBC BASIC; the `LOOP` terminator and `WHILE` counterpart are what
   each later dialect added on top of it (see `0006`).
2. **`REPEAT`/`REPEAT UNTIL` with `WHILE` as a guard clause usable anywhere inside the loop, not
   just at the top or bottom** (NextBASIC). A `WHILE cond` statement can appear at any point in the
   loop body, any number of times; if false, execution jumps straight past the matching
   `REPEAT UNTIL`. This is the most flexible of the four shapes - one loop body can have conditions
   checked at the top, the bottom, and the middle, in any combination - at the cost of reusing
   `REPEAT` itself as half of its own terminator (`REPEAT UNTIL condition`) rather than a distinct
   keyword.
3. **A single unified loop constructor with no dedicated `WHILE`/`UNTIL` keywords at all**
   (QL SuperBASIC's `REPeat`/`END REPeat`, labelled by an identifier, exited or restarted early via
   plain `IF...THEN EXIT`/`IF...THEN NEXT` statements placed anywhere in the body). This is
   `WHILE`/`UNTIL`'s job done entirely with the language's ordinary conditional and jump statements
   rather than dedicated loop-condition syntax - the QL manual's own words: "The `REPeat` structure
   does the jobs of both `REPEAT` and `WHILE` structures."
4. **A bare `REPEAT UNTIL` with no `WHILE`-equivalent at all** (YS MegaBasic - "simple `REPEAT UNTIL`
   loops are the only new control structure", per a contemporary review). The weakest of the four -
   no top-tested form, no anywhere-in-body guard, just the one bottom-tested construct.

The classic BBC-derived pair - a genuinely separate `REPEAT...UNTIL` (bottom-tested) alongside a
genuinely separate `WHILE...ENDWHILE`/`WEND` (top-tested), as two independent constructs - is the
shape BBC BASIC and COMAL use, and the shape `PLAN.md`'s item is actually describing (once `WEND`
is read as "the top-tested one", regardless of exact spelling). It's one option among the four
found here, not the only one, and not the one most of the Sinclair-heritage dialects (as opposed to
BBC/COMAL) actually picked. **COMAL, uniquely among every dialect surveyed, layers a third construct
on top of that pair**: `LOOP ... EXIT/EXITIF condition ... ENDLOOP`, for a condition in the *middle*
of the body - added to the language after its 1984 standard, but real (see Evidence) - making COMAL
alone in having a dedicated, separate keyword for each of the three testing positions (top, bottom,
middle) rather than making one shape do double or triple duty the way every other dialect here does.
Given Acorn Atom BASIC's own `DO...UNTIL` predates BBC BASIC on the same lineage (see above and
`0006`), BBC BASIC's `REPEAT...UNTIL` reads as a straightforward rename of Atom's `DO...UNTIL`
rather than a new construct - the genuinely new thing BBC BASIC contributed to this shape is the
independent `WHILE...ENDWHILE`, which Atom never had. **Laser BASIC**, meanwhile, adds neither - its
own shipped instructions confirm "no new looping or conditional structures at all" despite its
extensive procedure and sprite-command additions (see `0006`), confirming that adding procedures and
adding structured loops are genuinely separate design decisions in this survey, not a package deal.
**Pascalated ZX BASIC** looks, at first glance, like it belongs in the classic-pair camp too
(`REPEAT...UNTIL` and `WHILE...END WHILE` as apparently separate constructs) - but its actual
compiled output shows `REPEAT`/`UNTIL` are literally `#define`d as Boriel's own `DO`/`LOOP UNTIL`
(shape 1 above), while `WHILE`/`END WHILE` is Boriel's native keyword passed through unchanged - so
it's not a fifth shape, or even really the classic pair; it's shape 1's `DO` half wearing a
Pascal-flavoured name (see `0006`).

**Implication for BazLang:** the `FOR`/`NEXT` skip-scan `PLAN.md` already identifies as "the proven
pattern for locating matching terminators" is a bottom-line-compatible primitive with all four
shapes above, not just the BBC-style pair - so this survey doesn't push the implementation choice
either way. It does suggest the naming and shape question is more open than "add `WHILE`/`WEND` and
`REPEAT`/`UNTIL`" implies: SAM Coupé's/Beta BASIC's single `DO`/`LOOP` with a condition clause on
either end covers both cases with one pair of keywords instead of two, which may suit a
line-numbered, `GO TO`/`GO SUB`-authentic dialect like BazLang better than adding two unrelated
keyword pairs.

## Evidence

### BBC BASIC

- <https://www.bbcbasic.co.uk/bbcwin/tutorial/chapter12.html> - `REPEAT ... UNTIL condition`
  (bottom-tested, runs at least once):

  ```basic
  REPEAT
    INPUT "Enter a radius: " Radius
    Area = PI*Radius^2
    PRINT "Area of your circle is ";Area
    INPUT "Another go? Y/N " Reply$
  UNTIL Reply$="N"
  ```

  `WHILE condition ... ENDWHILE` (top-tested, may run zero times):

  ```basic
  Number = 1
  WHILE Number > 0
    LastNumber = Number
    Number = Number / 2
  ENDWHILE
  ```

  No `WEND` anywhere in this dialect.

### Beta BASIC (Spectrum)

- <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> - the 3.0 manual (Slovak
  translation). Confirms the full four-way matrix, exactly matching SAM Coupé's and Boriel's shape -
  condition on `DO` or `LOOP`, either polarity:

  ```basic
  DO                    DO WHILE condition       DO UNTIL condition
    statements             statements                statements
  LOOP                  LOOP                      LOOP

  DO                    DO
    statements             statements
  LOOP WHILE condition  LOOP UNTIL condition
  ```

  plus `EXIT IF condition` for an early exit from anywhere in the body. The manual's own keyword
  summary states outright: "`DO`, `LOOP`, `EXIT IF`, `WHILE`, `UNTIL` serve as `REPEAT` and `WHILE`
  in Pascal, but are more flexible" - a deliberate, Pascal-inspired design, not an incidental
  extension. `WHILE`/`UNTIL` are clause keywords attached to `DO`/`LOOP`, not a freestanding loop of
  their own, matching NextBASIC's and SAM Coupé's later choices (see Finding).
- Beta BASIC 4.0 supplement manual (see `0006`'s "Fetch technique notes"). Confirms the same
  `DO`/`LOOP` shape with an `UNTIL` clause on the `DO`:

  ```basic
  100 DEF PROC rdclear
        DO UNTIL CAT$()=""
          ERASE !CAT$()(1 TO 10)
        LOOP
      END PROC
  ```

  This is the same shape SAM Coupé BASIC's manual documents in full (see below) and credits to Beta
  BASIC's own manual for at least one example.

### SAM Coupé BASIC

- <https://sam.speccy.cz/basic/sam-basic_complete_guide.pdf> - full `DO`/`LOOP` documentation, with
  the condition allowed on either keyword:

  ```basic
  10 DO
  20 GET a$
  30 PRINT a$;
  40 LOOP UNTIL a$=" "
  ```

  ```basic
  10 DO UNTIL a$=" "
  20 GET a
  30 PRINT a$;
  40 LOOP
  ```

  Plus a third form using `EXIT IF` for an arbitrary early exit from anywhere in the body (see
  below). The guide states the rule explicitly: "Both `DO` and `LOOP` can be followed by either
  `UNTIL` or `WHILE`... `LOOP UNTIL a$=" "` is the same as `LOOP WHILE a$<>" "`" - i.e. `UNTIL` and
  `WHILE` are literally interchangeable, condition-negated forms of the same clause, not two
  different mechanisms. `EXIT IF condition` skips to the statement after the next `LOOP`
  unconditionally, independent of the `DO`/`LOOP`'s own condition - the manual specifically warns
  against using `GOTO` for this instead, since it "knocks the `DO` line off a storage area" and
  corrupts the loop-tracking stack. No `REPEAT` or `WHILE`-as-its-own-loop keyword exists in this
  dialect - only `DO`/`LOOP` with clauses.

### NextBASIC / SpecNext

- <https://element.zxfiles.net/DOCS/OTHER/NEXTBAS.PDF> - `REPEAT`/`REPEAT UNTIL condition`, with any
  number of `WHILE condition` guard statements placeable anywhere inside the loop body - each
  checked in sequence as execution reaches it, jumping past the matching `REPEAT UNTIL` the moment
  one is false:

  ```basic
  10 LET address=32768
  20 REPEAT
  30   READ b
  40 WHILE b>=0
  50   POKE address,b
  60   LET address=address+1
  70 REPEAT UNTIL 0
  80 DATA 62,25,1,112,17,201,-1
  ```

  (`REPEAT UNTIL 0` here means "never satisfied by the bottom test" - the loop only ends via the
  `WHILE` guard partway through.) Also confirmed: `REPEAT` loops nest to any depth, and a dedicated
  `EXIT [line-or-label]` command cleanly exits the current `FOR` or `REPEAT` loop from anywhere in
  its body - the manual recommends `EXIT` over `GOTO` for this "as the recommended way to exit from
  loops", matching SAM Coupé's identical warning about `GOTO` corrupting the loop-tracking stack. No
  `DO`/`LOOP` keyword pair exists in this dialect - only `REPEAT`/`REPEAT UNTIL` with `WHILE`
  clauses.

### Sinclair QL SuperBASIC

- <https://superbasic-manual.readthedocs.io/en/latest/R/repeat.html> - a single loop construct,
  `REPeat identifier ... END REPeat identifier`, labelled by an identifier so nested loops can be
  told apart. No dedicated `WHILE` or `UNTIL` keyword exists for it at all - `EXIT identifier`
  (terminate the loop, continue after `END REPeat`) and `NEXT identifier` (restart the loop from the
  top, skipping the rest of the body) are ordinary statements usable anywhere inside the body, and
  the manual documents using plain `IF...THEN EXIT`/`IF...THEN NEXT` to build both "test at the top"
  (`WHILE`-like) and "test at the bottom" (`UNTIL`-like) behaviour with the same construct:

  ```basic
  100 DEFine FuNction Getkey(key$)
  105   LOCal loop,k$
  110   REPeat loop
  120     k$=INKEY$:IF k$='':NEXT loop
  130     IF k$ INSTR key$&CHR$(27):RETurn CODE(k$)
  140   END REPeat loop
  150 END DEFine
  ```

  The manual's own summary: "The `REPeat` structure does the jobs of both `REPEAT` and `WHILE`
  structures and also cope[s] with other more awkward situations."

### COMAL

Two independent primary sources cover different points in COMAL's version history.

- <https://dn760101.eu.archive.org/0/items/COMAL_Reference_Guide/COMAL_Reference_Guide_djvu.txt> -
  the 1984 standard. Two genuinely separate constructs, the BBC-style pair:

  ```text
  WHILE <numeric expression> [DO]
    <statement list>
  ENDWHILE
  ```

  (also a short form, `WHILE expr DO statement`, with no `ENDWHILE`), and:

  ```text
  REPEAT
    <statement list>
  UNTIL <numeric expression>
  ```

  Plus a `FOR var:=init TO final [STEP n] DO ... ENDFOR [var]` counted loop. Has no
  `LOOP`/`ENDLOOP`/exit-anywhere construct - it's a later addition, documented below.
- <https://archive.org/details/COMAL_Handbook_1983_Reston_Publishing> (Len Lindsay, 1983, Reston
  Publishing) - COMAL 80 on the Commodore 64, versions 0.11-1.02. Documents
  `LOOP ... [EXIT|EXITIF condition] ... ENDLOOP` in full: a loop whose exit condition sits in the
  *middle* of the body rather than the top or bottom, closer in spirit to NextBASIC's
  anywhere-in-body `WHILE` guard than to the top/bottom-only `WHILE`/`REPEAT` pair above - making
  COMAL, across its full version history, the **third** dialect in this survey with an
  anywhere-in-body condition. Explicitly marked `COMAL STANDARD: [NO]`, unsupported in version 0.12,
  and "a last minute addition to version 1.02 as this Handbook was going to press" - added after the
  1984 standard guide above, which is why that guide doesn't mention it. The Handbook's own design
  advice: use `WHILE` if there are no statements before the exit test, `REPEAT` if there are none
  after it, and reserve `LOOP`/`ENDLOOP` - "rarely... never...more than one `EXIT` statement" - for
  genuine middle-of-body exits.

### Boriel ZX BASIC

- <https://raw.githubusercontent.com/boriel/zxbasic/main/docs/while.md> and
  <https://raw.githubusercontent.com/boriel/zxbasic/main/docs/do.md> - two independent constructs,
  matching BBC/COMAL's shape for `WHILE` but SAM/Beta's shape for the other:

  ```basic
  WHILE expression
     sentences
  END WHILE
  ```

  with `WEND` explicitly documented as an accepted alternative to `END WHILE` - making Boriel the
  only dialect surveyed that supports *both* the BBC-style spelling and the GW-BASIC-family `WEND`
  spelling `PLAN.md`'s item is named after. The docs state plainly this statement "does not exist in
  Sinclair Basic" - an explicit acknowledgement, in Boriel's own documentation, that `WHILE` is a
  deliberate non-authentic extension, the same judgement `PLAN.md`'s downgrade rationale already
  reaches independently for BazLang. Separately, `DO`/`LOOP` supports the condition on either end,
  either polarity, matching SAM Coupé's/Beta BASIC's shape exactly:

  ```basic
  DO UNTIL <condition>          DO WHILE <condition>
      [<sentences>]                 [<sentences>]
  LOOP                           LOOP

  DO                              DO
      [<sentences>]                   [<sentences>]
  LOOP UNTIL <condition>          LOOP WHILE <condition>
  ```

  The docs note explicitly: "No `REPEAT` keyword exists in this dialect" - Boriel's `DO`/`LOOP`
  fully replaces it, the same choice SAM Coupé and Beta BASIC made (see Open questions for whether
  that's homage or convergence).

### SpecBAS (see `0006`)

- <https://sites.google.com/site/pauldunn/home/manual> - `DO`/`LOOP`, condition on either end,
  either polarity - the same four-way shape as SAM Coupé/Beta BASIC/Boriel:

  ```basic
  DO WHILE numexpr        DO
    statements               statements
  LOOP                     LOOP UNTIL numexpr
  ```

  `EXIT` breaks out early from anywhere in the body, matching the same warn-off-`GOTO` convention
  SAM Coupé's and NextBASIC's manuals both state explicitly (see Evidence above). No `REPEAT`
  keyword in this dialect either.

### Mallard BASIC (not Sinclair-heritage - the real `WEND` source, see `0006`)

- <https://archive.org/details/Mallard-BASIC> - "Mallard BASIC: Introduction and Reference"
  (Locomotive Software, 2nd edition 1987/1989). The item's actual filename drops the hyphen from the
  display title (`MallardBASIC_djvu.txt`, not `Mallard-BASIC_djvu.txt`) - check
  `archive.org/metadata/` if a guessed URL 404s. Confirms the manual's own words: "The `WHILE` loop
  is started by the command `WHILE` and ended by the command `WEND`." No `REPEAT` or `DO`/`LOOP`
  keyword appears anywhere in the full text. A CP/M-general BASIC from Locomotive Software, bundled
  with the Spectrum +3's CP/M Plus option as a wholly separate boot path from ordinary Spectrum
  BASIC - not derived from Sinclair BASIC's codebase, and not descended from or related to
  Locomotive BASIC (the similarly-named but unrelated Amstrad CPC ROM BASIC) either, beyond sharing
  a publisher.

### YS MegaBasic (see `0006`)

- <https://www.crashonline.org.uk/25/basics.htm> - *CRASH* 25 (1985), "Battle of the Basics".
  "Simple `REPEAT UNTIL` loops are the only new control structure" - no `WHILE` in any form, no
  `DO`/`LOOP`, no anywhere-in-body exit mechanism documented.

### Acorn Atom BASIC (see `0006`)

- <https://www.theoddys.com/acorn/acorn_system_computers/atom/Atomic%20Theory%20and%20Practice.pdf> -
  the Atom BASIC language manual (227pp - not to be confused with a same-vintage-looking "Technical
  Manual" that turns out to be a hardware/construction guide, see `0006`). Section 5.2, "`DO...UNTIL`
  Loops": "ATOM BASIC provides an alternative pair of loop-control statements: `DO` and `UNTIL`. The
  `UNTIL` statement is followed by a condition, and everything between the `DO` statement and the
  `UNTIL` statement is repeatedly executed until the condition becomes true." Worked examples confirm
  both a same-line form (`DO PRINT "ATOM-"; UNTIL 0`) and a multi-line indented form:

  ```basic
  10 I=0
  30 I=I+1
  40 PRINT "!"
  50 UNTIL I=256
  60 END
  ```

  and that a statement may directly follow `DO` on its own line (`20 DO INPUT J`). Bottom-tested
  only - no `WHILE` variant, no `LOOP` terminator keyword at all, condition only ever on `UNTIL`.
  The manual's own reserved-word list (an early, possibly non-exhaustive subset: `BPUT, CLEAR, DIM,
  DO, DRAW, END, FOR, GOSUB, GOTO, IF, INPUT, LET, LINK, MOVE, NEXT, OLD, PLOT, PRINT, PUT, REM,
  RETURN, RUN, SAVE, SGET, SHUT, SPUT, UNTIL, WAIT`) includes `DO`/`UNTIL` but no `REPEAT`, `WHILE`,
  `PROC`, or `FN` - consistent with `0006`'s finding that Atom BASIC has no procedures or
  user-defined functions.

### Pascalated ZX BASIC (see `0006`)

- <https://arcalusitana.org/MuseuZX/Pascalated_ZXBASIC/> - the page's raw HTML (`WebFetch`'s
  markdown conversion of this page misses the per-lesson `<textarea>` content - see `0006`'s
  technique notes). `REPEAT`/`UNTIL` are **not** a classic BBC/COMAL-style independent pair - they're
  literal `#define REPEAT DO` / `#define UNTIL LOOP UNTIL` macros feeding Boriel's own compiler, so
  `REPEAT...UNTIL condition` *is*, after expansion, Boriel's `DO...LOOP UNTIL condition` (shape 1
  above). A comment in a second lesson's source, `'#define WHILE WHILE ' already defined`, confirms
  `WHILE`/`END WHILE` has no macro at all - it's Boriel's own native keyword, unchanged, not a
  Pascalated-specific spelling.

### Laser BASIC (confirmed to add nothing here - see `0006`)

- <https://spectrumcomputing.co.uk/pub/sinclair/games-info/l/LaserBasic.txt> - Laser BASIC's own
  shipped instructions: "Laser Basic does not introduce `WHILE`, `REPEAT`, or `DO` loops - only
  traditional `FOR`/`NEXT` structures from Sinclair BASIC are available."

## Dead ends

None specific to this note - fetch-technique dead ends (PDF extraction, 403s, and so on) are
recorded once, centrally, in `0006-related-basic-dialects-overview.md`'s "Fetch technique notes".

## Open questions

- Is Boriel ZX BASIC's `DO`/`LOOP` (replacing `REPEAT` entirely, matching SAM Coupé/Beta BASIC) a
  conscious homage to that Sinclair-heritage lineage, or an independent convergence from the wider
  BASIC-family `DO`/`LOOP` convention (Visual Basic, etc.)? Boriel's own docs don't credit a source
  for it the way SAM Coupé's manual credits Beta BASIC for its `DEF PROC` examples. Beta BASIC's own
  manual predates both with the same shape, and Acorn Atom BASIC's `DO...UNTIL` predates Beta BASIC
  too, so the `DO` keyword itself may go back further than any single credited lineage in this
  survey.
- Does Pascalated ZX BASIC's `REPEAT`/`UNTIL` support early exit from anywhere in the body? It
  compiles straight to Boriel's own `DO`/`LOOP`, and Boriel's own `DO`/`LOOP` docs don't mention one
  either (see `0002`'s Boriel subsection), so the answer is most likely just "whatever Boriel itself
  supports" - not independently confirmed here.
