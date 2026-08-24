# McpServer

`McpServer` is a native MCP (Model Context Protocol) server exposing BazLang programme debugging
as JSON-RPC tools, so any MCP client can attach directly and drive a running programme. `McpServer`
is a thin JSON-RPC adapter over [`DebugEngine`](architecture.md), which owns the interpreter,
breakpoints, and mock screen for the session.

## Running the server

```bash
./gradlew :app-bazlang:runMcpServer
```

The server prints one line to stderr on startup (agents should ignore stderr) and then waits on
stdin for newline-delimited JSON-RPC requests. There is no `+READY` line and no preload-by-argument
mode - load a programme with the `bazlang_program` tool once connected.

## Protocol version: 2026-07-28, modern-only

This server targets the **2026-07-28 MCP specification** - the stateless protocol revision - and
only that revision. Two consequences worth knowing before integrating:

- **No `initialize` handshake, no protocol session.** Every request carries its own protocol
  version in `_meta`; there is nothing to negotiate up front. State (the loaded programme,
  breakpoints, variables) lives in one `DebugEngine` instance for the lifetime of the server
  process - since stdio is already one subprocess per client, this is equivalent to a protocol
  session without needing one.
- **No legacy fallback.** A client still speaking the pre-2026-07-28 `initialize`-handshake
  protocol cannot use this server - per the spec's own compatibility matrix, "modern server,
  legacy client" fails outright. This was a deliberate scope decision - see
  [ADR-0004](../adr/0004-mcp-modern-only-protocol.md) for why, including what it replaced.
- **Lenient version checking.** If a request omits `_meta`'s protocol version field entirely, the
  server proceeds anyway rather than rejecting it - some early modern clients may not yet send it
  on every request. If a version *is* present and doesn't match, the server responds with
  `UnsupportedProtocolVersionError` (code `-32022`) listing what it supports.

## Transport

Newline-delimited JSON-RPC 2.0 over stdin/stdout. Each request line produces exactly one response
line, except JSON-RPC *notifications* (a message with no `id`), which never get a response -
`notifications/cancelled` is accepted and silently ignored (there is no cancel-while-running
mechanism).

## `server/discover`

Required by the spec. Advertises the supported protocol version, capabilities, and server
identity - call it first if you want to confirm compatibility before anything else.

```jsonc
-> {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}}
<- {"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","supportedVersions":["2026-07-28"],
   "capabilities":{"tools":{"listChanged":false}},
   "_meta":{"io.modelcontextprotocol/serverInfo":{"name":"bazlang-mcp","version":"1.0.0"}},
   "instructions":"Debug BazLang programmes: load a programme, set breakpoints, step through
   execution, and inspect state via the bazlang_* tools.","ttlMs":3600000,"cacheScope":"public"}}
```

## `tools/list`

Returns the seven tools below (static - `listChanged` is `false`, and the list carries a long
`ttlMs`/`cacheScope: "public"` since it never changes).

## `tools/call`

Every tool response follows the standard envelope: `resultType: "complete"`, a human-readable
`content` text block, `isError` (true for both bad arguments and BASIC runtime errors - anything a
model might reasonably retry after adjusting its call), and a `structuredContent` object with the
same information in a programmatic shape where one is useful.

### `bazlang_program`

Manages the loaded programme: create a new empty programme, load one from a file or inline
source, save the current programme to a file, add/replace/delete a single numbered line, or list
the current programme text.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `new` | - | Clears the programme and all runtime state, and flushes queued input |
| `load_file` | `path` | Loads a `.bas` file (bare names resolve against the example directory); flushes queued input |
| `load_source` | `source` | Replaces the whole programme with inline multi-line source; flushes queued input |
| `save_file` | `path` | Writes the current programme to `path`, one numbered line per file line |
| `edit_line` | `line`, `statement` | Adds/replaces line `line`; blank `statement` deletes it. Does **not** flush queued input |
| `list` | - | Returns the current programme text in `content` and `structuredContent.listing` |

`load_source` loads a whole multi-line programme (one BASIC line per `\n`) in one call, rather than
requiring one `edit_line` call per line. See "Input queue" below for why `new`/`load_file`/
`load_source` flush and `edit_line` doesn't. `save_file`'s `path` is **not** resolved against the
example directory the way `load_file`'s bare names are (matching the plain `SAVE` statement) - pass
a full or relative path, and it does not flush queued input, since it doesn't touch the loaded
programme.

