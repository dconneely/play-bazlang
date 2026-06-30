package com.davidconneely.repl;

/**
 * Represents the input source for the interactive REPL loop.
 *
 * <p>Implementing classes wrap the underlying terminal input mechanism (e.g., JLine's line reader,
 * standard input stream, or a mock list of inputs).
 */
public interface Shell extends AutoCloseable {
  /** Reads a line of REPL input. Returns null on EOF. Throws BreakException on Ctrl+C. */
  String readReplLine();

  @Override
  void close();
}
