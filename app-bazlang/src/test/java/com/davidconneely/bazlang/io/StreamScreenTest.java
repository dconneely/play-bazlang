package com.davidconneely.bazlang.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.davidconneely.bazlang.BStr;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StreamScreenTest {

  @Test
  void testPrintAndPrintln() {
    final var out = new ByteArrayOutputStream();
    final var printOut = new PrintStream(out, true, StandardCharsets.UTF_8);
    final var in = new ByteArrayInputStream(new byte[0]);

    try (var screen = new StreamScreen(in, printOut)) {
      assertEquals(0, screen.currentRow());
      assertEquals(0, screen.currentCol());

      screen.print("Hello");
      assertEquals(5, screen.currentCol());

      screen.println(" World");
      assertEquals(0, screen.currentCol()); // Reset after newline

      assertEquals("Hello World\n", out.toString(StandardCharsets.UTF_8));
    }
  }

  @Test
  void testReadlnWithPrompt() {
    final var out = new ByteArrayOutputStream();
    final var printOut = new PrintStream(out, true, StandardCharsets.UTF_8);
    final var inData = "User Input\n".getBytes(StandardCharsets.UTF_8);
    final var in = new ByteArrayInputStream(inData);

    try (var screen = new StreamScreen(in, printOut)) {
      final var result = screen.readln("Enter Name: ");
      assertEquals("User Input", result);
      assertEquals("Enter Name: ", out.toString(StandardCharsets.UTF_8));
    }
  }

  @Test
  void testInkey() {
    final var out = new ByteArrayOutputStream();
    final var printOut = new PrintStream(out, true, StandardCharsets.UTF_8);
    final var inData = "A".getBytes(StandardCharsets.UTF_8);
    final var in = new ByteArrayInputStream(inData);

    try (var screen = new StreamScreen(in, printOut)) {
      assertEquals(BStr.fromJavaString("A"), screen.inkey());
      assertEquals(BStr.EMPTY, screen.inkey()); // Empty on EOF
      assertEquals(BStr.EMPTY, screen.uinkey()); // Fallback uinkey
    }
  }

  @Test
  void testUinkeyAnsiEscape() {
    final var out = new ByteArrayOutputStream();
    final var printOut = new PrintStream(out, true, StandardCharsets.UTF_8);
    final var inData = new byte[] {27, (byte) '[', (byte) 'A'};
    final var in = new ByteArrayInputStream(inData);

    try (var screen = new StreamScreen(in, printOut)) {
      assertEquals(BStr.fromBytes(inData), screen.uinkey());
      assertEquals(BStr.EMPTY, screen.uinkey());
    }
  }

  @Test
  void testUinkeyUtf8() {
    final var out = new ByteArrayOutputStream();
    final var printOut = new PrintStream(out, true, StandardCharsets.UTF_8);
    final var inData = "£".getBytes(StandardCharsets.UTF_8);
    final var in = new ByteArrayInputStream(inData);

    try (var screen = new StreamScreen(in, printOut)) {
      assertEquals(BStr.fromBytes(inData), screen.uinkey());
      assertEquals(BStr.EMPTY, screen.uinkey());
    }
  }

  @Test
  void testDefaultNoOps() {
    final var screen = StreamScreen.nullScreen();
    // Verify sizing
    assertEquals(80, screen.printWidth());
    assertEquals(25, screen.printHeight());
    assertEquals(160, screen.plotWidth()); // inherited default
    assertEquals(50, screen.plotHeight()); // inherited default
    assertEquals(4, screen.plotMode()); // inherited default

    // Verify styling no-ops do not throw exceptions
    screen.setInk(1);
    screen.setPaper(2);
    screen.setBright(1);
    screen.setFlash(1);
    screen.setInverse(1);
    screen.setOver(1);
    screen.setFastMode(true);
    screen.plot(10, 10);
    assertEquals(0, screen.point(10, 10));
    assertFalse(screen.pollForBreak());
  }
}
