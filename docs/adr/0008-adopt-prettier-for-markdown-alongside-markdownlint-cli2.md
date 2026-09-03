---
status: "proposed"
date: 2026-09-03
decision-makers: {who decided - required once the status is not "proposed"}
---

# 8. Adopt Prettier for Markdown alongside markdownlint-cli2

## Context and Problem Statement

`.markdownlint.yaml` (via `igorshubovych/markdownlint-cli`) gates `MD013` (line length, 100 columns)
but has no auto-fix - `markdownlint-cli`'s successor, `markdownlint-cli2`, has none for it either,
confirmed directly. Every rewrap has been manual: edit, lint, find the violation, count columns,
rewrap by hand, re-lint.

This is the same move `doc-kit` already made (its ADR-0017) and `identigon/identigon` and
`identigon.github.io` both made before that, in the same split each time: `markdownlint-cli2` gates
structure and line length, Prettier does the actual reformatting. Checked directly against
`doc-kit`'s working `.prettierrc.json` and pre-commit wiring rather than assumed:
`proseWrap: "always"` is load-bearing (Prettier's own default, `"preserve"`, never rewraps prose at
all) and so is `embeddedLanguageFormatting: "off"` (Prettier's default, `"auto"`, reformats
recognised-language content inside fenced code blocks too - a risk for this repository's `jsonc`
example blocks in `docs/spec/mcp.md`).

The obstacle specific to adopting Prettier at all: its markdown printer has no configuration option
for single-emphasis style, confirmed empirically - a bare `*text*` becomes `_text_` on first run and
every run after. This repository's prose uses `*word*` and `*` list markers exclusively; adopting
Prettier is therefore not "run it and review the diff" but "accept a permanent, repository-wide
switch from `*word*` to `_word_` and from `*` to `-` list markers, with no way to configure around
it, in exchange for automatic line-wrapping."

A dry run against every tracked `.md` file (26 of 30 need reformatting; `CLAUDE.md`, `README.md`,
`docs/testing.md` and `docs/research/0000-template.md` are already compliant) confirmed the reformat
is cosmetic throughout: rewrap, bullet-marker and emphasis-style conversion, and table-cell
alignment padding, with fenced `jsonc` content and YAML front matter byte-identical before and
after.

## Considered Options

- Adopt Prettier for `*.md`, accept the emphasis-style and bullet-marker rewrite as the cost of
  automatic wrapping.
- Don't adopt Prettier; keep the manual rewrap-by-hand cost `MD013` already imposes today.
- Look for a narrower tool that wraps prose without also rewriting emphasis style and list markers -
  not found readily available, and not pursued further once the first option was judged worth its
  cost anyway.

## Decision Outcome

Chosen option: **adopt Prettier**, configured via `.prettierrc.json` - not CLI flags on the hook
line - scoping `proseWrap: "always"`, `printWidth: 100` and `embeddedLanguageFormatting: "off"` to
`*.md`. Prettier complements `markdownlint-cli2` rather than replacing it: a formatter (wrapping,
table alignment) and a linter (structural rules Prettier doesn't check at all - `MD024`, `MD032`,
and the rest of the enabled default set) are different jobs.

This record settles the trade-off. The one-time bulk reformat this decision requires touches every
`*.md` file that isn't already compliant, including several `docs/adr/*.md` records already
`accepted` - a direct exception to this repository's own rule (`AGENTS.md`: "never edit one that
says accepted"), made once, on the repository owner's explicit instruction, and recorded here rather
than treated as a standing carve-out for future formatting changes. Unlike `doc-kit` (ADR-0016),
this repository has adopted no general policy permitting formatting-only touches to accepted
records; a future formatting change to an accepted ADR needs the same explicit instruction again,
not a citation of this one.

### Consequences

- Good, because future edits no longer need manual line-counting and rewrapping - the exact gap
  `MD013`'s missing auto-fix leaves open.
- Good, because the two tools' responsibilities don't overlap: Prettier never needs to know
  `markdownlint-cli2`'s structural rules, and `markdownlint-cli2` needs no line-wrap logic of its
  own.
- Bad, because every existing `*word*` and `*` list marker in this repository's prose becomes
  `_word_` and `-` in one bulk reformat commit - a large diff with no semantic content, reviewed as
  such rather than read line by line.
- Bad, because that same bulk reformat necessarily rewrites several already-`accepted` ADRs'
  formatting - see the note above; this was a one-time exception, not a policy this record
  establishes.
- Neutral: table cell alignment padding (`| a | b |` -> `| a   | b   |`) is a side effect of the
  same bulk reformat, not a decision point of its own.
