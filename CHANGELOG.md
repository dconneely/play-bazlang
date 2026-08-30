# Changelog

All notable changes to this project are documented here, following
[Keep a Changelog](https://keepachangelog.com) and [Semantic Versioning](https://semver.org).

## Unreleased

### Added

- `BEEP duration, pitch` - play a square-wave tone.
- `PLAY string1 [, string2 [, string3]]` - up to 3 simultaneous channels of AY-chip-style music via
  a note-string DSL; blocks until every channel finishes.
- `APLAY string1 [, string2 [, string3]]` - non-blocking counterpart to `PLAY`; `"-"` as a channel
  string updates one channel of an already-running `APLAY` without disturbing the others.
- MCP: `bazlang_stack` tool - GOSUB return frames and active FOR loops.
- MCP: `bazlang_breakpoint(list)` - query which breakpoints are currently armed.
- MCP: `bazlang_eval(action=vars)` - list every currently-defined variable, array, and `DEF FN`; a
  later change extended it to also report numeric/string arrays and `DEF FN` definitions.
- MCP: `bazlang_eval(action=exec)` - run any single immediate-mode statement, not just `LET`.
- MCP: `bazlang_eval(action=array)` - return a whole array's contents in one call.
- MCP: `bazlang_step(action=status)` - report the current pause state without executing anything.
- MCP: `bazlang_step`'s `step_into`/`step_over` actions - single-statement stepping.
- MCP: `bazlang_program(action=save_file)` - write the loaded programme to a file.
- MCP: a per-call wall-clock safety timeout (`timeoutMs`, default 30s) on
  `run`/`goto`/`go`/`step_into`/`step_over`, so a programme with no breakpoint of its own can't block
  a call forever.
- `PLAY`/`APLAY`: triplet duration codes `10`-`12` (triplet semi-quaver/quaver/crotchet) and tied
  notes (`<duration>_<duration><note>`, e.g. `3_5A`), matching the ZX Spectrum 128 manual's own
  duration-code table and tie example exactly. Previously reserved as parse errors.
- `TL$ s$` - string with the first byte removed (ZX80 BASIC's "truncate left"), and `UTL$ s$` -
  BazLang's Unicode-aware counterpart, removing the first whole codepoint instead. Together with
  `CODE`/`UCODE`, iterate a string byte-by-byte or codepoint-by-codepoint.

### Changed

- MCP debugging moved from the hand-rolled `AgentDebugger` text protocol to a native MCP server
  (`McpServer`/`DebugEngine`) targeting the 2026-07-28 MCP specification, modern-only - see
  [ADR-0004](docs/adr/0004-mcp-modern-only-protocol.md).

### Removed

- The `AgentDebugger` text protocol, `DebugSession`, and the `runAgentDebugger` Gradle task,
  superseded by the MCP server above.

### Fixed

- `bazlang_program(load_file/save_file)` no longer lets a `path` containing an unescaped `"` close
  the synthesised `LOAD`/`SAVE` statement early and have the remainder parsed as further BASIC
  statements; a syntactically invalid path for the host platform now reports as a normal
  file-not-found error instead of crashing with an internal error.
- A breakpoint (particularly an `ELAPSE` one) no longer silently cancels a REPL command
  (`LOAD`/`NEW`/a numbered-line edit/an assignment) dispatched through the immediate-mode execution
  path.
- `AND` chained after a string comparison (e.g. `IF r$ <> "Y" AND r$ <> "n" THEN ...`) now parses as
  `(r$ <> "Y") AND (r$ <> "n")` instead of letting the comparison's right-hand string operand
  swallow the `AND` (`r$ <> ("Y" AND (r$ <> "n"))`); confirmed against real ZX81/ZX Spectrum BASIC.
- `PLAY`/`APLAY`'s `V0`-`V15` volume now follows the AY chip's measured logarithmic curve instead of
  a flat linear divide, so relative loudness between two volume settings matches real hardware (e.g.
  `V8` no longer plays too loud relative to `V15`).
- `PLAY`/`APLAY` tone edges no longer always land on a whole-sample boundary - a transition that
  falls mid-sample is now averaged across that sample instead of point-sampled, reducing the
  audible aliasing a naive square-wave oscillator produces on higher notes.
