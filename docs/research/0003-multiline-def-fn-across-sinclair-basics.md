# Do any Sinclair-family BASICs (or COMAL) extend `DEF FN` past a single-line/inlined expression, and how?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high - primary manual text was read directly for all seven core dialects surveyed,
plus SpecBAS. See `0006-related-basic-dialects-overview.md` for the wider dialect genealogy this
and the other topic notes share.

## Finding

Genuine multi-line, statement-bodied `DEF FN` - as opposed to `DEF PROC`'s statement body - is rare.
Of the seven core dialects surveyed, only **three** actually extend `FN`'s body past a single
expression:

- **QL SuperBASIC**'s `DEFine FuNction` is fully multi-line, with `LOCal` and a mandatory `RETurn`,
  ended by `END DEFine`. It reuses the exact same statement-bodied shape as `DEFine PROCedure`.
- **COMAL**'s `FUNC...ENDFUNC` is likewise a full statement body, ended by a mandatory `RETURN expr`
  (falling through to `ENDFUNC` without one is a runtime error), and can optionally be `CLOSED` for
  local-by-default scoping, exactly mirroring `PROC`.
- **Boriel ZX BASIC**'s `FUNCTION...END FUNCTION` is the same shape again, with the added twist of a
  static return type (`AS type`, defaulting to `Float`) and `RETURN` usable multiple times as an
  early exit, unlike QL/COMAL's single mandatory return point.

All three of these achieve it the same way conceptually: **`FN` is just `PROC` with a mandatory
return value**, not a separate mechanism. The header differs (a return type or `RETurn`
requirement), but the body, scoping, and parameter rules are identical to that dialect's own
procedure construct - confirmed explicitly in two of the three manuals ("Parameters and items are
treated in the same manner as with DEFine FuNction", QL SuperBASIC's `DEFine PROCedure` page,
worded from the PROC side of the same equivalence; COMAL's guide gives `FUNC`/`PROC` matching
syntax diagrams differing only in the mandatory `RETURN`).

