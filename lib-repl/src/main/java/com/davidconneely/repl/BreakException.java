package com.davidconneely.repl;

/**
 * Signals that the user interrupted input (e.g. Ctrl+C) while a {@link ReplReader} was reading a
 * line, so the {@link Repl} loop should restart rather than exit. Carries no message and disables
 * stack-trace capture, since it is used purely for control flow.
 */
public final class BreakException extends RuntimeException {
  /** Create a new instance. */
  public BreakException() {
    super(null, null, true, false);
  }
}
