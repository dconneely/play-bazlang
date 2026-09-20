package com.davidconneely.repl;

import java.util.List;

/**
 * Classifies spans of a line of text for syntax highlighting, without {@code lib-repl} needing to
 * know anything about the language being classified. The host application supplies an
 * implementation; {@code lib-repl} wires it into JLine's {@code Highlighter} internally, painting
 * each span with the {@link TextStyle} its name is mapped to in the palette registered alongside
 * this tokenizer (see {@code TerminalEngine}'s constructor).
 */
@FunctionalInterface
public interface LineTokenizer {
  /**
   * Classify {@code line} into spans to highlight.
   *
   * @param line the raw text to classify; may be a partial or invalid line, since this is called
   *     again on every keystroke while the user is still typing.
   * @return spans in ascending, non-overlapping order by {@link Span#start()}. Regions not covered
   *     by any span render unstyled.
   */
  List<Span> tokenize(String line);

  /**
   * One highlighted span of a line.
   *
   * @param start the index of the first character covered (inclusive).
   * @param end the index one past the last character covered (exclusive).
   * @param style the name, in the host application's registered style palette, of the {@link
   *     TextStyle} to paint this span with. Its meaning is entirely up to the host application -
   *     {@code lib-repl} only ever uses it as a lookup key.
   */
  record Span(int start, int end, String style) {}
}
