# How do Sinclair-family BASICs (and COMAL) implement `IF...THEN...ELSE`, single-line and block?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high - primary manual text was read directly for all dialects covered here except
Pascalated ZX BASIC, sourced from one real worked example program rather than a full manual. See
`0006-related-basic-dialects-overview.md` for the wider dialect genealogy.

## Finding

Every dialect surveyed that has `ELSE` at all (all seven core dialects do, plus SpecBAS) draws the
same basic distinction `PLAN.md`'s own item anticipates: a same-line, colon-joined `ELSE` that needs
no terminator, and - in most, but not all, cases - a genuine block form that does. Where they differ
is *how* the two forms are told apart (where a block form exists at all), whether the block form
truly nests, and what the terminator is spelled.

**Three dialects have no real block form whatsoever - one of them is Beta BASIC itself, the dialect
every other Sinclair-heritage `PROC`/structured-programming extension in this survey either directly
credits or echoes.** Beta BASIC's colon-joined `ELSE` can only ever appear on the same logical
program line as its `IF`. Its manual states the complete rule: "the statement is part of the
`IF`-`THEN`-`ELSE` structure. If the condition after `IF` is false, the statements after `THEN` are
not executed, but continue after the nearest `ELSE`. Before `ELSE` there must be `:`" - and nothing
else. No `ENDIF`/`END IF` keyword exists anywhere in the manual. What secondary sources describe as
"multiline `IF THEN ELSE`" is a display artifact: Beta BASIC's `LIST FORMAT 2` editor mode
auto-indents a single logical, colon-joined line across several screen lines - the same indentation
it applies to `FOR` loops and `DEF PROC`/`END PROC` bodies - not a genuine block construct. One
further quirk: "If `ELSE` was used without `IF`, it functions as `REM`" - a stray, unmatched `ELSE`
is simply treated as a comment rather than an error. Mallard BASIC - not Sinclair-heritage at all
(see `0006`) - independently confirms the identical single-line-only shape, and its own manual
states the rule explicitly: "the whole structure of `IF...THEN...ELSE` must be on a single program
line (though this can flow onto several screen lines)" - the same "program line" vs. "screen line"
distinction Beta BASIC's manual draws, in a completely unrelated dialect. **SpecBAS - a modern
dialect, decades later, with no direct lineage to either - makes the identical choice a third
time**: single-line `IF...THEN...ELSE[ENDIF]` only, no separate block form, nested `IF`s written by
colon-chaining (which is what produces its documented `ELSE ELSE` idiom, see below). So the family's
most influential structured-programming pioneer, its most business-oriented and least structured
relative, and its newest reimplementation all land in the same place on this one specific question -
and every dialect that *does* build a real nested block `IF` (SAM Coupé, NextBASIC, BBC, QL
SuperBASIC, COMAL, Boriel) added one on top of that same single-line starting point, not something
Sinclair-heritage BASIC arrives at by default.

**How short-form and long-form are distinguished** splits into two approaches:

- **Presence of `THEN` decides it** (SAM Coupé BASIC, NextBASIC). If the line has `THEN`, it's the
  short form and `ELSE`/`ELSE IF` must be colon-joined on the same physical line; if the `IF` has no
  `THEN` at all, it's the long form, terminated by `END IF` on its own line. Both manuals state this
  rule explicitly and in near-identical wording, down to the exact phrase "must each be the first
  statement of a line" for `ELSE`/`ELSE IF`/`END IF` - one more data point (alongside SAM Coupé's
  credited borrowing from Beta BASIC documented in `0002-def-proc-across-sinclair-basics.md`)
  suggesting a shared design lineage between these two Spectrum-descended dialects' structured
  extensions, though neither manual states this explicitly for `IF` the way SAM's manual does for
  `DEF PROC`.
- **Line position decides it, `THEN` stays optional either way** (BBC BASIC, QL SuperBASIC, Boriel
  ZX BASIC). If `THEN` is the *last* token on the line (nothing after it, not even a comment), the
  parser commits to a block `IF` and expects `ENDIF`/`END IF` later; otherwise it's a same-line
  form, with `THEN` itself optional or replaceable by a colon in most of these.

**Whether the block form truly nests** is a genuine split, not just wording:

