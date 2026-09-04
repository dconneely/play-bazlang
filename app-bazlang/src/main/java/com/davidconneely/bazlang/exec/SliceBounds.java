package com.davidconneely.bazlang.exec;

/**
 * Resolved 1-based inclusive slice bounds for string indexing/slicing. {@code start} may be {@code
 * end + 1} (the legal empty slice). Unset inputs are passed as {@code -1}.
 *
 * @param start 1-based inclusive start byte index.
 * @param end 1-based inclusive end byte index.
 */
public record SliceBounds(int start, int end) {
  /**
   * Length of the slice; zero for the empty slice.
   *
   * @return the slice length in bytes.
   */
  public int length() {
    return end - start + 1;
  }

  /**
   * Resolves an optional 1-based byte index plus optional slice start/end (each -1 when absent)
   * against a string of {@code maxLen} bytes. Returns null when out of bounds (callers report
   * SUBSCRIPT_WRONG "Slice out of bounds" with their own line context).
   *
   * @param byteIndex a 1-based byte index, or {@code -1} if absent.
   * @param sliceStart a 1-based slice start offset from {@code byteIndex}, or {@code -1} if absent.
   * @param sliceEnd a 1-based slice end offset from {@code byteIndex}, or {@code -1} if absent.
   * @param maxLen the target string's length in bytes.
   * @return the resolved bounds, or {@code null} if out of bounds.
   */
  public static SliceBounds resolve(int byteIndex, int sliceStart, int sliceEnd, int maxLen) {
    final int base = byteIndex != -1 ? byteIndex : 1;
    final int st = base + (sliceStart != -1 ? sliceStart - 1 : 0);
    final int en = base + (sliceEnd != -1 ? sliceEnd - 1 : (byteIndex != -1 ? 0 : maxLen - 1));
    if (st < 1 || en > maxLen || st > en + 1) {
      return null;
    }
    return new SliceBounds(st, en);
  }
}
