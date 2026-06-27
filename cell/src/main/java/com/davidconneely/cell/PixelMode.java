package com.davidconneely.cell;

public interface PixelMode {
  /** Number of pixels per character cell in the horizontal direction. */
  int pixelsPerCellX();

  /** Number of pixels per character cell in the vertical direction. */
  int pixelsPerCellY();

  /**
   * Encode a set of pixel state bits to a Unicode codepoint. The number of bits is pixelsPerCellX()
   * * pixelsPerCellY().
   */
  int encode(int stateBits);

  /**
   * Decode a Unicode codepoint to pixel state bits. Returns 0 (all pixels off) if the codepoint is
   * not a recognised pixel character for this mode.
   */
  int decode(int codepoint);

  /**
   * Return the bit mask for the subpixel at position (subX, subY) within a cell. subX is in [0,
   * pixelsPerCellX()), subY is in [0, pixelsPerCellY()) where subY=0 is the bottom of the cell and
   * subY=(pixelsPerCellY()-1) is the top.
   */
  int bitMask(int subX, int subY);
}
