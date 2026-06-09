package com.davidconneely.cell;

import java.io.PrintWriter;

public class CellBufferRenderer {
  public void renderContentRows(
      PrintWriter out, CellBuffer cellBuffer, int rowsToRender, int colsToRender) {
    for (int r = 0; r < rowsToRender; r++) {
      out.printf("\033[%d;1H", r + 1);
      int activeFgColor = CellAttributes.COLOR_DEFAULT;
      int activeBgColor = CellAttributes.COLOR_DEFAULT;
      int activeStyles = 0;
      for (int c = 0; c < colsToRender; c++) {
        int fg = cellBuffer.getFgColor(r, c);
        int bg = cellBuffer.getBgColor(r, c);
        int style = cellBuffer.getStyle(r, c);
        if (style != activeStyles || fg != activeFgColor || bg != activeBgColor) {
          boolean resetNeeded =
              (activeStyles & ~style) != 0
                  || ((activeFgColor & CellAttributes.COLOR_TYPE_MASK)
                          != CellAttributes.COLOR_DEFAULT
                      && (fg & CellAttributes.COLOR_TYPE_MASK) == CellAttributes.COLOR_DEFAULT)
                  || ((activeBgColor & CellAttributes.COLOR_TYPE_MASK)
                          != CellAttributes.COLOR_DEFAULT
                      && (bg & CellAttributes.COLOR_TYPE_MASK) == CellAttributes.COLOR_DEFAULT);
          if (resetNeeded) {
            out.print("\033[m");
            activeStyles = 0;
            activeFgColor = CellAttributes.COLOR_DEFAULT;
            activeBgColor = CellAttributes.COLOR_DEFAULT;
          }
          activeStyles = emitStyles(out, style, activeStyles);
          activeFgColor = emitColor(out, fg, activeFgColor, 38, 30, 90, "\033[39m");
          activeBgColor = emitColor(out, bg, activeBgColor, 48, 40, 100, "\033[49m");
        }
        out.print(Character.toString(cellBuffer.getCell(r, c)));
      }
      if (activeStyles != 0
          || activeFgColor != CellAttributes.COLOR_DEFAULT
          || activeBgColor != CellAttributes.COLOR_DEFAULT) {
        out.print("\033[m");
      }
      out.print("\033[K");
    }
  }

  private int emitStyles(PrintWriter out, int style, int activeStyles) {
    if ((style & CellAttributes.STYLE_BOLD) != 0
        && (activeStyles & CellAttributes.STYLE_BOLD) == 0) {
      out.print("\033[1m");
    }
    if ((style & CellAttributes.STYLE_FAINT) != 0
        && (activeStyles & CellAttributes.STYLE_FAINT) == 0) {
      out.print("\033[2m");
    }
    if ((style & CellAttributes.STYLE_ITALIC) != 0
        && (activeStyles & CellAttributes.STYLE_ITALIC) == 0) {
      out.print("\033[3m");
    }
    if ((style & CellAttributes.STYLE_UNDERLINE) != 0
        && (activeStyles & CellAttributes.STYLE_UNDERLINE) == 0) {
      out.print("\033[4m");
    }
    if ((style & CellAttributes.STYLE_BLINK) != 0
        && (activeStyles & CellAttributes.STYLE_BLINK) == 0) {
      out.print("\033[5m");
    }
    if ((style & CellAttributes.STYLE_INVERSE) != 0
        && (activeStyles & CellAttributes.STYLE_INVERSE) == 0) {
      out.print("\033[7m");
    }
    return style;
  }

  private int emitColor(
      PrintWriter out,
      int color,
      int activeColor,
      int trueColorSgr,
      int ansi8Base,
      int ansi8HiBase,
      String resetSeq) {
    int colorType = color & CellAttributes.COLOR_TYPE_MASK;
    if (colorType == CellAttributes.COLOR_TYPE_RGB
        || colorType == CellAttributes.COLOR_TYPE_INDEX) {
      if (activeColor == CellAttributes.COLOR_DEFAULT || color != activeColor) {
        if (colorType == CellAttributes.COLOR_TYPE_RGB) {
          out.printf(
              "\033[%d;2;%d;%d;%dm",
              trueColorSgr, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        } else {
          int index = color & 0xFFFFFF;
          if (index < 8) {
            out.printf("\033[%dm", ansi8Base + index);
          } else if (index < 16) {
            out.printf("\033[%dm", ansi8HiBase + (index - 8));
          } else {
            out.printf("\033[%d;5;%dm", trueColorSgr, index);
          }
        }
      }
    } else if ((activeColor & CellAttributes.COLOR_TYPE_MASK) != CellAttributes.COLOR_DEFAULT) {
      out.print(resetSeq);
    }
    return color;
  }
}