- SAM Coupé's and NextBASIC's manuals both note explicitly that their short-form `ELSE IF` chaining
  is **not real nesting** - "there is no marker to indicate the end of an `IF`... execution skips to
  the code following the next `ELSE`" - it's a flat scan for the next `ELSE`/`ELSE IF` on the same
  line, which happens to behave like nesting for a simple chain but isn't structurally one. Their
  *long*-form `IF...ELSE IF...END IF`, by contrast, is explicitly "properly-nested".
- BBC BASIC, QL SuperBASIC, COMAL, and Boriel ZX BASIC's block forms are all real nesting - an `IF`
  block can contain another complete `IF...ENDIF` inside it, at any depth, each closed by its own
  terminator.

**Terminator spelling** is scattered across all four plausible options, no two dialects agreeing:

| Dialect | Terminator | One word or two? |
| --- | --- | --- |
| BBC BASIC | `ENDIF` | one - the manual states "BBC BASIC won't accept `END IF` as two separate words" |
| SAM Coupé BASIC | `END IF` | two |
| NextBASIC | `ENDIF` | one |
| QL SuperBASIC | `END IF` | two |
| COMAL | `ENDIF` | one |
| Boriel ZX BASIC | `END IF` | two (optional entirely on single-line forms since v1.8+) |
| Beta BASIC | *(none - no block form)* | n/a |
| Mallard BASIC | *(none - no block form)* | n/a |
| SpecBAS | `ENDIF` | one, and optional even on the single-line form |
| Pascalated ZX BASIC | `END IF` | two |

**Chained `ELSE IF`** is universal across every dialect that has a block form at all, but the
keyword itself has three spellings: `ELSE IF` (two words: BBC, SAM Coupé, NextBASIC), `ELSEIF` (one
word: Boriel ZX BASIC, which explicitly prefers it over manually nested `IF`s "for cleaner code",
and Pascalated ZX BASIC, unsurprisingly given it compiles through Boriel), and `ELIF` (COMAL). QL
SuperBASIC has none at all - the official QL User Guide documents `IF`/`ELSE` explicitly as a
**two-way** decision only, with a completely separate `SELect ON var / ON var = value ... /
END SELect` construct - the same `CASE`/`WHEN` idea COMAL's `CASE...ENDCASE` already models in this
survey - as the recommended way to handle three or more branches. So a QL SuperBASIC "else-if chain"
doesn't idiomatically exist at all, not because `IF` can't nest (it can, freely, per the worked
examples read - a bare `ELSE` containing a complete nested `IF...END IF`), but because the manual
steers a 3+-way decision to `SELect` from the start.

SpecBAS's manual documents a genuinely new quirk none of the seven core dialects have: an `IF` with
no `ELSE` nested inside an `IF` that *does* have one needs **`ELSE ELSE`** to skip the inner `IF`'s
absent `ELSE` and reach the outer one -
`IF a=1 THEN PRINT "A is 1": IF b=2 THEN PRINT "B is 2" ELSE ELSE PRINT "A is not 1"` - the manual's
own words: "If a child-condition doesn't need an `ELSE`, but the parent `IF` does, then `ELSE ELSE`
is permitted." This is a direct, visible consequence of colon-chaining `IF`s inline rather than
using a real block form with its own terminator per `IF` - a cost none of BBC/QL/COMAL/Boriel's
real-nesting block forms pay, since each nested `IF` there closes with its own `ENDIF`/`END IF`
regardless of whether it has an `ELSE`.

**Implication for BazLang:** `PLAN.md`'s own downgrade rationale for this item ("Sinclair BASIC's
`IF`/`THEN` has no `ELSE` clause at all") holds up completely - none of the seven surveyed dialects'
`ELSE` is inherited from Sinclair BASIC itself; every one of them added it as an extension, the same
judgement `PLAN.md` already reaches independently. The two-tier design `PLAN.md` sketches (single-
line `ELSE` first, block `IF` as a larger follow-on step) matches the shape practically every
dialect here converged on independently. The one genuinely open design choice this survey
surfaces is SAM Coupé's/NextBASIC's "is there a `THEN`" rule for telling short-form from long-form
apart, versus BBC's/QL's/Boriel's "is `THEN` the last token on the line" rule - the former needs no
`THEN` at all in the long form, which reads slightly oddly next to BazLang's existing single-line
`IF...THEN`; the latter keeps `THEN` meaningful in both forms, matching how it's used everywhere
else in the language today.

