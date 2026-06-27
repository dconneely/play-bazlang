package com.davidconneely.repl;

@FunctionalInterface
public interface ReplHandler {
  /**
   * Handle one line of REPL input.
   *
   * @param line the input line (never null or blank)
   * @param ui the Shell for feedback (status, system output, prefill)
   * @return true to continue the REPL, false to exit
   */
  boolean handleReplInput(String line, Shell ui);
}
