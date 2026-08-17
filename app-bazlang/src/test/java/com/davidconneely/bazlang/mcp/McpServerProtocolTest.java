package com.davidconneely.bazlang.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    assertEquals(6, toolsList.getArray("tools").size());

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
