package com.davidconneely.bazlang.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import org.junit.jupiter.api.Test;

class ReformatVisitorTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;
  private static final ReformatVisitor FORMATTER = new ReformatVisitor();

  private void assertFormatsTo(String source, String expected) {
    final var program = PARSER.parseProgramLines("10 " + source);
    final String actual = program.get(10).getStatements(PARSER).accept(FORMATTER);
    assertEquals(expected, actual);
  }

  @Test
  void testFormatting() {
    assertFormatsTo("clear", "CLEAR");
    assertFormatsTo("cls", "CLS");
    assertFormatsTo("cont", "CONTINUE");
    assertFormatsTo("dim a(10)", "DIM a(10)");
    assertFormatsTo("dim a$(5, 10)", "DIM a$(5, 10)");
    assertFormatsTo("for i = 1 to 10", "FOR i = 1 TO 10");
    assertFormatsTo("for i = 1 to 10 step 2", "FOR i = 1 TO 10 STEP 2");
    assertFormatsTo("gosub 100", "GO SUB 100");
    assertFormatsTo("goto 100", "GO TO 100");
    assertFormatsTo("if a=1 then goto 200", "IF a = 1 THEN GO TO 200");
    assertFormatsTo("input a", "INPUT a");
    assertFormatsTo("let a=1", "LET a = 1");
    assertFormatsTo("let a=1:let b=2", "LET a = 1 : LET b = 2");
    assertFormatsTo("let a$=\"hello\"", "LET a$ = \"hello\"");
    assertFormatsTo("list", "LIST");
    assertFormatsTo("list 10 to 20", "LIST 10 TO 20");
    assertFormatsTo("load \"game\"", "LOAD \"game\"");
    assertFormatsTo("new", "NEW");
    assertFormatsTo("next i", "NEXT i");
    assertFormatsTo("pause 50", "PAUSE 50");
    assertFormatsTo("plot 10,20", "PLOT 10, 20");
    assertFormatsTo("plotmode 1", "PLOTMODE 1");
    assertFormatsTo("print \"hello\"", "PRINT \"hello\"");
    assertFormatsTo("print \"hello\" ' \"world\"", "PRINT \"hello\"' \"world\"");
    assertFormatsTo("print 2 ** 3", "PRINT 2 ^ 3");
    assertFormatsTo("print 2 ^ 3", "PRINT 2 ^ 3");
    assertFormatsTo("print at 10,20;\"x\"", "PRINT AT 10, 20; \"x\"");
    assertFormatsTo("print tab 10;\"x\"", "PRINT TAB 10; \"x\"");
    assertFormatsTo(
        "print val$(\"\"\"a\"\" + \"\"b\"\"\")", "PRINT VAL$ (\"\"\"a\"\" + \"\"b\"\"\")");
    assertFormatsTo("rand", "RANDOMIZE");
    assertFormatsTo("rand 0", "RANDOMIZE");
    assertFormatsTo("rem some comment", "REM some comment");
    assertFormatsTo("return", "RETURN");
    assertFormatsTo("run", "RUN");
    assertFormatsTo("run 100", "RUN 100");
    assertFormatsTo("save \"game\"", "SAVE \"game\"");
    assertFormatsTo("scroll", "SCROLL");
    assertFormatsTo("stop", "STOP");
    assertFormatsTo("print screen$(1, 2)", "PRINT SCREEN$(1, 2)");
    assertFormatsTo("print uscreen$(3, 4)", "PRINT USCREEN$(3, 4)");
    assertFormatsTo("print attr(1, 2)", "PRINT ATTR(1, 2)");
    assertFormatsTo("print xattr(3, 4, 5)", "PRINT XATTR(3, 4, 5)");
  }
}
