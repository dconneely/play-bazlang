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
 * spawns {@link McpServer} in a fresh JVM, feeds it a scripted newline-delimited JSON-RPC session,
 * and asserts on the *parsed* structure of each response line — raw string pinning is too fragile
 * for JSON (key/whitespace layout), unlike the plain-text AgentDebugger protocol.
 */
class McpServerProtocolTest {

  private static final String META =
      "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}";

  private static List<JsonValue.JsonObject> runSession(String script)
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
    String script =
        String.join(
            "\n",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{"
                + META
                + "}}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{" + META + "}}",
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_program\",\"arguments\":{\"action\":\"load_source\","
                + "\"source\":\"10 LET X = 0\\n20 LET X = X + 1\\n30 GO TO 20\"}}}",
            // Unconditional breakpoint: fires every visit to 20:1, so `go` below is guaranteed to
            // hit it again on the next loop iteration without risking an infinite run.
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_breakpoint\",\"arguments\":{\"action\":\"set\",\"line\":20,"
                + "\"statement\":1}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_step\",\"arguments\":{\"action\":\"run\"}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_eval\",\"arguments\":{\"expression\":\"X\"}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_step\",\"arguments\":{\"action\":\"go\"}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_step\",\"arguments\":{\"action\":\"stop\"}}}",
            "");
    List<JsonValue.JsonObject> responses = runSession(script);
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
    String script =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{"
            + META
            + ",\"name\":\"bazlang_nonexistent\",\"arguments\":{}}}\n";
    List<JsonValue.JsonObject> responses = runSession(script);
    assertEquals(1, responses.size());
    JsonValue.JsonObject err = error(responses.get(0));
    assertEquals(-32_602, err.getInt("code", 0));
    assertTrue(err.getString("message").contains("bazlang_nonexistent"));
  }

  @Test
  void unknownMethodIsMethodNotFound() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"bogus/method\",\"params\":{}}\n");
    assertEquals(1, responses.size());
    assertEquals(-32_601, error(responses.get(0)).getInt("code", 0));
  }

  @Test
  void malformedJsonIsParseError() throws Exception {
    List<JsonValue.JsonObject> responses = runSession("{not json\n");
    assertEquals(1, responses.size());
    JsonValue.JsonObject response = responses.get(0);
    assertEquals(-32_700, error(response).getInt("code", 0));
    assertTrue(response.get("id") instanceof JsonValue.JsonNull);
  }

  @Test
  void mismatchedProtocolVersionIsRejected() throws Exception {
    String script =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":"
            + "{\"io.modelcontextprotocol/protocolVersion\":\"2025-11-25\"}}}\n";
    List<JsonValue.JsonObject> responses = runSession(script);
    assertEquals(1, responses.size());
    JsonValue.JsonObject err = error(responses.get(0));
    assertEquals(-32_022, err.getInt("code", 0));
    JsonValue.JsonObject data = err.getObject("data");
    assertEquals("2025-11-25", data.getString("requested"));
    assertEquals(1, data.getArray("supported").size());
  }

  @Test
  void notificationsProduceNoResponseAndGoWithoutPauseIsAnError() throws Exception {
    String script =
        String.join(
            "\n",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_step\",\"arguments\":{\"action\":\"go\"}}}",
            "");
    List<JsonValue.JsonObject> responses = runSession(script);
    // Only the id=1 request gets a response; the notification is silently accepted.
    assertEquals(1, responses.size());
    JsonValue.JsonObject goResult = result(responses.get(0));
    assertTrue(goResult.getBoolean("isError", false));
  }

  @Test
  void runWithNoProgrammeLoadedIsAToolExecutionError() throws Exception {
    List<JsonValue.JsonObject> responses =
        runSession(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"bazlang_step\",\"arguments\":{\"action\":\"run\"}}}\n");
    assertEquals(1, responses.size());
    assertTrue(result(responses.get(0)).getBoolean("isError", false));
    assertNull(error(responses.get(0)));
  }
}
