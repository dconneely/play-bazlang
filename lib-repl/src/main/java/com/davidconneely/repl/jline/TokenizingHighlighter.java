package com.davidconneely.repl.jline;

import com.davidconneely.repl.LineTokenizer;
import com.davidconneely.repl.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * A JLine {@link Highlighter} that colours a line by delegating classification to a neutral {@link
 * LineTokenizer}, painting each returned span with the {@link TextStyle} its name is mapped to in a
 * host-supplied palette - so this class carries no knowledge of the language being highlighted, or
 * of what any given style is "for".
 */
class TokenizingHighlighter implements Highlighter {
  private final LineTokenizer tokenizer;
  private final Map<String, AttributedStyle> styles;

  TokenizingHighlighter(LineTokenizer tokenizer, Map<String, TextStyle> styles) {
    this.tokenizer = tokenizer;
    this.styles =
        styles.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, e -> toAttributedStyle(e.getValue())));
  }

  private static AttributedStyle toAttributedStyle(TextStyle style) {
    var attributedStyle =
        AttributedStyle.DEFAULT.foreground(style.red(), style.green(), style.blue());
    if (style.italic()) {
      attributedStyle = attributedStyle.italic();
    }
    return attributedStyle;
  }

  @Override
  public AttributedString highlight(LineReader reader, String buffer) {
    final List<LineTokenizer.Span> spans = tokenizer.tokenize(buffer);
    final var sb = new AttributedStringBuilder();
    int pos = 0;
    for (final var span : spans) {
      if (span.start() > pos) {
        sb.append(buffer, pos, span.start());
      }
      final AttributedStyle style = styles.getOrDefault(span.style(), AttributedStyle.DEFAULT);
      sb.append(buffer.subSequence(span.start(), span.end()), style);
      pos = span.end();
    }
    if (pos < buffer.length()) {
      sb.append(buffer, pos, buffer.length());
    }
    return sb.toAttributedString();
  }
}
