package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReformatVisitorTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;
  private static final ReformatVisitor FORMATTER = new ReformatVisitor();

  private void assertFormatsTo(String source, String expected) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines("10 " + source);
    String actual = program.get(10).getStatement(PARSER).accept(FORMATTER);
    assertEquals(expected, actual);
  }

  @Test
  void testFormatting() {
    assertFormatsTo("clear", "CLEAR");
    assertFormatsTo("cls", "CLS");
    assertFormatsTo("cont", "CONT");
    assertFormatsTo("copy", "COPY");
    assertFormatsTo("dim a(10)", "DIM A(10)");
    assertFormatsTo("dim a$(5, 10)", "DIM A$(5, 10)");
    assertFormatsTo("fast", "FAST");
    assertFormatsTo("for i = 1 to 10", "FOR I = 1 TO 10");
    assertFormatsTo("for i = 1 to 10 step 2", "FOR I = 1 TO 10 STEP 2");
    assertFormatsTo("gosub 100", "GOSUB 100");
    assertFormatsTo("goto 100", "GOTO 100");
    assertFormatsTo("if a=1 then goto 200", "IF A = 1 THEN GOTO 200");
    assertFormatsTo("input a", "INPUT A");
    assertFormatsTo("let a=1", "LET A = 1");
    assertFormatsTo("let a$=\"hello\"", "LET A$ = \"hello\"");
    assertFormatsTo("list", "LIST");
    assertFormatsTo("llist 10 to 20", "LLIST 10 TO 20");
    assertFormatsTo("load \"game\"", "LOAD \"game\"");
    assertFormatsTo("lprint \"test\"", "LPRINT \"test\"");
    assertFormatsTo("new", "NEW");
    assertFormatsTo("next i", "NEXT I");
    assertFormatsTo("pause 50", "PAUSE 50");
    assertFormatsTo("plot 10,20", "PLOT 10, 20");
    assertFormatsTo("plotmode 1", "PLOTMODE 1");
    assertFormatsTo("poke 16384,255", "POKE 16384, 255");
    assertFormatsTo("print \"hello\"", "PRINT \"hello\"");
    assertFormatsTo("rand", "RAND");
    assertFormatsTo("rand 0", "RAND");
    assertFormatsTo("rem some comment", "REM some comment");
    assertFormatsTo("return", "RETURN");
    assertFormatsTo("run", "RUN");
    assertFormatsTo("run 100", "RUN 100");
    assertFormatsTo("save \"game\"", "SAVE \"game\"");
    assertFormatsTo("scroll", "SCROLL");
    assertFormatsTo("slow", "SLOW");
    assertFormatsTo("stop", "STOP");
    assertFormatsTo("unplot 10,20", "UNPLOT 10, 20");
    assertFormatsTo("print at 10,20;\"x\"", "PRINT AT 10, 20; \"x\"");
    assertFormatsTo("print tab 10;\"x\"", "PRINT TAB 10; \"x\"");
  }
}
