package com.davidconneely.bazlang.mcp;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.debug.DebugEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * MCP (Model Context Protocol) server entrypoint: a newline-delimited JSON-RPC 2.0 stdio server
 * exposing the {@link McpTools} catalog over one shared {@link DebugEngine} instance - one implicit
 * debugging session per server subprocess. Targets the stateless 2026-07-28 protocol revision only:
 * there is no {@code initialize} handshake and no legacy-protocol fallback. See docs/spec/mcp.md
 * for the full protocol surface, tool catalog, and known limitations.
 */
public final class McpServer {

  private static final String PROTOCOL_VERSION = "2026-07-28";
  private static final String META_PROTOCOL_VERSION_KEY = "io.modelcontextprotocol/protocolVersion";
  private static final String META_SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo";

  private McpServer() {}

  /**
   * Runs the server, reading newline-delimited JSON-RPC requests from stdin and writing responses
   * to stdout until EOF.
   *
   * @param args ignored.
   */
  public static void main(String[] args) {
    System.err.println("Running BazLang MCP server (protocol " + PROTOCOL_VERSION + ")");
    AntlrParser parser = AntlrParser.INSTANCE;
    DebugEngine engine = new DebugEngine(parser);
    McpDebugAdapter adapter = new McpDebugAdapter(engine);
    BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line;
    try {
      while ((line = in.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        handleLine(line, adapter);
        System.out.flush();
      }
    } catch (IOException e) {
      System.err.println("stdin read error: " + e.getMessage());
    }
  }

  // A long-lived server must not die from one bad request; convert any unexpected exception into
  // a JSON-RPC error response instead of crashing the process.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private static void handleLine(String line, McpDebugAdapter adapter) {
    JsonValue request;
    try {
      request = JsonParser.parse(line);
    } catch (JsonParser.JsonParseException e) {
      writeError(JsonValue.JsonNull.INSTANCE, -32_700, "Parse error: " + e.getMessage(), null);
      return;
    }
    if (!(request instanceof JsonValue.JsonObject obj)) {
      writeError(
          JsonValue.JsonNull.INSTANCE, -32_600, "Invalid Request: expected a JSON object", null);
      return;
    }
    JsonValue id = obj.get("id"); // genuinely absent (Java null) => notification: never respond
    if (id == null) {
      // e.g. notifications/cancelled: accepted, no-op - there is no cancel-while-running
      // mechanism.
      return;
    }
    String method = obj.getString("method");
    JsonValue.JsonObject params = obj.getObject("params");
    if (method == null) {
      writeError(id, -32_600, "Invalid Request: missing 'method'", null);
      return;
    }
    try {
      dispatch(id, method, params, adapter);
    } catch (RuntimeException e) {
      writeError(id, -32_603, "Internal error: " + e.getMessage(), null);
    }
  }

  private static void dispatch(
      JsonValue id, String method, JsonValue.JsonObject params, McpDebugAdapter adapter) {
    switch (method) {
      case "server/discover" -> handleDiscover(id);
      case "tools/list" -> {
        if (checkProtocolVersion(id, params)) {
          handleToolsList(id);
        }
      }
      case "tools/call" -> {
        if (checkProtocolVersion(id, params)) {
          handleToolsCall(id, params, adapter);
        }
      }
      default -> writeError(id, -32_601, "Method not found: " + method, null);
    }
  }

  /**
   * Returns {@code true} when the request is missing a protocol version (lenient - some early
   * modern clients may not yet send {@code _meta} on every request) or matches ours. Writes an
   * {@code UnsupportedProtocolVersionError} and returns {@code false} otherwise.
   */
  private static boolean checkProtocolVersion(JsonValue id, JsonValue.JsonObject params) {
    JsonValue.JsonObject meta = params != null ? params.getObject("_meta") : null;
    String requested = meta != null ? meta.getString(META_PROTOCOL_VERSION_KEY) : null;
    if (requested == null || requested.equals(PROTOCOL_VERSION)) {
      return true;
    }
    JsonValue.JsonObject data =
        JsonValue.object()
            .put("supported", JsonValue.array().add(JsonValue.of(PROTOCOL_VERSION)))
            .put("requested", requested);
    writeError(id, -32_022, "Unsupported protocol version", data);
    return false;
  }

  private static void handleDiscover(JsonValue id) {
    JsonValue.JsonObject meta =
        JsonValue.object()
            .put(
                META_SERVER_INFO_KEY,
                JsonValue.objectOf("name", "bazlang-mcp", "version", "1.0.0"));
    JsonValue.JsonObject result =
        JsonValue.object()
            .put("resultType", "complete")
            .put("supportedVersions", JsonValue.array().add(JsonValue.of(PROTOCOL_VERSION)))
            .put(
                "capabilities",
                JsonValue.object().put("tools", JsonValue.object().put("listChanged", false)))
            .put("_meta", meta)
            .put(
                "instructions",
                "Debug BazLang programmes: load a programme, set breakpoints, step through "
                    + "execution, and inspect state via the bazlang_* tools.")
            .put("ttlMs", 3_600_000L)
            .put("cacheScope", "public");
    writeResult(id, result);
  }

  private static void handleToolsList(JsonValue id) {
    JsonValue.JsonArray tools = JsonValue.array();
    for (JsonValue.JsonObject tool : McpTools.ALL) {
      tools.add(tool);
    }
    JsonValue.JsonObject result =
        JsonValue.object()
            .put("resultType", "complete")
            .put("tools", tools)
            .put("ttlMs", 3_600_000L)
            .put("cacheScope", "public");
    writeResult(id, result);
  }

  private static void handleToolsCall(
      JsonValue id, JsonValue.JsonObject params, McpDebugAdapter adapter) {
    String name = params != null ? params.getString("name") : null;
    if (name == null) {
      writeError(id, -32_602, "Invalid params: 'name' is required", null);
      return;
    }
    JsonValue.JsonObject arguments = params.getObject("arguments");
    try {
      writeResult(id, adapter.callTool(name, arguments));
    } catch (McpDebugAdapter.UnknownToolException e) {
      writeError(id, -32_602, e.getMessage(), null);
    }
  }

  private static void writeResult(JsonValue id, JsonValue result) {
    JsonValue.JsonObject response =
        JsonValue.object().put("jsonrpc", "2.0").put("id", id).put("result", result);
    System.out.println(JsonWriter.write(response));
  }

  private static void writeError(
      JsonValue id, int code, String message, JsonValue.JsonObject data) {
    JsonValue.JsonObject error =
        JsonValue.object().put("code", (long) code).put("message", message);
    if (data != null) {
      error.put("data", data);
    }
    JsonValue.JsonObject response =
        JsonValue.object().put("jsonrpc", "2.0").put("id", id).put("error", error);
    System.out.println(JsonWriter.write(response));
  }
}
