package com.davidconneely.cell;

import java.io.PrintWriter;

public class CellBufferRenderer {
  public void renderContentRows(
      PrintWriter out, CellBuffer cellBuffer, int rowsToRender, int colsToRender) {
    for (int r = 0; r < rowsToRender; r++) {
      out.print("\033[");
      out.print(r + 1);
      out.print(";1H");
      long activeAttr =
          CellBuffer.packAttributes(CellAttributes.COLOR_DEFAULT, CellAttributes.COLOR_DEFAULT, 0);
      int activeFgColor = CellAttributes.COLOR_DEFAULT;
      int activeBgColor = CellAttributes.COLOR_DEFAULT;
      int activeStyles = 0;
      for (int c = 0; c < colsToRender; c++) {
        long attr = cellBuffer.getAttr(r, c);
        if (attr != activeAttr) {
          int fg = CellBuffer.unpackFgColor(attr);
          int bg = CellBuffer.unpackBgColor(attr);
          int style = CellBuffer.unpackStyle(attr);
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
          activeAttr = attr;
        }
        int cp = cellBuffer.getCell(r, c);
        if (cp == 0x2800) {
          out.print(' ');
        } else if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
          out.print((char) cp);
        } else {
          out.print(Character.toChars(cp));
        }
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
          out.print("\033[");
          out.print(trueColorSgr);
          out.print(";2;");
          out.print((color >> 16) & 0xFF);
          out.print(";");
          out.print((color >> 8) & 0xFF);
          out.print(";");
          out.print(color & 0xFF);
          out.print("m");
        } else {
          int index = color & 0xFFFFFF;
          if (index < 8) {
            out.print("\033[");
            out.print(ansi8Base + index);
            out.print("m");
          } else if (index < 16) {
            out.print("\033[");
            out.print(ansi8HiBase + (index - 8));
            out.print("m");
          } else {
            out.print("\033[");
            out.print(trueColorSgr);
            out.print(";5;");
            out.print(index);
            out.print("m");
          }
        }
      }
    } else if ((activeColor & CellAttributes.COLOR_TYPE_MASK) != CellAttributes.COLOR_DEFAULT) {
      out.print(resetSeq);
    }
    return color;
  }
}
