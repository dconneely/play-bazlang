package com.davidconneely.repl.jline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** A clipboard implementation that uses native OS CLI utilities (pbcopy, clip, xclip). */
class NativeProcessClipboard implements Clipboard {

  @Override
  public void copy(String text) {
    try {
      final String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
      ProcessBuilder pb;
      if (os.contains("mac")) {
        pb = new ProcessBuilder("pbcopy");
      } else if (os.contains("win")) {
        pb = new ProcessBuilder("clip");
      } else {
        pb = new ProcessBuilder("xclip", "-selection", "clipboard");
      }
      final var process = pb.start();
      process.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().close();
    } catch (IOException e) {
      // Ignore missing native clipboard utility
    }
  }
}
