package com.davidconneely.repl;

public final class Repl {
  private Repl() {
    /* prevent instantiation. */
  }

  public static void loop(ReplReader reader, ReplHandler handler) {
    while (true) {
      String line;
      try {
        line = reader.readReplInput();
      } catch (BreakException e) {
        continue;
      }
      if (line == null) {
        break;
      }
      if (line.isBlank()) {
        continue;
      }
      if (!handler.handleReplInput(line)) {
        break;
      }
    }
  }
}
