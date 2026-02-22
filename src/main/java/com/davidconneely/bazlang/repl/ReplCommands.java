package com.davidconneely.bazlang.repl;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.io.Display;

/**
 * Handles REPL-only commands that cannot be stored as part of a program: EDIT.
 *
 * <p>EDIT is intercepted before ANTLR parsing because it needs to pre-fill the input buffer. DELETE
 * and RENUM are now part of the grammar and can be stored in programs.
 */
public final class ReplCommands {

  private ReplCommands() {}

  /**
   * Try to handle a REPL-only command.
   *
   * @return true if the line was a REPL command and was handled, false if it should be passed to
   *     the normal parser
   */
  public static boolean tryHandle(String line, EvalState state, Display display) {
    String trimmed = line.trim();
    String upper = trimmed.toUpperCase();

    if (upper.startsWith("EDIT ") || upper.equals("EDIT")) {
      handleEdit(upper.equals("EDIT") ? "" : trimmed.substring(5).trim(), state, display);
      return true;
    }

    return false;
  }

  /** EDIT n - pre-fill input with line n for editing. */
  private static void handleEdit(String args, EvalState state, Display display) {
    if (args.isEmpty()) {
      throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, "EDIT requires a line number");
    }
    int lineNum = parseLineNumber(args, "EDIT requires a line number");
    ProgramLine line = state.program().get(lineNum);
    if (line != null) {
      display.prefillInput(lineNum + " " + line.sourceText());
    } else {
      display.prefillInput(lineNum + " ");
    }
  }

  private static int parseLineNumber(String text, String errorMessage) {
    try {
      int num = Integer.parseInt(text.trim());
      if (num < Limits.MIN_LINE_LABEL || num > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      return num;
    } catch (NumberFormatException e) {
      throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, errorMessage);
    }
  }
}
