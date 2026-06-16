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
          CellBuffer.packAttributes(
              CellAttributes.COLOUR_DEFAULT, CellAttributes.COLOUR_DEFAULT, 0);
      int activeFgColour = CellAttributes.COLOUR_DEFAULT;
      int activeBgColour = CellAttributes.COLOUR_DEFAULT;
      int activeStyles = 0;
      for (int c = 0; c < colsToRender; c++) {
        long attr = cellBuffer.getAttr(r, c);
        if (attr != activeAttr) {
          int fg = CellBuffer.unpackFgColour(attr);
          int bg = CellBuffer.unpackBgColour(attr);
          int style = CellBuffer.unpackStyle(attr);
          boolean resetNeeded =
              (activeStyles & ~style) != 0
                  || ((activeFgColour & CellAttributes.COLOUR_TYPE_MASK)
                          != CellAttributes.COLOUR_DEFAULT
                      && (fg & CellAttributes.COLOUR_TYPE_MASK) == CellAttributes.COLOUR_DEFAULT)
                  || ((activeBgColour & CellAttributes.COLOUR_TYPE_MASK)
                          != CellAttributes.COLOUR_DEFAULT
                      && (bg & CellAttributes.COLOUR_TYPE_MASK) == CellAttributes.COLOUR_DEFAULT);
          if (resetNeeded) {
            out.print("\033[m");
            activeStyles = 0;
            activeFgColour = CellAttributes.COLOUR_DEFAULT;
            activeBgColour = CellAttributes.COLOUR_DEFAULT;
          }
          activeStyles = emitStyles(out, style, activeStyles);
          activeFgColour = emitColour(out, fg, activeFgColour, 38, 30, 90, "\033[39m");
          activeBgColour = emitColour(out, bg, activeBgColour, 48, 40, 100, "\033[49m");
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
          || activeFgColour != CellAttributes.COLOUR_DEFAULT
          || activeBgColour != CellAttributes.COLOUR_DEFAULT) {
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

  private int emitColour(
      PrintWriter out,
      int colour,
      int activeColour,
      int trueColourSgr,
      int ansi8Base,
      int ansi8HiBase,
      String resetSeq) {
    int colourType = colour & CellAttributes.COLOUR_TYPE_MASK;
    if (colourType == CellAttributes.COLOUR_TYPE_RGB
        || colourType == CellAttributes.COLOUR_TYPE_INDEX) {
      if (activeColour == CellAttributes.COLOUR_DEFAULT || colour != activeColour) {
        if (colourType == CellAttributes.COLOUR_TYPE_RGB) {
          out.print("\033[");
          out.print(trueColourSgr);
          out.print(";2;");
          out.print((colour >> 16) & 0xFF);
          out.print(";");
          out.print((colour >> 8) & 0xFF);
          out.print(";");
          out.print(colour & 0xFF);
          out.print("m");
        } else {
          int index = colour & 0xFFFFFF;
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
            out.print(trueColourSgr);
            out.print(";5;");
            out.print(index);
            out.print("m");
          }
        }
      }
    } else if ((activeColour & CellAttributes.COLOUR_TYPE_MASK) != CellAttributes.COLOUR_DEFAULT) {
      out.print(resetSeq);
    }
    return colour;
  }
}
