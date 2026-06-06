package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockDisplay;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests covering the language features described in docs/language_features.md. */
class LanguageReferenceTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private EvalState runProgram(String source) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();
    MockDisplay display = new MockDisplay();
    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    }
    return state;
  }

  @Test
  void testNumFuncs() {
    String source =
        """
        10 LET A = ABS(-5)
        20 LET B = INT(3.14)
        30 LET C = SGN(-10)
        40 LET D = SQR(16)
        50 LET E = LEN("HELLO")
        60 LET F = VAL("123")
        70 LET G = CODE("A")
        """;
    EvalState state = runProgram(source);
    assertEquals(5.0, state.numVar("A"));
    assertEquals(3.0, state.numVar("B"));
    assertEquals(-1.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(5.0, state.numVar("E"));
    assertEquals(123.0, state.numVar("F"));
    assertEquals(65.0, state.numVar("G"));
  }

  @Test
  void testTrigFuncs() {
    // Basic check they run and return somewhat sane values
    String source =
        """
        10 LET S = SIN(0)
        20 LET C = COS(0)
        30 LET T = TAN(0)
        """;
    EvalState state = runProgram(source);
    assertEquals(0.0, state.numVar("S"), 0.0001);
    assertEquals(1.0, state.numVar("C"), 0.0001);
    assertEquals(0.0, state.numVar("T"), 0.0001);
  }

  @Test
  void testExpLogFuncs() {
    String source =
        """
        10 LET E = EXP(1)
        20 LET L = LN(E)
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.E, state.numVar("E"), 0.0001);
    assertEquals(1.0, state.numVar("L"), 0.0001);
  }

  @Test
  void testHardwareFuncs() {
    // PEEK and USR return 0.0
    String source =
        """
        10 LET P = PEEK(1234)
        20 LET U = USR(5678)
        """;
    EvalState state = runProgram(source);
    assertEquals(0.0, state.numVar("P"));
    assertEquals(0.0, state.numVar("U"));
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

  @Test
  void testNumOps() {
    String source =
        """
        10 LET A = 1 + 2 * 3
        20 LET B = (1 + 2) * 3
        30 LET C = 2 ** 3
        40 LET D = 5 / 2
        """;
    EvalState state = runProgram(source);
    assertEquals(7.0, state.numVar("A"));
    assertEquals(9.0, state.numVar("B"));
    assertEquals(8.0, state.numVar("C"));
    assertEquals(2.5, state.numVar("D"));
  }

  @Test
  void testInverseTrigFuncs() {
    String source =
        """
        10 LET A = ASN(1)
        20 LET B = ACS(0)
        30 LET C = ATN(1)
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI / 2, state.numVar("A"), 0.0001);
    assertEquals(Math.PI / 2, state.numVar("B"), 0.0001);
    assertEquals(Math.PI / 4, state.numVar("C"), 0.0001);
  }

  @Test
  void testNullaryFuncs() {
    String source =
        """
        10 LET P = PI
        20 LET R = RND
        30 LET I$ = INKEY$
        """;
    EvalState state = runProgram(source);
    assertEquals(Math.PI, state.numVar("P"), 0.0001);
    // RND returns value between 0 and 1
    double r = state.numVar("R");
    assertEquals(true, r >= 0.0 && r < 1.0);
    assertEquals("", ((EvalState.StrVar.Scalar) state.strVar("I$")).value().toJavaString());
  }

  @Test
  void testLogicalOperators() {
    String source =
        """
        10 LET A = 1 AND 1
        20 LET B = 1 AND 0
        30 LET C = 0 OR 1
        40 LET D = 0 OR 0
        50 LET E = NOT 0
        60 LET F = NOT 5
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(0.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(0.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(0.0, state.numVar("F"));
  }

  @Test
  void testNumericComparisons() {
    String source =
        """
        10 LET A = (5 = 5)
        20 LET B = (5 <> 3)
        30 LET C = (3 < 5)
        40 LET D = (5 > 3)
        50 LET E = (3 <= 3)
        60 LET F = (5 >= 5)
        70 LET G = (3 > 5)
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(1.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(1.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(1.0, state.numVar("F"));
    assertEquals(0.0, state.numVar("G"));
  }

  @Test
  void testStringComparisons() {
    String source =
        """
        10 LET A = ("ABC" = "ABC")
        20 LET B = ("ABC" <> "DEF")
        30 LET C = ("ABC" < "DEF")
        40 LET D = ("DEF" > "ABC")
        50 LET E = ("ABC" <= "ABC")
        60 LET F = ("ABC" >= "ABC")
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(1.0, state.numVar("B"));
    assertEquals(1.0, state.numVar("C"));
    assertEquals(1.0, state.numVar("D"));
    assertEquals(1.0, state.numVar("E"));
    assertEquals(1.0, state.numVar("F"));
  }

  @Test
  void testUnaryMinusWithPower() {
    // Standard precedence: ** binds tighter than unary minus
    // So -2**2 = -(2**2) = -4 (not (-2)**2 = 4 as in some BASIC dialects)
    String source =
        """
        10 LET A = -2**2
        20 LET B = (-2)**2
        30 LET C = 0-2**2
        """;
    EvalState state = runProgram(source);
    assertEquals(-4.0, state.numVar("A")); // -(2**2) = -4
    assertEquals(4.0, state.numVar("B")); // (-2)**2 = 4
    assertEquals(-4.0, state.numVar("C")); // 0-(2**2) = -4
  }

  @Test
  void testCaseInsensitiveVariables() {
    // Variable names should be case-insensitive
    String source =
        """
        10 LET myVar = 10
        20 LET MYVAR = MYVAR + 5
        30 LET MyVar = myvar * 2
        """;
    EvalState state = runProgram(source);
    // All refer to same variable, stored as uppercase
    assertEquals(30.0, state.numVar("MYVAR"));
  }

  @Test
  void testCaseInsensitiveStringVariables() {
    // String variable names should be case-insensitive
    String source =
        """
        10 LET name$ = "Hello"
        20 LET NAME$ = NAME$ + " World"
        """;
    EvalState state = runProgram(source);
    assertEquals(
        "Hello World", ((EvalState.StrVar.Scalar) state.strVar("NAME$")).value().toJavaString());
  }

  @Test
  void testCaseInsensitiveKeywords() {
    // Keywords should be case-insensitive
    String source =
        """
        10 let a = 1
        20 LET b = 2
        30 Let c = 3
        40 IF a = 1 then let d = 4
        50 if b = 2 THEN LET e = 5
        """;
    EvalState state = runProgram(source);
    assertEquals(1.0, state.numVar("A"));
    assertEquals(2.0, state.numVar("B"));
    assertEquals(3.0, state.numVar("C"));
    assertEquals(4.0, state.numVar("D"));
    assertEquals(5.0, state.numVar("E"));
  }

  @Test
  void testCaseSensitiveStringValues() {
    // String VALUES should remain case-sensitive
    String source =
        """
        10 LET a$ = "Hello"
        20 LET b$ = "HELLO"
        30 LET eq = (a$ = b$)
        """;
    EvalState state = runProgram(source);
    assertEquals("Hello", ((EvalState.StrVar.Scalar) state.strVar("A$")).value().toJavaString());
    assertEquals("HELLO", ((EvalState.StrVar.Scalar) state.strVar("B$")).value().toJavaString());
    assertEquals(0.0, state.numVar("EQ")); // Not equal - case sensitive
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
  void testChrOutOfRange() {
    // CHR$(n) for n > 255 is an error; use CODEPOINT$ instead
    assertThrows(ReportException.class, () -> runProgram("10 LET A$ = CHR$(256)"));
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
  void testValRejectsTrailingGarbage() {
    // VAL("1+2JUNK") must throw, not silently return 3
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
                10 LET A = VAL("1+2JUNK")
                """));
  }

  @Test
  void testValRejectsPartialExpression() {
    // VAL("1 2") must throw — "1" is a complete expression but "2" is trailing garbage
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                """
                10 LET A = VAL("1 2")
                """));
  }
}
