---
status: "accepted"
date: 2026-09-26
decision-makers: David Conneely
---

# 10. Answer the legacy MCP `initialize` handshake for the tools-only subset, honestly versioned

## Context and Problem Statement

[ADR-0004](0004-mcp-modern-only-protocol.md) made `McpServer` modern-only (MCP 2026-07-28, no
`initialize` handshake), reasoning that "modern server, legacy client" fails regardless, so
supporting the legacy handshake "would have bought nothing". In practice the project's main agent
client, Claude Code (2.1.283 at the time of writing), still opens a stdio session with a legacy
`initialize` request rather than probing with `server/discover`, so it cannot connect at all. Other
clients in use (GitHub Copilot, Antigravity) do connect, which is consistent with them being
dual-era and probing first.

The 2026-07-28 specification explicitly permits a server to support both eras ("A server that wishes
to support both legacy clients ... and modern clients ... MAY implement both behaviors"). The legacy
lifecycle (2025-11-25) requires the server to echo the requested protocol version only if it
supports it, and otherwise reply with another version it does support, leaving the client to
disconnect if it cannot speak that version.

For a server that only exposes tools over stdio, the legacy surface after the handshake is almost
the same as the modern one: `tools/list` and `tools/call` carry the same `content`, `isError`, and
`structuredContent` fields; the server already accepts requests without a `_meta` protocol version;
and notifications (including `notifications/initialized`) already get no response. The modern-only
result fields (`resultType`, `ttlMs`, `cacheScope`) are additional members that legacy result
schemas permit.

## Considered Options

- Keep modern-only (ADR-0004 as it stands); reject `initialize` with an error naming the supported
  modern version, and wait for clients to adopt 2026-07-28.
- Answer `initialize` by echoing whatever version the client requests.
- Answer `initialize` for the tools-only subset of the legacy revisions this server actually
  implements - 2025-11-25 and 2025-06-18 - echoing the requested version only when it is one of
  those, and otherwise replying with 2025-11-25. Also answer `ping`. Implement nothing else from the
  legacy protocol.
- Implement the full legacy protocol (resources, prompts, logging, progress, and so on).

## Decision Outcome

Chosen option: answer `initialize` for the tools-only subset of 2025-11-25 and 2025-06-18, because
it lets current legacy clients connect for about thirty lines of code, while the negotiated version
is always one whose tool rules the server genuinely follows.

2025-06-18 is the oldest revision included because it introduced `structuredContent` and removed
JSON-RPC batching; 2025-03-26 and earlier require batch support, which this server does not have (a
JSON array request gets `-32600`). Echoing any requested version was rejected because it would claim
conformance to revisions the server has not been checked against.

This does not revisit the rest of ADR-0004: the modern revision remains the primary target, and
`AgentDebugger` stays retired.

### Consequences

- Good, because Claude Code and other legacy-only clients can use the debugger today, with no change
  to the tool surface or `DebugEngine`.
- Good, because the version a legacy client is told is always true: an unsupported request is
  answered with 2025-11-25, and the client decides whether to continue.
- Bad, because the server is now dual-era, so protocol changes must be checked against two
  revisions' rules, and the legacy branch needs its own tests (`McpServerProtocolTest`'s legacy
  cases).
- Bad, because a legacy client asking for 2025-03-26 or older is offered a newer revision it may not
  speak, and will then disconnect.
- Neutral: `docs/spec/mcp.md` changes from "modern-only, no legacy fallback" to describe both entry
  paths. If accepted, ADR-0004's status would gain a pointer to this record (for example
  `accepted (refined by ADR-0010)`) - a change only a person may make.
