---
status: "accepted"
date: 2026-01-05
decision-makers: David Conneely
---

# 6. Use ANTLR 4 for the grammar, instead of a hand-written parser

## Context and Problem Statement

BazLang needed a lexer and parser from the project's first commit. A BASIC dialect's grammar is
small but has real structure to get right — operator precedence, function-argument binding, the
split between program statements and REPL-only commands — and that structure would need revisiting
repeatedly as the language grew.

## Considered Options

* A hand-written recursive-descent parser, with precedence handled by manual climbing.
* ANTLR 4, generating the lexer and parser from a declarative grammar file (`BazLang.g4`).

## Decision Outcome

Chosen option: ANTLR 4, because a declarative grammar states precedence as rule ordering rather than
as manually-climbed code, keeps error recovery and case-insensitive keyword matching largely built
in rather than hand-rolled, and turns changes to the language's syntax into grammar-rule edits rather
than parser-logic surgery. The grammar file doubles as the syntax documentation: precedence,
argument-binding tightness, and the statement/REPL-command split are all readable directly from
`BazLang.g4`'s rule structure.

### Consequences

* Good, because adding an operator or statement is a grammar-rule change plus an `AstLowering`/
  `ExpressionEvaluator`/`StatementExecutor` case, not a parser rewrite — see
  `docs/spec/architecture.md` "Adding new features".
* Good, because precedence and associativity (`<assoc=right>` for `**`/`^`) are declared, not
  implemented — there is no separate precedence-climbing function to keep in sync with the grammar.
* Bad, because a contributor needs to learn ANTLR's grammar syntax and generated-parser model, not
  just Java, to touch the parser.
* Neutral: the generated `BazLangLexer`/`BazLangParser` classes are build output, never hand-edited —
  see `DOC-MAP.md` "Machine-readable and generated parts".

<!-- Extracted from the former docs/grammar.md ("Why ANTLR?") during a doc-kit restructuring pass;
     the rest of that file folded into docs/spec/architecture.md's "Grammar" section. -->
