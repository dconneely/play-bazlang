package com.davidconneely.bazlang.mcp;

/**
 * A minimal recursive-descent JSON parser (RFC 8259), hand-written so the {@code mcp} package has
 * no external dependency. Not a general-purpose parser: it accepts standard JSON exactly and
 * rejects everything else with a {@link JsonParseException}.
 */
public final class JsonParser {

  /** Thrown for malformed JSON input; mapped to a JSON-RPC {@code -32700 Parse error}. */
  public static final class JsonParseException extends RuntimeException {
    /**
     * Creates a parse exception.
     *
     * @param message a message describing the malformed input.
     */
    public JsonParseException(String message) {
      super(message);
    }
  }

  private final String text;
  private int pos;

  private JsonParser(String text) {
    this.text = text;
    this.pos = 0;
  }

  /**
   * Parses a complete JSON document.
   *
   * @param text the JSON source text.
   * @return the parsed value tree.
   */
  public static JsonValue parse(String text) {
    JsonParser parser = new JsonParser(text);
    parser.skipWhitespace();
    JsonValue value = parser.parseValue();
    parser.skipWhitespace();
    if (parser.pos != text.length()) {
      throw new JsonParseException("Unexpected trailing content at position " + parser.pos);
    }
    return value;
  }

  private JsonValue parseValue() {
    char c = peek();
    return switch (c) {
      case '{' -> parseObject();
      case '[' -> parseArray();
      case '"' -> new JsonValue.JsonString(parseStringLiteral());
      case 't' -> parseLiteral("true", new JsonValue.JsonBoolean(true));
      case 'f' -> parseLiteral("false", new JsonValue.JsonBoolean(false));
      case 'n' -> parseLiteral("null", JsonValue.JsonNull.INSTANCE);
      default -> parseNumber();
    };
  }

  private JsonValue parseLiteral(String literal, JsonValue value) {
    if (!text.regionMatches(pos, literal, 0, literal.length())) {
      throw new JsonParseException("Invalid literal at position " + pos);
    }
    pos += literal.length();
    return value;
  }

  private JsonValue.JsonObject parseObject() {
    expect('{');
    JsonValue.JsonObject obj = JsonValue.object();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return obj;
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw new JsonParseException("Expected string key at position " + pos);
      }
      String key = parseStringLiteral();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      obj.put(key, parseValue());
      skipWhitespace();
      char c = next();
      if (c == '}') {
        return obj;
      }
      if (c != ',') {
        throw new JsonParseException("Expected ',' or '}' at position " + (pos - 1));
      }
    }
  }

  private JsonValue.JsonArray parseArray() {
    expect('[');
    JsonValue.JsonArray arr = JsonValue.array();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return arr;
    }
    while (true) {
      skipWhitespace();
      arr.add(parseValue());
      skipWhitespace();
      char c = next();
      if (c == ']') {
        return arr;
      }
      if (c != ',') {
        throw new JsonParseException("Expected ',' or ']' at position " + (pos - 1));
      }
    }
  }

  private String parseStringLiteral() {
    expect('"');
    StringBuilder sb = new StringBuilder();
    while (true) {
      char c = next();
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        char esc = next();
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> sb.append(parseUnicodeEscape());
          default -> throw new JsonParseException("Invalid escape character: \\" + esc);
        }
      } else if (c < 0x20) {
        throw new JsonParseException("Unescaped control character in string");
      } else {
        sb.append(c);
      }
    }
  }

  private char parseUnicodeEscape() {
    if (pos + 4 > text.length()) {
      throw new JsonParseException("Truncated \\u escape at position " + pos);
    }
    String hex = text.substring(pos, pos + 4);
    try {
      char c = (char) Integer.parseInt(hex, 16);
      pos += 4;
      return c;
    } catch (NumberFormatException e) {
      throw new JsonParseException("Invalid \\u escape: " + hex);
    }
  }

  private JsonValue.JsonNumber parseNumber() {
    int start = pos;
    if (peekOrNul() == '-') {
      pos++;
    }
    if (!isDigit(peekOrNul())) {
      throw new JsonParseException("Invalid number at position " + start);
    }
    if (text.charAt(pos) == '0') {
      pos++;
    } else {
      while (isDigit(peekOrNul())) {
        pos++;
      }
    }
    if (peekOrNul() == '.') {
      pos++;
      if (!isDigit(peekOrNul())) {
        throw new JsonParseException("Invalid number fraction at position " + pos);
      }
      while (isDigit(peekOrNul())) {
        pos++;
      }
    }
    if (peekOrNul() == 'e' || peekOrNul() == 'E') {
      pos++;
      if (peekOrNul() == '+' || peekOrNul() == '-') {
        pos++;
      }
      if (!isDigit(peekOrNul())) {
        throw new JsonParseException("Invalid number exponent at position " + pos);
      }
      while (isDigit(peekOrNul())) {
        pos++;
      }
    }
    String raw = text.substring(start, pos);
    return new JsonValue.JsonNumber(Double.parseDouble(raw), raw);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private char peek() {
    if (pos >= text.length()) {
      throw new JsonParseException("Unexpected end of input");
    }
    return text.charAt(pos);
  }

  private char peekOrNul() {
    return pos < text.length() ? text.charAt(pos) : '\0';
  }

  private char next() {
    if (pos >= text.length()) {
      throw new JsonParseException("Unexpected end of input");
    }
    return text.charAt(pos++);
  }

  private void expect(char c) {
    if (pos >= text.length() || text.charAt(pos) != c) {
      throw new JsonParseException("Expected '" + c + "' at position " + pos);
    }
    pos++;
  }

  private void skipWhitespace() {
    while (pos < text.length()) {
      char c = text.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        pos++;
      } else {
        break;
      }
    }
  }
}
