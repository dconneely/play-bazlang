package com.davidconneely.repl;

/**
 * Represents the input source for the interactive REPL loop, called by {@link Repl}
 *
 * <p>Implementing classes normally wrap TerminalEngine.readLine with behaviour such as adding any
 * prompt text or prefilled values that make sense for the next REPL line.
 */
@FunctionalInterface
public interface ReplReader {
  /** Reads a line of REPL input. Returns null on EOF. Throws BreakException on Ctrl+C. */
  String readReplInput();
}
