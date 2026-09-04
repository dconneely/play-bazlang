package com.davidconneely.cell;

/**
 * A scheme for packing sub-cell pixels into a single Unicode codepoint per character cell (e.g.
 * Braille dot patterns, block-element quadrants/sextants), so a {@link CellBuffer} can address
 * finer-grained graphics than one pixel per cell.
 */
public interface PixelMode {
  /**
   * Number of pixels per character cell in the horizontal direction.
   *
   * @return sub-cell width in pixels.
   */
  int pixelsPerCellX();

  /**
   * Number of pixels per character cell in the vertical direction.
   *
   * @return sub-cell height in pixels.
   */
  int pixelsPerCellY();

  /**
   * Encode a set of pixel state bits to a Unicode codepoint. The number of bits is <code>
   * pixelsPerCellX() * pixelsPerCellY()</code>.
   *
   * @param stateBits one bit per sub-cell pixel, packed per {@link #bitMask(int, int)}.
   * @return the codepoint representing that pixel pattern in this mode.
   */
  int encode(int stateBits);

  /**
   * Decode a Unicode codepoint to pixel state bits. Returns <code>0</code> (all pixels off) if the
   * codepoint is not a recognised pixel character for this mode.
   *
   * @param codepoint a Unicode codepoint, typically one previously returned by {@link
   *     #encode(int)}.
   * @return the packed pixel state bits for that codepoint, or <code>0</code> if unrecognised.
   */
  int decode(int codepoint);

  /**
   * Return the bit mask for the subpixel at position (<code>subX</code>, <code>subY</code>) within
   * a cell. <code>subX</code> is in the range <code>[0, pixelsPerCellX())</code> and <code>subY
   * </code> is in the range <code>[0, pixelsPerCellY())</code> where <code>subY ==
   * 0</code> is the bottom of the cell and <code>subY == pixelsPerCellY()-1</code> is the top.
   *
   * @param subX horizontal sub-cell coordinate, <code>0</code> at the left.
   * @param subY vertical sub-cell coordinate, <code>0</code> at the bottom.
   * @return the single bit (within {@code stateBits} for {@link #encode(int)}/{@link #decode(int)})
   *     that corresponds to that subpixel.
   */
  int bitMask(int subX, int subY);
}
