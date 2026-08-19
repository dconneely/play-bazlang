package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidconneely.bazlang.BStr;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the "play again (Y/N)" loop the example games use, after a report that answering
 * {@code Y} in hangman ended the game instead of restarting it.
 */
class PlayAgainProgramTest extends BaseProgramTest {

  @Test
  void answeringYesJumpsBackwardsRatherThanFallingThrough() {
    final String source =
        """
        10 LET r$ = INKEY$
        20 IF LEN (r$) <> 1 THEN GO TO 10
        30 IF r$ = "Y" OR r$ = "y" THEN GO TO 100
        40 PRINT "ENDED"
        50 STOP
        100 PRINT "RESTARTED"
        110 STOP
        """;
    final var result = runWithKeys(source, List.of(BStr.fromJavaString("Y")), List.of());
    assertEquals("RESTARTED\n", result.output());
  }

  @Test
  void answeringYesAfterAnAplayIsStillHonoured() {
    // Hangman answers this prompt immediately after kicking off its (asynchronous) losing music,
    // so check the background APLAY session doesn't disturb the prompt's control flow.
    final String source =
        """
        10 APLAY "T120O4N6$b3$b5$b5$b"
        20 LET r$ = INKEY$
        30 IF LEN (r$) <> 1 THEN GO TO 20
        40 IF r$ = "Y" OR r$ = "y" THEN GO TO 100
        50 PRINT "ENDED"
        60 STOP
        100 PRINT "RESTARTED"
        110 STOP
        """;
    final var result = runWithKeys(source, List.of(BStr.fromJavaString("Y")), List.of());
    assertEquals("RESTARTED\n", result.output());
  }

  @Test
  void aStaleKeyLeftOverFromGameplayDoesNotAnswerThePromptForYou() {
    // The actual hangman bug: TerminalScreen.inkey() keeps reporting the last key pressed for
    // ~100ms (approximating the Spectrum's key repeat), and hangman reaches its "Play again?"
    // prompt milliseconds after the player's final letter guess. The prompt therefore read that
    // stale letter, and because it fell through on anything that wasn't "Y", the game ended
    // immediately -- looking for all the world like pressing Y quit the game. The fix is to accept
    // only Y/N and ignore everything else, as the other example games already did.
    final String source =
        """
        10 LET r$ = INKEY$
        20 IF LEN (r$) <> 1 THEN GO TO 10
        30 IF (r$ <> "Y") AND (r$ <> "y") AND (r$ <> "N") AND (r$ <> "n") THEN GO TO 10
        40 IF r$ = "Y" OR r$ = "y" THEN GO TO 100
        50 PRINT "ENDED"
        60 STOP
        100 PRINT "RESTARTED"
        110 STOP
        """;
    // "E" is the stale letter guess still being reported; "Y" is what the player actually presses.
    final var result =
        runWithKeys(source, List.of(BStr.fromJavaString("E"), BStr.fromJavaString("Y")), List.of());
    assertEquals("RESTARTED\n", result.output());
  }

  @Test
  void theParenthesisedFourWayPromptGuardAcceptsBothYesAndNo() {
    // The exact guard hangman uses at line 5035. Worth testing all four accepted spellings and a
    // rejected one: the unparenthesised version of this guard happened to accept "Y" while
    // rejecting "N", so a test that only tried "Y" would have passed against the broken form.
    final String guard =
        """
        10 LET r$ = INKEY$
        20 IF LEN (r$) <> 1 THEN GO TO 10
        30 IF (r$ <> "Y") AND (r$ <> "y") AND (r$ <> "N") AND (r$ <> "n") THEN GO TO 10
        40 IF r$ = "Y" OR r$ = "y" THEN GO TO 100
        50 PRINT "ENDED"
        60 STOP
        100 PRINT "RESTARTED"
        110 STOP
        """;
    for (final String accepted : new String[] {"Y", "y"}) {
      final var result =
          runWithKeys(
              guard, List.of(BStr.fromJavaString("k"), BStr.fromJavaString(accepted)), List.of());
      assertEquals("RESTARTED\n", result.output(), "'" + accepted + "' should restart");
    }
    for (final String accepted : new String[] {"N", "n"}) {
      final var result =
          runWithKeys(
              guard, List.of(BStr.fromJavaString("k"), BStr.fromJavaString(accepted)), List.of());
      assertEquals("ENDED\n", result.output(), "'" + accepted + "' should end the game");
    }
  }

