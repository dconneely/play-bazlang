package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests exercising the PRINT statement and its positioning/formatting logic. */
class PrintProgramTest extends BaseProgramTest {

  @Test
  void testAtPositioning() {
    // PRINT AT row, col should work.
    assertTrue(
        runProgramCapture(
                """
        10 PRINT AT 5, 10; "X"
        """)
            .contains("X"));
  }

  @Test
  void testEmptyPrint() {
    // PRINT with no arguments just prints a newline
    runProgram("10 PRINT", "\n");
  }

  @Test
  void testHelloWorld() {
    runProgram(
        """
        10 PRINT "HELLO"
        """,
        "HELLO\n");
  }

  @Test
  void testMultiplePrintOnSameLine() {
    // Multiple PRINTs across lines with semicolons should accumulate
    runProgram(
        """
        10 PRINT "A";
        20 PRINT "B";
        30 PRINT "C"
        """,
        "ABC\n");
  }

  @Test
  void testNoTrailingZeros() {
    // Numbers should not have trailing zeros after decimal point
    final String[] lines =
        runProgramCapture(
                """
            10 PRINT 1.5
            20 PRINT 2.25
            30 PRINT 10.0
            """)
            .trim()
            .split("\n");
    assertEquals("1.5", lines[0]);
    assertEquals("2.25", lines[1]);
    assertEquals("10", lines[2]); // Integer, no decimal point
  }

  @Test
  void testPrintApostropheSeparator() {
    // Apostrophe separator moves print position down a line,
    // and suppresses default trailing newline if trailing.
    final String[] lines1 =
        runProgramCapture(
                """
        10 PRINT "A" ' "B"
        """)
            .split("\n");
    assertEquals("A", lines1[0]);
    assertEquals("B", lines1[1]);

    final String[] lines2 =
        runProgramCapture(
                """
        10 PRINT "A" ' ' "B"
        """)
            .split("\n");
    assertEquals("A", lines2[0]);
    assertEquals("", lines2[1]); // Consecutive apostrophe prints a blank line
    assertEquals("B", lines2[2]);

    // Test consecutive apostrophes without spaces (`''` and `'''`)
    final String[] lines2b =
        runProgramCapture(
                """
        10 PRINT "A" '' "B"
        """)
            .split("\n");
    assertEquals("A", lines2b[0]);
    assertEquals("", lines2b[1]);
    assertEquals("B", lines2b[2]);

    final String[] lines2c =
        runProgramCapture(
                """
        10 PRINT "A" ''' "B"
        """)
            .split("\n");
    assertEquals("A", lines2c[0]);
    assertEquals("", lines2c[1]);
    assertEquals("", lines2c[2]);
    assertEquals("B", lines2c[3]);

    final String[] lines3 =
        runProgramCapture(
                """
        10 PRINT "A" '
        20 PRINT "B"
        """)
            .split("\n");
    assertEquals("A", lines3[0]);
    assertEquals("B", lines3[1]); // Trailing apostrophe suppresses automatic newline

    final String[] lines4 =
        runProgramCapture(
                """
        10 PRINT ' "A"
        """)
            .split("\n");
    assertEquals("", lines4[0]); // Leading apostrophe prints a blank line
    assertEquals("A", lines4[1]);

    // Test case: 10 PRINT "first", 20 PRINT ' "second" -> single blank line
    final String[] lines5 =
        runProgramCapture(
                """
        10 PRINT "first"
        20 PRINT ' "second"
        """)
            .split("\n");
    assertEquals("first", lines5[0]);
    assertEquals("", lines5[1]); // Blank line
    assertEquals("second", lines5[2]);

    // Test case: 10 PRINT "first" '', 20 PRINT "second" -> single blank line
    final String[] lines6 =
        runProgramCapture(
                """
        10 PRINT "first" ''
        20 PRINT "second"
        """)
            .split("\n");
    assertEquals("first", lines6[0]);
    assertEquals("", lines6[1]); // Blank line
    assertEquals("second", lines6[2]);
  }

  @Test
  void testPrintCommaSeparators() {
    // Comma moves to next tab stop (16 chars)
    final String output =
        runProgramCapture(
            """
        10 PRINT "X", "Y", "Z"
        """);
    assertEquals(0, output.indexOf('X'));
    assertEquals(16, output.indexOf('Y'));
    assertEquals(32, output.indexOf('Z'));
  }

  @Test
  void testPrintMixedSeparators() {
    // Mixed separators
    final String output =
        runProgramCapture(
            """
        10 PRINT "A"; "B", "C"
        """);
    assertEquals(0, output.indexOf('A'));
    assertEquals(1, output.indexOf('B'));
    assertEquals(16, output.indexOf('C'));
  }

  @Test
  void testPrintSemicolonConcatenates() {
    // Semicolon concatenates without spacing
    runProgram(
        """
        10 PRINT "A"; "B"; "C"
        """,
        "ABC\n");
  }

  @Test
  void testPrintWithTrailingComma() {
    // Trailing comma suppresses newline but adds tab spacing
    final String output =
        runProgramCapture(
            """
        10 PRINT "A",
        """);
    assertFalse(output.endsWith("\n"));
    assertTrue(output.length() >= 16); // "A" plus padding to tab stop
  }

  @Test
  void testPrintWithTrailingSemicolon() {
    runProgram(
        """
        10 PRINT "HELLO";
        """,
        "HELLO");
  }

  @Test
  void testScientificNotation() {
    // Very large/small numbers use scientific notation
    final String[] lines =
        runProgramCapture(
                """
        10 PRINT 1E13
        20 PRINT 1E-6
        30 PRINT 1.23E15
        40 PRINT -5E14
        """)
            .trim()
            .split("\n");
    assertEquals("1E+13", lines[0]);
    assertEquals("1E-6", lines[1]);
    assertEquals("1.23E+15", lines[2]);
    assertEquals("-5E+14", lines[3]);
  }

  @Test
  void testTabPositioning() {
    // TAB moves head to next 16-char zone.
    final String output =
        runProgramCapture(
            """
        10 PRINT "A", "B"
        """);
    // "A" is at 0, next tab is at 16. So "B" should be at index 16.
    int aPos = output.indexOf('A');
    int bPos = output.indexOf('B');
    assertEquals(16, bPos - aPos);
  }

  @Test
  void testNumberFormatting() {
    // Sinclair ZX BASIC rules:
    // - 0 prints as "0"
    // - Integers print without decimal point
    // - Scientific notation for |x| < 10^-5 or |x| >= 10^13
    // - Up to 8 significant digits, no trailing zeros
    // - Leading zero dropped for |x| < 0.1 (e.g., 0.03 -> .03) [we don't follow this one!]
    final String[] lines =
        runProgramCapture(
                """
        10 PRINT 0
        20 PRINT 1
        30 PRINT 42
        40 PRINT -7
        50 PRINT 3.14159
        60 PRINT 0.5
        70 PRINT 0.03
        80 PRINT -0.03
        """)
            .trim()
            .split("\n");
    assertEquals("0", lines[0]);
    assertEquals("1", lines[1]);
    assertEquals("42", lines[2]);
    assertEquals("-7", lines[3]);
    assertEquals("3.14159", lines[4]);
    assertEquals("0.5", lines[5]);
    assertEquals("0.03", lines[6]);
    assertEquals("-0.03", lines[7]);
  }
}
