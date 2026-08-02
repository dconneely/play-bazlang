package com.davidconneely.repl.jline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class NativeProcessClipboardTest {

  @Test
  void testCopyDoesNotThrow() {
    // We cannot easily verify if the native clipboard utility worked or even exists,
    // but we can at least verify that it handles errors gracefully and doesn't crash.
    NativeProcessClipboard clipboard = new NativeProcessClipboard();
    assertDoesNotThrow(() -> clipboard.copy("test string"));
  }
}
