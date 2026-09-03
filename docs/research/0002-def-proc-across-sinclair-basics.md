# How do Sinclair-family BASICs (and COMAL) implement `PROC`/`DEF PROC`, and does any of them do local variables without reference parameters?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high - primary manual text was read directly for all nine dialects covered here. See
`0006-related-basic-dialects-overview.md` for the wider dialect genealogy, YS MegaBasic's much
thinner `PROC` mechanism, and Pascalated ZX BASIC, none detailed again here.

## Finding

Every native Sinclair/Acorn-orbit dialect surveyed - Beta BASIC, SAM Coupé BASIC, NextBASIC, QL
SuperBASIC - copies BBC BASIC's model: **global-by-default** scope with an explicit `LOCAL`
statement to opt individual names out, layered under a **reference-parameter mechanism** on top of
ordinary by-value parameters. The reference-parameter spelling varies (BBC's `RETURN name` prefix,
SAM's and NextBASIC's `REF` keyword, COMAL's own `REF`) but the shape - by-value is the default,
by-reference is an explicit opt-in per parameter - is identical across all of them. None treats
"local variables, but no reference parameters at all" as a documented design; it doesn't appear as a
deliberate choice in any manual found here, only as a possible reading of one part of QL SuperBASIC
(below).

Two dialects break from that BBC-derived shape entirely, in opposite directions:

- **Boriel ZX BASIC** drops `DEF PROC` altogether in favour of typed `SUB`/`FUNCTION` with
  `ByVal`/`ByRef` parameter modifiers - a modern redesign, not an extension of `DEF FN`.
- **COMAL** offers a _per-procedure_ scoping choice rather than one fixed default: an ordinary
  `PROC`/`FUNC` shares the same global-by-default visibility as the rest of the family, but adding
  the `CLOSED` keyword to that one procedure's heading flips it specifically to local-by-default
  ("all variables in the procedure will be local", per the primary reference guide - see Evidence),
  needing `IMPORT` to reach a global from inside it. So it's an opt-in inversion available per
  procedure, not a language-wide reversed default.

**QL SuperBASIC is not a "no reference passing" precedent.** The primary manual states plainly that
parameters to both `DEFine PROCedure` and `DEFine FuNction` are passed by reference - indeed, it
explains this is _how_ a `PROC`, which "strictly... cannot return a value", is nonetheless able to
hand one back to the caller. That leaves **no surveyed dialect** with a scalar-by-value-only,
no-reference-mechanism design - QL SuperBASIC goes further than BBC/SAM/NextBASIC/COMAL in the
opposite direction, making reference passing the _only_ mode for named parameters rather than an
opt-in on top of by-value.

A third scoping tier exists beyond "local" and "global": **SAM Coupé BASIC and NextBASIC both have
`PRIVATE`** - a numeric-only variable that behaves like `LOCAL` (invisible outside the procedure)
but _retains its value between calls_ until explicitly reset with `PRIVATE CLEAR` - a per-procedure
static, useful for counters/accumulators without exposing them.

There is also a real textual lineage between two of the dialects: the SAM Coupé manual's worked
recursion example (`diamond`, drawing a nested diamond pattern) is captioned **"(from the BetaBasic
manual!)"** in the guide itself - direct evidence that SAM BASIC's procedure design was consciously
modelled on Beta BASIC's, not arrived at independently.

