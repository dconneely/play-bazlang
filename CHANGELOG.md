# Changelog

All notable changes to this project are documented here, following
[Keep a Changelog](https://keepachangelog.com) and [Semantic Versioning](https://semver.org).

## Unreleased

### Added

- `BEEP duration, pitch` — play a square-wave tone.
- `PLAY string1 [, string2 [, string3]]` — up to 3 simultaneous channels of AY-chip-style music via
  a note-string DSL; blocks until every channel finishes.
- `APLAY string1 [, string2 [, string3]]` — non-blocking counterpart to `PLAY`; `"-"` as a channel
  string updates one channel of an already-running `APLAY` without disturbing the others.
- MCP: `bazlang_stack` tool — GOSUB return frames and active FOR loops.
- MCP: `bazlang_breakpoint(list)` — query which breakpoints are currently armed.
- MCP: `bazlang_eval(action=vars)` — list every currently-defined variable, array, and `DEF FN`; a
  later change extended it to also report numeric/string arrays and `DEF FN` definitions.
- MCP: `bazlang_eval(action=exec)` — run any single immediate-mode statement, not just `LET`.
- MCP: `bazlang_eval(action=array)` — return a whole array's contents in one call.
- MCP: `bazlang_step(action=status)` — report the current pause state without executing anything.
- MCP: `bazlang_step`'s `step_into`/`step_over` actions — single-statement stepping.
- MCP: `bazlang_program(action=save_file)` — write the loaded programme to a file.
- MCP: a per-call wall-clock safety timeout (`timeoutMs`, default 30s) on
  `run`/`goto`/`go`/`step_into`/`step_over`, so a programme with no breakpoint of its own can't block
  a call forever.

### Changed

- MCP debugging moved from the hand-rolled `AgentDebugger` text protocol to a native MCP server
  (`McpServer`/`DebugEngine`) targeting the 2026-07-28 MCP specification, modern-only — see
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
