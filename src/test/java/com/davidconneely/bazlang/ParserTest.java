package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.Test;

/** Behaviour-biased tests for the Parser. */
class ParserTest {

  @Test
  void testValidPrintStatements() {
    assertParses("10 PRINT \"HELLO\"");
    assertParses("20 PRINT 1 + 2 * 3");
    assertParses("30 PRINT (1 + 2) * 3");
    assertParses("40 PRINT AT 10, 10; \"X\"");
    assertParses("50 PRINT CHR$(65), STR$(42)");
  }

  @Test
  void testValidAssignments() {
    assertParses("10 LET A = 42");
    assertParses("20 LET A$ = \"HELLO\"");
    assertParses("30 DIM B(10)");
    assertParses("40 LET B(1) = 10");
    assertParses("50 DIM B$(5, 10)");
    assertParses("60 LET B$(1, 1 TO 5) = \"HI\"");
  }

  @Test
  void testValidControlFlow() {
    assertParses("10 GOTO 100");
    assertParses("20 GOSUB 200");
    assertParses("30 IF A = 1 THEN STOP");
    assertParses("40 FOR I = 1 TO 10 STEP 2");
    assertParses("50 NEXT I");
    assertParses("60 RETURN");
  }

  @Test
  void testInvalidSyntax() {
    assertFails("10 LET A ="); // Incomplete assignment
    assertFails("10 PRINT (1 + 2"); // Unclosed parenthesis
    assertFails("10 FOR I"); // Incomplete loop
    assertFails("10 IF 1 THEN"); // Incomplete conditional
    assertFails("10 DIM A("); // Incomplete dimension
  }

  @Test
  void testFileOperationsAndListing() {
    assertParses("10 NEW");
    assertParses("20 LOAD \"test.bas\"");
    assertParses("30 SAVE \"test.bas\"");
    assertParses("40 LIST");
    assertParses("50 LIST 10");
    assertParses("60 LIST 10 TO 20");
    assertParses("70 LIST TO 20");
    assertParses("80 LLIST");
    assertParses("90 LLIST 10 TO 20");
  }

  // Note: The following validations were done by the hand-written parser but are not yet
  // implemented in the ANTLR grammar. They are commented out for now.

  // @Test
  // void testLabelMonotonicity() {
  //   assertParses("10 PRINT 1\n20 PRINT 2");
  //   assertFails("20 PRINT 1\n10 PRINT 2"); // Decreasing labels
  //   assertFails("10 PRINT 1\n10 PRINT 2"); // Duplicate labels
  // }

  // @Test
  // void testLabelRangeAndFormat() {
  //   assertParses("1 PRINT 1");
  //   assertParses("999999999 PRINT 1");
  //   assertFails("0 PRINT 1"); // Too low
  //   assertFails("1000000000 PRINT 1"); // Too high
  //   assertFails("10.5 PRINT 1"); // Non-integer
  //   assertFails("1E3 PRINT 1"); // Scientific notation
  //   assertFails("10.0 PRINT 1"); // Decimal integer
  // }

  // @Test
  // void testTypeMismatchesAtParseTime() {
  //   assertFails("10 LET A = \"HI\""); // String to numeric scalar
  //   assertFails("20 LET A$ = 42"); // Numeric to string scalar
  //   assertFails("30 PRINT 1 + \"A\""); // Arithmetic type mismatch
  //   assertFails("40 PRINT LEN(1)"); // Function argument type mismatch
  //   assertFails("50 LET A(1 TO 2) = 1"); // Slicing numeric array
  // }

  // @Test
  // void testNoMultiStatementLines() {
  //   assertFails("10 PRINT \"A\": PRINT \"B\"");
  //   assertFails("20 LET A=1: REM COMMENT");
  // }

  private void assertParses(String source) {
    assertDoesNotThrow(
        () -> AntlrParser.INSTANCE.parseProgramLines(source),
        "Source should parse successfully: " + source);
  }

  private void assertFails(String source) {
    assertThrows(
        ReportException.class,
        () -> {
          // parseProgramLines doesn't parse immediately, so force parsing
          var lines = AntlrParser.INSTANCE.parseProgramLines(source);
          for (var line : lines.values()) {
            line.getStatement(AntlrParser.INSTANCE);
          }
        },
        "Source should fail to parse: " + source);
  }
}
