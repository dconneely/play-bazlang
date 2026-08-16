package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.io.MockScreen;

/**
 * Read-only text views of a {@link MockScreen} buffer: substring search (the {@code CSC} break
 * condition) and the compressed grid dump used by {@code /RSC} / {@code bazlang_screen}. {@link
 * #buildScreenString} is public so it is reusable from the {@code com.davidconneely.bazlang.mcp}
 * package.
 */
public final class ScreenText {

  private ScreenText() {}

  /** Returns true when any screen row contains {@code text} (case-insensitive). */
  static boolean containsText(MockScreen mockScreen, String text) {
    String query = text.toLowerCase();
    int rows = mockScreen.printHeight();
    int cols = mockScreen.printWidth();
    for (int r = 0; r < rows; r++) {
      StringBuilder sb = new StringBuilder();
      for (int c = 0; c < cols; c++) {
        sb.appendCodePoint(mockScreen.getScreenCodepoint(r, c));
      }
      if (sb.toString().toLowerCase().contains(query)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Dumps the given (0-based, inclusive, clamped) rectangle of the screen buffer. Rows are
   * separated by {@code \n}; runs of more than four spaces compress to <code>{N}</code>; {@code
   * showAttr} adds {@code [fg,bg]} annotations at attribute changes.
   */
  public static String buildScreenString(
      MockScreen mockScreen, int rStart, int rEnd, int cStart, int cEnd, boolean showAttr) {
    int maxRows = mockScreen.printHeight();
    int maxCols = mockScreen.printWidth();
    int r1 = Math.clamp(rStart, 0, maxRows - 1);
    int r2 = Math.clamp(rEnd, 0, maxRows - 1);
    int c1 = Math.clamp(cStart, 0, maxCols - 1);
    int c2 = Math.clamp(cEnd, 0, maxCols - 1);
    StringBuilder output = new StringBuilder();
    int lastAttr = -1;
    for (int r = r1; r <= r2; r++) {
      if (r > r1) {
        output.append('\n');
      }
      int c = c1;
      while (c <= c2) {
        int cp = mockScreen.getScreenCodepoint(r, c);
        if (cp == ' ') {
          int spaceAttr = showAttr ? mockScreen.getScreenAttributes(r, c) : 0;
          int start = c;
          while (c <= c2
              && mockScreen.getScreenCodepoint(r, c) == ' '
              && (!showAttr || mockScreen.getScreenAttributes(r, c) == spaceAttr)) {
            c++;
          }
          int count = c - start;
          if (showAttr && spaceAttr != lastAttr) {
            output
                .append('[')
                .append(spaceAttr & 7)
                .append(',')
                .append((spaceAttr >> 3) & 7)
                .append(']');
            lastAttr = spaceAttr;
          }
          if (count > 4) {
            output.append('{').append(count).append('}');
          } else {
            output.append(" ".repeat(count));
          }
        } else {
          if (showAttr) {
            int attr = mockScreen.getScreenAttributes(r, c);
            if (attr != lastAttr) {
              output.append('[').append(attr & 7).append(',').append((attr >> 3) & 7).append(']');
              lastAttr = attr;
            }
          }
          output.appendCodePoint(cp);
          c++;
        }
      }
    }
    return output.toString();
  }
}