**Implication for BazLang:** a `DEF PROC` with an optional `LOCAL` statement but no reference
parameters at all (see the design discussed against `PLAN.md`'s `DEF PROC & local scoping` entry)
sits deliberately outside every precedent surveyed here, rather than reproducing one of them -
value-only parameters with no reference mechanism at all isn't a documented choice in any of the
seven dialects surveyed, not even partially. It stays closest in spirit to `DEF FN`'s own existing
by-value shadowing, just extended from a single expression to a statement body.

## Design axes and where each dialect lands

The full space this survey was mapping against: value vs. reference parameters; scalar vs. array
parameters; local vs. global vs. persistent-local scoping; how parameter scope is opened (bound
automatically vs. declared); and surface syntax (call form, terminator, keyword spelling).

| Dialect                  | Value params                         | Reference params                                                                | Arrays                                                            | Default param values                                                       | Extra locals                                                                            | Persistent local                                            | Default scope                                                                                       | Call syntax                                                                                         | Terminator                                          |
| ------------------------ | ------------------------------------ | ------------------------------------------------------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| **BBC BASIC**            | yes (default)                        | `RETURN name` prefix (value-result)                                             | always by reference                                               | no                                                                         | `LOCAL name`                                                                            | no                                                          | global unless `LOCAL`'d                                                                             | `PROCname(args)` - no keyword, no space                                                             | `ENDPROC`                                           |
| **Beta BASIC**           | yes, no parens in the param list     | `REF` keyword prefix                                                            | passable; by-ref form for arrays specifically not confirmed       | `DEFAULT name=expr`, its own statement in the body (confirmed, not inline) | `LOCAL name`                                                                            | not found                                                   | global unless `LOCAL`'d                                                                             | bare name - no `PROC` keyword; the name becomes a language keyword                                  | `END PROC` (two words)                              |
| **SAM Coupé BASIC**      | yes (default); params are auto-local | `REF` keyword prefix; recommended for arrays/strings, discouraged for numerics  | `name()` array-param syntax                                       | `DEFAULT name=expr`, its own statement inside the body                     | `LOCAL name[,...]` (`=init` optional)                                                   | `PRIVATE name[=init]`, numeric only, `PRIVATE CLEAR` resets | global unless `LOCAL`'d/param                                                                       | bare name - no `PROC` keyword                                                                       | `ENDPROC`                                           |
| **NextBASIC (SpecNext)** | yes (default)                        | `REF` keyword prefix; must pass a bare var/array name; ints/int-arrays excluded | `name()` array-param syntax                                       | inline `name=expr` in the param list                                       | `LOCAL name[,...]`, multiple statements allowed                                         | `PRIVATE name[=expr]`, numeric only, `PRIVATE CLEAR` resets | global unless `LOCAL`'d/param                                                                       | `PROC name(args)` - explicit keyword                                                                | `ENDPROC`, or `ENDPROC = expr,...` to return values |
| **QL SuperBASIC**        | no - always by reference             | always (no by-value option documented)                                          | always by reference                                               | not confirmed                                                              | `LOCal name[,...]`, must be the statement right after `DEFine`                          | not found                                                   | global unless `LOCal`'d                                                                             | `name(args)` in an expression, no `FN`/`PROC` keyword at the call site                              | `END DEFine name`                                   |
| **COMAL**                | yes (default)                        | `REF` keyword prefix, or `REF array(...)`                                       | passable via `REF array(...)`; plain-array-by-value not confirmed | not found                                                                  | none needed inside `CLOSED` - everything is local there; `IMPORT name` reaches a global | not found                                                   | global by default; **local by default only if the procedure adds `CLOSED`** (opt-in, per-procedure) | `[EXEC] name [(args)]` - the `EXEC` keyword itself is optional, "supplied by the system" if omitted | `ENDPROC [name]`                                    |
| **Boriel ZX BASIC**      | yes, `ByVal` (default)               | `ByRef` modifier, per parameter                                                 | not documented in text retrieved                                  | not confirmed                                                              | real lexical scope, no `LOCAL` statement construct                                      | not found                                                   | real function scope, not line-based                                                                 | direct call, no `CALL`/`PROC` keyword                                                               | `END SUB` / `END FUNCTION`                          |
| **SpecBAS** (bonus)      | yes (default)                        | `REF` keyword prefix                                                            | not confirmed                                                     | not confirmed                                                              | `LOCAL name`                                                                            | not found                                                   | global unless `LOCAL`'d                                                                             | `PROC name(args)` - explicit keyword                                                                | `END PROC`                                          |

Cells marked "not confirmed" are gaps in what this note verified, not claims that the feature is
absent - see Open questions.

## Evidence

### BBC BASIC (the common ancestor)

Primary manual text, read directly.

- <https://www.bbcbasic.co.uk/bbcwin/tutorial/chapter16.html> - tutorial chapter, fetched and read
  in full. Confirms: no `CALL` keyword, `PROCname(args)` is itself a statement; `DEF PROC_name(...)`
  / `ENDPROC`; parameters by value unless prefixed `RETURN` (value-result, written back to the
  caller's variable); arrays always pass by reference; `LOCAL name` inside the body for additional
  locals, including `LOCAL name%()` for a local array.
- <http://www.riscos.com/support/developers/bbcbasic/part2/procedures.html> - corroborating primary
  manual page, found via search, not fetched directly; agrees on all points above.

### Beta BASIC (Spectrum)

- <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> - the Beta BASIC 3.0 manual
  (Slovak translation; see `0006`'s "Fetch technique notes" for the download route). Keyword index
  confirms `DEF PROC`, `END PROC`, `LOCAL`, `DEFAULT`, `REF`, `DO`, `LOOP`, `EXIT IF`, `WHILE`,
  `UNTIL`, `ITEM()` as BB03's structured-programming additions - **no `DEF FN` entry appears
  anywhere in this index or manual** (see `0003`). Worked examples confirm the no-parentheses
  `DEF PROC name param1 param2` shape (`DEF PROC dir číslo`, `DEF PROC hallo xkrat`,
  `DEF PROC box x,y,sirka,dlzka`) and `REF` for reference parameters
  (`DEF PROC vymena REF a$,REF b$`). It also contains the original `diamant` recursion example that
  SAM Coupé's manual credits as "(from the BetaBasic manual!)" (see the SAM Coupé subsection below):

  ```basic
  100 DEF PROC diamant x,y,size,diff
      DEFAULT diff=15
      PLOT x,y-size
      DRAW -size,size
      DRAW size,size
      DRAW size,-size
      DRAW -size,-size
  110  IF size>4 THEN
          diamant x,y+size,size-diff
          diamant x,y-size,size-diff
          diamant x-size,y,size-diff
          diamant x+size,y,size-diff
  120 END PROC
  ```

  called via `diamant 128,88,40` - `DEFAULT` is a standalone body statement in the original, not an
  inline clause on the `DEF PROC` line, matching SAM Coupé's adaptation exactly. The manual
  separately warns that using more than one `END PROC` in a single procedure is unreliable ("then
  the computer cannot find the real end of the procedure") - an implementation quirk, not a
  documented feature.

- <https://en.wikipedia.org/wiki/Beta_BASIC> - confirms named procedures/functions with local or
  reference parameters and settable defaults for missing arguments, and one genuine divergence from
  BBC: a named procedure doesn't need a `PROC` prefix at the call site - it becomes a new keyword in
  the language.
- <https://www.crashonline.org.uk/43/betabasic.htm> - CRASH 43 magazine review; confirms the same
  feature list in different words ("local or reference parameters", recursion, arrays passed to
  procedures) but gives no exact syntax.
- <https://worldofspectrum.net/pub/sinclair/games-info/b/BetaBasicV4.0.pdf> - the 4.0 128K
  supplement manual (see `0006`'s "Fetch technique notes" for the download route); superseded here
  by the fuller 3.0 manual above.

### SAM Coupé BASIC

Primary manual text, extracted and read in full.

- <https://sam.speccy.cz/basic/sam-basic_complete_guide.pdf> - "The Complete Guide", 29 pages (see
  `0006`'s "Fetch technique notes" for the extraction route). Confirms in detail:
  `DEFPROC name[(paramlist)]` / `ENDPROC`; called by bare name, no `PROC` keyword (`60 noise`,
  `70 write f$`); non-array parameters are automatically local, no separate `LOCAL` needed for them;
  array parameters are written `name()` in the paramlist; `REF` keyword marks a parameter as
  pass-by-reference (recommended for arrays/strings, discouraged for numerics "which are faster if
  passed by value"); `DEFAULT name=expr` is its own statement inside the body, creating a variable
  only if it doesn't already exist, so a caller can omit trailing arguments; `LOCAL name1,name2,...`
  for extra locals, restored at `ENDPROC`/`RETURN`; `PRIVATE name[=init]` for a numeric-only
  variable that persists its value across calls (like a static), reset for all privates at once via
  `PRIVATE CLEAR`; a `DATA` parameter lets the proc `READ` a variable-length tail of extra call
  arguments; `DEFFN name(params)=expr` (one word) for single-line functions, described as working
  "similarly to procedures" with local parameters - i.e. the same parameter-shadowing idea BazLang's
  own `DEF FN` already uses, independently arrived at here.

### NextBASIC / SpecNext

Primary manual text, extracted and read in full. Reference parameters use `REF`, matching SAM
Coupé's spelling (not `@`, as sometimes claimed).

- <https://element.zxfiles.net/DOCS/OTHER/NEXTBAS.PDF> - "NextBASIC New Commands and Features", 54
  pages, downloaded and text-extracted with `pypdf`. Confirms: `DEFPROC name(paramlist)` /
  `ENDPROC`; parameters may be numeric/string variables or arrays, numeric/string arrays written
  `name()`; default values written inline in the paramlist as `name=expr` (unlike SAM's separate
  `DEFAULT` statement); `REF` keyword marks a parameter as pass-by-reference, and such a parameter
  must be passed as a bare variable/array name at the call site (integers and integer arrays
  specifically cannot be passed by reference); called with an explicit `PROC name(args)` keyword,
  unlike SAM/Beta's bare-name call; `LOCAL name1,name2,...`, multiple `LOCAL` statements allowed in
  one procedure; `PRIVATE name[=expr]`, numeric only, persists across calls, `PRIVATE CLEAR` resets
  it (identical in spirit to SAM's `PRIVATE`, and the token table confirms it predates NextBASIC's
  own additions - `PRIVATE` is 48K-Spectrum-BASIC-era `$82`, `DEFPROC`/`ENDPROC`/`PROC`/`LOCAL` are
  the newer `$91`-`$94`); a trailing `DATA` parameter lets a proc `READ` a variable-length tail of
  extra call arguments, mirroring SAM exactly; procedures can return multiple values via
  `ENDPROC = expr1, expr2, ...` paired with `PROC name(args) TO paramlist` at the call site - a
  capability none of the other dialects surveyed document.
- <https://wiki.specnext.dev/NextBASIC> - fetched directly; only lists `DEFPROC`/`ENDPROC`/`PROC` as
  tokens, no syntax body was present in what was retrieved. Superseded by the PDF above.

### Sinclair QL SuperBASIC

Sources disagree on whether parameters are by value or by reference; the primary manual wins.

- <https://superbasic-manual.readthedocs.io/en/latest/D/define--procedure.html> and
  <https://superbasic-manual.readthedocs.io/en/latest/D/define--function.html> - a maintained mirror
  of the official _SBASIC/SuperBASIC Reference Manual_, fetched directly. States plainly that
  parameters are passed **by reference**: "calling parameters should not appear in brackets after
  the name (unless you intend to pass them otherwise than by reference!)", and that this is _why_ a
  `PROCedure` - which "strictly... cannot return a value" - can hand one back to the caller anyway.
  Full syntax:

  ```text
  DEFine PROCedure name [(item[,itemi])] [LOCal var[,vari]] [statements] [RETurn] END DEFine [name]
  DEFine FuNction name[$|%] [(item[,itemi])] [LOCal var[,vari]] [statements] RETurn value END DEFine [name]
  ```

  (a one-line form also exists, ending `:RETurn value` on the same line as the header). `LOCal` must
  be the statement immediately after the header or it errors. Missing parameters default to
  `0`/empty string; extra ones are ignored. A function's `RETurn` is mandatory - falling through to
  `END DEFine` without one is error `-17`. Recursion is allowed to 32,767 levels but "extremely
  memory-intensive; should be avoided". Calling one is by bare `name(args)` inside an expression -
  no `FN` or `PROC` keyword at the call site, matching Beta BASIC's and SAM Coupé's "the name
  becomes a keyword" pattern rather than BBC's `PROCname`/NextBASIC's `PROC name`/`FN name` split.

- <https://qlforum.co.uk/viewtopic.php?t=2199> - a forum thread claiming "scalars are by value
  only", contradicted by the primary manual above. Possible reconciliation, unconfirmed: the forum
  poster may have been describing observed behaviour for simple read-only use (a parameter that's
  never assigned inside the proc looks exactly like by-value from the caller's side), or describing
  a different BASIC entirely (the same thread mentions "SBasic" alongside SuperBASIC as a second,
  related dialect).
- <https://qlwiki.theqlforum.com/doku.php?id=qlwiki:superbasic> - agrees with the manual on the
  `DEFine PROCedure`/`DEFine FuNction`/`LOCal`/`END DEFine` shape, silent on value-vs-reference.
- <https://en.wikipedia.org/wiki/SuperBASIC> - corroborates the same shape with a worked
  `DEFine FN Iso(S,O)` example, no new detail on reference passing.
- <https://superbasic-manual.readthedocs.io/en/latest/R/repeat.html> and
  <https://superbasic-manual.readthedocs.io/en/latest/I/if.html> - the same manual mirror's pages
  for `REPeat` and `IF`, used in the sibling research notes on loops and conditionals.

### COMAL

Scoping is a per-procedure choice rather than a fixed default. Two independent primary sources cover
different points in COMAL's version history and are cited together below.

- <https://archive.org/details/COMAL_Reference_Guide> (Borge R. Christensen, 1984, "COMAL Reference
  Guide") - the 1984 standard. Full text via the `_djvu.txt` route (see `0006`'s "Fetch technique
  notes"). Exact syntax: `PROC id [(paramlist)] [CLOSED] ... ENDPROC [id]`, parameters as
  `[REF] variable` or `REF array(...)`; "If the keyword `CLOSED` terminates the procedure heading,
  all variables in the procedure will be local" - `CLOSED` is an opt-in per-procedure switch, not a
  language-wide default; a procedure without it behaves like the rest of the family (global-visible
  unless a name is a parameter). `FUNC id [(paramlist)] [CLOSED] ... ENDFUNC [id]`, mandatory
  `RETURN expr` (worked `DISTANCE` example included). Also documents
  `FOR var:=init TO final [STEP n] DO ... ENDFOR [var]`, `WHILE expr [DO] ... ENDWHILE`,
  `REPEAT ... UNTIL expr`, and `CASE selector [OF] {WHEN choicelist ...} [OTHERWISE ...] ENDCASE`.
  Has no `LOOP`/`ENDLOOP` construct - it's a later addition, documented below.
- <https://archive.org/details/COMAL_Handbook_1983_Reston_Publishing> (Len Lindsay, 1983, Reston
  Publishing) - COMAL 80 on the Commodore 64, versions 0.11-1.02. Call syntax:
  `[EXEC] procedure-name [(actual-parameter-list)]` - **`EXEC` itself is optional** ("if omitted,
  will be supplied by the system"), so in practice a call reads as a bare `name(args)`, the same
  shape QL SuperBASIC's and Beta BASIC's/SAM Coupé's calls have, with `EXEC` as the underlying,
  normally-hidden token. `LOOP ... [EXIT|EXITIF condition] ... ENDLOOP` is documented in full,
  explicitly marked `COMAL STANDARD: [NO]`, absent from version 0.12, added as "a last minute
  addition to version 1.02" - real, but added after the 1984 standard guide above, which is why that
  guide doesn't mention it.
- <https://en.wikipedia.org/wiki/COMAL> - confirms `PROC...ENDPROC` plus an `EXECUTE` statement to
  call a procedure (in early COMAL versions), and block structure generally.
- <https://www.c64-wiki.com/wiki/Comal> - a real example agreeing with the guide:
  `PROC set'cursor(x#,y#) CLOSED ... ENDPROC set'cursor`, and `REF` for reference parameters, e.g.
  `PROC set'color(REF color#)`.

### Boriel ZX BASIC (the outlier - no `DEF PROC` at all)

Primary docs, fetched directly.

- <https://github.com/boriel/zxbasic/blob/main/docs/sub.md> - raw markdown, fetched.
  `SUB name [(paramlist)] ... END SUB`, invoked directly (not via `GOSUB`), no return value,
  `RETURN` exits early.
- <https://github.com/boriel/zxbasic/blob/main/docs/function.md> - raw markdown, fetched.
  `FUNCTION name [(paramlist)] [AS type] ... END FUNCTION`; untyped parameters and untyped return
  default to `Float`; `ByVal`/`ByRef` exist as parameter modifiers (referenced in the doc's "See
  Also" but not spelled out in the body text retrieved). Legacy `DEF FN` is mentioned only as
  history - Boriel presents `SUB`/`FUNCTION` as new, not as an extension of it.

### SpecBAS (see `0006`)

- <https://sites.google.com/site/pauldunn/home/manual> - the SpecBAS Reference Manual, fetched
  directly. `DEF PROC name[(params)] ... END PROC`, called `PROC name(args)` - practically identical
  in shape to SAM Coupé's and NextBASIC's, down to the `REF` spelling for reference parameters
  (`DEF PROC myproc(REF a, b)`) and a separate `LOCAL` statement for extra locals, restored on exit.
  `END PROC` is two words, matching Beta BASIC's confirmed two-word spelling rather than SAM
  Coupé's/NextBASIC's one-word `ENDPROC`. See `0003` for SpecBAS's more novel contribution - a
  distinct `CALL` construct, not `DEF PROC` itself, for "a procedure that returns a value".

### Laser BASIC (see `0006`)

- <https://spectrumcomputing.co.uk/pub/sinclair/games-info/l/LaserBasic.txt> - Laser BASIC's own
  shipped instructions, fetched directly. The one dialect surveyed that builds its procedure
  mechanism by **hijacking the existing `DEF FN`/`FN` tokens** instead of adding new `PROC`-family
  keywords: `DEF FN NAME#(params) ... .RETN`, called via `.PROCFN NAME#(args)`. Up to 52 procedures,
  single-letter (optionally `$`-suffixed) names only - the same naming ceiling classic Sinclair
  `DEF FN` itself has, confirming this really is built on that mechanism rather than a fresh one.

## Dead ends

Fetch-technique dead ends from this note's research (PDF extraction, 403s, and so on) are recorded
once, centrally, in `0006-related-basic-dialects-overview.md`'s "Fetch technique notes" rather than
repeated per topic note.

## Open questions

- Does the qlforum.co.uk forum thread's "scalars by value" claim describe a different QL BASIC
  dialect (the thread also mentions "SBasic"), a specific case that merely looks like by-value, or
  is it simply mistaken? Not resolved - see the reconciliation note in Evidence.
- Is there a Sinclair-adjacent dialect not covered here (e.g. MasterBASIC on the SAM Coupé as an
  alternative to SAM BASIC itself, or a later Beta BASIC revision than 3.0) that documents "local
  variables, no reference parameters" as a deliberate choice rather than this project inferring it
  as a gap? Not searched for specifically (Mallard BASIC, see `0006`, has no `PROC`-equivalent at
  all, so it isn't a candidate).
- Does Beta BASIC's `REF` extend to array parameters specifically (the way SAM Coupé's and
  NextBASIC's manuals both explicitly recommend `REF` for arrays), or only to scalars? Not confirmed
  from the 3.0 manual text read here.
