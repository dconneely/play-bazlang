package com.davidconneely.repl;

public final class BreakException extends RuntimeException {
  public BreakException() {
    super(null, null, true, false);
  }
}
