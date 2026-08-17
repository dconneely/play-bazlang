# McpServer

`McpServer` is a native MCP (Model Context Protocol) server exposing BazLang programme debugging
as JSON-RPC tools, so any MCP client can attach directly instead of scraping the `AgentDebugger`
text protocol (see [language_debugger.md](language_debugger.md)). Both front-ends share the same
underlying [`DebugEngine`](implementation.md) — `McpServer` is a thin JSON-RPC adapter over it, just
as `DebugSession` is a thin text-protocol adapter over it.

## Running the server

```bash
./gradlew :app-bazlang:runMcpServer
```

The server prints one line to stderr on startup (agents should ignore stderr) and then waits on
stdin for newline-delimited JSON-RPC requests. There is no `+READY` line and no preload-by-argument
mode — load a programme with the `bazlang_program` tool once connected.

## Protocol version: 2026-07-28, modern-only

This server targets the **2026-07-28 MCP specification** — the stateless protocol revision — and
only that revision. Two consequences worth knowing before integrating:

- **No `initialize` handshake, no protocol session.** Every request carries its own protocol
  version in `_meta`; there is nothing to negotiate up front. State (the loaded programme,
  breakpoints, variables) lives in one `DebugEngine` instance for the lifetime of the server
  process — since stdio is already one subprocess per client, this is equivalent to a protocol
  session without needing one.
- **No legacy fallback.** A client still speaking the pre-2026-07-28 `initialize`-handshake
  protocol cannot use this server — per the spec's own compatibility matrix, "modern server,
  legacy client" fails outright. This was a deliberate scope decision (see the project history) to
  keep the implementation small; if your MCP client hasn't adopted 2026-07-28 yet, use
  `AgentDebugger`'s text protocol instead.
- **Lenient version checking.** If a request omits `_meta`'s protocol version field entirely, the
  server proceeds anyway rather than rejecting it — some early modern clients may not yet send it
  on every request. If a version *is* present and doesn't match, the server responds with
  `UnsupportedProtocolVersionError` (code `-32022`) listing what it supports.

## Transport

Newline-delimited JSON-RPC 2.0 over stdin/stdout — the same framing shape `AgentDebugger` already
uses, just with JSON bodies instead of `+`/`-` text lines. Each request line produces exactly one
response line, except JSON-RPC *notifications* (a message with no `id`), which never get a
response — `notifications/cancelled` is accepted and silently ignored (there is no
cancel-while-running mechanism, matching `AgentDebugger`, which has none either).

## `server/discover`

Required by the spec. Advertises the supported protocol version, capabilities, and server
identity — call it first if you want to confirm compatibility before anything else.

```jsonc
→ {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}
← {"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","supportedVersions":["2026-07-28"],
   "capabilities":{"tools":{"listChanged":false}},
   "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"bazlang-mcp","version":"1.0.0"}},
   "instructions":"Debug BazLang programmes: load a programme, set breakpoints, step through
   execution, and inspect state via the bazlang_* tools.","ttlMs":3600000,"cacheScope":"public"}}
```

## `tools/list`

Returns the six tools below (static — `listChanged` is `false`, and the list carries a long
`ttlMs`/`cacheScope: "public"` since it never changes).

## `tools/call`

Every tool response follows the standard envelope: `resultType: "complete"`, a human-readable
`content` text block, `isError` (true for both bad arguments and BASIC runtime errors — anything a
model might reasonably retry after adjusting its call), and a `structuredContent` object with the
same information in a programmatic shape where one is useful.

### `bazlang_program`

Consolidates `NEW`, `LOAD "path"`, single numbered-line edits, and `LIST`.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `new` | — | Clears the programme and all runtime state, and flushes queued input |
| `load_file` | `path` | Loads a `.bas` file (bare names resolve against the example directory); flushes queued input |
| `load_source` | `source` | Replaces the whole programme with inline multi-line source; flushes queued input |
| `edit_line` | `line`, `statement` | Adds/replaces line `line`; blank `statement` deletes it. Does **not** flush queued input |
| `list` | — | Returns the current programme text in `content` and `structuredContent.listing` |

