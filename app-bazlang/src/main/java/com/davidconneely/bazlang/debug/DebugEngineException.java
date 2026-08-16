package com.davidconneely.bazlang.debug;

/**
 * Thrown by {@link DebugEngine} for engine/protocol-level failures that are not BASIC runtime
 * errors — a malformed REPL command, an invalid breakpoint condition, calling {@link
 * DebugEngine#go()} when not paused, and similar. Distinct from {@link
 * com.davidconneely.bazlang.ReportException}, which represents a BASIC-level runtime error with a
 * structured report code and source location.
 */
public final class DebugEngineException extends RuntimeException {
  public DebugEngineException(String message) {
    super(message);
  }
}
