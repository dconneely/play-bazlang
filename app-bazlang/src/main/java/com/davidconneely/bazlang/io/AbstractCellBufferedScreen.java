package com.davidconneely.bazlang.io;

import com.davidconneely.cell.CellAttributes;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.PixelMode;

/**
 * Abstract base class for {@link VirtualScreen} / {@link VirtualInput} implementations that use a
 * {@link CellBuffer} to manage text and pixel attributes. Also implements {@link VirtualSpeaker},
 * inherited as a no-op by every subclass - real audio playback lives entirely in {@link
 * JavaSoundSpeaker}, an unrelated class that plays no screen role at all.
 */
public abstract class AbstractCellBufferedScreen
    implements VirtualScreen, VirtualInput, VirtualSpeaker {
  /** The underlying cell buffer holding this screen's text and pixel content. */
  protected final CellBuffer cellBuffer;

  /** The cursor's current row, 0-based from the top. */
  protected int cursorRow = 0;

  /** The cursor's current column, 0-based from the left. */
  protected int cursorCol = 0;

  // Active attribute states
  /** Current default ink (foreground) colour, or {@code -1} for the screen's own default. */
  protected int activeInk = -1;

  /** Current default paper (background) colour, or {@code -1} for the screen's own default. */
  protected int activePaper = -1;

  /** Current default brightness. */
  protected int activeBright = 0;

  /** Current default flash (blink) setting. */
  protected int activeFlash = 0;

  /** Current default inverse-video setting. */
  protected int activeInverse = 0;

  /** Current default overlay (XOR-plot) setting. */
  protected int activeOver = 0;

  /**
   * Create a screen backed by the given cell buffer.
   *
   * @param cellBuffer the buffer to render text and pixel content into.
   */
  protected AbstractCellBufferedScreen(CellBuffer cellBuffer) {
    this.cellBuffer = cellBuffer;
  }

  @Override
  public int currentRow() {
    return cursorRow;
  }

  @Override
  public int currentCol() {
    return cursorCol;
  }

  @Override
  public int printWidth() {
    return cellBuffer.cols();
  }

  @Override
  public int printHeight() {
    return cellBuffer.rows();
  }

  @Override
  public int plotWidth() {
    return cellBuffer.pixelWidth();
  }

  @Override
  public int plotHeight() {
    return cellBuffer.pixelHeight();
  }

  @Override
  public int plotMode() {
    return cellBuffer.mode().pixelsPerCellX() * cellBuffer.mode().pixelsPerCellY();
  }

  @Override
  public void locate(int row, int col) {
    cursorRow = Math.clamp(row, 0, cellBuffer.rows() - 1);
    cursorCol = Math.clamp(col, 0, cellBuffer.cols() - 1);
  }

  @Override
  public void setInk(int colour) {
    this.activeInk = colour;
  }

  @Override
  public void setPaper(int colour) {
    this.activePaper = colour;
  }

  @Override
  public void setBright(int bright) {
    this.activeBright = bright;
  }

  @Override
  public void setFlash(int flash) {
    this.activeFlash = flash;
  }

  @Override
  public void setInverse(int inverse) {
    this.activeInverse = inverse;
  }

  @Override
  public void setOver(int over) {
    this.activeOver = over;
  }

  @Override
  public void setPlotMode(PixelMode mode) {
    cellBuffer.setMode(mode);
  }

  @Override
  public void plot(int x, int y) {
    final int cellFg = getMappedColour(activeInk, activePaper);
    final int cellBg = getMappedColour(activePaper, activeInk);
    final int currentStyle =
        cellBuffer.isPixelInBounds(x, y)
            ? cellBuffer.getStyle(cellBuffer.pixelToCellRow(y), cellBuffer.pixelToCellCol(x))
            : 0;
    cellBuffer.plot(
        x, y, cellFg, cellBg, getMappedStyle(currentStyle), activeInverse != 1, activeOver == 1);
    if (cellBuffer.isPixelInBounds(x, y)) {
      cursorRow = cellBuffer.pixelToCellRow(y);
      cursorCol = cellBuffer.pixelToCellCol(x) + 1;
    }
    afterPlot();
  }

  /** Hook called after a plot operation completes, e.g. to trigger rendering. */
  protected abstract void afterPlot();

  @Override
  public int point(int x, int y) {
    return cellBuffer.point(x, y);
  }

  @Override
  public int getScreenCodepoint(int row, int col) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      return 32;
    }
    return cellBuffer.getCell(row, col);
  }

  @Override
  public int getScreenAttributes(int row, int col) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      return 56;
    }
    int fg = cellBuffer.getFgColour(row, col);
    int bg = cellBuffer.getBgColour(row, col);
    int style = cellBuffer.getStyle(row, col);

    int flash = (style & CellAttributes.STYLE_BLINK) != 0 ? 1 : 0;
    int bright = (style & CellAttributes.STYLE_BOLD) != 0 ? 1 : 0;
    int ink = resolveZxColour(fg, true);
    int paper = resolveZxColour(bg, false);

    return (flash * 128) + (bright * 64) + (paper * 8) + ink;
  }

  @Override
  public int getXAttributes(int row, int col, int select) {
    if (row < 0 || row >= cellBuffer.rows() || col < 0 || col >= cellBuffer.cols()) {
      if (select == 0 || select == 1) {
        return -1;
      }
      return 0;
    }
    switch (select) {
      case 0:
        int fg = cellBuffer.getFgColour(row, col);
        if (CellAttributes.isDefault(fg)) {
          return -1;
        }
        if (CellAttributes.isIndex(fg)) {
          return 256 + CellAttributes.valueOf(fg);
        }
        return 16_777_216 + CellAttributes.valueOf(fg);
      case 1:
        int bg = cellBuffer.getBgColour(row, col);
        if (CellAttributes.isDefault(bg)) {
          return -1;
        }
        if (CellAttributes.isIndex(bg)) {
          return 256 + CellAttributes.valueOf(bg);
        }
        return 16_777_216 + CellAttributes.valueOf(bg);
      case 2:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_BLINK) != 0 ? 1 : 0;
      case 3:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_BOLD) != 0 ? 1 : 0;
      case 4:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_INVERSE) != 0 ? 1 : 0;
      case 5:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_ITALIC) != 0 ? 1 : 0;
      case 6:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_UNDERLINE) != 0 ? 1 : 0;
      case 7:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_STRIKETHROUGH) != 0 ? 1 : 0;
      case 8:
        return (cellBuffer.getStyle(row, col) & CellAttributes.STYLE_FAINT) != 0 ? 1 : 0;
      default:
        return 0;
    }
  }

  private static final int[] ZX_TO_RGB = {
    0x000000, 0x0000D7, 0xD70000, 0xD700D7, 0x00D700, 0x00D7D7, 0xD7D700, 0xD7D7D7
  };

  private static final int[] ZX_TO_RGB_BRIGHT = {
    0x000000, 0x0000FF, 0xFF0000, 0xFF00FF, 0x00FF00, 0x00FFFF, 0xFFFF00, 0xFFFFFF
  };

  /**
   * Maps a BazLang colour code to a {@link CellAttributes}-encoded colour value.
   *
   * <p>BazLang colour codes:
   *
   * <ul>
   *   <li>0-7: ZX Spectrum colour index (black, blue, red, magenta, green, cyan, yellow, white)
   *   <li>8: transparent (preserve existing cell colour)
   *   <li>9: contrast (auto-select black or white against the opposing colour)
   *   <li>256 - 511: terminal 256-colour index (value minus 256 is the ANSI index)
   *   <li>16,777,216 - 33,554,431: 24-bit RGB (value minus 16,777,216 is the RGB24 component)
   *   <li>-1 / anything else: terminal default colour
   * </ul>
   *
   * @param colourCode the BazLang ink or paper value
   * @param opposingCode the other colour (used for contrast mode 9)
   * @return a {@link CellAttributes}-encoded colour, or {@code -1} for transparent
   */
  protected int getMappedColour(int colourCode, int opposingCode) {
    if (colourCode == 8) {
      return -1; // Transparent: preserve existing cell colour
    }
    if (colourCode >= 256 && colourCode <= 511) {
      return CellAttributes.index(colourCode - 256);
    }
    if (colourCode >= 16_777_216 && colourCode <= 33_554_431) {
      return CellAttributes.rgb(colourCode - 16_777_216);
    }
    int zxColour = colourCode;
    if (colourCode == 9) {
      zxColour = (opposingCode >= 0 && opposingCode <= 3) ? 7 : 0;
    }
    if (zxColour >= 0 && zxColour <= 7) {
      int rgb = activeBright == 1 ? ZX_TO_RGB_BRIGHT[zxColour] : ZX_TO_RGB[zxColour];
      return CellAttributes.rgb(rgb);
    }
    return CellAttributes.COLOUR_DEFAULT;
  }

  /**
   * Builds a {@link CellAttributes} style bitmask from BazLang style flags.
   *
   * @param currentStyle the existing {@link CellAttributes} style bitmask for the target cell
   * @return a {@link CellAttributes} style bitmask
   */
  protected int getMappedStyle(int currentStyle) {
    int style = 0;
    if (activeFlash == 8) {
      style |= currentStyle & CellAttributes.STYLE_BLINK;
    } else if (activeFlash == 1) {
      style |= CellAttributes.STYLE_BLINK;
    }
    if (activeBright == 8) {
      style |= currentStyle & CellAttributes.STYLE_BOLD;
    } else if (activeBright == 1) {
      style |= CellAttributes.STYLE_BOLD;
    }
    return style;
  }

  /**
   * Maps a {@link CellAttributes}-encoded colour back to a ZX Spectrum colour index (0-7).
   *
   * @param cellColour a {@link CellAttributes}-encoded colour
   * @param isInk {@code true} when resolving an ink (foreground) colour; used to pick the fallback
   *     (7 = white for ink, 0 = black for paper)
   * @return a ZX Spectrum colour index in the range 0-7
   */
  private int resolveZxColour(int cellColour, boolean isInk) {
    if (CellAttributes.isRgb(cellColour)) {
      int rgb = CellAttributes.valueOf(cellColour);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      int bitR = r > 155 ? 1 : 0;
      int bitG = g > 155 ? 1 : 0;
      int bitB = b > 155 ? 1 : 0;
      return (bitG << 2) | (bitR << 1) | bitB;
    }
    if (CellAttributes.isIndex(cellColour)) {
      int idx = CellAttributes.valueOf(cellColour);
      if (idx >= 0 && idx <= 15) {
        // ANSI 16-colour mapping: low 8 standard, high 8 bright (same hue, ignored here)
        final int[] mapping = {0, 2, 4, 6, 1, 3, 5, 7};
        return mapping[idx & 7];
      }
      if (idx >= 16 && idx <= 231) {
        // 6x6x6 colour cube
        int code = idx - 16;
        int rVal = code / 36;
        int gVal = (code % 36) / 6;
        int bVal = code % 6;
        int bitR = rVal >= 3 ? 1 : 0;
        int bitG = gVal >= 3 ? 1 : 0;
        int bitB = bVal >= 3 ? 1 : 0;
        return (bitG << 2) | (bitR << 1) | bitB;
      }
      if (idx >= 232 && idx <= 255) {
        // Grayscale ramp: indices 232-243 are dark (-> black), 244-255 are light (-> white)
        return idx < 244 ? 0 : 7;
      }
    }
    return isInk ? 7 : 0;
  }
}
