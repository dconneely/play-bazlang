package com.davidconneely.bazlang.mcp;

/**
 * Serializes a {@link JsonValue} tree to compact JSON text (RFC 8259), hand-written so the {@code
 * mcp} package has no external dependency.
 */
public final class JsonWriter {

  private JsonWriter() {}

  public static String write(JsonValue value) {
    StringBuilder sb = new StringBuilder();
    writeValue(sb, value);
    return sb.toString();
  }

  private static void writeValue(StringBuilder sb, JsonValue value) {
    switch (value) {
      case JsonValue.JsonObject(var members) -> {
        sb.append('{');
        boolean first = true;
        for (var entry : members.entrySet()) {
          if (!first) {
            sb.append(',');
          }
          first = false;
          writeString(sb, entry.getKey());
          sb.append(':');
          writeValue(sb, entry.getValue());
        }
        sb.append('}');
      }
      case JsonValue.JsonArray(var items) -> {
        sb.append('[');
        boolean first = true;
        for (JsonValue item : items) {
          if (!first) {
            sb.append(',');
          }
          first = false;
          writeValue(sb, item);
        }
        sb.append(']');
      }
      case JsonValue.JsonString(String s) -> writeString(sb, s);
      case JsonValue.JsonNumber(double d, String raw) ->
          sb.append(raw != null ? raw : formatNumber(d));
      case JsonValue.JsonBoolean(boolean b) -> sb.append(b);
      case JsonValue.JsonNull ignored -> sb.append("null");
    }
  }

  private static String formatNumber(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      throw new IllegalArgumentException("JSON cannot represent NaN/Infinity: " + d);
    }
    if (d == Math.rint(d) && Math.abs(d) < 1e15) {
      return Long.toString((long) d);
    }
    return Double.toString(d);
  }

  private static void writeString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
