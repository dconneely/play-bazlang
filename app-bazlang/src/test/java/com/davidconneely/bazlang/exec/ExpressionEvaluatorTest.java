package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private EvalState state;
  private ExpressionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    state = new EvalState();
    // For simple expression tests, we don't need a real screen or input
    evaluator = new ExpressionEvaluator(state, null, null, PARSER);
  }

  @Test
  void testNumericAddition() {
    final double result = evaluator.evaluateNumericExpression("5 + 7.5");
    assertEquals(12.5, result);
  }

  @Test
  void testStringConcatenation() {
    final BStr result = evaluator.evaluateStringExpression("\"HELLO\" + \" WORLD\"");
    assertEquals(BStr.fromJavaString("HELLO WORLD"), result);
  }

  @Test
  void testNumericVariable() {
    state.setNumVar("X", 42.0);
    final double result = evaluator.evaluateNumericExpression("X * 2");
    assertEquals(84.0, result);
  }

  @Test
  void testStringVariable() {
    state.setStrVar("A$", new EvalState.StrVar.Scalar(BStr.fromJavaString("TEST")));
    final BStr result = evaluator.evaluateStringExpression("A$ + \"ING\"");
    assertEquals(BStr.fromJavaString("TESTING"), result);
  }
}
