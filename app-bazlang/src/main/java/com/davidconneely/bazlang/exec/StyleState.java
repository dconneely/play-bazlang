package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.io.VirtualScreen;

/**
 * The six default style attributes (INK, PAPER, BRIGHT, FLASH, INVERSE, OVER) set by the
 * corresponding style statements. Colour values:
 *
 * <ul>
 *   <li>-1: terminal default colour
 *   <li>0..7: ZX Spectrum colour codes
 *   <li>8: transparent/preserve existing cell colour
 *   <li>9: contrast colour
 *   <li>256..511: xterm colour index + 256
 *   <li>2^24..2^25-1: 24-bit RGB colour value + 2^24
 * </ul>
 */
public final class StyleState {
  private int ink = -1;
  private int paper = -1;
  private int bright = 0;
  private int flash = 0;
  private int inverse = 0;
  private int over = 0;

  /** Create a state at power-on defaults; see {@link #reset()}. */
  public StyleState() {}

  /**
   * Default ink (foreground) colour.
   *
   * @return the colour code.
   */
  public int ink() {
    return ink;
  }

  /**
   * Set the default ink (foreground) colour.
   *
   * @param ink the new colour code.
   */
  public void setInk(int ink) {
    this.ink = ink;
  }

  /**
   * Default paper (background) colour.
   *
   * @return the colour code.
   */
  public int paper() {
    return paper;
  }

  /**
   * Set the default paper (background) colour.
   *
   * @param paper the new colour code.
   */
  public void setPaper(int paper) {
    this.paper = paper;
  }

  /**
   * Default brightness.
   *
   * @return the brightness value.
   */
  public int bright() {
    return bright;
  }

  /**
   * Set the default brightness.
   *
   * @param bright the new brightness value.
   */
  public void setBright(int bright) {
    this.bright = bright;
  }

  /**
   * Default flash (blink) setting.
   *
   * @return the flash value.
   */
  public int flash() {
    return flash;
  }

  /**
   * Set the default flash (blink) setting.
   *
   * @param flash the new flash value.
   */
  public void setFlash(int flash) {
    this.flash = flash;
  }

  /**
   * Default inverse-video setting.
   *
   * @return the inverse value.
   */
  public int inverse() {
    return inverse;
  }

  /**
   * Set the default inverse-video setting.
   *
   * @param inverse the new inverse value.
   */
  public void setInverse(int inverse) {
    this.inverse = inverse;
  }

  /**
   * Default overlay (XOR-plot) setting.
   *
   * @return the over value.
   */
  public int over() {
    return over;
  }

  /**
   * Set the default overlay (XOR-plot) setting.
   *
   * @param over the new over value.
   */
  public void setOver(int over) {
    this.over = over;
  }

  /** Resets to power-on defaults (terminal colours, all styles off). */
  public void reset() {
    ink = -1; // Terminal default
    paper = -1; // Terminal default
    bright = 0;
    flash = 0;
    inverse = 0;
    over = 0;
  }

  /**
   * Pushes all six values onto the screen as the active attributes.
   *
   * @param screen the screen to apply these styles to.
   */
  public void applyTo(VirtualScreen screen) {
    screen.setInk(ink);
    screen.setPaper(paper);
    screen.setBright(bright);
    screen.setFlash(flash);
    screen.setInverse(inverse);
    screen.setOver(over);
  }
}
