package com.davidconneely.bazlang.mcp;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.debug.BreakpointEngine;
import com.davidconneely.bazlang.debug.DebugEngine;
import com.davidconneely.bazlang.debug.DebugEngineException;
import com.davidconneely.bazlang.debug.ScreenText;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin adapter from MCP {@code tools/call} arguments onto one shared {@link DebugEngine} instance
 * (one implicit debugging session per server subprocess - see docs/spec/mcp.md). Converts each of
 * the seven {@link McpTools} tools into the corresponding engine call, and converts the result (or
 * a thrown {@link DebugEngineException}/{@link ReportException}) into the {@code resultType}/{@code
 * content}/{@code structuredContent}/{@code isError} envelope.
 */
final class McpDebugAdapter {

  /** Thrown for a {@code tools/call} naming a tool outside {@link McpTools#ALL}. */
  static final class UnknownToolException extends RuntimeException {
    UnknownToolException(String toolName) {
      super("Unknown tool: " + toolName);
    }
  }

  private final DebugEngine engine;

  McpDebugAdapter(DebugEngine engine) {
    this.engine = engine;
  }

  JsonValue.JsonObject callTool(String name, JsonValue.JsonObject arguments) {
    JsonValue.JsonObject a = arguments != null ? arguments : JsonValue.object();
    try {
      return switch (name) {
        case "bazlang_program" -> callProgram(a);
        case "bazlang_step" -> callStep(a);
        case "bazlang_breakpoint" -> callBreakpoint(a);
        case "bazlang_eval" -> callEval(a);
        case "bazlang_screen" -> callScreen(a);
        case "bazlang_input" -> callInput(a);
        case "bazlang_stack" -> callStack();
        default -> throw new UnknownToolException(name);
      };
    } catch (DebugEngineException | ReportException e) {
      return error(e.getMessage());
    }
  }

  // ---- bazlang_program ----

  private JsonValue.JsonObject callProgram(JsonValue.JsonObject a) {
    String action = a.getString("action");
    if (action == null) {
      return error("bazlang_program requires an 'action'");
    }
    return switch (action) {
      case "new" -> {
        engine.applyReplCommand("NEW");
        yield success("OK", null);
      }
      case "load_file" -> {
        String path = a.getString("path");
        if (path == null || path.isBlank()) {
          yield error("action=load_file requires 'path'");
        }
        String literal = toBasicStringLiteral(path);
        if (literal == null) {
          yield error("action=load_file 'path' must not contain a line break");
        }
        engine.applyReplCommand("LOAD " + literal);
        yield success("Loaded " + path, null);
      }
      case "load_source" -> {
        String source = a.getString("source");
        if (source == null) {
          yield error("action=load_source requires 'source'");
        }
        engine.loadSource(source);
        yield success("Loaded programme (" + countLines(source) + " lines)", null);
      }
      case "save_file" -> {
        String path = a.getString("path");
        if (path == null || path.isBlank()) {
          yield error("action=save_file requires 'path'");
        }
        String literal = toBasicStringLiteral(path);
        if (literal == null) {
          yield error("action=save_file 'path' must not contain a line break");
        }
        engine.applyReplCommand("SAVE " + literal);
        yield success("Saved " + path, null);
      }
      case "edit_line" -> {
        if (!a.has("line")) {
          yield error("action=edit_line requires a positive 'line'");
        }
        int line = a.getInt("line", -1);
        if (line <= 0) {
          yield error("action=edit_line requires a positive 'line'");
        }
        String statement = a.getString("statement");
        boolean deleting = statement == null || statement.isBlank();
        engine.applyReplCommand(deleting ? String.valueOf(line) : line + " " + statement);
        yield success(deleting ? line + " deleted" : "OK", null);
      }
      case "list" -> {
        String listing = engine.listProgram();
        yield success(listing, JsonValue.objectOf("listing", listing));
      }
      default -> error("Unknown action for bazlang_program: " + action);
    };
  }

