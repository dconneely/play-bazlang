package com.davidconneely.bazlang.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Round-trip and error-handling tests for the hand-rolled {@link JsonParser}/{@link JsonWriter}.
 */
class JsonCodecTest {

  @Test
  void parsesAndWritesPrimitives() {
    assertEquals("\"hello\"", JsonWriter.write(JsonParser.parse("\"hello\"")));
    assertEquals("42", JsonWriter.write(JsonParser.parse("42")));
    assertEquals("-3.5", JsonWriter.write(JsonParser.parse("-3.5")));
    assertEquals("true", JsonWriter.write(JsonParser.parse("true")));
    assertEquals("false", JsonWriter.write(JsonParser.parse(" false ")));
    assertEquals("null", JsonWriter.write(JsonParser.parse("null")));
  }

  @Test
  void parsesNestedObjectsAndArrays() {
    String text = "{\"a\":1,\"b\":[1,2,3],\"c\":{\"d\":true,\"e\":null}}";
    JsonValue value = JsonParser.parse(text);
    assertTrue(value instanceof JsonValue.JsonObject);
    JsonValue.JsonObject obj = (JsonValue.JsonObject) value;
    assertEquals(1, obj.getInt("a", -1));
    assertEquals(3, obj.getArray("b").size());
    assertEquals(true, obj.getObject("c").getBoolean("d", false));
    // Round-trip: re-serializing the parsed tree reproduces the same text, key order preserved.
    assertEquals(text, JsonWriter.write(value));
  }

  @Test
  void unescapesStringContent() {
    JsonValue value = JsonParser.parse("\"line\\nbreak \\\"quoted\\\" \\u0041\"");
    assertEquals("line\nbreak \"quoted\" A", ((JsonValue.JsonString) value).value());
  }

  @Test
  void escapesStringContentOnWrite() {
    JsonValue value = JsonValue.of("line\nbreak \"quoted\" \u0007");
    assertEquals("\"line\\nbreak \\\"quoted\\\" \\u0007\"", JsonWriter.write(value));
  }

  @Test
  void preservesIntegerFormattingOnRoundTrip() {
    // A programmatically-built integral double writes without a trailing ".0".
    assertEquals("5", JsonWriter.write(JsonValue.of(5.0)));
    assertEquals("5", JsonWriter.write(JsonValue.of(5L)));
    assertEquals("2.5", JsonWriter.write(JsonValue.of(2.5)));
  }

  @Test
  void objectHelpersBuildExpectedShape() {
    JsonValue.JsonObject obj =
        JsonValue.object().put("name", "bazlang_step").put("count", 3).put("ok", true);
    assertEquals("{\"name\":\"bazlang_step\",\"count\":3,\"ok\":true}", JsonWriter.write(obj));
  }

  @Test
  void objectOfBuildsFromAlternatingPairs() {
    JsonValue.JsonObject obj = JsonValue.objectOf("line", 10L, "stmt", 1L, "ok", true);
    assertEquals("{\"line\":10,\"stmt\":1,\"ok\":true}", JsonWriter.write(obj));
  }

  @Test
  void rejectsMalformedInput() {
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("{"));
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("[1,]"));
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("\"unterminated"));
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("nul"));
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("01"));
    assertThrows(JsonParser.JsonParseException.class, () -> JsonParser.parse("{\"a\":1} trailing"));
  }
}
