# Behavioural Rules for Agents

- Prefer relative paths to absolute paths for links in Markdown documents.
- Use UK spelling, grammar, and localisation. Avoid US cultural references and language.
- Line-wrap Markdown files at column 100, using GitHub Flavoured Markdown and sentence case for
  headings.
- Never run `git commit` unless explicitly asked. Present the changes and a suggested commit
  message, then wait.
- Never run `git push`, in any repository. The user always pushes. When work is committed and ready,
  say so and leave the push to them.
- Use neutral, professional tone. Be direct about incorrect prompts. Prefer precise, technical
  answers over analogies and rhetorical padding. ASCII punctuation only: hyphen-minus `-`, straight
  quotes `" '`, no emoji.
- Java: Google Java Format via `./gradlew spotlessApply`; Java 25 idioms.

## Making changes

- Follow existing conventions (style, formatting, structure). Prioritise correctness,
  maintainability and readability over novelty.
- Use Conventional Commits when suggesting commit messages. Make small, reviewable changes with
  accurate descriptions.
- Avoid unnecessary dependencies; justify additions. Do not modify CI, build scripts, or dependency
  versions without explicit request; explain the impact on versioning and compatibility when
  proposing any.
- Treat data as potentially sensitive; never log secrets or personal data.

## Architecture

- Maintain clean separation of concerns. Challenge decisions that violate SOLID principles.
- The screen / cell layer (`lib-cell`, `VirtualScreen`) must not contain BASIC business logic.
- Prefer explicit feedback mechanisms over implicit detection logic in lower layers.
- When in doubt about an architectural decision, ask before implementing.

## Example programs

Examples live in `app-bazlang/src/example/bas/` (flat - no subdirectories). Convention observed
across the existing set: a `REM ### Title ###` header (optionally a second `REM` line describing it)
as the first program line, line numbers starting at 1000 in steps of 10, and bare `RANDOMIZE` (not a
fixed seed) wherever `RND` is used. Match this rather than inventing a different shape.

## Documentation

[`DOC-MAP.md`](DOC-MAP.md) is the map: it says which file a given fact belongs in. Read it before
writing anything down.

When documents disagree, tense settles it:

- `docs/spec.md` - present tense, authoritative about what BazLang does now.
- `CHANGELOG.md` - past tense. What changed, never what is true today.
- `docs/plan.md` - intent. Nothing described in it exists yet.
- `docs/decisions.md` - why: index of decision records; only `accepted` records bind.
- `docs/quirks.md` - deliberate deviations. **Do not "fix" anything listed here.**
- `docs/research/` - sourced findings with confidence levels.
- `docs/testing.md` - test strategy, and what is deliberately not covered.

Before you edit:

- The specification follows the work. Change it because behaviour changed, not because it would read
  better. Its purpose and scope are not yours to revise.
- An agent may create a decision record as `proposed`, by copying `docs/decisions/0000-template.md`
  rather than an existing record, and may revise a `proposed` record. Accepting, rejecting,
  reopening, deprecating or deleting a record needs the owner's explicit permission, asked for
  first. The owner may also approve a combine of records; absorbed records become `deprecated` and
  link to the surviving record. Never change a status or edit an accepted record without that
  permission.
- On accepting, set `decision-makers`, `approval` (the owner's words, verbatim) and `date` (today).
- Cite records as `ADR-NNNN`, and another repository's record by a link. Fix every citation a change
  makes untrue in the same commit.
- Delete completed `docs/plan.md` entries rather than marking them done.
- Write the least that is true: no history or account of how you got there, in documents or code
  comments.
- A code comment states why the code is as it is now. History goes in the commit message; a reason
  spanning more than one site goes in a decision record the comment names.
- Adding a document means updating `DOC-MAP.md` in the same commit.
