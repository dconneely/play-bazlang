# Testing

The test strategy, and — the part that only lives here — what is deliberately **not** covered.

## What is verified

Three layers, all JUnit 5:

- **Program-level tests** (`app-bazlang/src/test/java/.../program/*ProgramTest.java`, extending
  `BaseProgramTest`) — run a whole BazLang programme against a `MockScreen` and assert on the
  resulting screen state, variables, or report. This is the main coverage for language semantics:
  broadly one test class per statement or feature (`ForNextProgramTest`, `DataReadProgramTest`,
  `PlayProgramTest`, `BeepProgramTest`, `RenumProgramTest`, and so on). `ExampleProgramTest` /
  `ExampleParseTest` run the example games in `src/example/bas/` themselves, and
  `ExampleGameSoundEffectsTest` exercises `BEEP`/`PLAY`/`APLAY` inside them.
- **Component-level tests** (alongside the production classes, e.g. `exec/`, `exec/ast/`, `edit/`,
  `io/`, `debug/`, `mcp/`, `play/`) — drive one class directly: `ExpressionEvaluatorTest`,
  `StatementExecutorTest`, `EvalStateTest`, `AstLoweringStatementTest`, `ReformatVisitorTest`,
  `BreakpointEngineTest`, `DebugEngineTest`, `McpServerProtocolTest`, `JsonCodecTest`,
  `PlayParserTest`, `PlaySequencerTest`, `TerminalScreenTest`, `StreamScreenTest`,
  `ProgramStorageTest`, plus lexer/parser tests (`LexerTest`, `ParserTest`, `AntlrParserTest`) and
  value-type tests (`BStrTest`, `ReportCodeTest`, `ReportExceptionTest`). Since ANTLR has no builder
  API for parse trees, these parse small source snippets and feed the resulting contexts to the
  component under test, rather than constructing AST nodes by hand.
- **Library tests** — `lib-cell` (`CellBufferTest`, `CellAttributesTest`, `CellBufferRendererTest`,
  `PixelModeTest`) and `lib-repl` (`ReplTest`, `NativeProcessClipboardTest`) are tested in complete
  isolation from BazLang; neither library depends on `app-bazlang`.

## What is verified only approximately

Nothing currently relies on sampling, snapshot, or tolerance-based assertions — program-level tests
assert exact screen/variable state, and component tests assert exact values.

## What is deliberately not covered

- **Real audio device output.** `TerminalScreen`'s actual `javax.sound.sampled` playback (the tone
  synthesis behind `BEEP`/`PLAY`/`APLAY`) is not exercised end-to-end — there is no audio device in
  CI. `TerminalScreenTest` covers the pure pitch-to-Hz conversion in isolation instead; the
  `StatementExecutor`-level DSL parsing, scheduling, and BREAK-handling logic that sits above the
  device is fully covered by `PlayParserTest`/`PlaySequencerTest`/the program-level `Play`/`Beep`
  tests, since all of that runs through the headless-testable `VirtualSpeaker` no-op default.
- **Interactive terminal rendering.** `TerminalScreen`'s raw-mode JLine wiring (window resize
  handling, real ANSI escape sequences) is not tested headlessly for the same reason `lib-repl`
  isn't: it requires a real terminal. `StreamScreen` and `MockScreen` carry the headless-testable
  contract instead.
- **Whole-screen baseline/snapshot output for the interactive example games** (e.g. `lander.bas`,
  `monster.bas`) — there is no automated comparison of a scripted playthrough's final screen state
  against a stored snapshot. Program-level tests cover individual language features in isolation
  instead. This is a known gap, not an oversight; see `PLAN.md`.

## Running them

```bash
./gradlew test
```

`./gradlew clean build` also runs Checkstyle, PMD, SpotBugs and Spotless checks ahead of the test
task.
