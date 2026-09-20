package com.davidconneely.bazlang;

import com.davidconneely.bazlang.io.VirtualScreen;

/**
 * Prints a line of BazLang source to a {@link VirtualScreen}, syntax-highlighted the same way as
 * JLine's live REPL-input highlighting (see {@link BazLangLineTokenizer}) - shared by {@code LIST}
 * and the REPL's accepted-line echo so the two can't drift apart.
 */
public final class HighlightedLinePrinter {
  private HighlightedLinePrinter() {}

  /**
   * Prints {@code text}, painting each span {@link BazLangLineTokenizer#tokenize} returns with its
   * ink and everything else at the screen's current ink. Does not print a trailing newline.
   *
   * @param screen the screen to print to.
   * @param text the BazLang source line (with or without a leading line number).
   */
  public static void print(VirtualScreen screen, String text) {
    int pos = 0;
    for (final var span : BazLangLineTokenizer.INSTANCE.tokenize(text)) {
      if (span.start() > pos) {
        screen.print(text.substring(pos, span.start()));
      }
      screen.setInk(BazLangLineTokenizer.inkFor(span.style()));
      screen.print(text.substring(span.start(), span.end()));
      screen.setInk(-1);
      pos = span.end();
    }
    if (pos < text.length()) {
      screen.print(text.substring(pos));
    }
  }
}