## Evidence

### BBC BASIC

- <https://www.bbcbasic.co.uk/bbcwin/tutorial/chapter09.html> - single-line:
  `IF condition THEN statement [ELSE statement]`, `THEN` optional "provided the meaning is clear"
  (`IF Score%>=40 Pass=1 ELSE Pass=0`). Block form:

  ```basic
  IF Salary>1000000 THEN
    BuyYacht=1
  ELSE
    PRINT "Work, work and more work"
  ENDIF
  ```

  Rule for telling the two apart: "When `THEN` is the last statement on a line... BBC BASIC knows
  that it is about to be presented with a multi-line `IF`." `ENDIF` is one word - "BBC BASIC won't
  accept `END IF` as two separate words." `ELSE` must be the first thing on its line; `ENDIF` must
  be the first thing on its line. True nesting, one `ENDIF` per `IF`, optionally one `ELSE`.

### Beta BASIC (Spectrum)

- <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> - the Beta BASIC 3.0 manual
  (Slovak translation; see `0002`'s Evidence for the download route). The `ELSE` keyword entry
  states the complete rule quoted in Finding above; no `ENDIF`/`END IF` keyword exists anywhere in
  the manual. A worked recursion example - the `diamant`/diamond one SAM Coupé's manual credits to
  Beta BASIC (see `0002`) - shows `IF size>4 THEN` followed by four indented recursive calls with no
  `ELSE` and no closing keyword at all:

  ```basic
  110  IF size>4 THEN
          diamant x,y+size,size-diff
          diamant x,y-size,size-diff
          diamant x-size,y,size-diff
          diamant x+size,y,size-diff
  120 END PROC
  ```

  The manual's own footnote on this exact example explains the indentation: "Note: The listing is
  in `LIST FORMAT 2`" - one of Beta BASIC's own listing modes that auto-indents a program's
  structure for readability on screen, mentioned elsewhere in the manual as indenting `FOR`-loops
  and `DEF PROC`-`END PROC` blocks the same way.
- <https://en.wikipedia.org/wiki/Beta_BASIC> and the CRASH 43 review
  (<https://www.crashonline.org.uk/43/betabasic.htm>) both describe a "multiline
  `IF...THEN...ELSE`" that can be nested - technically accurate in the sense the primary manual
  above clarifies (the *display* spans multiple lines), but neither gives the exact syntax, which
  turns out not to include a terminator at all.
- The Beta BASIC 4.0 supplement (see `0006`'s "Fetch technique notes") independently shows the same
  shape - an `IF...THEN` followed by several indented statements and a colon-joined `ELSE`, no
  terminator visible.

### SAM Coupé BASIC

- <https://sam.speccy.cz/basic/sam-basic_complete_guide.pdf> - short form: `ELSE` can be used to
  deal with false conditions, colon-joined: `IF a=5 THEN ZAP: ELSE STOP`, chainable with `ELSE IF`
  acting "as a kind of OR". Long form, no `THEN` at all - that absence is what selects it:

  ```basic
  10 IF A$="hamster"
  15   PAUSE 50
  20   POW
  25 ELSE IF a$="bird"
  ...
  85 END IF
  ```

  The manual is explicit that the short form's `ELSE IF` chaining is **not true nesting**: "there is
  no marker to indicate the end of an `IF`. When any `IF` condition fails, execution skips to the
  code following the next `ELSE` statement within the same line" - it's a flat scan, not a stack.
  The long form, by contrast, is explicitly "properly-nested... unlike the single-line
  `IF..THEN..ELSE`". `IF`, `ELSE`, and `END IF` "must each be the first statement of a line".

### NextBASIC / SpecNext

- <https://element.zxfiles.net/DOCS/OTHER/NEXTBAS.PDF> - near-identical design to SAM Coupé's, down
  to matching phrasing. Short form: `ELSE` "must be on the same line as the `IF`, and preceded by a
  colon", nestable-looking but explicitly not real nesting: "this is not 'true' nesting since there
  is no marker to indicate the end of an `IF`. When any `IF` condition fails, execution skips to the
  code following the next `ELSE` statement within the same line" (see Open questions on whether the
  near-identical wording to SAM Coupé's manual is borrowing or coincidence), e.g.:

  ```basic
  10 IF x=0 THEN PRINT "null":BEEP 1,0:ELSE IF x=1 THEN PRINT "one":BEEP 1,1:ELSE PRINT "x was ";x
  ```

  Long form: "No `THEN` statement is used in the `IF` (this is what determines whether it is a
  short-form or long-form `IF`)", real nesting this time, `ELSE IF` chains permitted:

  ```basic
  100 IF x>7:PRINT "x>7"
  105     IF x>1000:PRINT "In fact it's huge"
  110   ELSE PRINT "But not too big"
  115   ENDIF
  120 ELSE IF x>3:PRINT "x>3 but x<=7"
  140 ELSE IF x=3:PRINT "x=3"
  160 ELSE
  170   PRINT "x is too small to bother with"
  180 ENDIF
  ```

  `ENDIF` is one word here (unlike SAM's `END IF`) - confirmed both from this worked example and
  from the manual's own error-message text ("No `ENDIF`") and byte-code token table (`ENDIF $84`,
  distinct from a separate `IFELSE $83` token the manual notes is "internal use only: displays as
  `IF`, but indicates `ELSE` is present on same line" - a genuine implementation detail, not
  user-facing syntax).

### Sinclair QL SuperBASIC

- <https://superbasic-manual.readthedocs.io/en/latest/I/if.html> - inline form:
  `IF condition {THEN|:} statement[:statement] [:ELSE statement[:statement]]` - `THEN` replaceable
  by a colon or omitted entirely. Multi-line form:
  `IF condition [{THEN|:}] [:statement]... [ELSE] [:statement]... END IF` - `THEN` may be omitted
  here too; a colon must precede `ELSE`, and the manual advises (not requires) one after it too.
  Worked example:

  ```basic
  100 REMark Long form IF...ELSE...END IF
  110 LET sunny = RND(0 TO 1)
  120 IF sunny THEN
  130   PRINT 'Wear sunglasses'
  140   PRINT 'Go for walk'
  150 ELSE
  160   PRINT 'Wear coat'
  170   PRINT 'Go to cinema'
  180 END IF
  ```

  `END IF` is two words. The manual separately warns of real interpreter bugs on ROM versions before
  Minerva v1.92 involving inline `IF`s with `ELSE`, and that inline `IF...ELSE...END IF` can
  misbehave with a following `GOSUB` - implementation quirks, not language design, but notable as
  the kind of thing `docs/quirks.md` exists to record if BazLang ever needs the equivalent.
- <https://archive.org/details/SinclairQLHomepage> (the official *Sinclair QL User Guide*'s
  programming section - the manual mirror above is a modern fan-maintained one; this is the primary
  official source it's based on). Every worked multi-branch example in this Guide's own
  programming-techniques chapter uses a **bare `ELSE` containing a complete nested `IF...END IF`**,
  never a chained keyword - and the text says why: "SuperBASIC offers extensions of this structure
  and **a completely new one** for handling situations with more than two alternative courses of
  action" - that new structure is `SELect ON var / ON var = value / ... / END SELect` (see `0002`'s
  and `0004`'s COMAL findings for the same "add a separate `CASE`-style construct rather than chain
  `IF`" choice there).

### COMAL

- <https://dn760101.eu.archive.org/0/items/COMAL_Reference_Guide/COMAL_Reference_Guide_djvu.txt> -
  the 1984 *COMAL Reference Guide*. Real, chainable, properly-nested `IF`:

  ```text
  IF <logical expression> [THEN]
    <statement list>
  {ELIF <logical expression> [THEN]
    <statement list>}
  [ELSE
    <statement list>]
  ENDIF
  ```

  plus a short form with no `ENDIF` at all: `IF expr THEN statement`. Worked example:

  ```comal
  IF D>0 THEN
    PRINT "TWO REAL ROOTS:"
    PRINT "X1 = ", (-B+SQR(D))/2/A
  ELIF D=0 THEN
    PRINT "ONE REAL ROOT:"
    PRINT "X = ",-B/2/A
  ELSE
    PRINT "NO REAL ROOTS."
  ENDIF
  ```

  `ELIF` is real COMAL syntax, confirmed against this primary guide (see Dead ends for a fabricated
  example that had attached itself to the same, correct, keyword claim elsewhere).

### Boriel ZX BASIC

- <https://raw.githubusercontent.com/boriel/zxbasic/main/docs/if.md> - single-line:
  `IF expression [THEN] sentences [: END IF]` - as of v1.8+, the trailing `END IF` is optional on a
  single-line statement at all (`IF a < 5 THEN PRINT "..." ELSE PRINT "..."` needs no terminator).
  Block form:

  ```basic
  If a < 5 Then
      Print "A is less than five"
      a = a + 5
  Else
      Print "A is greater than five"
  End If
  ```

  `THEN` optional but recommended for readability in both forms. `ELSEIF` (one word) is the
  documented chaining keyword, explicitly preferred over manually nested `IF`s "for cleaner code".
  `END IF` is two words. Real nesting, each nested `IF` needing its own `END IF`. A real Pascalated
  ZX BASIC program (see below) uses `ELSE IF` as two words in a chain closed by a single `END IF` -
  the compiler accepts two-word `ELSE IF` as equivalent to the documented one-word `ELSEIF` (a
  single `END IF` closes what would need two if it were genuine `ELSE` containing a nested `IF`), a
  spacing nuance this doc page itself doesn't mention.

### SpecBAS (see `0006`)

- <https://sites.google.com/site/pauldunn/home/manual> - `IF numexpr THEN statement [ELSE statement]
  [ENDIF]` - a single-line form only (no distinct block form with its own multi-statement body was
  found in the pages read), with `ENDIF` itself optional. Nested `IF`s are written by colon-chaining
  another `IF` after `THEN`/`ELSE`, which is what produces the `ELSE ELSE` idiom described in
  Finding above.

### Mallard BASIC (not Sinclair-heritage - see `0006`)

- <https://archive.org/details/Mallard-BASIC> - full text (see `0004`'s Evidence for the exact
  item-filename gotcha). The manual's own words, quoted in full: "Note that, as for `IF...THEN`, the
  whole structure of `IF...THEN...ELSE` must be on a single program line (though this can flow onto
  several screen lines)." Confirms `IF...THEN...ELSE`, nestable via colon-chained `ELSE`, but
  single-line only - no block form, no `ENDIF`/`END IF` at all.

### Pascalated ZX BASIC (see `0006`)

- <https://arcalusitana.org/MuseuZX/Pascalated_ZXBASIC/> - the page's raw HTML (see `0006`'s
  technique notes for why `WebFetch`'s markdown conversion misses the lesson source). No macro
  renames `IF`/`ELSE`/`END IF` at all (unlike `REPEAT`/`UNTIL`/`PROCEDURE`, see `0004`) - it's
  Boriel's own `IF`/`ELSE`/`END IF` passed straight through, confirmed by a real worked chain from
  the source itself:

  ```basic
  IF limit >= 12 THEN
    ...
  ELSE IF limit < 0 THEN
    ...
  ELSE
    ...
  END IF
  ```

  one `END IF` closing all three branches - see the Boriel subsection above for what this confirms
  about two-word `ELSE IF` there.

### Laser BASIC (confirmed to add nothing here - see `0006`)

- <https://spectrumcomputing.co.uk/pub/sinclair/games-info/l/LaserBasic.txt> - "standard Sinclair
  BASIC conditionals are preserved" - unmodified single-line
  `IF condition THEN statement [ELSE statement]`, no extension, consistent with its confirmed lack
  of any loop extension too (see `0004`).

## Dead ends

A search result claimed COMAL's `ELIF` came bundled with a "grade calculator" example that turned
out to be entirely fabricated (see `0006`'s technique notes for the general lesson). The keyword
itself was real; the example wasn't, and has been replaced with the primary guide's own
`D>0`/quadratic-roots example in Evidence above.

## Open questions

- Is NextBASIC's near-identical wording to SAM Coupé's manual (down to the specific "not 'true'
  nesting... no marker to indicate the end of an `IF`" phrasing) evidence of one manual's author
  consciously drawing on the other, a shared unattributed source, or coincidence from both starting
  at the same classic-Sinclair-BASIC baseline and solving the same problem the same way? Unlike the
  SAM Coupé/Beta BASIC `DEF PROC` lineage (which SAM's own manual states outright), no source here
  makes an explicit claim of borrowing between SAM Coupé and NextBASIC.
