package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.io.MockScreen;
import com.davidconneely.cell.CellAttributes;
import org.junit.jupiter.api.Test;

class HighlightedLinePrinterTest {

  @Test
  void printsKeywordsInInkAndEverythingElseAtTheDefaultInk() {
    final var screen = new MockScreen();

    HighlightedLinePrinter.print(screen, "PRINT X");

    assertEquals("PRINT X", screen.getOutput());
    final int teal = CellAttributes.rgb(0x00D7D7);
    assertEquals(teal, screen.fgColourAt(0, 0), "'P' of PRINT");
    assertEquals(teal, screen.fgColourAt(0, 4), "'T' of PRINT");
    assertEquals(CellAttributes.COLOUR_DEFAULT, screen.fgColourAt(0, 5), "space");
    assertEquals(CellAttributes.COLOUR_DEFAULT, screen.fgColourAt(0, 6), "identifier X");
  }

  @Test
  void aLineNumberAndAKeywordEachGetTheirOwnInk() {
    final var screen = new MockScreen();

    HighlightedLinePrinter.print(screen, "10 PRINT 1");

    final int teal = CellAttributes.rgb(0x00D7D7);
    final int darkGrey = CellAttributes.rgb(0x808080);
    assertEquals(darkGrey, screen.fgColourAt(0, 0), "'1' of the line number");
    assertEquals(darkGrey, screen.fgColourAt(0, 1), "'0' of the line number");
    assertEquals(teal, screen.fgColourAt(0, 3), "'P' of PRINT");
  }

  @Test
  void remCommentIsItalicDarkGreyAndTheKeywordItselfIsTealAndUpright() {
    final var screen = new MockScreen();

    HighlightedLinePrinter.print(screen, "REM a comment");

    final int teal = CellAttributes.rgb(0x00D7D7);
    final int darkGrey = CellAttributes.rgb(0x808080);
    assertEquals(teal, screen.fgColourAt(0, 0), "'R' of REM");
    assertFalse(screen.isItalicAt(0, 0), "'R' of REM is not italic");
    assertEquals(darkGrey, screen.fgColourAt(0, 3), "the space right after REM");
    assertTrue(screen.isItalicAt(0, 3), "the space right after REM is italic, part of the comment");
    assertEquals(darkGrey, screen.fgColourAt(0, 4), "'a' of the comment text");
    assertTrue(screen.isItalicAt(0, 4), "'a' of the comment text is italic");
  }

  @Test
  void plainTextWithNoKeywordsIsUntouched() {
    final var screen = new MockScreen();

    HighlightedLinePrinter.print(screen, "A = B + 1");

    assertEquals("A = B + 1", screen.getOutput());
    assertEquals(CellAttributes.COLOUR_DEFAULT, screen.fgColourAt(0, 0));
  }
}
