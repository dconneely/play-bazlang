---
status: "accepted"
date: 2026-10-29
decision-makers: David Conneely
---

# 11. How the documentation is organised

## Context and Problem Statement

This repository's documentation was built from a pre-0.5.0 doc-kit shape: `SPECIFICATION.md` and
`PLAN.md` at the root, `docs/adr/` and `docs/tasks/`, a hand-customised map with free-text audiences
and inline formats, and no generated decision index. The content was already specific to this
repository: repository-wide documentation for one product across three Gradle modules, a
three-member specification tree, module READMEs, research notes with confidence levels, a quirks
register and task notes.

Two failures had already shaped the structure. A pre-adoption `localonly-BAZLANG-ROADMAP.md`
accumulated dated "done and removed" notes, and was split into the changelog and decision records.
[`docs/spec/architecture.md`](../spec/architecture.md) and [`docs/spec/mcp.md`](../spec/mcp.md) have
carried dated prose in the past. The owner authorised a re-adoption and an ADR review on 2026-10-29;
the accepted records were amended and combined under that authorisation, with the owner's approval
recorded in each record.

The structure comes from doc-kit at commit `5312a15`. Its decision records are at
`https://github.com/dconneely/doc-kit/tree/5312a15/docs/decisions`.

## Considered Options

- Adopt doc-kit 0.6.0's structure, keeping this repository's content and customisations.
- Keep the pre-0.5.0 shape.
- Keep everything in one `README.md` or `AGENTS.md`.

## Decision Outcome

Chosen option: "Adopt doc-kit 0.6.0's structure", because the old paths and map had drifted from the
current kit, and the repository has enough documentation that one map is worth keeping accurate.

Deviations from the kit:

- Documentation is repository-wide, not per module, because there is one product; `lib-cell` and
  `lib-repl` are internal libraries with no consumers outside `app-bazlang`.
- The module READMEs are aliases of `README.md`; the two library READMEs are also each library's
  whole documentation.
- Accepted decision records are `amendable`: the owner may approve an amendment or a combine, and an
  absorbed record is deprecated with a link to the survivor. Agents never decide a status.
- `docs/glossary.md`, `docs/howto.md`, `docs/sources.md` and `docs/archive/` are deliberately
  absent; the map records why.
- Local `.agents/` tooling is outside the documentation map.

### Consequences

- Good, because each fact has one home, and `DOC-MAP.md` says which.
- Good, because completed plan entries are deleted, so the plan stays a list of open work.
- Good, because the changelog lists only effects users can see, so it does not become a commit log.
- Good, because only the owner accepts or changes a decision record, so nothing an agent session
  writes binds until the owner says so, and each record stays the current answer to its question.
- Good, because the specification states current behaviour, not how it came to be.
- Good, because research notes state the current finding and its confidence, not how it was reached,
  and findings with sources are not lost in commit messages and comments.
- Good, because prose links to machine-readable contracts rather than restating them.
- Good, because text, code comments included, states what is true rather than recounting its
  history.
- Bad, because the map must name every documentation file, and someone has to keep it so.
- Bad, because migrating existing documentation proceeds one document at a time, with no end date.
- Bad, because the `.agents/skills/renumber_reformat/SKILL.md` file is gitignored, so the map cannot
  name it without naming a file a fresh clone lacks; it stays outside the structure.

## Confirmation

`sh ../doc-kit/tools/doc-kit-check.sh` passes, and the owner reviews the map and the ADR set.
