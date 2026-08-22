# Documentation map

This is the map. It describes what each document in this project is for, who reads it, how long it
lives, and — the question it exists to answer — **where a given fact belongs**.

Start here when you have something to write down and are not sure which file it goes in.

## Where does it go?

The tense of the sentence you are writing usually settles it:

| If you are writing… | It belongs in |
| --- | --- |
| "BazLang does X" / "the interpreter does X" | the specification |
| "we chose X because Y" | an ADR |
| "X used to be Y, now it is Z" | the changelog |
| "we should do X" | the plan |
| "we knowingly differ from Sinclair ZX BASIC (or from a reasonable expectation) here" | `docs/quirks.md` |
| "the disassembly/manual says X, another source says Y, and X won because…" | a research note |
| "the schema / grammar / wire format accepts X" | the machine-readable contract, linked from the specification — never restated in prose |
| "this is how each document is used" | this file |

If a sentence seems to fit two places, it is usually two sentences. Split it and file each half.

## Layout

```text
DOC-MAP.md                 this file — the map
AGENTS.md                  behavioural rules for agents; carries the pointer into this map
CLAUDE.md                  a pointer to AGENTS.md, for tools that look for this name specifically
README.md                  repository orientation, one screen, links outward
SPECIFICATION.md           index into the docs/ tree below
CHANGELOG.md               what shipped, across the whole repository
PLAN.md                    single ranked backlog, items tagged bug/debt/feature/docs
docs/
  quirks.md                  deliberate ZX BASIC eccentricities and accepted-wrong behaviour
  testing.md                 test strategy, and what is deliberately not covered
  adr/*.md                   decisions about the interpreter's design
  research/*.md              reverse-engineering and reference notes
docs/spec/
  language.md                the language reference (BazLang programmers)
  architecture.md             grammar, Java structure, execution model (interpreter implementers);
                              the grammar itself (app-bazlang/src/main/antlr/BazLang.g4) is the
                              machine-readable member
  mcp.md                      MCP debugger protocol and tool reference (MCP client developers)
app-bazlang/README.md       module orientation, links outward to the root docs/ above
lib-cell/README.md          self-contained: small internal library, no doc-kit tree of its own
lib-repl/README.md          self-contained: small internal library, no doc-kit tree of its own
.agents/skills/renumber_reformat/SKILL.md  one agent tool skill, not project documentation
```

