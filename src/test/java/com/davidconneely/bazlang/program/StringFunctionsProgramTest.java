package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.ReportException;
import org.junit.jupiter.api.Test;

/** Tests exercising built-in string functions. */
class StringFunctionsProgramTest extends BaseProgramTest {

  @Test
  void testChrOutOfRange() {
    // CHR$(n) for n > 255 is an error; use CODEPOINT$ instead
    assertThrows(ReportException.class, () -> runProgram("10 LET A$ = CHR$(256)"));
  }

  @Test
  void testChrVsCodepointSemantics() {
    // CHR$(n) produces a single raw byte; CODEPOINT$(n) produces UTF-8 encoding
    EvalState state =
        runProgram(
            """
            10 LET A = LEN(CHR$(255))
            20 LET B = CODE(CHR$(255))
            30 LET C = LEN(CODEPOINT$(255))
            40 LET D = CODEPOINT(CODEPOINT$(255))
            """);
    assertEquals(1.0, state.numVar("A")); // CHR$(255) = 1 raw byte
    assertEquals(255.0, state.numVar("B")); // CODE returns raw byte value
    assertEquals(2.0, state.numVar("C")); // U+00FF encodes to 2 UTF-8 bytes
    assertEquals(255.0, state.numVar("D")); // CODEPOINT recovers U+00FF codepoint
  }

  @Test
  void testCodepointStrFunction() {
    // CODEPOINT$(n) produces the UTF-8 encoding of Unicode codepoint n
    EvalState state =
        runProgram(
            """
            10 LET A$ = CODEPOINT$(65)
            20 LET B$ = CODEPOINT$(9608)
            30 LET C = LEN(B$)
            40 LET D = CODEPOINT(B$)
            """);
    assertEquals("A", ((EvalState.StrVar.Scalar) state.strVar("A$")).value().toJavaString());
    assertEquals("█", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    assertEquals(3.0, state.numVar("C")); // █ is 3 UTF-8 bytes
    assertEquals(9608.0, state.numVar("D")); // CODEPOINT recovers original value
  }

  @Test
  void testLenReturnsByteCount() {
    // LEN returns the number of bytes, not the number of characters
    EvalState state =
        runProgram(
            """
            10 LET A = LEN("Hello")
            20 LET B = LEN(CODEPOINT$(9608))
            30 LET C = LEN(CODEPOINT$(128512))
            """);
    assertEquals(5.0, state.numVar("A")); // ASCII: bytes == chars
    assertEquals(3.0, state.numVar("B")); // █ U+2588: 3 bytes
    assertEquals(4.0, state.numVar("C")); // 😀 U+1F600: 4 bytes
  }

  @Test
  void testNextcpFunction() {
    // NEXTCP(s$, i) returns the 1-based byte position of the next codepoint after position i
    EvalState state =
        runProgram(
            """
            10 LET S$ = CODEPOINT$(9608)
            20 LET A = NEXTCP(S$, 1)
            30 LET B$ = "Hello"
            40 LET C = NEXTCP(B$, 1)
            50 LET D = NEXTCP(B$, 5)
            """);
    assertEquals(4.0, state.numVar("A")); // █ is 3 bytes: next cp starts at 4
    assertEquals(2.0, state.numVar("C")); // 'H' is 1 byte: next cp starts at 2
    assertEquals(6.0, state.numVar("D")); // 'o' is 1 byte: next cp starts at 6 (= LEN+1)
  }

  @Test
  void testNextcpFunctionBrokenLead() {
    // [0xC2, 0x20]: broken lead 0xC2 advances by 1, then ASCII space advances by 1
    EvalState state =
        runProgram(
            """
            10 LET S$ = CHR$(194) + CHR$(32)
            20 LET A = NEXTCP(S$, 1)
            30 LET B = NEXTCP(S$, 2)
            """);
    assertEquals(2.0, state.numVar("A")); // 0xC2 invalid → next at 2
    assertEquals(3.0, state.numVar("B")); // 0x20 ASCII → next at 3
  }

  @Test
  void testNextcpFunctionInvalidByte() {
    // NEXTCP on an invalid byte advances by 1 (utf8-c8: each invalid byte is one "codepoint")
    EvalState state =
        runProgram(
            """
            10 LET S$ = CHR$(255)
            20 LET A = NEXTCP(S$, 1)
            """);
    assertEquals(2.0, state.numVar("A"));
  }

  @Test
  void testStrDollarUsesZx81Formatting() {
    // STR$ should use the same formatting as PRINT
    String output =
        runProgramCapture(
            """
        10 PRINT STR$ 0
        20 PRINT STR$ 42
        30 PRINT STR$ 3.14159
        40 PRINT STR$ (-7)
        """);
    String[] lines = output.trim().split(System.lineSeparator());
    assertEquals("0", lines[0]);
    assertEquals("42", lines[1]);
    assertEquals("3.14159", lines[2]);
    assertEquals("-7", lines[3]);
  }

  @Test
  void testStrFuncs() {
    String source =
        """
        10 LET A$ = CHR$(65)
        20 LET B$ = STR$(123)
        """;
    EvalState state = runProgram(source);
    assertEquals("A", ((EvalState.StrVar.Scalar) state.strVar("A$")).value().toJavaString());
    assertEquals("123", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
  }
}