`load_file`/`save_file`'s `path` is embedded into a synthesized `LOAD "..."`/`SAVE "..."` statement,
with embedded `"` characters escaped (doubled, matching the language's own string-literal escape) so
a quote in `path` can't close the literal early and let the rest of `path` be parsed as further
`:`-separated BASIC statements. A `path` containing a line break is rejected outright (`isError`),
since a BazLang string literal can't represent one at all. A syntactically invalid path for the host
platform (e.g. a bare `:` on Windows) is reported as a normal file-not-found error, not a crash.

```jsonc
-> {"name":"bazlang_program","arguments":{"action":"load_source","source":"10 LET X=0\n20 PRINT X"}}
<- {"resultType":"complete","content":[{"type":"text","text":"Loaded programme (2 lines)"}],"isError":false}
```

### `bazlang_step`

Drives programme execution. `run`/`goto`/`go`/`step_into`/`step_over` block until the programme
next breaks, elapses, steps, hits its safety timeout, or stops, then return the pause reason - each
MCP call is already one full request/response round trip, so there is no separate "wait for the
next event" phase to model.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `run` | `timeoutMs` (optional) | Clears runtime state and runs from the first line. Error if no programme is loaded |
| `goto` | `line`, `timeoutMs` (optional) | Runs from `line` without clearing state |
| `go` | `timeoutMs` (optional) | Resumes from a breakpoint. Error if not currently paused |
| `step_into` | `timeoutMs` (optional) | Executes exactly one statement, entering any `GOSUB` it calls. Error if not currently paused |
| `step_over` | `timeoutMs` (optional) | Executes exactly one statement, running any `GOSUB` it calls to completion instead of pausing inside it. Error if not currently paused |
| `stop` | - | Terminates the running programme, without ending the server process |
| `status` | - | Reports the current pause state immediately, without executing anything |

`bazlang_step`'s `stop` action just resets run state so a later `run`/`goto` can start again - the
server stays up until stdin closes. `status` never blocks, unlike the other five actions - use it
to check whether the programme is currently paused (and where) without triggering execution, e.g.
after a few `bazlang_eval`/`bazlang_stack` calls while paused.

`step_into`/`step_over` require the programme to already be paused, exactly like `go` - to start
stepping from the very first line, set a breakpoint there and `run` first. A breakpoint inside a
call `step_over` is running to completion still interrupts it, so stepping over never accidentally
skips past a breakpoint an agent placed inside that call.

Every blocking action arms a wall-clock safety cap - default 30 seconds, overridable per call via
`timeoutMs` - so a programme with an accidental infinite loop and no breakpoint of its own can't
block the call (and, since the server processes one request at a time, the whole session) forever.
A `bazlang_step(go)` (or `step_into`/`step_over`) after hitting the cap simply keeps going, arming a
fresh deadline.

`structuredContent.reason` is one of `break` (with `line`/`stmt`), `elapse`, `step` (with
`line`/`stmt` - `step_into`/`step_over`'s landing spot), `limit` (with `line`/`stmt` - the safety
cap fired), `stop` (with `code`, `message`, `line`, `stmt` - see `ReportCode` for the code table),
or `stopped` (the `stop` action). `status`'s response instead carries `structuredContent.paused`
(boolean) plus `line`/`stmt` for the current (or last) execution position, regardless of whether the
programme is currently paused.

```jsonc
-> {"name":"bazlang_step","arguments":{"action":"run"}}
<- {"resultType":"complete","content":[{"type":"text","text":"BREAK AT 20:1"}],"isError":false,
   "structuredContent":{"reason":"break","line":20,"stmt":1}}
```

### `bazlang_breakpoint`

Sets or clears breakpoints, with optional conditions.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `set` | `persistent` (default `true`), `line`, `statement`, `condition` | Adds a breakpoint |
| `clear` | `line`, `statement` | Removes breakpoints registered at that exact location |
| `clear_all` | - | Removes every persistent breakpoint |
| `list` | - | Reports every currently-active breakpoint in `structuredContent.breakpoints` |

Omit `line`/`statement` for a condition-only breakpoint checked on every statement.
`persistent: false` fires the breakpoint once, then removes itself. `list` is the only way to
recover what's currently armed - there's no other query for it, so an agent that sets several
breakpoints across a session and loses track needs `list` rather than remembering its own calls.
Each entry has `line`/`statement` (omitted for a condition-only breakpoint), `persistent`, and
`condition` (omitted for an unconditional one) in the same shape `set` accepts.

`condition` is optional (omit it for an unconditional location breakpoint) and, when present, is
one of:

| `condition.type` | Fields | Fires when |
| --- | --- | --- |
| `csc` | `text` | The screen contains `text` (case-insensitive) |
| `elapse` | `milliseconds` | At least `milliseconds` of wall-clock time have elapsed since the last resume |
| `expr` | `expression` | The BazLang expression `expression` is truthy |
| `every` | `everyN` | Every `everyN`th time the condition is checked |

```jsonc
-> {"name":"bazlang_breakpoint","arguments":{"action":"set","line":100,"statement":1,
   "condition":{"type":"expr","expression":"FUEL<50"}}}
<- {"resultType":"complete","content":[{"type":"text","text":"OK"}],"isError":false}
```

### `bazlang_eval`

| `action` | Arguments | Effect |
| --- | --- | --- |
| `eval` (default) | `expression` | Evaluates `expression`, or executes it as a `LET` assignment |
| `exec` | `statement` | Executes any single immediate-mode statement, not just `LET` |
| `vars` | - | Lists every currently-defined variable, array, and `DEF FN` |
| `array` | `name` | Returns the full contents of array `name` |

`eval` covers both evaluation and assignment with one `expression` argument. A leading `LET` is the
disambiguating cue for assignment - everything else is evaluated as an expression. This is
deliberate, not a heuristic guess: bare `X=3` is a valid BazLang *expression* (an equality test), so
only an explicit `LET X=3` can mean "assign" without ambiguity.

`exec` runs `statement` exactly as the interactive REPL would with any single immediate-mode input
line - `GOSUB`, `PRINT`, `DIM`, `CLS`, `RESTORE`, `RANDOMIZE`, a bare numbered line, even `NEW`/
`LOAD`/`DELETE`/`RENUM` - a strict superset of `eval`'s `LET`-only assignment handling. Prefer
`bazlang_program`'s structured actions for programme management (`new`/`load_file`/`edit_line`/
etc.) - `exec` supports them too, but without the friendlier per-action argument shape or
`load_file`/`save_file`'s path-escaping (see above); it's meant for one-off statements like `GOSUB`
that have no dedicated tool at all.

