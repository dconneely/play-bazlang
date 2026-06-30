package com.davidconneely.repl;

public final class ReplLoop {
  public void run(ReplReader reader, ReplHandler handler) {
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
