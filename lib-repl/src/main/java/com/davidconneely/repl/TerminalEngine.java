package com.davidconneely.repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.IntConsumer;

/**
 * A facade for the underlying terminal implementation (e.g. JLine). This completely isolates the
 * application from the underlying terminal library dependencies.
 */
public interface TerminalEngine extends AutoCloseable {
  /**
   * Current terminal height.
   *
   * @return number of rows.
   */
  int getRows();

  /**
   * Current terminal width.
   *
   * @return number of columns.
   */
  int getColumns();

  /**
   * The writer to send output through.
   *
   * @return a writer for the terminal's output stream.
   */
  PrintWriter writer();

  /**
   * Read a single key, blocking until one is available or the timeout expires.
   *
   * @param timeoutMs how long to wait, in milliseconds; {@code -1} to wait indefinitely.
   * @return the key read, or {@code -1} on timeout.
   * @throws IOException if the underlying terminal read fails.
   */
  int readKey(long timeoutMs) throws IOException;

  /**
   * Read one line of input, editable in place.
   *
   * @param prompt the prompt to display before the input.
   * @param prefill text to pre-populate the input with, or {@code null} for none.
   * @return the line read, or {@code null} on EOF.
   */
  String readLine(String prompt, String prefill);

  /**
   * Register a callback invoked whenever the height of the current multi-line input changes.
   *
   * @param listener called with the new input height in rows.
   */
  void setInputHeightListener(IntConsumer listener);

  /** Force a full redraw of the terminal from the current cursor position onward. */
  void forceRedrawFromCursor();

  /**
   * Register a callback invoked when the user sends an interrupt (e.g. Ctrl+C).
   *
   * @param handler the callback to run.
   */
  void onInterrupt(Runnable handler);

  /**
   * Register a callback invoked when the terminal is resized.
   *
   * @param handler the callback to run.
   */
  void onResize(Runnable handler);

  /** Release the underlying terminal resources and restore its prior state. */
  @Override
  void close();
}