This repository has three Gradle modules, but only one product: `app-bazlang` is the interpreter,
`lib-cell` and `lib-repl` are small internal libraries with no consumers outside it. Rather than
splitting documentation by module (doc-kit's usual monorepo default), everything about BazLang's
behaviour lives together under the repository-level `docs/`, since it describes one thing, not
several independent ones. `lib-cell` and `lib-repl` keep only their own `README.md` (see
"Deliberately not here").

**The specification is a set, not a file, and it is partly machine-readable.** `SPECIFICATION.md` is
an index over `docs/spec/` — the one place in `docs/` you can glob (`docs/spec/*.md`) to find every
specification member without reading any other file. One of that tree's members, the ANTLR grammar
`app-bazlang/src/main/antlr/BazLang.g4`, is itself the executable source of truth for syntax, not a
description of one. See "Machine-readable and generated parts" below.

## Artifacts

| Artifact | Purpose — and the standard it follows, if any | Tense | Durability | Audience |
| --- | --- | --- | --- | --- |
| `DOC-MAP.md` | This map: what each document is for, and where a fact belongs. **No standard**; nearest practice is a `docs/README.md` index, with [Diátaxis](https://diataxis.fr) supplying the rationale for splitting docs at all | present | rewritten in place | anyone adding documentation |
| `README.md` | Orient a newcomer fast at the repository root. Loose convention; [Standard Readme](https://github.com/RichardLitt/standard-readme) is the nearest written spec | present | rewritten in place | anyone |
| `app-bazlang/README.md` | **Alias** of `README.md` — same purpose, scoped to the interpreter module | present | rewritten in place | anyone |
| `lib-cell/README.md` | **Alias** of `README.md` — same purpose, scoped to this library. Also this library's *whole* documentation; see "Deliberately not here" | present | rewritten in place | anyone |
| `lib-repl/README.md` | **Alias** of `README.md` — same purpose, scoped to this library. Also this library's *whole* documentation; see "Deliberately not here" | present | rewritten in place | anyone |
| `SPECIFICATION.md` | The BazLang behaviour contract. An index over the `docs/` tree beside it. **No standard for the file.** Use [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) keywords (MUST/SHOULD/MAY) for requirement strength | present | rewritten in place | BazLang programmers + interpreter implementers + MCP client developers |
| `docs/spec/language.md` | The language reference: lines, variables, operators, commands, functions, slicing, errors. A specification member | present | rewritten in place | BazLang programmers |
| `docs/spec/architecture.md` | The interpreter's grammar, Java package/class structure, execution model, and performance-load-bearing patterns. A specification member; the grammar file itself is the machine-readable one — see "Machine-readable and generated parts" | present | rewritten in place | interpreter implementers, including agents modifying the code |
| `docs/spec/mcp.md` | The MCP debugger's JSON-RPC protocol and tool reference. A specification member | present | rewritten in place | MCP client developers |
| `docs/adr/*.md` | Why we chose this, for the interpreter's design. **Real convention:** [MADR](https://adr.github.io/madr/) minimal template, after Nygard 2011; see also [adr.github.io](https://adr.github.io) and [adr-tools](https://github.com/npryce/adr-tools) | past | immutable | future maintainers |
| `CHANGELOG.md` | What shipped, user-visible, across the whole repository. **Real standard:** [Keep a Changelog](https://keepachangelog.com) + [SemVer](https://semver.org); generatable from [Conventional Commits](https://www.conventionalcommits.org), which this repository already writes | past | append-only | users |
| `PLAN.md` | Single ranked backlog, items tagged bug/debt/feature/docs. **No standard**, and no named source — the closest analogue is the RFC/design-doc tradition, which prescribes no root-level file of this name | future | volatile | the team |
| `docs/quirks.md` | Deliberate deviations from Sinclair ZX BASIC, and bugs knowingly left unfixed — the one artifact that says *do not change this*. **No standard.** Nearest analogues are W3C conformance clauses and browser-compat tables | present | rewritten in place | BazLang programmers comparing against ZX BASIC, and anyone — human or agent — about to "fix" something odd |
| `docs/research/*.md` | Sourced findings with explicit confidence levels — chiefly reverse-engineering reference material (disassemblies, ports, manuals) for BazLang features modelled on original Sinclair software. **No standard.** Orthodox home is an ADR's *Context* section; splitting it out suits a project that does real investigation | past | append-only | implementers |
| `docs/testing.md` | Test strategy, and what is deliberately *not* covered. ISO/IEC/IEEE 29119-3 exists (superseded IEEE 829) but is enterprise-heavy for this project | present | rewritten in place | contributors |
| `AGENTS.md` | Behavioural rules for coding agents working in this repository, including the pointer into this map. **Real convention:** the emerging [agents.md](https://agents.md) format, which specifies root placement for auto-discovery | present | rewritten in place | coding agents |
| `CLAUDE.md` | **Alias** of `AGENTS.md` — some tools look for this filename specifically rather than (or as well as) `AGENTS.md`; the file itself is just a pointer, kept in sync trivially since it never changes | present | rewritten in place | coding agents |
| `.agents/skills/renumber_reformat/SKILL.md` | Step-by-step procedure for one agent tool skill (renumbering/reformatting `.bas` files). Not documentation of BazLang's own behaviour — a how-to for a specific piece of agent tooling | imperative | rewritten in place | coding agents |

## Lifecycle

| Artifact | Created when | Removed / closed when |
| --- | --- | --- |
| `DOC-MAP.md` | the structure is first agreed | never — revised when an artifact is added, removed or repurposed |
| `README.md` | project starts | never |
| `app-bazlang/README.md` | the module is created | never |
| `lib-cell/README.md` | the module is created | never |
| `lib-repl/README.md` | the module is created | never |
| `SPECIFICATION.md` | behaviour is decided | never — edited forever |
| `docs/spec/language.md` | behaviour is decided | never — edited forever |
| `docs/spec/architecture.md` | behaviour is decided | never — edited forever |
| `docs/spec/mcp.md` | behaviour is decided | never — edited forever |
| `docs/adr/*.md` | a choice a newcomer would question | never — status flips to `superseded by ADR-NNNN` |
| `CHANGELOG.md` entry | at release, if user-visible | never |
| `PLAN.md` entry | idea occurs — one paragraph, no design | **deleted** when done, not struck through |
| `docs/quirks.md` entry | a deviation is chosen, or a bug accepted | when the deviation ends |
| `docs/research/*.md` | a question is investigated | never — confidence gets revised |
| `docs/testing.md` | the second test approach appears (already true) | never |
| `AGENTS.md` | agents first work in this repository | never |
| `CLAUDE.md` | agents first work in this repository | never |
| `.agents/skills/renumber_reformat/SKILL.md` | the skill is written | the skill is retired |

## Flow

A change moves through the documents in this order:

`PLAN.md` entry → **ADR** if a real choice was made → **`SPECIFICATION.md`** (or the
`docs/` member it indexes) updated in present tense → **`CHANGELOG.md`** line if user-visible →
`PLAN.md` entry **deleted**.

Most changes skip the ADR. Nothing skips the deletion. This repository's backlog has stayed small
enough that no `docs/tasks/*.md` working-note step is needed; add one back (and a row for it in both
tables above) if the backlog grows past roughly 20 open items.

## Prescribed formats

These artifacts have a shape worth keeping to; the rest are free-form prose.

**ADR** — one file per decision, numbered `0001-short-title.md`, following the
[MADR](https://adr.github.io/madr/) minimal template. Copy `docs/adr/0000-template.md`
for the shape. The rules that file cannot express, and which live here:

- Status is one of `proposed`, `rejected`, `accepted`, `deprecated`, `superseded by ADR-NNNN`, and
  may carry a forward pointer: `accepted (refined by ADR-NNNN)` when a decision stands but a later
  record revised something following from it.
- **Only `accepted` binds.** A `proposed` record is a suggestion — merge it undecided if you like,
  since an open question is more visible in the tree than in a branch nobody is watching.
- **Immutable once accepted**, except to change status and date. Correct one by writing its
  successor, not by editing it.
- **Changing a status is a human action.** A tool may draft a record and argue it; only a person
  decides one.

A record that exists to protect a constraint — *we do not do the obvious thing here, because it
breaks* — is worth naming the constrained code in, for the same reason quirk entries do. The next
reader to reach for the obvious thing may be searching rather than browsing.

**Changelog** — reverse-chronological, an `Unreleased` section at the top, six fixed categories:
`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`. Entries describe user-visible
effects — new statements, functions, MCP tools, REPL commands — not internal refactors.

**Plan entry** — a heading, a tag line, then one paragraph. No design; anything longer needs an ADR.
Entries are deleted when done, never annotated.

```markdown
## Short title, imperative
**Type:** bug — **Importance:** high — **Effort:** medium
```

- **Type** — `bug`, `debt`, `feature` or `docs`. Debt is a tag here, not a separate file: the
  debt-versus-feature trade-off can only be made inside one ordered list.
- **Importance** — `low`, `medium`, `high`: what it costs to keep not doing this.
- **Effort** — `low` under a day, `medium` under a week, `high` larger or not yet known.

**Research note** — sources, and a confidence level of `high` (verified directly against the thing
itself — e.g. against original disassembly or a working port), `medium` (sources agree, not verified
directly) or `low` (inferred, or a single unverified source). Confidence is revised in place as
evidence changes. An unsourced note is not research: it is specification if it states BazLang's own
behaviour, an ADR if it states a choice BazLang made.

**Quirk entry** — the expected behaviour, the actual behaviour, and whether the deviation is
**deliberate** or **accepted-wrong**. An accepted-wrong entry names the test pinning today's output,
so nobody "fixes" it, and states what would have to change for the entry to go.

## Machine-readable and generated parts

Three kinds of thing get confused with each other, and the rules differ:

| Kind | Rule |
| --- | --- |
| **Source of truth** — `app-bazlang/src/main/antlr/BazLang.g4` | versioned and reviewed like code; it *is* BazLang's syntax, not a description of one |
| **Generated view** — the ANTLR-generated `BazLangLexer`/`BazLangParser` classes | never hand-edited; regenerating from `BazLang.g4` must produce no meaningful diff |
| **Prose that cannot be derived** — `docs/spec/architecture.md`'s "Grammar" section, the rest of `docs/spec/architecture.md`, rationale, invariants | the only part that belongs in `docs/` as writing |

`docs/spec/architecture.md`'s "Grammar" section explains *how* the grammar is structured and *why*;
it must never restate a production the grammar file itself already states, or the two will drift.

Note also that **append-only sequences are changelog-shaped, whatever they describe.** This project
has no data-store migrations, but the same rule is why `CHANGELOG.md` itself is append-only rather
than rewritten.

## Three rules that hold it together

1. **Each fact lives in exactly one place**, and the tense of the sentence tells you which:
   rationale → ADR, behaviour → specification, history → changelog, intent → plan.
2. **Never edit an accepted ADR** — supersede it, and set the old status to
   `superseded by ADR-NNNN`. Its whole value is being faithful to what was known at the time.
3. **The specification may link to a machine-readable contract; it must never restate it.** The
   moment prose repeats a grammar production, there are two sources of truth and one of them is
   already wrong. Link to the artifact, then cover only what the artifact cannot express.

## Failure modes this guards against

- **The plan becomes a graveyard** — entries annotated "DONE" rather than deleted, until nobody
  can see what is actually open. This repository's pre-adoption `localonly-BAZLANG-ROADMAP.md` did
  exactly this — dated "done and removed" notes accumulating at the top of an otherwise-future-tense
  file — which is why that narrative split into `CHANGELOG.md` entries and ADRs instead of carrying
  over.
- **The changelog becomes a commit log** — every internal refactor listed, so users cannot find
  what affects them.
- **ADRs get edited** — destroying their value as a record of what was known at the time.
- **The specification accumulates history** — "previously X, now Y", turning reference material
  into narrative. `docs/spec/architecture.md` and `docs/spec/mcp.md` are watched for this in
  particular, since both have carried dated prose in the past.
- **Findings evaporate** — an investigation's sources and dead ends survive only in a commit
  message or a code comment, and the next person repeats the work.

## Deliberately not here

Absent on purpose, so that adding any of them later is a decision rather than a drift:

- **A separate technical-debt file.** Debt shares its tense, mutability and audience with the plan,
  so splitting it out divides on category where everything else divides on those three properties.
  Use the type tag instead.
- **`docs/glossary.md`.** The domain has real jargon (report codes, the flat skip-scan, flattened
  statement addressing, the `DATA` pointer). It is currently defined once, at the point each term is
  introduced, in `language.md`/`architecture.md`/`quirks.md`. Revisit if that stops being enough —
  this is the first candidate to add back if cross-references start getting confusing.
- **`docs/archive/`.** Nothing has yet had its currency called into question strongly enough to
  archive rather than fix or delete — one stale Copilot-instructions file turned up during adoption
  (with a factually wrong claim, not just an outdated one) and was deleted outright rather than
  archived, since nothing in it was worth keeping.
- **A general `docs/tasks/` backlog.** Below roughly 20 open items, `PLAN.md` entries hold their own
  detail without needing a working-note file per item.
- **A `SPECIFICATION.md`/ADR tree for `lib-cell` or `lib-repl`.** Both are small, internal-only
  libraries with a single consumer (`app-bazlang`) and no external users; their `README.md` already
  states their whole contract, and a library that size does not earn more.
- **An issue tracker**, until the backlog outgrows a file — roughly 20–30 open items.
- **Community health files** — `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` — which earn
  their place when outside contributions begin, not before.
- **`RELEASING.md`**, which waits on there being releases (none yet — no tags exist), and
  **runbooks**, which belong to deployed services rather than a CLI tool and its libraries.

Only **Keep a Changelog** and **ADRs** are genuine standards; everything else here is convention.

## Adding a new kind of document

Before adding one, check it has a **distinct tense, mutability and audience** from everything in the
artifacts table. If it shares all three with an existing document, it is a section or a tag within
that document, not a new file. Most proposed additions fail this test — which is the point.

If it passes, add it to both tables here in the same commit. A map that omits an artifact is worse
than no map, because it is believed.
