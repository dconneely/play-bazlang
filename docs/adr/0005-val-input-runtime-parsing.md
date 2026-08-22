---
status: "accepted"
date: 2026-08-03
decision-makers: David Conneely
---

# 5. Re-parse `VAL`/`VAL$`/`INPUT` expressions at runtime; do not cache the lowered AST

## Context and Problem Statement

`ExpressionEvaluator.evaluateNumericExpression`/`evaluateStringExpression` (used by `VAL`, `VAL$`,
numeric `INPUT`, and immediate mode) call `parser.parseNumExpr`/`parseStrExpr` plus
`AstLowering.lowerNum`/`lowerStr` on every invocation — unlike every other literal and operator in
the AST, which `AstLowering` resolves once at lowering time and never touches again (see
`docs/spec/architecture.md` "Performance & memory optimisations"). This looked, on the surface,
like a missed caching opportunity.

## Considered Options

* Cache the lowered AST once per source string, the way ordinary literals and operators are resolved
  once at lowering time.
* Memoize keyed on the runtime string content, storing the lowered AST — never a result — so a
  repeated identical string skips re-parsing.
* Keep re-parsing and re-lowering on every call, as today.

## Decision Outcome

Chosen option: keep re-parsing every call, because the operand is a runtime string value (`VAL A$`,
an `INPUT` line) that can differ on every call — there is no stable AST node to cache against the way
a literal has, so the first option is simply wrong (it would evaluate a stale expression against a
changed variable). Even the correct memoized form has marginal value: numeric `INPUT` can never hit,
since each entry is a fresh string, and `VAL` only hits when the identical string recurs, which a
programmer can usually hoist out of a loop themselves. It also erodes fidelity — authentic ZX BASIC
`VAL` is slow precisely because it re-parses.

This decision does not settle whether memoization is ever worth adding — it settles that it must
never be the *literal-style* cache, and that its value is speculative rather than demonstrated.

### Consequences

* Good, because it is correct by construction: no risk of evaluating a cached AST against a variable
  whose value has since changed.
* Bad, because it forgoes a possible (marginal) performance win on repeated identical `VAL` calls.
* Neutral: if a value-keyed memoization is ever implemented, it must re-lower with the *current*
  call's line number every time rather than reuse a tree lowered against a stale one — a `BIN`
  literal's value resolves using the `lineNumber` `AstLowering.lowerNum`/`lowerStr` were called with,
  for the "exceeds 64 digits" error's line attribution.

<!-- Extracted from the gitignored localonly-BAZLANG-IMPROVEMENTS.md ("VAL / VAL$ / INPUT parse at
     runtime — intentional; memoization is marginal") during the doc-kit migration (see
     docs/tasks/adopt-doc-kit.md). -->