`load_file` resolves bare names the same way the text protocol's `>LOAD` does. `load_source` has
no text-protocol equivalent — the old protocol could only build a programme one `>n stmt` line at
a time; `load_source` loads a whole multi-line programme (one BASIC line per `\n`) in one call.
See "Input queue" below for why `new`/`load_file`/`load_source` flush and `edit_line` doesn't.

```jsonc
→ {"name":"bazlang_program","arguments":{"action":"load_source","source":"10 LET X=0\n20 PRINT X"}}
← {"resultType":"complete","content":[{"type":"text","text":"Loaded programme (2 lines)"}],"isError":false}
```

### `bazlang_step`

Consolidates `RUN`, `GOTO n`, `GO`, and `STOP`. Blocks until the programme next breaks, elapses, or
stops, then returns the pause reason — there is no separate "wait for the next command" phase the
way the text protocol's deferred responses work, because each MCP call is already one full
request/response round trip.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `run` | — | Clears runtime state and runs from the first line. Error if no programme is loaded |
| `goto` | `line` | Runs from `line` without clearing state |
| `go` | — | Resumes from a breakpoint. Error if not currently paused |
| `stop` | — | Terminates the running programme, without ending the server process |

Unlike the text protocol's `/STOP`, which ends the whole `AgentDebugger` session, `bazlang_step`'s
`stop` action just resets run state so a later `run`/`goto` can start again — the server stays up
until stdin closes.

`structuredContent.reason` is one of `break` (with `line`/`stmt`), `elapse`, `stop` (with `code`,
`message`, `line`, `stmt` — see `ReportCode` for the code table), or `stopped` (the `stop` action).

```jsonc
→ {"name":"bazlang_step","arguments":{"action":"run"}}
← {"resultType":"complete","content":[{"type":"text","text":"BREAK AT 20:1"}],"isError":false,
   "structuredContent":{"reason":"break","line":20,"stmt":1}}
```

### `bazlang_breakpoint`

Consolidates `SPB`, `S1B`, and `CPB`.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `set` | `persistent` (default `true`), `line`, `statement`, `condition` | Adds a breakpoint |
| `clear` | `line`, `statement` | Removes breakpoints registered at that exact location |
| `clear_all` | — | Removes every persistent breakpoint |

Omit `line`/`statement` for a condition-only breakpoint checked on every statement.
`persistent: false` is the text protocol's `S1B` — fires once, then removes itself.

`condition` is optional (omit it for an unconditional location breakpoint) and, when present, is
one of:

| `condition.type` | Fields | Matches the text protocol's |
| --- | --- | --- |
| `csc` | `text` | `CSC "<text>"` — screen contains text (case-insensitive) |
| `elapse` | `milliseconds` | `ELAPSE <ms>` — wall-clock time since the last resume |
| `expr` | `expression` | `?<expr>` — a BazLang expression is truthy |
| `every` | `everyN` | `EVERY <n>` — fires every nth check |

```jsonc
→ {"name":"bazlang_breakpoint","arguments":{"action":"set","line":100,"statement":1,
   "condition":{"type":"expr","expression":"FUEL<50"}}}
← {"resultType":"complete","content":[{"type":"text","text":"OK"}],"isError":false}
```

### `bazlang_eval`

Consolidates `?<expr>` and `!<assignment>` into one `expression` argument. A leading `LET` is the
disambiguating cue for assignment — everything else is evaluated as an expression. This is
deliberate, not a heuristic guess: bare `X=3` is a valid BazLang *expression* (an equality test,
exactly as in `?X=3`), so only an explicit `LET X=3` can mean "assign" without ambiguity.

```jsonc
→ {"name":"bazlang_eval","arguments":{"expression":"SCORE"}}
← {"resultType":"complete","content":[{"type":"text","text":"42"}],"isError":false,
   "structuredContent":{"value":42}}

→ {"name":"bazlang_eval","arguments":{"expression":"LET SCORE=0"}}
← {"resultType":"complete","content":[{"type":"text","text":"OK"}],"isError":false}
```