**A fourth, genuinely different mechanism exists outside the core seven: SpecBAS's `CALL`.** SpecBAS
(a modern, from-scratch Sinclair-BASIC-superset reimplementation - see `0006`) keeps classic
single-line `DEF FN`/`FN` completely unchanged, the same choice SAM Coupé and NextBASIC make below -
but *separately* adds a new keyword, `CALL`, documented as "identical to the `PROC` command, but...
you are able to return a value from the procedure." Rather than a `RETURN`/`RETurn` statement, a
`CALL`-defined procedure gets an **implicit `result`/`result$` variable**, auto-created on entry and
whatever it holds on exit is the returned value - no explicit return statement anywhere in the
body. Of every multi-line-function mechanism found across all eight dialects surveyed (BBC's bare
`=`, QL/COMAL/Boriel's `RETURN`-generalizes-`PROC`, and this), SpecBAS's implicit named-variable
approach is the only one that needs no dedicated return statement at all.

The other four dialects keep `DEF FN` (or `DEFFN`/`DEFPROC`+something) as a **strictly single-line,
single-expression construct**, the original Sinclair shape, even where they went on to build
elaborate multi-line `PROC` systems:

- **SAM Coupé BASIC**'s manual states outright that a multi-line function is achieved by
  *composing several single-line `DEFFN`s* that call each other - not by making `DEFFN` itself
  multi-line. This is a deliberate design statement, not an oversight: the manual explicitly frames
  it as "the way of getting a function to be made up of several lines".
- **NextBASIC** extended `DEF FN` with `REF` parameters and recursion, but the manual states
  plainly it remains a single `=expr` construct, and separately notes it doesn't support
  user-defined integer (`%`) functions at all - multi-value/multi-statement "function-like" work is
  done with `DEFPROC`'s `ENDPROC = expr,...` multi-return mechanism instead, a genuinely different
  route to the same end.
- **BBC BASIC** is the interesting middle case: `DEF FN` *can* be multi-line, but not via a distinct
  `ENDFN` keyword - a bare line starting with `=` is what ends the function and supplies its return
  value, reusing the same "restore `LOCAL`s and return" machinery `ENDPROC` uses. Multiple such
  `=expr` lines can act as multiple early-exit points, though the manual recommends using one exit
  via a result variable instead, for maintainability.
- **Beta BASIC** keeps `DEF FN` unextended too, joining SAM Coupé and NextBASIC. Its 3.0 manual
  documents every one of its own extensions individually in its keyword index (`DEF PROC`,
  `DEF KEY`, `DEFAULT`, ...), and `DEF FN` simply isn't in it - the only mention of `DEF FN` in the
  whole manual is a passing comparison, not a syntax description. Absence from an index isn't
  absolute proof (see Open questions), but a manual this thorough about its own extensions would be
  expected to document a multi-line `DEF FN` explicitly, the way it documents `DEF PROC`, if it had
  one.

**Implication for BazLang:** the `DEF FN`-shaped `DEF PROC` design already discussed against
`PLAN.md` (value-only params shadowing globals, no reference parameters) runs the comparison in
reverse from every extended dialect here. QL SuperBASIC, COMAL, and Boriel all built their
multi-line function by *generalizing PROC to require a return value*; SpecBAS built a parallel but
separate `CALL` construct to do the same job by implicit variable rather than statement. The design
under discussion for BazLang instead proposes *generalizing FN's existing value-only shadowing to a
statement body, without adding a return value* (since a `DEF PROC` isn't a function). SAM Coupé's
and NextBASIC's explicit choice to leave `DEFFN`/`DEF FN` single-expression and compose or use
`PROC`'s own multi-return mechanism instead is the clearest existing precedent for keeping `FN` and
`PROC` as genuinely separate mechanisms rather than unifying them - which is the same choice
BazLang's design already makes.

## Evidence

### BBC BASIC

- <https://www.bbcbasic.co.uk/bbcwin/tutorial/chapter17.html> - `DEF FN_name(params)`, no `ENDFN`; a
  bare `=expr` line (anywhere in the body) both ends the function and supplies its return value,
  e.g.:

  ```basic
  DEF FN_Square(Num)
  =Num^2
  ```

  `LOCAL` works identically to `PROC`'s, restored at the `=` line. Worked example with a `LOCAL`
  and an `IF...ELSE...ENDIF` computing a boolean result before the final `=Result%`. The guide
  advises "only have one exit point" - i.e. prefer a single trailing `=` over several conditional
  ones - as a style recommendation, not a language restriction. Functions return only a single
  scalar value, not arrays or structures. Multi-line functions must be placed where they won't be
  executed "out of sequence" (typically after an `END` statement, alongside `PROC` definitions).

### Beta BASIC (Spectrum)

- <https://softhouse.speccy.cz/documents/download/BetaBasic3.txt> - the Beta BASIC 3.0 manual
  (Slovak translation; see `0002`'s Evidence for the download route, and its `DEF PROC`/`END PROC`/
  `REF`/`DEFAULT` findings, not repeated here). `DEF FN` appears exactly once in the whole text, in
  a sentence explaining that a freshly-typed `DEF PROC` definition does nothing until called,
  "similar to `DEF FN`" - a comparison, not a syntax description. The keyword index (which
  separately lists `DEF PROC`, `DEF KEY`, `DEFAULT`, `DO`, `LOOP`, `EXIT IF`, `WHILE`, `UNTIL`,
  `REF`, and `ITEM()` as BB03's own additions) has no `DEF FN` entry at all.
- Beta BASIC 4.0 supplement manual (see `0006`'s "Fetch technique notes"): confirms `DEF FN` exists
  as a single-line construct at minimum - `1000 DEF FN n$()="sect1": REM or "sect2" or "sect3"` -
  called as `FN n$()`, consistent with the 3.0 manual's silence on any multi-line form.
- <https://en.wikipedia.org/wiki/Beta_BASIC> and a secondary summary elsewhere state that Beta
  BASIC's `DEF FN` "worked the same way as `DEF PROC` and could take multiple lines" - contradicted
  by the primary manual's silence above (see Open questions).

### SAM Coupé BASIC

- <https://sam.speccy.cz/basic/sam-basic_complete_guide.pdf> - "The Complete Guide" (see `0006`'s
  "Fetch technique notes" for the extraction route). States outright: `DEFFN name(params)=expr`
  (one word, single-line, single-expression - the classic Sinclair shape, extended only with SAM's
  fuller parameter machinery: array/`REF` params, defaults via context), e.g.
  `DEFFN double$(a$)=a$+a$`, called `FN double$("hello")`. On multi-line bodies, the guide's own
  words: "The way of getting a function to be made up of several lines is simply to make use of
  several functions" - i.e. deliberately composing single-expression `DEFFN`s that call each other
  (`FN total` calling out to other `FN`s in its own expression), not a language feature for a
  multi-statement function body.

### NextBASIC / SpecNext

- <https://element.zxfiles.net/DOCS/OTHER/NEXTBAS.PDF> - `DEF FN` is extended but stays
  single-expression: `DEF FN gladys(harold)=harold+2`, `DEF FN ian$(REF jenny$(),index)=jenny$(index)`
  (recursion also supported: an in-manual factorial example). Explicitly states "`DEF FN` does not
  support user-defined integer functions" (the `%` suffix type). A `%CODE` system-variable bit
  toggles "legacy `DEF FN` entry" mode, for programs whose `DEF FN` pokes the old `DEFADD` system
  variable directly - "New and legacy `DEF FN`s may be mixed in the same program", implying the
  underlying execution mechanism changed under NextBASIC without changing the surface single-line
  syntax. Multi-statement, multi-return-value work is instead done via `DEFPROC`'s
  `ENDPROC = expr1, expr2, ...` mechanism (see `0002`) - a structurally different feature, not an
  extended `DEF FN`.

### Sinclair QL SuperBASIC

- <https://superbasic-manual.readthedocs.io/en/latest/D/define--function.html> - two forms:

  ```text
  DEFine FuNction name[$|%] [(item[,itemi])] :statement[:statement]:RETurn value
  ```

  ```text
  DEFine FuNction name[$|%] [(item[,itemi])]
    [LOCal var[,vari]]
    [statements]
    RETurn value
  END DEFine [name]
  ```

  `RETurn` is mandatory - falling through without one is error `-17`. `$`/`%` on the function name
  fixes its return type (string/integer); otherwise it's the default numeric type. Recursion allowed
  to 32,767 levels but discouraged as memory-hungry. Parameters and `LOCal` behave exactly as
  documented for `DEFine PROCedure` (see `0002` - parameters are by reference) - the manual
  explicitly cross-references the two rather than describing parameter handling twice.

### COMAL

- <https://dn760101.eu.archive.org/0/items/COMAL_Reference_Guide/COMAL_Reference_Guide_djvu.txt> -
  full text of Borge R. Christensen's 1984 *COMAL Reference Guide*. Exact syntax:

  ```text
  FUNC id [(paramlist)] [CLOSED]
    <function body>
  ENDFUNC [id]
  ```

  identical in shape to `PROC id [(paramlist)] [CLOSED] ... ENDPROC [id]`, differing only in the
  mandatory `RETURN expr`. Worked example:

  ```comal
  FUNC DISTANCE (X,Y)
    IF X<=Y THEN
      RETURN Y-X
    ELSE
      RETURN X-Y
    ENDIF
  ENDFUNC DISTANCE
  ```

  showing `RETURN` used as an early exit from inside a conditional, not only as a trailing
  statement - closer to Boriel's model than to QL SuperBASIC's single mandatory-return-at-the-end
  style.

### Boriel ZX BASIC (the outlier)

- <https://github.com/boriel/zxbasic/blob/main/docs/function.md> - `FUNCTION name [(paramlist)]
  [AS type] ... END FUNCTION`; untyped parameters and untyped return both default to `Float`.
  `RETURN expr` supplies the return value and can be used more than once, as an early exit, similar
  to COMAL's style above. Recursion demonstrated with a `Factorial` example. Legacy Sinclair
  `DEF FN` is mentioned only as history - not extended, replaced.

### SpecBAS (see `0006`)

- <https://sites.google.com/site/pauldunn/home/manual> - classic
  `DEF FN name[(var1[,var2...])] = Expression` confirmed unchanged, e.g.
  `DEF FN pythagoras(opp, adj) = SQR((opp*opp)+(adj*adj))`, called `FN pythagoras(...)`, parameters
  by value only. Separately, `CALL name[(params)]` - described in the manual's own words as acting
  "identically to the `PROC` command, but... you are able to return a value from the procedure" -
  is multi-line like `DEF PROC`/`END PROC`, and returns whatever an implicit `result` (numeric) or
  `result$` (string) variable holds on exit, e.g. `LET a=CALL myproc(1,2)*100`. No `RETURN`
  statement is used or needed anywhere in the mechanism.

## Dead ends

Fetch-technique dead ends (PDF extraction, 403s, and so on) are recorded once, centrally, in
`0006-related-basic-dialects-overview.md`'s "Fetch technique notes" rather than repeated per topic
note. A secondary claim that Beta BASIC's `DEF FN` "worked the same way as `DEF PROC`" couldn't be
traced to a specific, independently re-fetchable source, and is contradicted by the primary 3.0
manual's silence on it (see Evidence) - not worth re-searching for unless a Beta BASIC revision
later than 3.0/4.0 turns up (see Open questions).

## Open questions

- Beta BASIC's 3.0 manual's silence on `DEF FN` is strong but not conclusive evidence it stays
  single-line - does a Beta BASIC revision later than 3.0/4.0 document an actual multi-line
  `DEF FN`?
- Given Beta BASIC's confirmed paren-free `DEF PROC param1 param2` syntax, does `DEF FN` share it,
  or does `DEF FN` keep the classic Sinclair `DEF FN name(params)=expr` parenthesized form even if
  Beta BASIC's own `DEF PROC` doesn't? No worked `DEF FN` example with parameters turned up in
  either Beta BASIC manual read here.
- NextBASIC's "legacy `DEF FN`... `DEFADD` system variable" footnote implies 48K-era Spectrum BASIC
  had some machine-code-assisted way to extend `DEF FN` beyond a single expression, predating all of
  these dialects. Worth its own investigation if BazLang's own quirks/compatibility notes ever need
  it, but out of scope here.