  @Test
  void chainedAndOverStringComparisonsMisparses() {
    // Documents a real grammar bug, found via hangman's play-again prompt refusing to accept "N".
    //
    // The grammar offers both `numExpr AND numExpr` and `strExpr AND numExpr` (the "string AND
    // number" form that yields "" when the number is zero). Parsing `r$ <> "Y" AND r$ <> "y"`, the
    // right-hand operand of `<>` is a strExpr, and it greedily extends across the AND, so this
    // parses as r$ <> ("Y" AND (r$ <> "y")) rather than (r$ <> "Y") AND (r$ <> "y").
    //
    // The two readings agree often enough to hide the problem -- with r$ = "Y" both say false --
    // which is precisely why it survived until a case where they differ. Here r$ = "n" should make
    // the guard false (n is one of the listed letters), but the misparse reports true.
    //
    // OR is unaffected: there is no `strExpr OR numExpr` form to compete. Parenthesising each
    // comparison and nested IFs both give the right answer; hangman uses the parenthesised form.
    final String source =
        """
        10 LET r$ = "n"
        20 IF r$ <> "Y" AND r$ <> "n" THEN PRINT "GUARD TRUE"
        30 IF r$ <> "Y" THEN IF r$ <> "n" THEN PRINT "NESTED TRUE"
        40 IF (r$ <> "Y") AND (r$ <> "n") THEN PRINT "PAREN TRUE"
        50 PRINT "DONE"
        """;
    // "GUARD TRUE" is the bug: r$ IS "n", so none of these guards should hold. Nested IFs and
    // parenthesised operands both give the right answer -- parenthesising forces each comparison to
    // close as a complete numeric expression before the AND, so the strExpr can't reach across it.
    assertEquals("GUARD TRUE\nDONE\n", runProgramCapture(source));
  }

  @Test
  void restartingRereadsTheWordListFromTheStart() {
    // Hangman's "play again" jumps back to its RESTORE + READ word-picking lines, so a second pass
    // must be able to re-read the DATA. If it couldn't, the restart would die with "Out of DATA" --
    // an error, which is also why the usual end-of-program "press any key" pause would be skipped.
    final String source =
        """
        10 LET pass = 0
        20 LET n = 3
        30 RESTORE 100
        40 FOR i = 1 TO n : READ w$ : NEXT i
        50 PRINT w$
        60 LET pass = pass + 1
        70 IF pass < 2 THEN GO TO 30
        80 STOP
        100 DATA "A", "B", "C", "D"
        """;
    assertEquals("C\nC\n", runProgramCaptureIgnoringExceptions(source));
  }

  @Test
  void hangmanPlaysThroughToItsPromptAndQuitsWithoutError()
      throws java.io.IOException, InterruptedException {
    // Runs the real hangman end-to-end: guess every letter of the alphabet (so the game reaches
    // either a win or six misses whichever way the random word falls), then answer the "play
    // again" prompt with N. Nothing here should raise a ReportException -- when one escapes,
    // MainClass.runFile prints it and skips its usual end-of-program "press any key" pause, which
    // is exactly the symptom of the game "exiting without the prompt".
    final String source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("app-bazlang", "src", "example", "bas", "hangman.bas"));
    final var keys = new java.util.ArrayList<BStr>();
    for (char c = 'a'; c <= 'z'; c++) {
      keys.add(BStr.fromJavaString(String.valueOf(c)));
    }
    for (int i = 0; i < 5; i++) {
      keys.add(BStr.fromJavaString("N")); // answer (and re-answer) the play-again prompt
    }
    final var screen = new com.davidconneely.bazlang.io.MockScreen();
    for (final var k : keys) {
      screen.queueInkey(k);
    }
    final var state = new com.davidconneely.bazlang.exec.EvalState();
    final var executor =
        new com.davidconneely.bazlang.exec.StatementExecutor(state, screen, screen, screen);
    final var failure =
        new java.util.concurrent.atomic.AtomicReference<
            com.davidconneely.bazlang.ReportException>();
    // Run on a bounded thread: if the game can't finish it would otherwise spin forever on an
    // exhausted key queue, and a silent hang says far less than the screen contents at that point.
    final var runner =
        new Thread(
            () -> {
              try {
                new com.davidconneely.bazlang.exec.Interpreter(state, executor)
                    .execute(PARSER.parseProgramLines(source));
              } catch (com.davidconneely.bazlang.ReportException e) {
                failure.set(e); // a BASIC-level error -- what MainClass would print and exit on
              }
            });
    runner.setDaemon(true);
    runner.start();
    runner.join(20_000);

    if (failure.get() != null) {
      org.junit.jupiter.api.Assertions.fail(
          "hangman raised "
              + failure.get()
              + "\nscreen so far:\n"
              + screen.getOutput()
              + "\nlast line/stmt: "
              + state.currentLineLabel()
              + ":"
              + state.currentStatementIndex());
    }
    org.junit.jupiter.api.Assertions.assertFalse(
        runner.isAlive(),
        "hangman never finished; stuck at line "
            + state.currentLineLabel()
            + ":"
            + state.currentStatementIndex()
            + "\nscreen so far:\n"
            + screen.getOutput());
    org.junit.jupiter.api.Assertions.assertTrue(
        screen.getOutput().contains("Thanks for playing"),
        "expected hangman to reach its sign-off, got:\n" + screen.getOutput());
  }
}
