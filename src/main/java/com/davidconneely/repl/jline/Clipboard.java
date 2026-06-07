package com.davidconneely.repl.jline;

/** Façade for system clipboard interactions. */
@FunctionalInterface
public interface Clipboard {

  /**
   * Copies the given text to the system clipboard.
   *
   * @param text the text to copy
   */
  void copy(String text);

  /**
   * Returns the default clipboard implementation for the current platform.
   *
   * @return the default clipboard
   */
  static Clipboard getDefault() {
    return new NativeProcessClipboard();
  }
}
