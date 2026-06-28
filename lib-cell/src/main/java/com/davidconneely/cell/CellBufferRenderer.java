package com.davidconneely.cell;

import java.io.PrintWriter;

public class CellBufferRenderer {
  public void renderContentRows(
      PrintWriter out, CellBuffer cellBuffer, int rowsToRender, int colsToRender) {
    final var sb = new StringBuilder();
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
        final long attr = cellBuffer.getAttr(r, c);
        if (attr != activeAttr) {
          final int fg = CellBuffer.unpackFgColour(attr);
          final int bg = CellBuffer.unpackBgColour(attr);
          final int style = CellBuffer.unpackStyle(attr);

          final boolean resetNeeded =
              (activeStyles & ~style) != 0
                  || (!CellAttributes.isDefault(activeFgColour) && CellAttributes.isDefault(fg))
                  || (!CellAttributes.isDefault(activeBgColour) && CellAttributes.isDefault(bg));

          sb.setLength(0);
          if (resetNeeded) {
            sb.append('0');
            activeStyles = 0;
            activeFgColour = CellAttributes.COLOUR_DEFAULT;
            activeBgColour = CellAttributes.COLOUR_DEFAULT;
          }

          emitStyles(sb, style, activeStyles);
          activeStyles = style;

          activeFgColour = emitColour(sb, fg, activeFgColour, 38, 39);
          activeBgColour = emitColour(sb, bg, activeBgColour, 48, 49);

          if (!sb.isEmpty()) {
            out.print("\033[");
            out.print(sb);
            out.print('m');
          }
          activeAttr = attr;
        }
        final int cp = cellBuffer.getCell(r, c);
        if (cp == 0x2800) {
          out.print(' ');
        } else if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
          out.print((char) cp);
        } else {
          out.print(Character.toChars(cp));
        }
      }
      if (activeStyles != 0
          || !CellAttributes.isDefault(activeFgColour)
          || !CellAttributes.isDefault(activeBgColour)) {
        out.print("\033[m");
      }
      out.print("\033[K");
    }
  }

  private static void appendSep(StringBuilder sb) {
    if (!sb.isEmpty()) {
      sb.append(';');
    }
  }

  private static void emitStyles(StringBuilder sb, int style, int activeStyles) {
    if ((style & CellAttributes.STYLE_BOLD) != 0
        && (activeStyles & CellAttributes.STYLE_BOLD) == 0) {
      appendSep(sb);
      sb.append('1');
    }
    if ((style & CellAttributes.STYLE_FAINT) != 0
        && (activeStyles & CellAttributes.STYLE_FAINT) == 0) {
      appendSep(sb);
      sb.append('2');
    }
    if ((style & CellAttributes.STYLE_ITALIC) != 0
        && (activeStyles & CellAttributes.STYLE_ITALIC) == 0) {
      appendSep(sb);
      sb.append('3');
    }
    if ((style & CellAttributes.STYLE_UNDERLINE) != 0
        && (activeStyles & CellAttributes.STYLE_UNDERLINE) == 0) {
      appendSep(sb);
      sb.append('4');
    }
    if ((style & CellAttributes.STYLE_BLINK) != 0
        && (activeStyles & CellAttributes.STYLE_BLINK) == 0) {
      appendSep(sb);
      sb.append('5');
    }
    if ((style & CellAttributes.STYLE_INVERSE) != 0
        && (activeStyles & CellAttributes.STYLE_INVERSE) == 0) {
      appendSep(sb);
      sb.append('7');
    }
    if ((style & CellAttributes.STYLE_STRIKETHROUGH) != 0
        && (activeStyles & CellAttributes.STYLE_STRIKETHROUGH) == 0) {
      appendSep(sb);
      sb.append('9');
    }
  }

  private static int emitColour(
      StringBuilder sb, int colour, int activeColour, int prefix, int defaultCode) {
    if (!CellAttributes.isDefault(colour)) {
      if (activeColour == CellAttributes.COLOUR_DEFAULT || colour != activeColour) {
        appendSep(sb);
        if (CellAttributes.isRgb(colour)) {
          sb.append(prefix)
              .append(";2;")
              .append((colour >> 16) & 0xFF)
              .append(';')
              .append((colour >> 8) & 0xFF)
              .append(';')
              .append(colour & 0xFF);
        } else {
          final int index = CellAttributes.valueOf(colour);
          if (index < 8) {
            sb.append(prefix - 8 + index);
          } else if (index < 16) {
            sb.append(prefix + 52 + (index - 8));
          } else {
            sb.append(prefix).append(";5;").append(index);
          }
        }
        return colour;
      }
      return activeColour;
    } else if (!CellAttributes.isDefault(activeColour)) {
      appendSep(sb);
      sb.append(defaultCode);
      return CellAttributes.COLOUR_DEFAULT;
    }
    return activeColour;
  }
}
