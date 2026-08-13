package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component tests for {@link ExpressionEvaluator}: parses source text with the existing {@link
 * AntlrParser}, lowers it via {@code AstLowering}, evaluates it, and asserts the result — covering
 * every operator, every numeric/string function, variable/array access, slicing, and DEF FN
 * shadowing.
 */
class ExpressionEvaluatorTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState state;
  private MockScreen screen;
  private ExpressionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    screen = new MockScreen();
    evaluator = new ExpressionEvaluator(state, screen, screen, PARSER);
  }

  private double evalN(String src) {
    return evaluator.evaluateNumericExpression(src);
  }

  private BStr evalS(String src) {
    return evaluator.evaluateStringExpression(src);
  }

  private void defineFn(String defSource) {
    final var stmts = PARSER.parseStatementsContext(defSource);
    final var defStmt = (BazLangParser.DefFnStmtContext) stmts.statement(0);
    final String name = defStmt.name.getText().toUpperCase(Locale.ROOT);
    final List<String> params =
        defStmt.params != null
            ? defStmt.params.stream()
                .map(Token::getText)
                .map(t -> t.toUpperCase(Locale.ROOT))
                .toList()
            : List.of();
    state.setFn(
        name,
        new EvalState.FnDefinition(
            name, params, AstLowering.lowerExpression(defStmt.expression(), 0)));
  }

  @Nested
  class Arithmetic {
    @Test
    void addSubMulDiv() {
      assertEquals(12.5, evalN("5 + 7.5"));
      assertEquals(2.5, evalN("5 - 2.5"));
      assertEquals(21.0, evalN("3 * 7"));
      assertEquals(4.0, evalN("12 / 3"));
    }

    @Test
    void power() {
      assertEquals(8.0, evalN("2 ** 3"));
      assertEquals(8.0, evalN("2 ^ 3"));
      assertEquals(-4.0, evalN("-2**2")); // ** binds tighter than unary minus
      assertEquals(-8.0, evalN("(-2)**3")); // integer exponent: negative base allowed
    }

    @Test
    void negativeBaseNonIntegerExponentThrows() {
      assertThrows(ReportException.class, () -> evalN("(-2)**0.5"));
    }

    @Test
    void unaryMinus() {
      assertEquals(-5.0, evalN("-5"));
      assertEquals(5.0, evalN("- -5"));
    }

    @Test
    void divisionByZeroThrows() {
      assertThrows(ReportException.class, () -> evalN("1 / 0"));
    }

    @Test
    void overflowThrows() {
      assertThrows(ReportException.class, () -> evalN("1E300 * 1E300"));
    }
  }

  @Nested
  class Comparisons {
    @Test
    void numeric() {
      assertEquals(1.0, evalN("5 = 5"));
      assertEquals(0.0, evalN("5 <> 5"));
      assertEquals(1.0, evalN("3 < 5"));
      assertEquals(1.0, evalN("5 <= 5"));
      assertEquals(1.0, evalN("5 > 3"));
      assertEquals(1.0, evalN("5 >= 5"));
    }

    @Test
    void string() {
      assertEquals(1.0, evalN("\"ABC\" = \"ABC\""));
      assertEquals(1.0, evalN("\"ABC\" <> \"ABD\""));
      assertEquals(1.0, evalN("\"ABC\" < \"ABD\""));
      assertEquals(1.0, evalN("\"ABC\" <= \"ABC\""));
      assertEquals(1.0, evalN("\"B\" > \"A\""));
      assertEquals(1.0, evalN("\"B\" >= \"B\""));
    }
  }

  @Nested
  class Logical {
    @Test
    void not() {
      assertEquals(1.0, evalN("NOT 0"));
      assertEquals(0.0, evalN("NOT 5"));
    }

    @Test
    void and() {
      assertEquals(7.0, evalN("7 AND 1")); // A if B<>0
      assertEquals(0.0, evalN("7 AND 0")); // 0 if B=0
    }

    @Test
    void or() {
      assertEquals(1.0, evalN("0 OR 5")); // 1 if B<>0
      assertEquals(7.0, evalN("7 OR 0")); // A if B=0
    }

    @Test
    void strAnd() {
      assertEquals(BStr.fromJavaString("HI"), evalS("\"HI\" AND 1"));
      assertEquals(BStr.EMPTY, evalS("\"HI\" AND 0"));
    }
  }

  @Nested
  class VariablesAndArrays {
    @Test
    void numericVariable() {
      state.setNumVar("X", 42.0);
      assertEquals(84.0, evalN("X * 2"));
    }

    @Test
    void undefinedNumericVariableThrows() {
      assertThrows(ReportException.class, () -> evalN("UNDEFINEDVAR"));
    }

    @Test
    void stringVariable() {
      state.setStrVar("A$", new EvalState.StrVar.Scalar(BStr.fromJavaString("TEST")));
      assertEquals(BStr.fromJavaString("TESTING"), evalS("A$ + \"ING\""));
    }

    @Test
    void stringConcatenation() {
      assertEquals(BStr.fromJavaString("HELLO WORLD"), evalS("\"HELLO\" + \" WORLD\""));
    }

    @Test
    void numericArrayAccess() {
      state.setNumArray("A", new EvalState.NumArray(new int[] {3}, new double[] {10, 20, 30}));
      assertEquals(20.0, evalN("A(2)"));
    }

    @Test
    void nestedArrayIndex() {
      state.setNumArray("A", new EvalState.NumArray(new int[] {3}, new double[] {10, 20, 30}));
      state.setNumVar("I", 2.0);
      assertEquals(30.0, evalN("A(I + 1)"));
    }

    @Test
    void stringArraySubscriptAndSlice() {
      // A string array of 2 elements, each 5 bytes wide: "HELLO" and "WORLD".
      final byte[] data = "HELLOWORLD".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
      state.setStrVar("A$", new EvalState.StrVar.Array(new int[] {2}, 5, data));
      assertEquals(BStr.fromJavaString("HELLO"), evalS("A$(1)"));
      assertEquals(BStr.fromJavaString("WORLD"), evalS("A$(2)"));
      assertEquals(BStr.fromJavaString("ELL"), evalS("A$(1, 2 TO 4)"));
    }

    @Test
    void scalarStringSlicing() {
      state.setStrVar("A$", new EvalState.StrVar.Scalar(BStr.fromJavaString("HELLO")));
      assertEquals(BStr.fromJavaString("ELL"), evalS("A$(2 TO 4)"));
      assertEquals(BStr.fromJavaString("HELLO"), evalS("A$(TO)"));
      assertEquals(BStr.fromJavaString("L"), evalS("A$(3)"));
    }
  }

  @Nested
  class NumericFunctions {
    @Test
    void trig() {
      assertEquals(1.0, evalN("COS 0"));
      assertEquals(0.0, evalN("SIN 0"));
      assertEquals(0.0, evalN("TAN 0"));
      assertEquals(0.0, evalN("ACS 1"));
      assertEquals(0.0, evalN("ASN 0"));
      assertEquals(0.0, evalN("ATN 0"));
      assertThrows(ReportException.class, () -> evalN("ACS 2"));
      assertThrows(ReportException.class, () -> evalN("ASN 2"));
    }

    @Test
    void expLnSqrt() {
      assertEquals(1.0, evalN("EXP 0"));
      assertEquals(0.0, evalN("LN 1"));
      assertEquals(2.0, evalN("SQR 4"));
      assertThrows(ReportException.class, () -> evalN("LN 0"));
      assertThrows(ReportException.class, () -> evalN("SQR(-1)"));
    }

    @Test
    void intSgnAbs() {
      // Bare '-' isn't a valid numAtom (no parens = binds to the function, not a full numExpr),
      // so a negative function argument needs explicit parens: SGN(-5), not SGN -5.
      assertEquals(3.0, evalN("INT 3.7"));
      assertEquals(-1.0, evalN("SGN(-5)"));
      assertEquals(5.0, evalN("ABS(-5)"));
    }

    @Test
    void piAndRnd() {
      assertEquals(Math.PI, evalN("PI"));
      final double r = evalN("RND");
      assertTrue(r >= 0.0 && r < 1.0);
    }

    @Test
    void frames() {
      assertTrue(evalN("FRAMES") >= 0.0);
    }

    @Test
    void codeAndLen() {
      assertEquals(65.0, evalN("CODE \"A\""));
      assertEquals(0.0, evalN("CODE \"\""));
      assertEquals(5.0, evalN("LEN \"HELLO\""));
    }

    @Test
    void ucodeAndUlen() {
      assertEquals("A".codePointAt(0), evalN("UCODE \"A\""));
      assertEquals(5.0, evalN("ULEN \"HELLO\""));
      assertThrows(ReportException.class, () -> evalN("UCODE \"\""));
    }

    @Test
    void ucnext() {
      // 1-based byte position 1 -> next codepoint starts at byte 2 (1-based) for single-byte chars.
      assertEquals(2.0, evalN("UCNEXT(\"AB\", 1)"));
      assertThrows(ReportException.class, () -> evalN("UCNEXT(\"AB\", 0)"));
    }

    @Test
    void val() {
      assertEquals(12.0, evalN("VAL \"3*4\""));
      assertEquals(7.0, evalN("5 + VAL \"2\""));
    }

    @Test
    void colour() {
      final double result = evalN("COLOUR(255, 0, 128)");
      assertEquals(16_777_216.0 + (255 << 16) + 128, result);
      assertThrows(ReportException.class, () -> evalN("COLOUR(256, 0, 0)"));
    }

    @Test
    void textAndPlotDimensionsDelegateToScreen() {
      assertEquals(screen.printWidth(), (int) evalN("TEXTW"));
      assertEquals(screen.printHeight(), (int) evalN("TEXTH"));
      assertEquals(screen.currentCol(), (int) evalN("TEXTX"));
      assertEquals(screen.currentRow(), (int) evalN("TEXTY"));
      assertEquals(screen.plotWidth(), (int) evalN("PLOTW"));
      assertEquals(screen.plotHeight(), (int) evalN("PLOTH"));
      assertEquals(screen.plotMode(), (int) evalN("PLOTMODE"));
    }

    @Test
    void plotCursorDelegatesToState() {
      state.setGraphicsCursorX(17);
      state.setGraphicsCursorY(23);
      assertEquals(17.0, evalN("PLOTX"));
      assertEquals(23.0, evalN("PLOTY"));
    }

    @Test
    void attrPointXattrDelegateToScreen() {
      assertEquals(screen.getScreenAttributes(0, 0), (int) evalN("ATTR(0, 0)"));
      assertEquals(screen.point(0, 0), (int) evalN("POINT(0, 0)"));
      assertEquals(screen.getXAttributes(0, 0, 0), (int) evalN("XATTR(0, 0, 0)"));
      assertThrows(ReportException.class, () -> evalN("XATTR(0, 0, 9)"));
    }
  }

  @Nested
  class StringFunctions {
    @Test
    void chrAndUchr() {
      assertEquals(BStr.fromByte(65), evalS("CHR$ 65"));
      assertEquals(BStr.fromJavaString("A"), evalS("UCHR$ 65"));
      assertThrows(ReportException.class, () -> evalS("CHR$ 256"));
    }

    @Test
    void strDollar() {
      assertEquals(BStr.fromJavaString("42"), evalS("STR$ 42"));
    }

    @Test
    void valOfEmptyStringThrows() {
      // VAL "" trims to an empty expression, which evaluateNumericExpression rejects outright.
      assertThrows(ReportException.class, () -> evalN("VAL \"\""));
    }

    @Test
    void valDollar() {
      // The BazLang source string literal """HELLO""" (doubled-quote escaping) has the *value*
      // "HELLO" (with quotes); VAL$ re-parses that value as a string expression, yielding HELLO.
      assertEquals(BStr.fromJavaString("HELLO"), evalS("VAL$ \"\"\"HELLO\"\"\""));
    }

    @Test
    void inkeyAndUinkey() {
      screen.queueInkey(BStr.fromJavaString("Q"));
      assertEquals(BStr.fromJavaString("Q"), evalS("INKEY$"));
      screen.queueUinkey(BStr.fromJavaString("Z"));
      assertEquals(BStr.fromJavaString("Z"), evalS("UINKEY$"));
    }

    @Test
    void screenDollarAndUscreenDollar() {
      screen.locate(0, 0);
      screen.print("A");
      assertEquals(BStr.fromByte('A'), evalS("SCREEN$(0, 0)"));
      assertEquals(BStr.fromJavaString("A"), evalS("USCREEN$(0, 0)"));
    }
  }

  @Nested
  class UserDefinedFunctions {
    @Test
    void numericFnShadowsOuterVariable() {
      defineFn("DEF FN F(X) = X * 2");
      state.setNumVar("X", 100.0);
      assertEquals(10.0, evalN("FN F(5)"));
      assertEquals(100.0, state.numVar("X")); // outer X restored, unaffected by the call
    }

    @Test
    void stringFnShadowsOuterVariable() {
      defineFn("DEF FN N$(A$) = A$ + \"!\"");
      state.setStrVar("A$", new EvalState.StrVar.Scalar(BStr.fromJavaString("OUTER")));
      assertEquals(BStr.fromJavaString("HI!"), evalS("FN N$(\"HI\")"));
      final var outer = (EvalState.StrVar.Scalar) state.strVar("A$");
      assertEquals(BStr.fromJavaString("OUTER"), outer.value()); // outer A$ restored
    }

    @Test
    void undefinedFnThrows() {
      assertThrows(ReportException.class, () -> evalN("FN UNDEFINED(1)"));
    }

    @Test
    void wrongArgCountThrows() {
      defineFn("DEF FN F(X) = X * 2");
      assertThrows(ReportException.class, () -> evalN("FN F(1, 2)"));
    }
  }
}
