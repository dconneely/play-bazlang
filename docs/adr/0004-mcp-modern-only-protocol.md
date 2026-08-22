---
status: "accepted"
date: 2026-08-17
decision-makers: David Conneely
---

# 4. Target only the MCP 2026-07-28 protocol revision, and retire the `AgentDebugger` text protocol

## Context and Problem Statement

The project needed an agent-facing debugging front-end. It already had a hand-rolled stdin/stdout
text protocol (`AgentDebugger`/`DebugSession`) and wanted native MCP support. A choice was needed
between running both front-ends, supporting both legacy and modern MCP clients in one server, or
committing to one protocol and one front-end.

## Considered Options

* Keep `AgentDebugger`'s text protocol alongside a new MCP server — two front-ends over the same
  debugging core.
* Support both the legacy `initialize`-handshake MCP protocol and the modern 2026-07-28 stateless
  revision in one server.
* Build `McpServer` targeting only the 2026-07-28 revision, and delete `AgentDebugger` entirely.

## Decision Outcome

Chosen option: modern-only MCP, single front-end, because per the MCP spec's own compatibility
matrix "modern server, legacy client" fails outright regardless — supporting the legacy handshake
would have bought nothing. Maintaining two debugging front-ends (a hand-rolled text protocol and a
spec-compliant one) duplicated the same surface for no benefit once MCP was in place. `DebugEngine`
was extracted as a protocol-agnostic core specifically to make this retirement clean: it owns the
interpreter, `BreakpointEngine`, and `MockScreen`, and `McpServer` is its sole adapter.

### Consequences

* Good, because there is one debugging front-end to keep in sync with the interpreter, not two, and
  a smaller codebase — `AgentDebugger.java`, `DebugSession.java`,
  `AgentDebuggerProtocolTest.java`, `QuotedArg.java`, `QuotedArgTest.java`, and
  `docs/language_debugger.md` were all deleted, along with the `runAgentDebugger` Gradle task.
* Bad, because a client still speaking the pre-2026-07-28 `initialize`-handshake protocol cannot use
  this server at all — see [`docs/spec/mcp.md`](../spec/mcp.md) "Known limitations".
* Neutral: `BreakpointEngine.parseCondition` (the text protocol's `CSC`/`ELAPSE`/`?expr`/`EVERY`
  condition-string parser) was deleted with it — `McpServer` builds `BreakCondition`s from structured
  JSON directly and never needed a string parser.

<!-- Extracted from docs/mcp_server.md's "Protocol version" note and the gitignored
     localonly-BAZLANG-IMPROVEMENTS.md's AgentDebugger-retirement narrative during the doc-kit
     migration (see docs/tasks/adopt-doc-kit.md). -->
