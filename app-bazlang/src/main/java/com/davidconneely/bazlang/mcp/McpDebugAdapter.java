package com.davidconneely.bazlang.mcp;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.debug.BreakpointEngine;
import com.davidconneely.bazlang.debug.DebugEngine;
import com.davidconneely.bazlang.debug.DebugEngineException;
import com.davidconneely.bazlang.debug.ScreenText;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin adapter from MCP {@code tools/call} arguments onto one shared {@link DebugEngine} instance
 * (one implicit debugging session per server subprocess — see docs/mcp_server.md). Converts each of
 * the six {@link McpTools} tools into the corresponding engine call, and converts the result (or a
 * thrown {@link DebugEngineException}/{@link ReportException}) into the {@code resultType}/{@code
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
        engine.applyReplCommand("LOAD \"" + path + "\"");
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
        yield pauseResultResponse(engine.run());
      }
      case "goto" -> {
        if (!a.has("line")) {
          yield error("action=goto requires 'line'");
        }
        yield pauseResultResponse(engine.gotoLine(a.getInt("line", 0)));
      }
      case "go" -> {
        if (!engine.isPaused()) {
          yield error("not paused at a breakpoint; use action=run or action=goto");
        }
        yield pauseResultResponse(engine.go());
      }
      case "stop" -> {
        engine.stop();
        yield success("stopped", JsonValue.objectOf("reason", "stopped"));
      }
      default -> error("Unknown action for bazlang_step: " + action);
    };
  }

  private JsonValue.JsonObject pauseResultResponse(DebugEngine.PauseResult result) {
    return switch (result) {
      case DebugEngine.PauseResult.Break(int line, int stmt) ->
          success(
              "BREAK AT " + line + ":" + stmt,
              JsonValue.objectOf("reason", "break", "line", line, "stmt", stmt));
      case DebugEngine.PauseResult.Elapse ignored ->
          success("ELAPSE", JsonValue.objectOf("reason", "elapse"));
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
      default -> error("Unknown action for bazlang_breakpoint: " + action);
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
    String expr = a.getString("expression");
    if (expr == null || expr.isBlank()) {
      return error("bazlang_eval requires 'expression'");
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
    String text = a.getString("text");
    if (text == null) {
      return error("bazlang_input requires 'text'");
    }
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
    return success("OK", null);
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