`vars` returns `structuredContent.numeric`/`.string` (scalar name -> value), `.numericArrays`/
`.stringArrays` (array name -> dimensions - a string array's entry also carries `stringLength`, the
fixed per-element length from its trailing `DIM` dimension), and `.functions` (`DEF FN` name ->
parameter names), for exploring an unfamiliar or already-paused programme without knowing any names
up front - `eval` requires naming one. Array entries are metadata only, not their full contents.

`array` returns a whole array's data in one call instead of requiring one `eval` call per element -
`structuredContent.dimensions` plus a flattened `structuredContent.values` (row-major, last
dimension fastest, matching how `A(i,j)` itself is laid out; 1-based indices as in BASIC
subscripts). A string array's response also carries `stringLength`; each returned value is
space-padded to that fixed length, exactly as `A$(i)` itself would read (padding is part of the
value, not trimmed).

```jsonc
-> {"name":"bazlang_eval","arguments":{"expression":"SCORE"}}
<- {"resultType":"complete","content":[{"type":"text","text":"42"}],"isError":false,
   "structuredContent":{"value":42}}

-> {"name":"bazlang_eval","arguments":{"expression":"LET SCORE=0"}}
<- {"resultType":"complete","content":[{"type":"text","text":"OK"}],"isError":false}

-> {"name":"bazlang_eval","arguments":{"action":"exec","statement":"GOSUB 1000"}}
<- {"resultType":"complete","content":[{"type":"text","text":"OK"}],"isError":false}

-> {"name":"bazlang_eval","arguments":{"action":"vars"}}
<- {"resultType":"complete","content":[{"type":"text","text":"3 variable(s)/array(s)/function(s)"}],
   "isError":false,"structuredContent":{
     "numeric":{"SCORE":0},"string":{"NAME$":"ADA"},
     "numericArrays":{"HISCORES":[10]},"stringArrays":{},"functions":{"F":["X"]}}}

-> {"name":"bazlang_eval","arguments":{"action":"array","name":"HISCORES"}}
<- {"resultType":"complete","content":[{"type":"text","text":"10 element(s)"}],"isError":false,
   "structuredContent":{"dimensions":[10],"values":[100,90,80,70,60,50,40,30,20,10]}}
```

### `bazlang_screen`

Reads a rectangle of the virtual screen buffer, or resizes it.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `read` | `rowTop`, `colLeft`, `rowBottom`, `colRight`, `attr` (default `false`) | Dumps a screen rectangle into `content`/`structuredContent.grid` |
| `resize` | `rows`, `cols` | Resizes the virtual screen buffer |

`read`'s grid is a plain JSON string, one row per `\n`-separated line, with runs of five or more
spaces compressed to `{N}`. With `attr: true`, a `[fg,bg]` colour tag is prepended at the start of
each run of cells that share a colour (omitted when unchanged from the previous cell); `{N}` then
means N spaces sharing the current colour.

### `bazlang_input`

Queues keyboard/`INPUT` text for the programme to consume, or discards queued input.

