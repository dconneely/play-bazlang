package com.davidconneely.repl;

/** Processes each line of input read by the {@link Repl} loop. */
@FunctionalInterface
public interface ReplHandler {
  /**
   * Handle one line of REPL input.
   *
   * @param line the input line (never null or blank)
   * @return true to continue the REPL, false to exit
   */
  boolean handleReplInput(String line);
}
