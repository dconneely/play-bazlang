package com.davidconneely.bazlang.debug;

/**
 * The QuotedArg string format used by the AgentDebugger protocol (see docs/language_debugger.md):
 * double-quoted strings with {@code \"}, {@code \\}, {@code \n}, {@code \r}, and {@code \e}
 * escapes.
 */
final class QuotedArg {

  private QuotedArg() {}

  /**
   * Parses a double-quoted string argument such as {@code "hello \"world\\"}.
   *
   * <p>Returns {@code null} if the argument does not start with {@code "}, is not properly closed,
   * or ends with an unmatched backslash.
   */
  static String parse(String arg) {
    if (!arg.startsWith("\"")) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    int i = 1;
    while (i < arg.length()) {
      char c = arg.charAt(i);
      if (c == '"') {
        return i == arg.length() - 1 ? sb.toString() : null;
      } else if (c == '\\') {
        if (i + 1 >= arg.length()) {
          return null;
        }
        char next = arg.charAt(i + 1);
        if (next == '"') {
          sb.append('"');
        } else if (next == '\\') {
          sb.append('\\');
        } else if (next == 'n') {
          sb.append('\n');
        } else if (next == 'r') {
          sb.append('\r');
        } else if (next == 'e') {
          sb.append('\u001B');
        } else {
          sb.append('\\').append(next);
        }
        i += 2;
      } else {
        sb.append(c);
        i++;
      }
    }
    return null; // missing closing quote
  }

  /** Encodes a Java string as a QuotedArg for protocol output. */
  static String format(String value) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\u001B' -> sb.append("\\e");
        default -> sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
