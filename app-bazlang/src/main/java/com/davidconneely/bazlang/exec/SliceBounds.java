package com.davidconneely.bazlang.exec;

/**
 * Resolved 1-based inclusive slice bounds for string indexing/slicing. {@code start} may be {@code
 * end + 1} (the legal empty slice). Unset inputs are passed as {@code -1}.
 */
public record SliceBounds(int start, int end) {
  /** Length of the slice; zero for the empty slice. */
  public int length() {
    return end - start + 1;
  }

  /**
   * Resolves an optional 1-based byte index plus optional slice start/end (each -1 when absent)
   * against a string of {@code maxLen} bytes. Returns null when out of bounds (callers report
   * SUBSCRIPT_WRONG "Slice out of bounds" with their own line context).
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