| `action` | Arguments | Effect |
| --- | --- | --- |
| `queue` | `text` | Queues `text` for `INKEY$`/`UINKEY$`/`INPUT` to consume |
| `clear` | - | Discards all queued input without adding any |

`text` is a plain JSON string. `action=clear` is for cancelling a mis-queued value or resetting
mid-session without reloading the programme - see "Input queue" below for the far more common
case, which is handled automatically and needs no explicit call.

### `bazlang_stack`

Inspects interpreter call-stack state that no BazLang expression can reach - unlike variables,
which `bazlang_eval` can always read via `?X`, there's no BASIC syntax that exposes the GOSUB
return stack or FOR-loop bookkeeping. Takes no arguments.

```jsonc
-> {"name":"bazlang_stack","arguments":{}}
<- {"resultType":"complete","content":[{"type":"text","text":"1 GOSUB frame(s), 1 active FOR loop(s)"}],
   "isError":false,"structuredContent":{
     "gosub":[{"line":20,"statement":2}],
     "forLoops":[{"variable":"I","current":1,"limit":3,"step":1,"loopLine":10,"loopStatement":1}]}}
```

`structuredContent.gosub` is the return-address stack, innermost (most recently called) frame
first - each entry is where `RETURN` will resume execution, not where `GOSUB` was called from.
`structuredContent.forLoops` is every currently-active `FOR` loop, keyed by loop variable: `current`
is the loop variable's live value, `limit`/`step` are the `TO`/`STEP` bounds, and `loopLine`/
`loopStatement` is the `FOR` statement's own location (where `NEXT` jumps back to).

## Input queue

`bazlang_input(queue)` queues the same text for all three input primitives at once (`INKEY$` gets
it byte-by-byte, `UINKEY$` codepoint-by-codepoint, `INPUT` as one line), because the queuer can't
know in advance which one the programme will actually read. That's fine within one programme's
lifetime, but `DebugEngine`'s queues live on one
`MockScreen` for as long as the engine does - across many `bazlang_program` calls, not just one
run - so **anything queued but never consumed by one programme is still sitting there, in all three
queues, when the next programme loads.**

This was found the hard way: a session that queued single-key guesses for a hangman-style
programme (which only ever calls `INKEY$`) then loaded a real-time programme reading `UINKEY$`
found its very first keypress was actually a stale hangman guess, several calls deep into the
queue. A later session hit the same thing from the other side - a programme's first `INPUT` call
consumed a stale value queued many calls earlier by a completely different programme, and (correctly)
reported a parse error for it.

`bazlang_program`'s `new`, `load_file`, and `load_source` actions now flush all three queues
automatically whenever they replace the whole programme, so input queued for a programme you've
moved on from can never leak into the next one. `edit_line` deliberately does **not** flush -
editing one line of the *current* programme is a much smaller change than replacing it, and a
workflow that's iterating on one programme in a loop plausibly wants queued input to survive a
line tweak. Use `bazlang_input(clear)` directly if you need to flush without a reload (e.g. you
queued the wrong key and want to cancel it before the programme consumes it).

## Error codes

| Code | Meaning |
| --- | --- |
| `-32700` | Parse error - the line wasn't valid JSON |
| `-32600` | Invalid Request - not a JSON object, or missing `method` |
| `-32601` | Method not found |
| `-32602` | Invalid params - unknown tool name, or missing `tools/call.name` |
| `-32603` | Internal error - an unexpected exception while dispatching a request |
| `-32022` | Unsupported protocol version (see `error.data.supported`/`requested`) |

A tool that fails because of bad arguments or a BASIC runtime error (e.g. an undefined variable, an
invalid breakpoint condition, calling `bazlang_step(go)` when not paused) is **not** one of these -
it's a normal `tools/call` result with `isError: true`, per the spec's distinction between protocol
errors and tool execution errors.

## Known limitations

- **Modern-only, no legacy fallback** (see above) - a real compatibility risk with MCP clients that
  haven't adopted 2026-07-28 yet.
- **No true cancellation.** `notifications/cancelled` is accepted but has no effect - a `tools/call`
  always runs to completion; there is no cancel-while-running mechanism. `bazlang_step`'s
  `timeoutMs` safety cap (see above) is a mitigation, not a substitute: it guarantees a runaway
  programme with no breakpoint of its own can't block forever, but doesn't let an agent interrupt a
  call early on demand.
- **No `listChanged`.** The tool set is static, so `tools/list`'s long `ttlMs` is safe to cache.
- **One implicit session per process.** There's no protocol-level session (2026-07-28 has none) and
  no explicit session handle - the whole server process *is* the session, one process per debugging
  session.
