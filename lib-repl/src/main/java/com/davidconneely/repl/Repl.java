package com.davidconneely.repl;

/** Drives the interactive read-eval-print loop, independent of terminal or input concerns. */
public final class Repl {
  private Repl() {
    /* prevent instantiation. */
  }

  /**
   * Repeatedly reads a line via {@code reader} and dispatches it to {@code handler}, until EOF, a
   * blank line's ambient {@link BreakException} is thrown by {@code reader}, or {@code handler}
   * returns {@code false}. A {@link BreakException} from {@code reader} (e.g. Ctrl+C) restarts the
   * loop rather than exiting it; a blank line is silently skipped.
   *
   * @param reader supplies each line of input.
   * @param handler processes each non-blank line; returning {@code false} ends the loop.
   */
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
