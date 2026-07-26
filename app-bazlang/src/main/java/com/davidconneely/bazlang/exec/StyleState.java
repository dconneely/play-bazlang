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

  public int ink() {
    return ink;
  }

  public void setInk(int ink) {
    this.ink = ink;
  }

  public int paper() {
    return paper;
  }

  public void setPaper(int paper) {
    this.paper = paper;
  }

  public int bright() {
    return bright;
  }

  public void setBright(int bright) {
    this.bright = bright;
  }

  public int flash() {
    return flash;
  }

  public void setFlash(int flash) {
    this.flash = flash;
  }

  public int inverse() {
    return inverse;
  }

  public void setInverse(int inverse) {
    this.inverse = inverse;
  }

  public int over() {
    return over;
  }

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

  /** Pushes all six values onto the screen as the active attributes. */
  public void applyTo(VirtualScreen screen) {
    screen.setInk(ink);
    screen.setPaper(paper);
    screen.setBright(bright);
    screen.setFlash(flash);
    screen.setInverse(inverse);
    screen.setOver(over);
  }
}
