package com.davidconneely.repl;

public interface Shell extends AutoCloseable {
  /** Read a line of REPL input. Returns null on EOF. Throws BreakException on Ctrl+C. */
  String readReplLine();

  /** Pre-fill the input buffer for the next readReplLine() call (e.g., EDIT command). */
  void prefillInput(String text);

  /** Show a status or error message (persists until next input). */
  void setStatus(String status);

  /** Print a system message to the screen (e.g., REPL feedback, line echoes). */
  void systemPrintln(String text);

  @Override
  void close();
}
