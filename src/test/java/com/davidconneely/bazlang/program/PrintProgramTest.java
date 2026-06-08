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
    String output = runProgramCapture("10 PRINT AT 5, 10; \"X\"");
    assertTrue(output.contains("X"));
  }

  @Test
  void testEmptyPrint() {
    // PRINT with no arguments just prints a newline
    runProgram("10 PRINT", System.lineSeparator());
  }

  @Test
  void testHelloWorld() {
    runProgram("10 PRINT \"HELLO\"", "HELLO" + System.lineSeparator());
  }

  @Test
  void testMultiplePrintOnSameLine() {
    // Multiple PRINTs across lines with semicolons should accumulate
    String output = runProgramCapture("10 PRINT \"A\";\n20 PRINT \"B\";\n30 PRINT \"C\"");
    assertEquals("ABC" + System.lineSeparator(), output);
  }

  @Test
  void testNoTrailingZeros() {
    // Numbers should not have trailing zeros after decimal point
    String output =
        runProgramCapture(
            """
        10 PRINT 1.5
        20 PRINT 2.25
        30 PRINT 10.0
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("1.5", lines[0]);
    assertEquals("2.25", lines[1]);
    assertEquals("10", lines[2]); // Integer, no decimal point
  }

  @Test
  void testPrintApostropheSeparator() {
    // Apostrophe separator moves print position down a line,
    // and suppresses default trailing newline if trailing.
    String output1 =
        runProgramCapture(
            """
        10 PRINT "A" ' "B"
        """);
    String[] lines1 = output1.split(System.lineSeparator());
    assertEquals("A", lines1[0]);
    assertEquals("B", lines1[1]);

    String output2 =
        runProgramCapture(
            """
        10 PRINT "A" ' ' "B"
        """);
    String[] lines2 = output2.split(System.lineSeparator());
    assertEquals("A", lines2[0]);
    assertEquals("", lines2[1]); // Consecutive apostrophe prints a blank line
    assertEquals("B", lines2[2]);

    // Test consecutive apostrophes without spaces ('' and ''')
    String output2b =
        runProgramCapture(
            """
        10 PRINT "A" '' "B"
        """);
    String[] lines2b = output2b.split(System.lineSeparator());
    assertEquals("A", lines2b[0]);
    assertEquals("", lines2b[1]);
    assertEquals("B", lines2b[2]);

    String output2c =
        runProgramCapture(
            """
        10 PRINT "A" ''' "B"
        """);
    String[] lines2c = output2c.split(System.lineSeparator());
    assertEquals("A", lines2c[0]);
    assertEquals("", lines2c[1]);
    assertEquals("", lines2c[2]);
    assertEquals("B", lines2c[3]);

    String output3 =
        runProgramCapture(
            """
        10 PRINT "A" '
        20 PRINT "B"
        """);
    String[] lines3 = output3.split(System.lineSeparator());
    assertEquals("A", lines3[0]);
    assertEquals("B", lines3[1]); // Trailing apostrophe suppresses automatic newline

    String output4 =
        runProgramCapture(
            """
        10 PRINT ' "A"
        """);
    String[] lines4 = output4.split(System.lineSeparator());
    assertEquals("", lines4[0]); // Leading apostrophe prints a blank line
    assertEquals("A", lines4[1]);

    // Test LPRINT behavior with apostrophe separators
    String outputL1 =
        runProgramCapture(
            """
        10 LPRINT "A" '' "B"
        """);
    String[] linesL1 = outputL1.split(System.lineSeparator());
    assertEquals("A", linesL1[0]);
    assertEquals("", linesL1[1]);
    assertEquals("B", linesL1[2]);

    // Test case: 10 PRINT "first", 20 PRINT ' "second" -> single blank line
    String output5 =
        runProgramCapture(
            """
        10 PRINT "first"
        20 PRINT ' "second"
        """);
    String[] lines5 = output5.split(System.lineSeparator());
    assertEquals("first", lines5[0]);
    assertEquals("", lines5[1]); // Blank line
    assertEquals("second", lines5[2]);

    // Test case: 10 PRINT "first" '', 20 PRINT "second" -> single blank line
    String output6 =
        runProgramCapture(
            """
        10 PRINT "first" ''
        20 PRINT "second"
        """);
    String[] lines6 = output6.split(System.lineSeparator());
    assertEquals("first", lines6[0]);
    assertEquals("", lines6[1]); // Blank line
    assertEquals("second", lines6[2]);
  }

  @Test
  void testPrintCommaSeparators() {
    // Comma moves to next tab stop (16 chars)
    String output = runProgramCapture("10 PRINT \"X\", \"Y\", \"Z\"");
    assertEquals(0, output.indexOf('X'));
    assertEquals(16, output.indexOf('Y'));
    assertEquals(32, output.indexOf('Z'));
  }

  @Test
  void testPrintMixedSeparators() {
    // Mixed separators
    String output = runProgramCapture("10 PRINT \"A\"; \"B\", \"C\"");
    assertEquals(0, output.indexOf('A'));
    assertEquals(1, output.indexOf('B'));
    assertEquals(16, output.indexOf('C'));
  }

  @Test
  void testPrintSemicolonConcatenates() {
    // Semicolon concatenates without spacing
    runProgram("10 PRINT \"A\"; \"B\"; \"C\"", "ABC" + System.lineSeparator());
  }

  @Test
  void testPrintWithTrailingComma() {
    // Trailing comma suppresses newline but adds tab spacing
    String output = runProgramCapture("10 PRINT \"A\",");
    assertFalse(output.endsWith(System.lineSeparator()));
    assertTrue(output.length() >= 16); // "A" plus padding to tab stop
  }

  @Test
  void testPrintWithTrailingSemicolon() {
    runProgram("10 PRINT \"HELLO\";", "HELLO");
  }

  @Test
  void testScientificNotation() {
    // Very large/small numbers use scientific notation
    String output =
        runProgramCapture(
            """
        10 PRINT 1E13
        20 PRINT 1E-6
        30 PRINT 1.23E15
        40 PRINT -5E14
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("1E+13", lines[0]);
    assertEquals("1E-6", lines[1]);
    assertEquals("1.23E+15", lines[2]);
    assertEquals("-5E+14", lines[3]);
  }

  @Test
  void testTabPositioning() {
    // TAB moves head to next 16-char zone.
    String output = runProgramCapture("10 PRINT \"A\", \"B\"");
    // "A" is at 0, next tab is at 16. So "B" should be at index 16.
    int aPos = output.indexOf('A');
    int bPos = output.indexOf('B');
    assertEquals(16, bPos - aPos);
  }

  @Test
  void testZx81NumberFormatting() {
    // ZX81 rules:
    // - 0 prints as "0"
    // - Integers print without decimal point
    // - Scientific notation for |x| < 10^-5 or |x| >= 10^13
    // - Up to 8 significant digits, no trailing zeros
    // - Leading zero dropped for |x| < 0.1 (e.g., 0.03 -> .03)
    String output =
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
        """);
    String[] lines = output.trim().split(System.lineSeparator());
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
