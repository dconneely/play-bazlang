---
status: "accepted"
date: 2026-10-29
decision-makers: David Conneely
approval: "Approved."
---

# 4. Which MCP protocol revisions and debugging front-ends does the server support?

## Context and Problem Statement

The project needed an agent-facing debugging front-end. It already had a hand-rolled stdin/stdout
text protocol (`AgentDebugger`/`DebugSession`) and wanted native MCP support. A choice was needed
between running both front-ends, supporting one or both MCP eras in one server, and committing to
one protocol and one front-end.

The modern 2026-07-28 revision was the primary target. In practice the main agent client, Claude
Code (2.1.283 at the time), still opens a stdio session with a legacy `initialize` request rather
than probing with `server/discover`, so it could not connect to a modern-only server. The 2026-07-28
specification explicitly permits a server to support both eras.

## Considered Options

- Keep `AgentDebugger`'s text protocol alongside a new MCP server - two front-ends over the same
  debugging core.
- Support both the legacy `initialize` handshake and the modern 2026-07-28 revision in one server,
  including the full legacy protocol (resources, prompts, logging, progress, and so on).
- Build `McpServer` targeting only 2026-07-28, and delete `AgentDebugger` entirely.
- Answer `initialize` for the tools-only subset of the legacy revisions this server actually
  implements - 2025-11-25 and 2025-06-18 - echoing the requested version only when it is one of
  those, otherwise replying with 2025-11-25. Also answer `ping`. Implement nothing else from the
  legacy protocol.

## Decision Outcome

Chosen options, which together are the current answer:

1. **One front-end: `McpServer`; `AgentDebugger` retired.** Two front-ends duplicated the same
   surface for no benefit once MCP was in place. `DebugEngine` was extracted as a protocol-agnostic
   core specifically to make the retirement clean; it owns the interpreter, `BreakpointEngine` and
   `MockScreen`, and `McpServer` is its sole adapter.
2. **2026-07-28 is the primary revision.** No handshake and no protocol session: every request
   carries its own protocol version in `_meta`.
3. **The legacy `initialize` handshake is answered for the tools-only subset of 2025-11-25 and
   2025-06-18.** This lets current legacy clients connect for about thirty lines of code. 2025-06-18
   is the oldest revision included because it introduced `structuredContent` and removed JSON-RPC
   batching; 2025-03-26 and earlier require batching, which this server does not have. Echoing any
   requested version was rejected because it would claim conformance to revisions the server has not
   been checked against.

## Consequences

- Good, because there is one debugging front-end to keep in sync with the interpreter, not two, and
  a smaller codebase - `AgentDebugger.java`, `DebugSession.java`, `AgentDebuggerProtocolTest.java`,
  `QuotedArg.java`, `QuotedArgTest.java`, and `docs/language_debugger.md` were deleted, along with
  the `runAgentDebugger` Gradle task.
- Good, because Claude Code and other legacy-only clients can use the debugger today, with no change
  to the tool surface or `DebugEngine`.
- Good, because the version a legacy client is told is always true: an unsupported request is
  answered with 2025-11-25, and the client decides whether to continue.
- Bad, because the server is dual-era, so protocol changes must be checked against two revisions'
  rules, and the legacy branch needs its own tests (`McpServerProtocolTest`'s legacy cases).
- Bad, because a legacy client asking for 2025-03-26 or older is offered a newer revision it may not
  speak, and will then disconnect.
- Neutral: `BreakpointEngine.parseCondition` (the text protocol's `CSC`/`ELAPSE`/`?expr`/`EVERY`
  condition-string parser) was deleted with `AgentDebugger`; `McpServer` builds `BreakCondition`s
  from structured JSON directly and never needed a string parser.
- Neutral: [`docs/spec/mcp.md`](../spec/mcp.md) describes both entry paths.
