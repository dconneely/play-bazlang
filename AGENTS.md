# Behavioural Rules for Agents

- Prefer relative paths to absolute paths for links in Markdown documents.
- Use UK spelling, grammar, and localisation. Avoid US cultural references and language.
- Line-wrap Markdown files at column 100, using GitHub Flavoured Markdown and sentence case for
  headings.
- DO NOT execute `git add`, `git commit`, or `git push`. The user will always do these.
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

Examples live in `app-bazlang/src/example/bas/` (flat — no subdirectories). Convention observed
across the existing set: a `REM ### Title ###` header (optionally a second `REM` line describing it)
as the first program line, line numbers starting at 1000 in steps of 10, and bare `RANDOMIZE` (not a
fixed seed) wherever `RND` is used. Match this rather than inventing a different shape.

## Documentation

[`DOC-MAP.md`](DOC-MAP.md) is the map: it says which file a given fact belongs in. Read it before
writing anything down.

When documents disagree, tense settles it:

- `SPECIFICATION.md` — present tense, authoritative about what BazLang does now.
- `CHANGELOG.md` — past tense. What changed, never what is true today.
- `PLAN.md` — intent. Nothing described in it exists yet.
- `docs/adr/` — why. Only `accepted` records bind; check the status before relying on one.
- `docs/quirks.md` — deliberate deviations. **Do not "fix" anything listed here.**

Before you edit:

- The specification follows the work. Change it because behaviour changed, not because it would
  read better. Its purpose and scope are not yours to revise.
- Never change an ADR's `status`, and never edit one that says `accepted`. Drafting a record is
  yours; deciding one is not — leave `decision-makers` as the template's placeholder too.
- Delete completed `PLAN.md` entries rather than marking them done.
- Adding a document means updating `DOC-MAP.md` in the same commit.
