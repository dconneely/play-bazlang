package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class KeyDecoder {
  @FunctionalInterface
  interface ByteReader {
    int read() throws IOException;
  }

  static BStr decodeSequence(ByteReader reader) throws IOException {
    int first = reader.read();
    if (first < 0) {
      return null;
    }
    if (first == 3) { // Ctrl+C / ETX
      return BStr.EMPTY;
    }
    final var bos = new ByteArrayOutputStream();
    bos.write(first);
    if (first == 27) { // ESC sequence
      int next = reader.read();
      if (next >= 0) {
        bos.write(next);
        if (next == '[' || next == 'O') {
          while (true) {
            int seq = reader.read();
            if (seq < 0) {
              break;
            }
            bos.write(seq);
            if (seq >= 0x40 && seq <= 0x7E) {
              break;
            }
          }
        }
      }
    } else if ((first & 0x80) != 0) {
      // UTF-8 sequence
      int len = 0;
      if ((first & 0xE0) == 0xC0) {
        len = 1;
      } else if ((first & 0xF0) == 0xE0) {
        len = 2;
      } else if ((first & 0xF8) == 0xF0) {
        len = 3;
      }
      for (int i = 0; i < len; i++) {
        int follow = reader.read();
        if (follow >= 0) {
          bos.write(follow);
        }
      }
    }
    return BStr.fromBytes(bos.toByteArray());
  }

  private KeyDecoder() {}
}
