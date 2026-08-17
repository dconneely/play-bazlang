package com.davidconneely.bazlang.mcp;

import java.util.List;

/**
 * The static catalog of MCP tools exposed by {@link McpServer}: a consolidated surface over the
 * {@code AgentDebugger} command set documented in docs/language_debugger.md — see
 * docs/mcp_server.md for the JSON-RPC shape of each tool.
 */
final class McpTools {

  private McpTools() {}

  static final List<JsonValue.JsonObject> ALL =
      List.of(programTool(), stepTool(), breakpointTool(), evalTool(), screenTool(), inputTool());

  private static JsonValue.JsonObject tool(
      String name, String description, JsonValue.JsonObject inputSchema) {
    return JsonValue.object()
        .put("name", name)
        .put("description", description)
        .put("inputSchema", inputSchema);
  }

  private static JsonValue.JsonObject schema(JsonValue.JsonObject properties, String... required) {
    JsonValue.JsonObject schema =
        JsonValue.object().put("type", "object").put("properties", properties);
    if (required.length > 0) {
      JsonValue.JsonArray req = JsonValue.array();
      for (String r : required) {
        req.add(JsonValue.of(r));
      }
      schema.put("required", req);
    }
    return schema;
  }

  private static JsonValue.JsonObject props(Object... nameAndSchema) {
    return JsonValue.objectOf(nameAndSchema);
  }

  private static JsonValue.JsonObject stringProp(String description) {
    return JsonValue.object().put("type", "string").put("description", description);
  }

  private static JsonValue.JsonObject intProp(String description) {
    return JsonValue.object().put("type", "integer").put("description", description);
  }

  private static JsonValue.JsonObject boolProp(String description) {
    return JsonValue.object().put("type", "boolean").put("description", description);
  }

  private static JsonValue.JsonObject enumProp(String description, String... values) {
    JsonValue.JsonObject p = stringProp(description);
    JsonValue.JsonArray enumArr = JsonValue.array();
    for (String v : values) {
      enumArr.add(JsonValue.of(v));
    }
    p.put("enum", enumArr);
    return p;
  }

  private static JsonValue.JsonObject programTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "What to do to the programme.",
                "new",
                "load_file",
                "load_source",
                "edit_line",
                "list"),
            "path",
            stringProp(
                "Bare filename or path for action=load_file (resolved against the example "
                    + "directory if bare)."),
            "source",
            stringProp(
                "Whole multi-line BASIC source (one statement line per \\n) for "
                    + "action=load_source."),
            "line",
            intProp("Line number for action=edit_line."),
            "statement",
            stringProp("Statement text for action=edit_line; omit or empty to delete the line."));
    return tool(
        "bazlang_program",
        "Manage the loaded BazLang programme: create a new empty programme, load one from a file "
            + "or inline source, add/replace/delete a single numbered line, or list the current "
            + "programme text. Consolidates the text protocol's NEW, LOAD, numbered-line edits, "
            + "and LIST.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject stepTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "run: clear state and run from the first line. goto: jump to a line without "
                    + "clearing state. go: resume from a breakpoint. stop: terminate the "
                    + "running programme.",
                "run",
                "goto",
                "go",
                "stop"),
            "line",
            intProp("Target line number for action=goto."));
    return tool(
        "bazlang_step",
        "Drive programme execution. Blocks until the programme next breaks, elapses, or stops, "
            + "then returns the pause reason. Consolidates the text protocol's RUN, GOTO, GO, "
            + "and STOP.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject breakpointTool() {
    JsonValue.JsonObject condition =
        JsonValue.object()
            .put("type", "object")
            .put(
                "description",
                "Optional break condition; omit for an unconditional location breakpoint.")
            .put(
                "properties",
                props(
                    "type",
                    enumProp(
                        "csc: screen contains text. elapse: milliseconds since last resume. "
                            + "expr: BazLang expression is truthy. every: fires every nth "
                            + "check.",
                        "csc",
                        "elapse",
                        "expr",
                        "every"),
                    "text",
                    stringProp("Screen text to search for (type=csc)."),
                    "milliseconds",
                    intProp("Elapsed-time threshold (type=elapse)."),
                    "expression",
                    stringProp("BazLang expression (type=expr)."),
                    "everyN",
                    intProp("Fire every nth check (type=every).")));
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "set: add a breakpoint. clear: remove breakpoints at a location. clear_all: "
                    + "remove every persistent breakpoint.",
                "set",
                "clear",
                "clear_all"),
            "persistent",
            boolProp(
                "action=set only: true keeps firing until cleared, false fires once then "
                    + "removes itself. Default true."),
            "line",
            intProp(
                "Line number; omit (with statement) for a condition-only breakpoint checked "
                    + "on every statement."),
            "statement",
            intProp("1-based statement index within the line."),
            "condition",
            condition);
    return tool(
        "bazlang_breakpoint",
        "Set or clear breakpoints, with optional CSC/ELAPSE/expression/EVERY conditions. "
            + "Consolidates the text protocol's SPB, S1B, and CPB.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject evalTool() {
    JsonValue.JsonObject properties =
        props(
            "expression",
            stringProp(
                "A bare BazLang expression to evaluate (e.g. \"SCORE\"), or an assignment "
                    + "(e.g. \"SCORE=0\") to execute as a LET statement."));
    return tool(
        "bazlang_eval",
        "Evaluate an expression or execute a single assignment in the live programme context. "
            + "Consolidates the text protocol's ? and !.",
        schema(properties, "expression"));
  }

  private static JsonValue.JsonObject screenTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "read: dump a screen rectangle. resize: change the screen buffer dimensions.",
                "read",
                "resize"),
            "rowTop",
            intProp("0-based top row (action=read)."),
            "colLeft",
            intProp("0-based left column (action=read)."),
            "rowBottom",
            intProp("0-based bottom row, inclusive (action=read)."),
            "colRight",
            intProp("0-based right column, inclusive (action=read)."),
            "attr",
            boolProp("Include [fg,bg] colour annotations (action=read). Default false."),
            "rows",
            intProp("New row count (action=resize)."),
            "cols",
            intProp("New column count (action=resize)."));
    return tool(
        "bazlang_screen",
        "Read a rectangle of the virtual screen buffer, or resize it. Consolidates the text "
            + "protocol's RSC and SSD.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject inputTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "queue: append text for INKEY$/UINKEY$/INPUT to consume. clear: discard all "
                    + "queued input without adding any.",
                "queue",
                "clear"),
            "text",
            stringProp("Text to queue (action=queue)."));
    return tool(
        "bazlang_input",
        "Queue keyboard/INPUT text for the programme to consume, or discard queued input. "
            + "Consolidates the text protocol's PIQ. bazlang_program(new/load_file/load_source) "
            + "already discards queued input automatically when it replaces the programme — see "
            + "docs/mcp_server.md's \"Input queue\" section — so action=clear is for cancelling a "
            + "mis-queued value or resetting mid-session without reloading.",
        schema(properties, "action"));
  }
}
