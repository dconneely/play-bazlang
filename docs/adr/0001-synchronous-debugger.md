---
status: "accepted"
date: 2026-08-17
decision-makers: David Conneely
---

# 1. Run the debugger synchronously on the caller's thread, with no reentrant command loop

## Context and Problem Statement

`DebugEngine` needs to support `run`/`goto`/`go`/`stepInto`/`stepOver` for MCP clients driving a
running BazLang programme. Each of these has to block until the programme next breaks, elapses,
steps, hits a safety timeout, or stops, then hand control back — and the debugger has to decide what
thread that execution actually runs on, and how a breakpoint gets from "the programme should pause"
to "the caller's blocking call actually returns".

## Considered Options

* A command thread with a handoff queue — the conventional debugger architecture: a dedicated thread
  runs the interpreter, and commands (breakpoints, step, go) are posted to it and waited on.
* Synchronous, single-threaded: `run`/`gotoLine`/`go`/`stepInto`/`stepOver` each drive
  `Interpreter.resume()` directly on the caller's own thread until the programme next pauses, with no
  blocking wait for a "next command" inside the engine itself.

## Decision Outcome

Chosen option: synchronous single-threaded execution, because it entirely avoids the shared-state
concurrency, lock management, and thread-safety work a command-thread design would need across the
whole of `EvalState` — and because stdio is already one subprocess per MCP client, so there is no
separate "protocol session" to synchronise against in the first place.

A breakpoint pauses execution by having `Interpreter.setExecutionListener`'s callback set
`EvalState.setRunning(false)` before the triggering statement executes, which unwinds
`Interpreter.resume()` straight back to the caller; a later `go()` resumes at the exact same
location, guarded so the same breakpoint does not immediately re-fire. `stepInto`/`stepOver` reuse
that same resume guard.

### Consequences

* Good, because it is a genuine simplification: no concurrency bugs, no lock management across
  `EvalState`, and a deterministically testable execution model.
* Bad, because the debugger can only gain control at statement boundaries — it cannot interrupt a
  programme stuck mid-statement in an infinite loop. Every run-control call arms a per-call
  wall-clock safety timeout (default 30s, overridable via `timeoutMs`) as a mitigation, not a
  substitute: see [`docs/spec/mcp.md`](../spec/mcp.md) "Known limitations" and the open `PLAN.md`
  item on true `tools/call` cancellation.
* Neutral: `notifications/cancelled` is accepted by the MCP server but has no effect under this
  design — a full fix would move `Interpreter.resume()` onto a worker thread so a cancellation
  notification arriving on stdin could interrupt it mid-run, reintroducing the concurrency this
  decision avoids.

<!-- Extracted from docs/implementation.md "Debugger architecture decision" during the doc-kit
     migration (see docs/tasks/adopt-doc-kit.md); a pointer was left there. -->