### `bazlang_screen`

Consolidates `RSC` and `SSD`.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `read` | `rowTop`, `colLeft`, `rowBottom`, `colRight`, `attr` (default `false`) | Dumps a screen rectangle into `content`/`structuredContent.grid`, in the same `{N}`-compressed, `[fg,bg]`-annotated text format as `/RSC` (see [language_debugger.md](language_debugger.md#quotedarg-format)) — plain JSON string, no `QuotedArg` escaping needed |
| `resize` | `rows`, `cols` | Resizes the virtual screen buffer |

### `bazlang_input`

Consolidates `PIQ`, plus a queue-flush action with no text-protocol equivalent.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `queue` | `text` | Queues `text` for `INKEY$`/`UINKEY$`/`INPUT` to consume |
| `clear` | — | Discards all queued input without adding any |

`text` is a plain JSON string (JSON's own escaping replaces the text protocol's `QuotedArg`
format). `action=clear` is for cancelling a mis-queued value or resetting mid-session without
reloading the programme — see "Input queue" below for the far more common case, which is handled
automatically and needs no explicit call.

## Input queue

`bazlang_input(queue)` — like the text protocol's `/PIQ` before it — queues the same text for all
three input primitives at once (`INKEY$` gets it byte-by-byte, `UINKEY$` codepoint-by-codepoint,
`INPUT` as one line), because the queuer can't know in advance which one the programme will
actually read. That's fine within one programme's lifetime, but `DebugEngine`'s queues live on one
`MockScreen` for as long as the engine does — across many `bazlang_program` calls, not just one
run — so **anything queued but never consumed by one programme is still sitting there, in all three
queues, when the next programme loads.**

This was found the hard way: a session that queued single-key guesses for a hangman-style
programme (which only ever calls `INKEY$`) then loaded a real-time programme reading `UINKEY$`
found its very first keypress was actually a stale hangman guess, several calls deep into the
queue. A later session hit the same thing from the other side — a programme's first `INPUT` call
consumed a stale value queued many calls earlier by a completely different programme, and (correctly)
reported a parse error for it.

`bazlang_program`'s `new`, `load_file`, and `load_source` actions now flush all three queues
automatically whenever they replace the whole programme, so input queued for a programme you've
moved on from can never leak into the next one. `edit_line` deliberately does **not** flush —
editing one line of the *current* programme is a much smaller change than replacing it, and a
workflow that's iterating on one programme in a loop plausibly wants queued input to survive a
line tweak. Use `bazlang_input(clear)` directly if you need to flush without a reload (e.g. you
queued the wrong key and want to cancel it before the programme consumes it).

## Error codes

| Code | Meaning |
| --- | --- |
| `-32700` | Parse error — the line wasn't valid JSON |
| `-32600` | Invalid Request — not a JSON object, or missing `method` |
| `-32601` | Method not found |
| `-32602` | Invalid params — unknown tool name, or missing `tools/call.name` |
| `-32603` | Internal error — an unexpected exception while dispatching a request |
| `-32022` | Unsupported protocol version (see `error.data.supported`/`requested`) |

A tool that fails because of bad arguments or a BASIC runtime error (e.g. an undefined variable, an
invalid breakpoint condition, calling `bazlang_step(go)` when not paused) is **not** one of these —
it's a normal `tools/call` result with `isError: true`, per the spec's distinction between protocol
errors and tool execution errors.

## Known limitations

- **Modern-only, no legacy fallback** (see above) — a real compatibility risk with MCP clients that
  haven't adopted 2026-07-28 yet.
- **No true cancellation.** `notifications/cancelled` is accepted but has no effect — a `tools/call`
  always runs to completion. Not a regression: `AgentDebugger` has no cancel-while-running
  mechanism either.
- **No `listChanged`.** The tool set is static, so `tools/list`'s long `ttlMs` is safe to cache.
- **One implicit session per process.** There's no protocol-level session (2026-07-28 has none) and
  no explicit session handle — the whole server process *is* the session, exactly as one
  `AgentDebugger` process is one debugging session today.
