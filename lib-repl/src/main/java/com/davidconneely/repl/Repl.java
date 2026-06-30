package com.davidconneely.repl;

public final class Repl {
  public void run(Shell ui, ReplHandler handler) {
    while (true) {
      String line;
      try {
        line = ui.readReplLine();
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
