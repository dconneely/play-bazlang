package com.davidconneely.bazlang.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, hand-rolled JSON value model - no external dependency. Covers exactly what the MCP
 * JSON-RPC layer needs: objects (insertion-ordered, for deterministic output), arrays, strings,
 * numbers, booleans, and null. Parsed with {@link JsonParser}, serialized with {@link JsonWriter}.
 */
public sealed interface JsonValue {

  /** An insertion-ordered JSON object. The {@link Map} is always a {@link LinkedHashMap}. */
  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    public JsonObject put(String key, JsonValue value) {
      members.put(key, value);
      return this;
    }

    public JsonObject put(String key, String value) {
      return put(key, value == null ? JsonNull.INSTANCE : new JsonString(value));
    }

    public JsonObject put(String key, long value) {
      return put(key, of(value));
    }

    public JsonObject put(String key, double value) {
      return put(key, of(value));
    }

    public JsonObject put(String key, boolean value) {
      return put(key, new JsonBoolean(value));
    }

    public boolean has(String key) {
      return members.containsKey(key);
    }

    public JsonValue get(String key) {
      return members.get(key);
    }

    /** Returns the string at {@code key}, or {@code null} if absent, not a string, or JSON null. */
    public String getString(String key) {
      return get(key) instanceof JsonString(String value) ? value : null;
    }

    /** Returns the numeric value at {@code key} as an int, or {@code defaultValue} if unusable. */
    public int getInt(String key, int defaultValue) {
      return get(key) instanceof JsonNumber(double value, String _) ? (int) value : defaultValue;
    }

    public JsonObject getObject(String key) {
      return get(key) instanceof JsonObject obj ? obj : null;
    }

    public JsonArray getArray(String key) {
      return get(key) instanceof JsonArray arr ? arr : null;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
      return get(key) instanceof JsonBoolean(boolean value) ? value : defaultValue;
    }
  }

  /** An ordered JSON array. */
  record JsonArray(List<JsonValue> items) implements JsonValue {
    public JsonArray add(JsonValue value) {
      items.add(value);
      return this;
    }

    public int size() {
      return items.size();
    }

    public JsonValue get(int index) {
      return items.get(index);
    }
  }

  record JsonString(String value) implements JsonValue {}

  /**
   * A JSON number. {@code raw} holds the exact source text when this value came from {@link
   * JsonParser} (so re-serializing an id/argument round-trips byte-for-byte); it is {@code null}
   * for numbers built programmatically, in which case {@link JsonWriter} formats {@code value}
   * canonically.
   */
  record JsonNumber(double value, String raw) implements JsonValue {
    public int intValue() {
      return (int) value;
    }

    public long longValue() {
      return (long) value;
    }
  }

  record JsonBoolean(boolean value) implements JsonValue {}

  record JsonNull() implements JsonValue {
    public static final JsonNull INSTANCE = new JsonNull();
  }

  // ---- factories ----

  static JsonObject object() {
    return new JsonObject(new LinkedHashMap<>());
  }

  static JsonArray array() {
    return new JsonArray(new ArrayList<>());
  }

  static JsonValue of(String value) {
    return value == null ? JsonNull.INSTANCE : new JsonString(value);
  }

  static JsonValue of(long value) {
    return new JsonNumber(value, null);
  }

  static JsonValue of(double value) {
    return new JsonNumber(value, null);
  }

  static JsonValue of(boolean value) {
    return new JsonBoolean(value);
  }

  /**
   * Builds a {@link JsonObject} from alternating key/{@link JsonValue} pairs, for terse literals.
   */
  static JsonObject objectOf(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("objectOf requires an even number of arguments");
    }
    JsonObject obj = object();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      String key = (String) keysAndValues[i];
      Object value = keysAndValues[i + 1];
      obj.put(key, value instanceof JsonValue jv ? jv : coerce(value));
    }
    return obj;
  }

  private static JsonValue coerce(Object value) {
    return switch (value) {
      case null -> JsonNull.INSTANCE;
      case String s -> new JsonString(s);
      case Boolean b -> new JsonBoolean(b);
      case Integer i -> of((long) i);
      case Long l -> of((long) l);
      case Double d -> of((double) d);
      default ->
          throw new IllegalArgumentException("Unsupported literal type: " + value.getClass());
    };
  }
}
