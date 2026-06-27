package com.davidconneely.repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.IntConsumer;

/**
 * A facade for the underlying terminal implementation (e.g. JLine). This completely isolates the
 * application from the underlying terminal library dependencies.
 */
public interface TerminalEngine extends AutoCloseable {
  int getRows();

  int getColumns();

  PrintWriter writer();

  int readKey(long timeoutMs) throws IOException;

  String readLine(String prompt, String prefill);

  void setInputHeightListener(IntConsumer listener);

  void forceRedrawFromCursor();

  void onInterrupt(Runnable handler);

  void onResize(Runnable handler);

  @Override
  void close();
}
