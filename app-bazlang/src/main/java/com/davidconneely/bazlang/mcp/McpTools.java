package com.davidconneely.bazlang.mcp;

import java.util.List;

/**
 * The static catalog of MCP tools exposed by {@link McpServer}: a consolidated surface over {@link
 * com.davidconneely.bazlang.debug.DebugEngine} — see docs/spec/mcp.md for the JSON-RPC shape of
 * each tool.
 */
final class McpTools {

  private McpTools() {}

  static final List<JsonValue.JsonObject> ALL =
      List.of(
          programTool(),
          stepTool(),
          breakpointTool(),
          evalTool(),
          screenTool(),
          inputTool(),
          stackTool());

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
                "save_file",
                "edit_line",
                "list"),
            "path",
            stringProp(
                "For action=load_file: bare filename or path (resolved against the example "
                    + "directory if bare). For action=save_file: the file path to write — bare "
                    + "names are NOT resolved against the example directory (matching the plain "
                    + "SAVE statement), so use a full/relative path."),
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
            + "or inline source, save the current programme to a file, add/replace/delete a "
            + "single numbered line, or list the current programme text.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject stepTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "run: clear state and run from the first line. goto: jump to a line without "
                    + "clearing state. go: resume from a breakpoint. step_into: execute exactly "
                    + "one statement, entering any GOSUB it calls. step_over: execute exactly "
                    + "one statement, running any GOSUB it calls to completion instead of "
                    + "pausing inside it. stop: terminate the running programme. status: report "
                    + "the current pause state without executing anything.",
                "run",
                "goto",
                "go",
                "step_into",
                "step_over",
                "stop",
                "status"),
            "line",
            intProp("Target line number for action=goto."),
            "timeoutMs",
            intProp(
                "Safety cap in milliseconds for run/goto/go/step_into/step_over: pauses with "
                    + "reason=limit if no other pause reason fires first, so a programme with an "
                    + "accidental infinite loop and no breakpoint of its own can't block the "
                    + "call forever. Default 30000; not used by stop/status."));
    return tool(
        "bazlang_step",
        "Drive programme execution. run/goto/go/step_into/step_over block until the programme "
            + "next breaks, elapses, steps, hits its safety timeout, or stops, then return the "
            + "pause reason; status returns the current pause state immediately without "
            + "executing anything. step_into/step_over require the programme to already be "
            + "paused (like go).",
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
                    + "remove every persistent breakpoint. list: report every currently-active "
                    + "breakpoint.",
                "set",
                "clear",
                "clear_all",
                "list"),
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
        "Set, clear, or list breakpoints, with optional CSC/ELAPSE/expression/EVERY conditions.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject evalTool() {
    JsonValue.JsonObject properties =
        props(
            "action",
            enumProp(
                "eval (default): evaluate 'expression', or execute it as a LET assignment. "
                    + "exec: execute any single immediate-mode statement ('statement'), not "
                    + "just LET — e.g. GOSUB, PRINT, DIM, CLS, RESTORE. vars: list every "
                    + "currently-defined variable, array, and DEF FN — useful for exploring an "
                    + "unfamiliar or already-paused programme without knowing names up front. "
                    + "array: return the full contents of array 'name'.",
                "eval",
                "exec",
                "vars",
                "array"),
            "expression",
            stringProp(
                "A bare BazLang expression to evaluate (e.g. \"SCORE\"), or an assignment "
                    + "(e.g. \"SCORE=0\") to execute as a LET statement. Required for action=eval "
                    + "(the default); ignored otherwise."),
            "statement",
            stringProp(
                "Any single immediate-mode BASIC statement or REPL command line — the same "
                    + "input the interactive REPL accepts. Required for action=exec; ignored "
                    + "otherwise. Prefer bazlang_program's structured actions for programme "
                    + "management (NEW/LOAD/edit_line/etc.) — exec supports them too, but "
                    + "without the friendlier per-action argument shape."),
            "name",
            stringProp(
                "Array name (e.g. \"A\" or \"B$\"). Required for action=array; ignored "
                    + "otherwise."));
    return tool(
        "bazlang_eval",
        "Evaluate an expression, execute a statement, list every currently-defined "
            + "variable/array/function, or read a whole array's contents, in the live "
            + "programme context. action=vars reports array names and dimensions only, not "
            + "their full contents — use action=array (or read a known element directly, e.g. "
            + "expression=\"A(3)\") for that.",
        schema(properties));
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
        "Read a rectangle of the virtual screen buffer, or resize it.",
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
            + "bazlang_program(new/load_file/load_source) already discards queued input "
            + "automatically when it replaces the programme — see docs/spec/mcp.md's \"Input "
            + "queue\" section — so action=clear is for cancelling a mis-queued value or "
            + "resetting mid-session without reloading.",
        schema(properties, "action"));
  }

  private static JsonValue.JsonObject stackTool() {
    return tool(
        "bazlang_stack",
        "Inspect interpreter call-stack state that no BazLang expression can reach: active "
            + "GOSUB return frames (innermost/most-recently-called first) and active FOR loops "
            + "(each with its current value, limit, step, and loop-back location).",
        schema(props()));
  }
}
