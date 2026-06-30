package com.davidconneely.cell;

public interface PixelMode {
  /** Number of pixels per character cell in the horizontal direction. */
  int pixelsPerCellX();

  /** Number of pixels per character cell in the vertical direction. */
  int pixelsPerCellY();

  /**
   * Encode a set of pixel state bits to a Unicode codepoint. The number of bits is <code>
   * pixelsPerCellX() * pixelsPerCellY()</code>.
   */
  int encode(int stateBits);

  /**
   * Decode a Unicode codepoint to pixel state bits. Returns <code>0</code> (all pixels off) if the
   * codepoint is not a recognised pixel character for this mode.
   */
  int decode(int codepoint);

  /**
   * Return the bit mask for the subpixel at position (<code>subX</code>, <code>subY</code>) within
   * a cell. <code>subX</code> is in the range <code>[0, pixelsPerCellX())</code> and <code>subY
   * </code> is in the range <code>[0, pixelsPerCellY())</code> where <code>subY ==
   * 0</code> is the bottom of the cell and <code>subY == pixelsPerCellY()-1</code> is the top.
   */
  int bitMask(int subX, int subY);
}