  private static int countLines(String source) {
    int count = 0;
    for (String ln : source.split("\n", -1)) {
      if (!ln.isBlank()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Formats {@code raw} as a BazLang string literal (doubling embedded quotes, per the grammar's
   * {@code STR_LITERAL} escape - see {@code AstLowering.parseStrLiteral}), for embedding a
   * caller-supplied value into a synthesized immediate statement such as {@code LOAD "..."}.
   * Without this, an unescaped {@code "} in {@code raw} would close the literal early and let the
   * rest of {@code raw} be parsed as further {@code :}-separated BASIC statements (e.g. a path of
   * {@code x" : NEW : REM } would silently wipe the loaded programme). Returns {@code null} if
   * {@code raw} contains a line break, which a BazLang string literal cannot represent at all
   * (STR_LITERAL excludes {@code \r}/{@code \n} outright, with no escape for them) - the caller
   * must reject the value rather than embed it.
   */
  private static String toBasicStringLiteral(String raw) {
    if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
      return null;
    }
    return "\"" + raw.replace("\"", "\"\"") + "\"";
  }

  // ---- bazlang_step ----

  private JsonValue.JsonObject callStep(JsonValue.JsonObject a) {
    String action = a.getString("action");
    if (action == null) {
      return error("bazlang_step requires an 'action'");
    }
    return switch (action) {
      case "run" -> {
        if (engine.state().program().isEmpty()) {
          yield error("no programme loaded");
        }
        yield pauseResultResponse(engine.run(timeoutMs(a)));
      }
      case "goto" -> {
        if (!a.has("line")) {
          yield error("action=goto requires 'line'");
        }
        yield pauseResultResponse(engine.gotoLine(a.getInt("line", 0), timeoutMs(a)));
      }
      case "go" -> {
        if (!engine.isPaused()) {
          yield error("not paused at a breakpoint; use action=run or action=goto");
        }
        yield pauseResultResponse(engine.go(timeoutMs(a)));
      }
      case "step_into" -> {
        if (!engine.isPaused()) {
          yield error("not paused at a breakpoint; step_into requires a paused programme");
        }
        yield pauseResultResponse(engine.stepInto(timeoutMs(a)));
      }
      case "step_over" -> {
        if (!engine.isPaused()) {
          yield error("not paused at a breakpoint; step_over requires a paused programme");
        }
        yield pauseResultResponse(engine.stepOver(timeoutMs(a)));
      }
      case "stop" -> {
        engine.stop();
        yield success("stopped", JsonValue.objectOf("reason", "stopped"));
      }
      case "status" -> {
        boolean paused = engine.isPaused();
        int line = engine.state().currentLineLabel();
        int stmt = engine.state().currentStatementIndex();
        String text = paused ? "PAUSED AT " + line + ":" + stmt : "not paused";
        yield success(text, JsonValue.objectOf("paused", paused, "line", line, "stmt", stmt));
      }
      default -> error("Unknown action for bazlang_step: " + action);
    };
  }

  /** {@code timeoutMs}, defaulting to {@link DebugEngine#DEFAULT_STEP_TIMEOUT_MS} if omitted. */
  private static long timeoutMs(JsonValue.JsonObject a) {
    return a.getInt("timeoutMs", (int) DebugEngine.DEFAULT_STEP_TIMEOUT_MS);
  }

  private JsonValue.JsonObject pauseResultResponse(DebugEngine.PauseResult result) {
    return switch (result) {
      case DebugEngine.PauseResult.Break(int line, int stmt) ->
          success(
              "BREAK AT " + line + ":" + stmt,
              JsonValue.objectOf("reason", "break", "line", line, "stmt", stmt));
      case DebugEngine.PauseResult.Elapse ignored ->
          success("ELAPSE", JsonValue.objectOf("reason", "elapse"));
      case DebugEngine.PauseResult.Step(int line, int stmt) ->
          success(
              "STEP AT " + line + ":" + stmt,
              JsonValue.objectOf("reason", "step", "line", line, "stmt", stmt));
      case DebugEngine.PauseResult.Limit(int line, int stmt) ->
          success(
              "LIMIT AT " + line + ":" + stmt,
              JsonValue.objectOf("reason", "limit", "line", line, "stmt", stmt));
      case DebugEngine.PauseResult.Stopped(ReportException report) ->
          success(
              "STOP " + report.format(),
              JsonValue.objectOf(
                  "reason",
                  "stop",
                  "code",
                  report.reportCode() != null ? String.valueOf(report.reportCode().getCode()) : "-",
                  "message",
                  report.getMessage() != null ? report.getMessage() : "",
                  "line",
                  report.lineLabel(),
                  "stmt",
                  report.statementIndex()));
    };
  }

  // ---- bazlang_breakpoint ----

  private JsonValue.JsonObject callBreakpoint(JsonValue.JsonObject a) {
    String action = a.getString("action");
    if (action == null) {
      return error("bazlang_breakpoint requires an 'action'");
    }
    return switch (action) {
      case "set" -> {
        int line = a.has("line") ? a.getInt("line", -1) : -1;
        int stmt = a.has("statement") ? a.getInt("statement", -1) : -1;
        boolean persistent = a.getBoolean("persistent", true);
        BreakpointEngine.BreakCondition cond =
            buildBreakCondition(line, stmt, persistent, a.getObject("condition"));
        if (cond == null) {
          yield error("Invalid condition for bazlang_breakpoint");
        }
        engine.breakpoints().add(cond);
        yield success("OK", null);
      }
      case "clear" -> {
        if (!a.has("line") || !a.has("statement")) {
          yield error("action=clear requires 'line' and 'statement'");
        }
        engine.breakpoints().clearAt(a.getInt("line", -1), a.getInt("statement", -1));
        yield success("OK", null);
      }
      case "clear_all" -> {
        engine.breakpoints().clearPersistent();
        yield success("OK", null);
      }
      case "list" -> {
        var breaks = engine.breakpoints().list();
        JsonValue.JsonArray arr = JsonValue.array();
        for (var b : breaks) {
          arr.add(breakpointToJson(b));
        }
        yield success(breaks.size() + " breakpoint(s)", JsonValue.objectOf("breakpoints", arr));
      }
      default -> error("Unknown action for bazlang_breakpoint: " + action);
    };
  }

  private static JsonValue.JsonObject breakpointToJson(BreakpointEngine.BreakCondition b) {
    JsonValue.JsonObject obj = JsonValue.object();
    if (b.line() >= 0) {
      obj.put("line", b.line());
    }
    if (b.stmt() >= 0) {
      obj.put("statement", b.stmt());
    }
    obj.put("persistent", b.persistent());
    JsonValue.JsonObject condition = conditionToJson(b);
    if (condition != null) {
      obj.put("condition", condition);
    }
    return obj;
  }

  private static JsonValue.JsonObject conditionToJson(BreakpointEngine.BreakCondition b) {
    return switch (b.type()) {
      case NONE -> null;
      case VIEW -> JsonValue.objectOf("type", "csc", "text", b.seeText());
      case ELAPSE -> JsonValue.objectOf("type", "elapse", "milliseconds", b.timeoutMs());
      case EXPR -> JsonValue.objectOf("type", "expr", "expression", b.seeText());
      case EVERY -> JsonValue.objectOf("type", "every", "everyN", b.everyN());
    };
  }

  private static BreakpointEngine.BreakCondition buildBreakCondition(
      int line, int stmt, boolean persistent, JsonValue.JsonObject condition) {
    if (condition == null) {
      return new BreakpointEngine.BreakCondition(
          line, stmt, BreakpointEngine.ConditionType.NONE, null, 0, persistent, 0, null);
    }
    String type = condition.getString("type");
    if (type == null) {
      return null;
    }
    return switch (type) {
      case "csc" -> {
        String text = condition.getString("text");
        yield text == null
            ? null
            : new BreakpointEngine.BreakCondition(
                line, stmt, BreakpointEngine.ConditionType.VIEW, text, 0, persistent, 0, null);
      }
      case "elapse" -> {
        if (!condition.has("milliseconds")) {
          yield null;
        }
        long ms = condition.getInt("milliseconds", 0);
        yield new BreakpointEngine.BreakCondition(
            line, stmt, BreakpointEngine.ConditionType.ELAPSE, null, ms, persistent, 0, null);
      }
      case "expr" -> {
        String expr = condition.getString("expression");
        yield expr == null
            ? null
            : new BreakpointEngine.BreakCondition(
                line, stmt, BreakpointEngine.ConditionType.EXPR, expr, 0, persistent, 0, null);
      }
      case "every" -> {
        int n = condition.getInt("everyN", 0);
        yield n <= 0
            ? null
            : new BreakpointEngine.BreakCondition(
                line,
                stmt,
                BreakpointEngine.ConditionType.EVERY,
                null,
                0,
                persistent,
                n,
                new AtomicInteger());
      }
      default -> null;
    };
  }

  // ---- bazlang_eval ----

  private JsonValue.JsonObject callEval(JsonValue.JsonObject a) {
    String action = a.has("action") ? a.getString("action") : "eval";
    return switch (action == null ? "eval" : action) {
      case "eval" -> callEvalExpression(a);
      case "exec" -> callEvalExec(a);
      case "vars" -> callEvalVars();
      case "array" -> callEvalArray(a);
      default -> error("Unknown action for bazlang_eval: " + action);
    };
  }

  private JsonValue.JsonObject callEvalExec(JsonValue.JsonObject a) {
    String statement = a.getString("statement");
    if (statement == null || statement.isBlank()) {
      return error("action=exec requires 'statement'");
    }
    engine.applyReplCommand(statement);
    return success("OK", null);
  }

  private JsonValue.JsonObject callEvalExpression(JsonValue.JsonObject a) {
    String expr = a.getString("expression");
    if (expr == null || expr.isBlank()) {
      return error("action=eval requires 'expression'");
    }
    String trimmed = expr.trim();
    // A leading LET is the unambiguous assignment cue: bare "x=3" is equality (a valid BazLang
    // expression), not assignment, so only an explicit LET routes to executeAssignment.
    if (trimmed.length() >= 4 && trimmed.substring(0, 4).equalsIgnoreCase("LET ")) {
      engine.executeAssignment(trimmed);
      return success("OK", null);
    }
    DebugEngine.EvalResult result = engine.evalExpression(trimmed);
    return switch (result) {
      case DebugEngine.EvalResult.Num(double value) ->
          success(ExpressionEvaluator.formatNum(value), JsonValue.objectOf("value", value));
      case DebugEngine.EvalResult.Str(String value) ->
          success(value, JsonValue.objectOf("value", value));
    };
  }

  private JsonValue.JsonObject callEvalVars() {
    var state = engine.state();
    var numVars = state.variablesSnapshot();
    var strVars = state.stringVariablesSnapshot();
    var numArrays = state.numArraysSnapshot();
    var strArrays = state.strArraysSnapshot();
    var fns = state.fnDefinitionsSnapshot();

    JsonValue.JsonObject numeric = JsonValue.object();
    for (var e : numVars.entrySet()) {
      numeric.put(e.getKey(), e.getValue());
    }
    JsonValue.JsonObject string = JsonValue.object();
    for (var e : strVars.entrySet()) {
      string.put(e.getKey(), e.getValue());
    }
    JsonValue.JsonObject numericArrays = JsonValue.object();
    for (var e : numArrays.entrySet()) {
      numericArrays.put(e.getKey(), dimensionsToJson(e.getValue().dimensions()));
    }
    JsonValue.JsonObject stringArrays = JsonValue.object();
    for (var e : strArrays.entrySet()) {
      stringArrays.put(
          e.getKey(),
          JsonValue.object()
              .put("dimensions", dimensionsToJson(e.getValue().arrayDimensions()))
              .put("stringLength", e.getValue().stringLength()));
    }
    JsonValue.JsonObject functions = JsonValue.object();
    for (var e : fns.entrySet()) {
      JsonValue.JsonArray params = JsonValue.array();
      for (String p : e.getValue().params()) {
        params.add(JsonValue.of(p));
      }
      functions.put(e.getKey(), params);
    }

    int total = numVars.size() + strVars.size() + numArrays.size() + strArrays.size() + fns.size();
    return success(
        total + " variable(s)/array(s)/function(s)",
        JsonValue.objectOf(
            "numeric", numeric,
            "string", string,
            "numericArrays", numericArrays,
            "stringArrays", stringArrays,
            "functions", functions));
  }

  // Both call sites pass an existing int[] (NumArray.dimensions()/StrVar.Array.arrayDimensions()),
  // never an ad-hoc literal list, so varargs would add an unnecessary array copy at each call site.
  @SuppressWarnings("PMD.UseVarargs")
  private static JsonValue.JsonArray dimensionsToJson(int[] dimensions) {
    JsonValue.JsonArray arr = JsonValue.array();
    for (int d : dimensions) {
      arr.add(JsonValue.of((long) d));
    }
    return arr;
  }

  /**
   * Returns the full contents of array {@code name} - avoids requiring one {@code eval} call per
   * element to inspect a whole array. Flattened in row-major order (the last dimension varies
   * fastest), matching {@code ExpressionEvaluator.calculateArrayIndex}; 1-based indices, as in
   * BASIC subscripts, so element {@code i} (0-based, in {@code values}) is {@code A(i/dims[1]+1,
   * i%dims[1]+1)} for a 2-D array, generalising the same way for more dimensions.
   */
  private JsonValue.JsonObject callEvalArray(JsonValue.JsonObject a) {
    String name = a.getString("name");
    if (name == null || name.isBlank()) {
      return error("action=array requires 'name'");
    }
    String upperName = name.trim().toUpperCase(Locale.ROOT);
    var state = engine.state();
    if (state.hasNumArray(upperName)) {
      EvalState.NumArray array = state.numArray(upperName);
      JsonValue.JsonArray values = JsonValue.array();
      for (double v : array.data()) {
        values.add(JsonValue.of(v));
      }
      return success(
          array.data().length + " element(s)",
          JsonValue.objectOf("dimensions", dimensionsToJson(array.dimensions()), "values", values));
    }
    if (state.hasStrVar(upperName)
        && state.strVar(upperName) instanceof EvalState.StrVar.Array array) {
      int stringLength = array.stringLength();
      int total = stringLength == 0 ? 0 : array.data().length / stringLength;
      JsonValue.JsonArray values = JsonValue.array();
      for (int i = 0; i < total; i++) {
        values.add(
            JsonValue.of(
                BStr.fromBytes(array.data(), i * stringLength, stringLength).toJavaString()));
      }
      return success(
          total + " element(s)",
          JsonValue.objectOf(
              "dimensions", dimensionsToJson(array.arrayDimensions()),
              "stringLength", stringLength,
              "values", values));
    }
    return error("No such array: " + name);
  }

  // ---- bazlang_screen ----

  private JsonValue.JsonObject callScreen(JsonValue.JsonObject a) {
    String action = a.getString("action");
    if (action == null) {
      return error("bazlang_screen requires an 'action'");
    }
    return switch (action) {
      case "read" -> {
        if (!a.has("rowTop") || !a.has("colLeft") || !a.has("rowBottom") || !a.has("colRight")) {
          yield error("action=read requires rowTop, colLeft, rowBottom, colRight");
        }
        boolean attr = a.getBoolean("attr", false);
        String grid =
            ScreenText.buildScreenString(
                engine.screen(),
                a.getInt("rowTop", 0),
                a.getInt("rowBottom", 0),
                a.getInt("colLeft", 0),
                a.getInt("colRight", 0),
                attr);
        yield success(grid, JsonValue.objectOf("grid", grid));
      }
      case "resize" -> {
        if (!a.has("rows") || !a.has("cols")) {
          yield error("action=resize requires 'rows' and 'cols'");
        }
        engine.screen().resize(a.getInt("rows", 0), a.getInt("cols", 0));
        yield success("OK", null);
      }
      default -> error("Unknown action for bazlang_screen: " + action);
    };
  }

  // ---- bazlang_input ----

  private JsonValue.JsonObject callInput(JsonValue.JsonObject a) {
    String action = a.getString("action");
    if (action == null) {
      return error("bazlang_input requires an 'action'");
    }
    return switch (action) {
      case "queue" -> {
        String text = a.getString("text");
        if (text == null) {
          yield error("action=queue requires 'text'");
        }
        queueText(text);
        yield success("OK", null);
      }
      case "clear" -> {
        engine.screen().clearInputQueues();
        yield success("cleared", null);
      }
      default -> error("Unknown action for bazlang_input: " + action);
    };
  }

  private void queueText(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      engine.screen().queueInkey(BStr.fromByte(b & 0xFF));
    }
    text.codePoints()
        .forEach(
            cp ->
                engine
                    .screen()
                    .queueUinkey(BStr.fromJavaString(new String(Character.toChars(cp)))));
    engine.screen().queueInput(text);
  }

  // ---- bazlang_stack ----

  private JsonValue.JsonObject callStack() {
    var state = engine.state();
    JsonValue.JsonArray gosub = JsonValue.array();
    for (var frame : state.returnStackSnapshot()) {
      gosub.add(JsonValue.objectOf("line", frame.lineLabel(), "statement", frame.statementIndex()));
    }
    JsonValue.JsonArray forLoops = JsonValue.array();
    for (var entry : state.forLoopsSnapshot().entrySet()) {
      String var = entry.getKey();
      var data = entry.getValue();
      JsonValue.JsonObject obj = JsonValue.object().put("variable", var);
      if (state.hasNumVar(var)) {
        obj.put("current", state.numVar(var));
      }
      obj.put("limit", data.limit())
          .put("step", data.step())
          .put("loopLine", data.loopPcLabel())
          .put("loopStatement", data.loopPcStatementIndex());
      forLoops.add(obj);
    }
    String text = gosub.size() + " GOSUB frame(s), " + forLoops.size() + " active FOR loop(s)";
    return success(text, JsonValue.objectOf("gosub", gosub, "forLoops", forLoops));
  }

  // ---- response envelope helpers ----

  private static JsonValue.JsonObject success(String text, JsonValue.JsonObject structuredContent) {
    JsonValue.JsonObject result =
        JsonValue.object()
            .put("resultType", "complete")
            .put("content", JsonValue.array().add(textContent(text)))
            .put("isError", false);
    if (structuredContent != null) {
      result.put("structuredContent", structuredContent);
    }
    return result;
  }

  private static JsonValue.JsonObject error(String message) {
    return JsonValue.object()
        .put("resultType", "complete")
        .put("content", JsonValue.array().add(textContent(message)))
        .put("isError", true);
  }

  private static JsonValue.JsonObject textContent(String text) {
    return JsonValue.object().put("type", "text").put("text", text);
  }
}
