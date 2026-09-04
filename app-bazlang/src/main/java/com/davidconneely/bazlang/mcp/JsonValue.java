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

  /**
   * An insertion-ordered JSON object. The {@link Map} is always a {@link LinkedHashMap}.
   *
   * @param members the object's key/value pairs, in insertion order.
   */
  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    /**
     * Sets a member, replacing any existing value at {@code key}.
     *
     * @param key the member's key.
     * @param value the member's value.
     * @return this object, for chaining.
     */
    public JsonObject put(String key, JsonValue value) {
      members.put(key, value);
      return this;
    }

    /**
     * Sets a string member, replacing any existing value at {@code key}.
     *
     * @param key the member's key.
     * @param value the member's value, or {@code null} for JSON null.
     * @return this object, for chaining.
     */
    public JsonObject put(String key, String value) {
      return put(key, value == null ? JsonNull.INSTANCE : new JsonString(value));
    }

    /**
     * Sets an integer member, replacing any existing value at {@code key}.
     *
     * @param key the member's key.
     * @param value the member's value.
     * @return this object, for chaining.
     */
    public JsonObject put(String key, long value) {
      return put(key, of(value));
    }

    /**
     * Sets a floating-point member, replacing any existing value at {@code key}.
     *
     * @param key the member's key.
     * @param value the member's value.
     * @return this object, for chaining.
     */
    public JsonObject put(String key, double value) {
      return put(key, of(value));
    }

    /**
     * Sets a boolean member, replacing any existing value at {@code key}.
     *
     * @param key the member's key.
     * @param value the member's value.
     * @return this object, for chaining.
     */
    public JsonObject put(String key, boolean value) {
      return put(key, new JsonBoolean(value));
    }

    /**
     * Whether a member is present at {@code key} (including an explicit JSON null).
     *
     * @param key the key to check.
     * @return {@code true} if present.
     */
    public boolean has(String key) {
      return members.containsKey(key);
    }

    /**
     * The member at {@code key}.
     *
     * @param key the member's key.
     * @return the value, or {@code null} if absent.
     */
    public JsonValue get(String key) {
      return members.get(key);
    }

    /**
     * Returns the string at {@code key}, or {@code null} if absent, not a string, or JSON null.
     *
     * @param key the member's key.
     * @return the string value, or {@code null}.
     */
    public String getString(String key) {
      return get(key) instanceof JsonString(String value) ? value : null;
    }

    /**
     * Returns the numeric value at {@code key} as an int, or {@code defaultValue} if unusable.
     *
     * @param key the member's key.
     * @param defaultValue the value to return if absent or not a number.
     * @return the int value, or {@code defaultValue}.
     */
    public int getInt(String key, int defaultValue) {
      return get(key) instanceof JsonNumber(double value, String _) ? (int) value : defaultValue;
    }

    /**
     * Returns the object at {@code key}, or {@code null} if absent or not an object.
     *
     * @param key the member's key.
     * @return the object value, or {@code null}.
     */
    public JsonObject getObject(String key) {
      return get(key) instanceof JsonObject obj ? obj : null;
    }

    /**
     * Returns the array at {@code key}, or {@code null} if absent or not an array.
     *
     * @param key the member's key.
     * @return the array value, or {@code null}.
     */
    public JsonArray getArray(String key) {
      return get(key) instanceof JsonArray arr ? arr : null;
    }

    /**
     * Returns the boolean value at {@code key}, or {@code defaultValue} if unusable.
     *
     * @param key the member's key.
     * @param defaultValue the value to return if absent or not a boolean.
     * @return the boolean value, or {@code defaultValue}.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
      return get(key) instanceof JsonBoolean(boolean value) ? value : defaultValue;
    }
  }

  /**
   * An ordered JSON array.
   *
   * @param items the array's elements, in order.
   */
  record JsonArray(List<JsonValue> items) implements JsonValue {
    /**
     * Appends an element.
     *
     * @param value the element to append.
     * @return this array, for chaining.
     */
    public JsonArray add(JsonValue value) {
      items.add(value);
      return this;
    }

    /**
     * Element count.
     *
     * @return the number of elements.
     */
    public int size() {
      return items.size();
    }

    /**
     * The element at {@code index}.
     *
     * @param index the 0-based index.
     * @return the element.
     */
    public JsonValue get(int index) {
      return items.get(index);
    }
  }

  /**
   * A JSON string.
   *
   * @param value the string's value.
   */
  record JsonString(String value) implements JsonValue {}

  /**
   * A JSON number. {@code raw} holds the exact source text when this value came from {@link
   * JsonParser} (so re-serializing an id/argument round-trips byte-for-byte); it is {@code null}
   * for numbers built programmatically, in which case {@link JsonWriter} formats {@code value}
   * canonically.
   *
   * @param value the numeric value.
   * @param raw the exact source text this value was parsed from, or {@code null}.
   */
  record JsonNumber(double value, String raw) implements JsonValue {
    /**
     * The value, truncated to an int.
     *
     * @return the int value.
     */
    public int intValue() {
      return (int) value;
    }

    /**
     * The value, truncated to a long.
     *
     * @return the long value.
     */
    public long longValue() {
      return (long) value;
    }
  }

  /**
   * A JSON boolean.
   *
   * @param value the boolean's value.
   */
  record JsonBoolean(boolean value) implements JsonValue {}

  /** JSON null. Use {@link #INSTANCE} rather than constructing a new one. */
  record JsonNull() implements JsonValue {
    /** The shared instance. */
    public static final JsonNull INSTANCE = new JsonNull();
  }

  // ---- factories ----

  /**
   * Creates an empty, mutable {@link JsonObject}.
   *
   * @return the new object.
   */
  static JsonObject object() {
    return new JsonObject(new LinkedHashMap<>());
  }

  /**
   * Creates an empty, mutable {@link JsonArray}.
   *
   * @return the new array.
   */
  static JsonArray array() {
    return new JsonArray(new ArrayList<>());
  }

  /**
   * Wraps a string as a {@link JsonValue}.
   *
   * @param value the string, or {@code null} for JSON null.
   * @return the wrapped value.
   */
  static JsonValue of(String value) {
    return value == null ? JsonNull.INSTANCE : new JsonString(value);
  }

  /**
   * Wraps an integer as a {@link JsonValue}.
   *
   * @param value the value.
   * @return the wrapped value.
   */
  static JsonValue of(long value) {
    return new JsonNumber(value, null);
  }

  /**
   * Wraps a floating-point value as a {@link JsonValue}.
   *
   * @param value the value.
   * @return the wrapped value.
   */
  static JsonValue of(double value) {
    return new JsonNumber(value, null);
  }

  /**
   * Wraps a boolean as a {@link JsonValue}.
   *
   * @param value the value.
   * @return the wrapped value.
   */
  static JsonValue of(boolean value) {
    return new JsonBoolean(value);
  }

  /**
   * Builds a {@link JsonObject} from alternating key/{@link JsonValue} pairs, for terse literals.
   *
   * @param keysAndValues alternating {@code String} keys and values (each value either a {@link
   *     JsonValue} already, or a {@code String}/{@code Boolean}/{@code Integer}/{@code Long}/{@code
   *     Double}/{@code null} to coerce).
   * @return the built object.
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
