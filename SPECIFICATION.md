# Specification

BazLang's behaviour contract: what the interpreter does, and what a BazLang programme or an MCP
client may rely on. **This file holds none of that contract directly** — it is an index, stating only
scope, the unspecified boundary, and which member covers what. If you're about to write a behaviour
or architecture fact *here*, it belongs in one of the three members below instead; this file only
ever names them, never substitutes for them. The specification is a small tree, not one document,
because BazLang programmers, interpreter implementers, and MCP client developers need it organised
differently.

Requirement keywords — MUST, MUST NOT, SHOULD, SHOULD NOT, MAY — are used as defined in
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) where the member documents use them.

## 1. Scope

Covers the BazLang language (statements, functions, operators, REPL commands) and the MCP debugger
protocol exposed by `McpServer`. Does not cover `lib-cell` or `lib-repl`, whose contracts are their
own `README.md` — neither is BazLang-specific or has a consumer outside `app-bazlang`.

## 2. Members

Three, one per audience — each is the *only* place its kind of fact belongs, so a fact that seems to
need two of these is usually two sentences, not one (see "Which member, for a given fact" below):

All three live under `docs/spec/` — the one place in `docs/` you can glob for "is this part of the
contract" (`docs/spec/*.md`) without reading this file at all:

- **[`docs/spec/language.md`](docs/spec/language.md)** — the language reference: lines, variables,
  operators, commands, functions, slicing, errors. Audience: **BazLang programmers**, who never need
  to read Java or a wire protocol to use the language.
- **[`docs/spec/architecture.md`](docs/spec/architecture.md)** — the interpreter's grammar, Java
  package/class structure, execution model, and the performance-load-bearing patterns a refactoring
  must preserve. The grammar file itself,
  [`app-bazlang/src/main/antlr/BazLang.g4`](app-bazlang/src/main/antlr/BazLang.g4), is the
  machine-readable member and the actual source of truth for syntax — `architecture.md`'s "Grammar"
  section explains it and must never restate a production. Audience: **interpreter implementers**,
  including agents modifying the code.
- **[`docs/spec/mcp.md`](docs/spec/mcp.md)** — the MCP debugger's JSON-RPC protocol and tool
  reference. Audience: **MCP client developers** — a population that includes people who will never
  read `architecture.md` or touch this codebase's Java at all.

Read [`docs/quirks.md`](docs/quirks.md) alongside these — it is not itself part of this contract
(see `DOC-MAP.md`: deviations, not behaviour, is its own category), but every member above assumes
it before "fixing" anything that looks wrong.

### Which member, for a given fact

Pick by **audience** — who actually needs to know this, and does that population need anything
else here to make sense of it:

| The fact you're writing... | Goes in |
| --- | --- |
| "BazLang does/means/returns X" — what a programmer relying on the language (not Java, not MCP) can depend on | `language.md` |
| "the interpreter's grammar/Java code is built like X" — no consumer outside this codebase's own implementers needs it | `architecture.md` |
| "the MCP tool/protocol accepts/returns X" — the wire contract an external MCP client depends on | `mcp.md` |

Two failure modes to watch for, both already found and fixed once during this structure's adoption:

- **Restating an outcome as its own mechanism.** Operator precedence is the recurring example:
  `language.md` states the resulting order as a plain table, `architecture.md`'s "Grammar" section
  explains it as ANTLR rule ordering. That is one fact with two representations, not two facts —
  state the outcome once in `language.md`, and have `architecture.md` link back rather than
  re-derive the same table. The same applies to `architecture.md`'s "Statement execution notes":
  they cover mechanism only and link to `language.md` for the behaviour the mechanism produces.
- **A behavioural fact with no home, because it only ever got written down as supporting context
  for an architecture note.** The error-report format and "`STOP` during `INPUT` raises `H STOP in
  INPUT`" both existed only in `architecture.md` before this pass — genuinely BazLang-programmer
  facts, accidentally undocumented in `language.md` because they'd been explained once, in the
  wrong file, as background for an implementation detail. If a `language.md`-shaped fact is missing
  from `language.md`, check `architecture.md` and `mcp.md` for a stray copy before writing it fresh.

`mcp.md` occasionally needs a sentence of BazLang-language context to justify a tool's design (e.g.
why `bazlang_eval` treats a leading `LET` as the assignment cue) — that's fine in small doses, since
the fact being stated there is about the *tool*, not the language; it should still read as
consistent with `language.md`, not as a second, independent description of the language rule itself.

## 3. Deliberately unspecified

Behaviour callers MUST NOT rely on: the exact wording of a `ReportException` message beyond its
report code and `<line>:<statement>` location; `RND`'s underlying algorithm (Java `Random`, not a
faithful reproduction of the ZX81's linear feedback shift register — see `docs/spec/language.md`
"Divergences"); the internal thread a background `APLAY` runs on; and anything documented in
`docs/quirks.md` as **accepted-wrong**, which by definition may change without notice once fixed.
