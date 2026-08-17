package com.davidconneely.bazlang.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the MCP JSON-RPC stdio protocol documented in docs/mcp_server.md. Each test
 * spawns {@link McpServer} in a fresh JVM, feeds it a scripted newline-delimited JSON-RPC session
 * (each line built with {@link JsonValue}/{@link JsonWriter} rather than hand-typed JSON text, so
 * nothing needs manual escaping), and asserts on the *parsed* structure of each response line — raw
 * string pinning is too fragile for JSON key/whitespace layout to be worth pinning verbatim.
 */
class McpServerProtocolTest {

  private static final String PROTOCOL_VERSION = "2026-07-28";

  // ---- scripted-request builders ----

  private static String request(long id, String method, JsonValue.JsonObject params) {
    return JsonWriter.write(
        JsonValue.object()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params));
  }

  private static String request(long id, String method) {
    return request(id, method, JsonValue.object());
  }

  private static String notification(String method) {
    return JsonWriter.write(
        JsonValue.object()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", JsonValue.object()));
  }

  private static String toolCall(long id, String toolName, JsonValue.JsonObject arguments) {
    return request(
        id, "tools/call", JsonValue.object().put("name", toolName).put("arguments", arguments));
  }

  private static JsonValue.JsonObject withProtocolVersion(String version) {
    return JsonValue.object()
        .put("_meta", JsonValue.object().put("io.modelcontextprotocol/protocolVersion", version));
  }

  // ---- process/session harness ----

  private static List<JsonValue.JsonObject> runSession(String... scriptLines)
      throws IOException, InterruptedException {
    return runSession(0, scriptLines);
  }

  /**
   * Spawns {@link McpServer} in a fresh JVM, optionally waits {@code startupDelayMs} after it
   * starts (simulating a server that has been alive for a while — e.g. proving an {@code ELAPSE}
   * breakpoint measures from run-start, not from server/BreakpointEngine construction), writes
   * {@code scriptLines} (one JSON-RPC message per line), and returns the parsed response lines.
   */
  private static List<JsonValue.JsonObject> runSession(long startupDelayMs, String... scriptLines)
      throws IOException, InterruptedException {
    final String javaExe =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    final var pb =
        new ProcessBuilder(
            javaExe,
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-cp",
            System.getProperty("java.class.path"),
            McpServer.class.getName());
    pb.redirectErrorStream(false);
    final Process proc = pb.start();
    if (startupDelayMs > 0) {
      Thread.sleep(startupDelayMs);
    }
    final String script = String.join("\n", scriptLines) + "\n";
    proc.getOutputStream().write(script.getBytes(StandardCharsets.UTF_8));
    proc.getOutputStream().close();
    final String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "server did not exit within 30s");
    return stdout.lines().map(JsonParser::parse).map(v -> (JsonValue.JsonObject) v).toList();
  }

  private static JsonValue.JsonObject result(JsonValue.JsonObject response) {
    return response.getObject("result");
  }

  private static JsonValue.JsonObject error(JsonValue.JsonObject response) {
    return response.getObject("error");
  }

  @Test
  void discoverListAndFullDebugSession() throws Exception {
    String source = "10 LET X = 0\n20 LET X = X + 1\n30 GO TO 20";
    List<JsonValue.JsonObject> responses =
        runSession(
            request(1, "server/discover", withProtocolVersion(PROTOCOL_VERSION)),
            request(2, "tools/list", withProtocolVersion(PROTOCOL_VERSION)),
            toolCall(
                3,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            // Unconditional breakpoint: fires every visit to 20:1, so `go` below is guaranteed to
            // hit it again on the next loop iteration without risking an infinite run.
            toolCall(
                4,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "line", 20L, "statement", 1L)),
            toolCall(5, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(6, "bazlang_eval", JsonValue.objectOf("expression", "X")),
            toolCall(7, "bazlang_step", JsonValue.objectOf("action", "go")),
            toolCall(8, "bazlang_step", JsonValue.objectOf("action", "stop")));
    assertEquals(8, responses.size());

    // server/discover
    JsonValue.JsonObject discover = result(responses.get(0));
    assertEquals("complete", discover.getString("resultType"));
    JsonValue supportedVersion = discover.getArray("supportedVersions").get(0);
    assertEquals("2026-07-28", ((JsonValue.JsonString) supportedVersion).value());

    // tools/list
    JsonValue.JsonObject toolsList = result(responses.get(1));
    assertEquals(7, toolsList.getArray("tools").size());

    // bazlang_program load_source
    assertFalse(result(responses.get(2)).getBoolean("isError", true));

    // bazlang_breakpoint set (unconditional, at 20:1)
    assertFalse(result(responses.get(3)).getBoolean("isError", true));

    // bazlang_step run -> breaks at 20:1 the first time round (before X is incremented)
    JsonValue.JsonObject runResult = result(responses.get(4));
    assertFalse(runResult.getBoolean("isError", true));
    JsonValue.JsonObject runStructured = runResult.getObject("structuredContent");
    assertEquals("break", runStructured.getString("reason"));
    assertEquals(20, runStructured.getInt("line", -1));
    assertEquals(1, runStructured.getInt("stmt", -1));

    // bazlang_eval X -> 0 (LET X=X+1 at 20:1 hasn't executed yet)
    JsonValue.JsonObject evalResult = result(responses.get(5));
    assertFalse(evalResult.getBoolean("isError", true));
    assertEquals(0, evalResult.getObject("structuredContent").getInt("value", -1));

    // bazlang_step go -> resumes correctly (guarded against re-firing this exact visit), X
    // increments to 1, GOTO 20 loops back around, and the same breakpoint fires again.
    JsonValue.JsonObject goResult = result(responses.get(6));
    assertFalse(goResult.getBoolean("isError", true));
    assertEquals("break", goResult.getObject("structuredContent").getString("reason"));

    // bazlang_step stop -> succeeds without terminating the server (next response still arrives)
    JsonValue.JsonObject stopResult = result(responses.get(7));
    assertFalse(stopResult.getBoolean("isError", true));
    assertEquals("stopped", stopResult.getObject("structuredContent").getString("reason"));
  }

  @Test
  void unknownToolIsAProtocolError() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(toolCall(1, "bazlang_nonexistent", JsonValue.object()));
    assertEquals(1, responses.size());
    JsonValue.JsonObject err = error(responses.get(0));
    assertEquals(-32_602, err.getInt("code", 0));
    assertTrue(err.getString("message").contains("bazlang_nonexistent"));
  }

  @Test
  void unknownMethodIsMethodNotFound() throws Exception {
    List<JsonValue.JsonObject> responses = runSession(request(1, "bogus/method"));
    assertEquals(1, responses.size());
    assertEquals(-32_601, error(responses.get(0)).getInt("code", 0));
  }

  @Test
  void malformedJsonIsParseError() throws Exception {
    List<JsonValue.JsonObject> responses = runSession("{not json");
    assertEquals(1, responses.size());
    JsonValue.JsonObject response = responses.get(0);
    assertEquals(-32_700, error(response).getInt("code", 0));
    assertTrue(response.get("id") instanceof JsonValue.JsonNull);
  }

  @Test
  void mismatchedProtocolVersionIsRejected() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(request(1, "tools/list", withProtocolVersion("2025-11-25")));
    assertEquals(1, responses.size());
    JsonValue.JsonObject err = error(responses.get(0));
    assertEquals(-32_022, err.getInt("code", 0));
    JsonValue.JsonObject data = err.getObject("data");
    assertEquals("2025-11-25", data.getString("requested"));
    assertEquals(1, data.getArray("supported").size());
  }

  @Test
  void notificationsProduceNoResponseAndGoWithoutPauseIsAnError() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(
            notification("notifications/cancelled"),
            toolCall(1, "bazlang_step", JsonValue.objectOf("action", "go")));
    // Only the id=1 request gets a response; the notification is silently accepted.
    assertEquals(1, responses.size());
    JsonValue.JsonObject goResult = result(responses.get(0));
    assertTrue(goResult.getBoolean("isError", false));
  }

  @Test
  void runWithNoProgrammeLoadedIsAToolExecutionError() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(toolCall(1, "bazlang_step", JsonValue.objectOf("action", "run")));
    assertEquals(1, responses.size());
    assertTrue(result(responses.get(0)).getBoolean("isError", false));
    assertNull(error(responses.get(0)));
  }

  @Test
  void elapseBreakpointMeasuresFromRunNotFromServerStartup() throws Exception {
    // Regression test: BreakpointEngine's ELAPSE clock starts ticking at construction (server
    // startup). run()/gotoLine() must reset it, or a long-lived server's first `run` sees a
    // breakpoint that is already "overdue" and fires on the very first statement, before the
    // programme has done anything. Simulate "long-lived" with a startup delay well past the
    // breakpoint's own threshold, then prove real work happened before the pause.
    String source = "10 LET N = 0\n20 LET N = N + 1\n30 GO TO 20";
    JsonValue.JsonObject elapseCondition =
        JsonValue.objectOf("type", "elapse", "milliseconds", 50L);
    List<JsonValue.JsonObject> responses =
        runSession(
            300,
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(
                2,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "condition", elapseCondition)),
            toolCall(3, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(4, "bazlang_eval", JsonValue.objectOf("expression", "N")));
    assertEquals(4, responses.size());
    JsonValue.JsonObject runResult = result(responses.get(2));
    assertFalse(runResult.getBoolean("isError", true));
    assertEquals("elapse", runResult.getObject("structuredContent").getString("reason"));
    // If the clock had measured from server startup, the 50ms threshold would already have been
    // exceeded by the 300ms startup delay, and the breakpoint would fire before line 20 ever ran.
    JsonValue.JsonObject evalResult = result(responses.get(3));
    assertFalse(evalResult.getBoolean("isError", true));
    assertTrue(evalResult.getObject("structuredContent").getInt("value", 0) > 0);
  }

  @Test
  void breakpointsDoNotInterceptReplCommands() throws Exception {
    // Regression test: Interpreter.executeImmediate (used by LOAD, NEW, a numbered-line edit, and
    // an assignment via applyReplCommand/executeAssignment) dispatches through the same
    // ExecutionListener as real programme execution, using line 0 as a sentinel. An unconditional
    // breakpoint (condition type NONE, matched via checkFired's "true" case) used to fire on that
    // dispatch too, setting running=false *before* Interpreter.resume() reached
    // executor.execute(stmt) — silently cancelling the REPL command while InterpreterReplHandler
    // still reported success, since it has no way to distinguish "cancelled by a breakpoint" from
    // "ran fine". A location/condition breakpoint isn't needed to trigger it: an unconditional one
    // (no line, no statement, no condition) always fires, making this fully deterministic.
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", "10 LET X = 1")),
            toolCall(2, "bazlang_breakpoint", JsonValue.objectOf("action", "set")),
            toolCall(
                3,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", "20 LET X = 2")),
            toolCall(4, "bazlang_program", JsonValue.objectOf("action", "list")));
    assertEquals(4, responses.size());
    assertFalse(result(responses.get(2)).getBoolean("isError", true));
    JsonValue.JsonObject listResult = result(responses.get(3));
    assertEquals("20 LET X = 2", listResult.getObject("structuredContent").getString("listing"));
  }

  @Test
  void loadingANewProgrammeFlushesQueuedInput() throws Exception {
    // Found via live use: on one long-lived engine, input queued while one programme was loaded
    // (or left over from a previous one) could be silently consumed by the next programme loaded,
    // if that programme happened to read a different input primitive than the one the queued text
    // was originally meant for — see docs/mcp_server.md "Input queue". bazlang_program's
    // new/load_file/load_source actions now flush all queued input before the new programme runs.
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(1, "bazlang_input", JsonValue.objectOf("action", "queue", "text", "Z")),
            toolCall(
                2,
                "bazlang_program",
                JsonValue.objectOf(
                    "action", "load_source", "source", "10 LET A$ = INKEY$\n20 STOP")),
            toolCall(3, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(4, "bazlang_eval", JsonValue.objectOf("expression", "A$")));
    assertEquals(4, responses.size());
    JsonValue.JsonObject evalResult = result(responses.get(3));
    assertFalse(evalResult.getBoolean("isError", true));
    assertEquals("", evalResult.getObject("structuredContent").getString("value"));
  }

  @Test
  void bazlangProgramSaveFileWritesAndRoundTrips() throws Exception {
    Path tempFile = Files.createTempFile("bazlang-mcp-test-", ".bas");
    try {
      String source = "10 LET X = 1\n20 PRINT X";
      List<JsonValue.JsonObject> responses =
          runSession(
              toolCall(
                  1,
                  "bazlang_program",
                  JsonValue.objectOf("action", "load_source", "source", source)),
              toolCall(
                  2,
                  "bazlang_program",
                  JsonValue.objectOf("action", "save_file", "path", tempFile.toString())),
              toolCall(3, "bazlang_program", JsonValue.objectOf("action", "new")),
              toolCall(
                  4,
                  "bazlang_program",
                  JsonValue.objectOf("action", "load_file", "path", tempFile.toString())),
              toolCall(5, "bazlang_program", JsonValue.objectOf("action", "list")));
      assertEquals(5, responses.size());
      assertFalse(result(responses.get(1)).getBoolean("isError", true));
      JsonValue.JsonObject listResult = result(responses.get(4));
      assertFalse(listResult.getBoolean("isError", true));
      assertEquals(source, listResult.getObject("structuredContent").getString("listing"));
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void bazlangStackReportsGosubFramesAndForLoops() throws Exception {
    // GOSUB return frames have no BASIC-expression equivalent (unlike variables, which ?X can
    // always reach), so bazlang_stack is the only way to see them.
    String source =
        String.join(
            "\n",
            "10 FOR I=1 TO 3",
            "20 GOSUB 100",
            "30 NEXT I",
            "40 STOP",
            "100 LET X=1",
            "110 RETURN");
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(
                2,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "line", 100L, "statement", 1L)),
            toolCall(3, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(4, "bazlang_stack", JsonValue.object()));
    assertEquals(4, responses.size());
    JsonValue.JsonObject runResult = result(responses.get(2));
    assertEquals("break", runResult.getObject("structuredContent").getString("reason"));

    JsonValue.JsonObject stack = result(responses.get(3)).getObject("structuredContent");
    JsonValue.JsonArray gosub = stack.getArray("gosub");
    assertEquals(1, gosub.size());
    JsonValue.JsonObject frame = (JsonValue.JsonObject) gosub.get(0);
    // GOSUB 100 is the sole statement on line 20, so the pushed return address is line 20,
    // statement 2 (StatementExecutor.executeGosubStmt: currentStatementIndex() + 1) — line 20 has
    // no statement 2, so execution actually resumes on line 30 once RETURN restores this address.
    assertEquals(20, frame.getInt("line", -1));
    assertEquals(2, frame.getInt("statement", -1));

    JsonValue.JsonArray forLoops = stack.getArray("forLoops");
    assertEquals(1, forLoops.size());
    JsonValue.JsonObject loop = (JsonValue.JsonObject) forLoops.get(0);
    assertEquals("I", loop.getString("variable"));
    assertEquals(1, loop.getInt("current", -1)); // paused before line 100's body runs
    assertEquals(3, loop.getInt("limit", -1));
    assertEquals(1, loop.getInt("step", -1));
    assertEquals(10, loop.getInt("loopLine", -1));
    assertEquals(1, loop.getInt("loopStatement", -1));
  }

  @Test
  void bazlangBreakpointListReportsActiveBreakpoints() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_breakpoint",
                JsonValue.objectOf(
                    "action",
                    "set",
                    "line",
                    100L,
                    "statement",
                    1L,
                    "condition",
                    JsonValue.objectOf("type", "csc", "text", "Game Over"))),
            toolCall(
                2, "bazlang_breakpoint", JsonValue.objectOf("action", "set", "persistent", false)),
            toolCall(3, "bazlang_breakpoint", JsonValue.objectOf("action", "list")),
            toolCall(4, "bazlang_breakpoint", JsonValue.objectOf("action", "clear_all")),
            toolCall(5, "bazlang_breakpoint", JsonValue.objectOf("action", "list")));
    assertEquals(5, responses.size());

    JsonValue.JsonArray first =
        result(responses.get(2)).getObject("structuredContent").getArray("breakpoints");
    assertEquals(2, first.size());
    JsonValue.JsonObject located = (JsonValue.JsonObject) first.get(0);
    assertEquals(100, located.getInt("line", -1));
    assertEquals(1, located.getInt("statement", -1));
    assertTrue(located.getBoolean("persistent", false));
    assertEquals("csc", located.getObject("condition").getString("type"));
    assertEquals("Game Over", located.getObject("condition").getString("text"));
    JsonValue.JsonObject oneShot = (JsonValue.JsonObject) first.get(1);
    assertFalse(oneShot.has("line"));
    assertFalse(oneShot.getBoolean("persistent", true));
    assertNull(oneShot.get("condition"));

    // clear_all only removes persistent breakpoints, so the one-shot one survives.
    JsonValue.JsonArray second =
        result(responses.get(4)).getObject("structuredContent").getArray("breakpoints");
    assertEquals(1, second.size());
    assertFalse(((JsonValue.JsonObject) second.get(0)).getBoolean("persistent", true));
  }

  @Test
  void bazlangEvalVarsListsScalarVariables() throws Exception {
    String source = "10 LET X = 42\n20 LET A$ = \"hi\"\n30 STOP";
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(2, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(3, "bazlang_eval", JsonValue.objectOf("action", "vars")));
    assertEquals(3, responses.size());
    JsonValue.JsonObject structured = result(responses.get(2)).getObject("structuredContent");
    assertEquals(42, structured.getObject("numeric").getInt("X", -1));
    assertEquals("hi", structured.getObject("string").getString("A$"));
  }

  @Test
  void bazlangEvalVarsListsArraysAndFunctions() throws Exception {
    String source =
        String.join(
            "\n",
            "10 DIM A(3)",
            "20 LET A(1) = 9",
            "30 DIM B$(2,4)",
            "40 DEF FN F(X) = X * 2",
            "50 STOP");
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(2, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(3, "bazlang_eval", JsonValue.objectOf("action", "vars")),
            // Array metadata is name+dimensions only; a known element still reads directly.
            toolCall(4, "bazlang_eval", JsonValue.objectOf("expression", "A(1)")));
    assertEquals(4, responses.size());
    JsonValue.JsonObject structured = result(responses.get(2)).getObject("structuredContent");

    JsonValue.JsonArray dimsA = structured.getObject("numericArrays").getArray("A");
    assertEquals(1, dimsA.size());
    assertEquals(3, ((JsonValue.JsonNumber) dimsA.get(0)).intValue());

    // DIM B$(2,4): the trailing dimension is the fixed string length, not an array dimension —
    // arrayDimensions is [2] (one dimension) with stringLength 4, not [2,4].
    JsonValue.JsonObject bMeta = structured.getObject("stringArrays").getObject("B$");
    JsonValue.JsonArray dimsB = bMeta.getArray("dimensions");
    assertEquals(1, dimsB.size());
    assertEquals(2, ((JsonValue.JsonNumber) dimsB.get(0)).intValue());
    assertEquals(4, bMeta.getInt("stringLength", -1));

    JsonValue.JsonArray paramsF = structured.getObject("functions").getArray("F");
    assertEquals(1, paramsF.size());

    assertEquals(9, result(responses.get(3)).getObject("structuredContent").getInt("value", -1));
  }

  @Test
  void bazlangEvalArrayReturnsFullContentsInRowMajorOrder() throws Exception {
    String source =
        String.join(
            "\n",
            "10 DIM A(2,2)",
            "20 LET A(1,1) = 1",
            "30 LET A(1,2) = 2",
            "40 LET A(2,1) = 3",
            "50 LET A(2,2) = 4",
            "60 DIM B$(2,3)",
            "70 LET B$(1) = \"X\"",
            "80 LET B$(2) = \"YZ\"",
            "90 STOP");
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(2, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(3, "bazlang_eval", JsonValue.objectOf("action", "array", "name", "A")),
            toolCall(4, "bazlang_eval", JsonValue.objectOf("action", "array", "name", "B$")),
            toolCall(5, "bazlang_eval", JsonValue.objectOf("action", "array", "name", "NOSUCH")));
    assertEquals(5, responses.size());

    JsonValue.JsonObject aResult = result(responses.get(2)).getObject("structuredContent");
    JsonValue.JsonArray aValues = aResult.getArray("values");
    assertEquals(4, aValues.size());
    // Row-major, last dimension fastest: A(1,1),A(1,2),A(2,1),A(2,2).
    for (int i = 0; i < 4; i++) {
      assertEquals(i + 1, ((JsonValue.JsonNumber) aValues.get(i)).intValue());
    }

    JsonValue.JsonObject bResult = result(responses.get(3)).getObject("structuredContent");
    JsonValue.JsonArray bValues = bResult.getArray("values");
    assertEquals(2, bValues.size());
    // Fixed-length, space-padded to stringLength 3 — padding is part of the value, not trimmed.
    assertEquals("X  ", ((JsonValue.JsonString) bValues.get(0)).value());
    assertEquals("YZ ", ((JsonValue.JsonString) bValues.get(1)).value());

    assertTrue(result(responses.get(4)).getBoolean("isError", false));
  }

  @Test
  void bazlangEvalExecRunsAnyImmediateStatementNotJustLet() throws Exception {
    // DIM is not a LET statement, so action=eval's LET-detection cue could never reach it — exec
    // is the only way to run it (or any other non-assignment statement) in the live context.
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1, "bazlang_eval", JsonValue.objectOf("action", "exec", "statement", "DIM Q(3)")),
            toolCall(
                2,
                "bazlang_eval",
                JsonValue.objectOf("action", "exec", "statement", "LET Q(2) = 42")),
            toolCall(3, "bazlang_eval", JsonValue.objectOf("expression", "Q(2)")),
            toolCall(
                4,
                "bazlang_eval",
                JsonValue.objectOf("action", "exec", "statement", "NONSENSE HERE")));
    assertEquals(4, responses.size());
    assertFalse(result(responses.get(0)).getBoolean("isError", true));
    assertFalse(result(responses.get(1)).getBoolean("isError", true));
    assertEquals(42, result(responses.get(2)).getObject("structuredContent").getInt("value", -1));
    assertTrue(result(responses.get(3)).getBoolean("isError", false));
  }

  @Test
  void loadFileAndSaveFileEscapeEmbeddedQuotesRatherThanInjectingStatements() throws Exception {
    // A path containing an unescaped '"' used to close the synthesized LOAD "..."/SAVE "..."
    // literal early, letting the rest of the path be parsed as further ':'-separated BASIC
    // statements — e.g. this exact path would execute NEW as a second statement if unescaped.
    String maliciousPath = "nonexistent\" : NEW : REM ";
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", "10 LET X = 1")),
            toolCall(
                2,
                "bazlang_program",
                JsonValue.objectOf("action", "load_file", "path", maliciousPath)),
            toolCall(3, "bazlang_program", JsonValue.objectOf("action", "list")));
    assertEquals(3, responses.size());
    // The whole malicious string is treated as one (nonexistent) filename, so this fails with a
    // file-not-found error rather than succeeding and silently having run NEW.
    assertTrue(result(responses.get(1)).getBoolean("isError", false));
    // If NEW had actually executed, the programme would be empty here instead.
    assertEquals(
        "10 LET X = 1",
        result(responses.get(2)).getObject("structuredContent").getString("listing"));
  }

  @Test
  void loadFileAndSaveFileRejectPathsContainingALineBreak() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_file", "path", "foo\nbar")),
            toolCall(
                2,
                "bazlang_program",
                JsonValue.objectOf("action", "save_file", "path", "foo\nbar")));
    assertEquals(2, responses.size());
    assertTrue(result(responses.get(0)).getBoolean("isError", false));
    assertTrue(result(responses.get(1)).getBoolean("isError", false));
  }

  @Test
  void bazlangStepIntoAndStepOverWalkThroughAGosubCall() throws Exception {
    String source = "10 GOSUB 100\n20 STOP\n100 LET X = 1\n110 RETURN";
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(
                2,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "line", 10L, "statement", 1L)),
            toolCall(3, "bazlang_step", JsonValue.objectOf("action", "run")),
            // step_into follows the GOSUB rather than treating it as one atomic statement.
            toolCall(4, "bazlang_step", JsonValue.objectOf("action", "step_into")));
    assertEquals(4, responses.size());
    JsonValue.JsonObject intoResult = result(responses.get(3)).getObject("structuredContent");
    assertEquals("step", intoResult.getString("reason"));
    assertEquals(100, intoResult.getInt("line", -1));
    assertEquals(1, intoResult.getInt("stmt", -1));

    // step_over, from the same starting breakpoint, treats the whole call as one step instead.
    List<JsonValue.JsonObject> overResponses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(
                2,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "line", 10L, "statement", 1L)),
            toolCall(3, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(4, "bazlang_step", JsonValue.objectOf("action", "step_over")),
            toolCall(5, "bazlang_eval", JsonValue.objectOf("expression", "X")));
    assertEquals(5, overResponses.size());
    JsonValue.JsonObject overResult = result(overResponses.get(3)).getObject("structuredContent");
    assertEquals("step", overResult.getString("reason"));
    assertEquals(20, overResult.getInt("line", -1));
    assertEquals(1, overResult.getInt("stmt", -1));
    assertEquals(
        1, result(overResponses.get(4)).getObject("structuredContent").getInt("value", -1));
  }

  @Test
  void stepIntoAndStepOverRequireBeingPaused() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(1, "bazlang_step", JsonValue.objectOf("action", "step_into")),
            toolCall(2, "bazlang_step", JsonValue.objectOf("action", "step_over")));
    assertEquals(2, responses.size());
    assertTrue(result(responses.get(0)).getBoolean("isError", false));
    assertTrue(result(responses.get(1)).getBoolean("isError", false));
  }

  @Test
  void bazlangStepRunPausesWithLimitReasonForARunawayLoop() throws Exception {
    // Regression/feature test: a programme with no breakpoint of its own must not be able to hang
    // the call (and, for a real server, the whole single-threaded session) forever.
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", "10 GO TO 10")),
            toolCall(2, "bazlang_step", JsonValue.objectOf("action", "run", "timeoutMs", 50L)));
    assertEquals(2, responses.size());
    JsonValue.JsonObject runResult = result(responses.get(1));
    assertFalse(runResult.getBoolean("isError", true));
    JsonValue.JsonObject structured = runResult.getObject("structuredContent");
    assertEquals("limit", structured.getString("reason"));
    assertEquals(10, structured.getInt("line", -1));
    assertEquals(1, structured.getInt("stmt", -1));
  }

  @Test
  void bazlangStepStatusReportsPauseStateWithoutExecuting() throws Exception {
    String source = "10 LET X = 1\n20 STOP";
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(1, "bazlang_step", JsonValue.objectOf("action", "status")),
            toolCall(
                2,
                "bazlang_program",
                JsonValue.objectOf("action", "load_source", "source", source)),
            toolCall(
                3,
                "bazlang_breakpoint",
                JsonValue.objectOf("action", "set", "line", 20L, "statement", 1L)),
            toolCall(4, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(5, "bazlang_step", JsonValue.objectOf("action", "status")),
            toolCall(6, "bazlang_step", JsonValue.objectOf("action", "go")),
            toolCall(7, "bazlang_step", JsonValue.objectOf("action", "status")));
    assertEquals(7, responses.size());

    // Before anything is loaded: not paused.
    JsonValue.JsonObject beforeLoad = result(responses.get(0)).getObject("structuredContent");
    assertFalse(beforeLoad.getBoolean("paused", true));

    // Paused at the breakpoint: status reflects it without needing another run/go call.
    JsonValue.JsonObject whilePaused = result(responses.get(4)).getObject("structuredContent");
    assertTrue(whilePaused.getBoolean("paused", false));
    assertEquals(20, whilePaused.getInt("line", -1));
    assertEquals(1, whilePaused.getInt("stmt", -1));

    // After go() runs the programme to completion (STOP): not paused any more.
    JsonValue.JsonObject afterStop = result(responses.get(6)).getObject("structuredContent");
    assertFalse(afterStop.getBoolean("paused", true));
  }

  @Test
  void inputClearActionDiscardsQueuedText() throws Exception {
    // Load first, so the load itself has nothing queued to flush yet — isolates action=clear's own
    // effect from the auto-flush-on-load behaviour covered by the test above.
    List<JsonValue.JsonObject> responses =
        runSession(
            toolCall(
                1,
                "bazlang_program",
                JsonValue.objectOf(
                    "action", "load_source", "source", "10 LET A$ = INKEY$\n20 STOP")),
            toolCall(2, "bazlang_input", JsonValue.objectOf("action", "queue", "text", "Z")),
            toolCall(3, "bazlang_input", JsonValue.objectOf("action", "clear")),
            toolCall(4, "bazlang_step", JsonValue.objectOf("action", "run")),
            toolCall(5, "bazlang_eval", JsonValue.objectOf("expression", "A$")));
    assertEquals(5, responses.size());
    assertFalse(result(responses.get(2)).getBoolean("isError", true));
    JsonValue.JsonObject evalResult = result(responses.get(4));
    assertFalse(evalResult.getBoolean("isError", true));
    assertEquals("", evalResult.getObject("structuredContent").getString("value"));
  }
}
