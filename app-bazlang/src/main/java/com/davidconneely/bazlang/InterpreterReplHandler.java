package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import com.davidconneely.repl.ReplHandler;

/**
 * The interactive REPL's {@link ReplHandler}: dispatches each line to the interpreter or editor.
 */
public final class InterpreterReplHandler implements ReplHandler {
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final AntlrParser parser;
  private final EvalState state;
  private final StatementExecutor executor;
  private final ProgramEditor programEditor;
  private final Interpreter interpreter;

  /**
   * Creates a REPL handler over the given collaborators.
   *
   * @param screen the screen to render output to.
   * @param input the input source for {@code INPUT}/{@code INKEY$}/...
   * @param parser the parser to use for REPL lines.
   * @param state the interpreter state to run against.
   * @param executor the statement executor to dispatch immediate-mode statements to.
   * @param programEditor the editor for {@code DELETE}/{@code RENUM}/{@code REFORMAT}/{@code EDIT}.
   * @param interpreter the interpreter to run programs on.
   */
  public InterpreterReplHandler(
      VirtualScreen screen,
      VirtualInput input,
      AntlrParser parser,
      EvalState state,
      StatementExecutor executor,
      ProgramEditor programEditor,
      Interpreter interpreter) {
    this.screen = screen;
    this.input = input;
    this.parser = parser;
    this.state = state;
    this.executor = executor;
    this.programEditor = programEditor;
    this.interpreter = interpreter;
  }

  @Override
  public boolean handleReplInput(String line) {
    try {
      final var parsed = parser.parseReplLine(line);
      boolean result = true;
      if (parsed instanceof AntlrParser.ParsedLine.Numbered(int lineNumber, String statementText)) {
        // Reset current execution position on program modification
        state.setCurrentLineLabel(0);
        state.setCurrentStatementIndex(1);
        result = handleNumberedLine(lineNumber, statementText, line);
      } else if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
        // REPL command is immediate execution at 0:1
        state.setCurrentLineLabel(0);
        state.setCurrentStatementIndex(1);
        result = handleReplCommand(ctx);
      } else if (parsed instanceof AntlrParser.ParsedLine.Immediate(var _)) {
        echoAcceptedLine(line.trim());
        result = handleImmediateStatement(line);
      }

      // Success! Update last report info to OK with the current/last execution location
      state.setLastReport(
          new EvalState.ReportState(
              ReportCode.OK, state.currentLineLabel(), state.currentStatementIndex()));
      if (screen != null) {
        screen.setStatus(
            new ReportException(
                    ReportCode.OK,
                    state.lastReport().lineLabel(),
                    state.lastReport().statementIndex(),
                    "Ready")
                .format());
      }
      return result;
    } catch (ReportException e) {
      state.setLastReport(
          new EvalState.ReportState(e.reportCode(), e.lineLabel(), e.statementIndex()));
      if (screen != null) {
        screen.setStatus(e.format());
      }
    }
    return true;
  }

  private boolean handleNumberedLine(int lineNumber, String statementText, String originalLine) {
    if (statementText.isBlank()) {
      // A bare line number deletes the line
      state.program().remove(lineNumber);
      if (screen != null) {
        screen.systemPrintln(lineNumber + " deleted");
      }
    } else {
      state.program().put(lineNumber, new ProgramLine(lineNumber, statementText));
      echoAcceptedLine(originalLine.trim());
    }
    return true;
  }

  // Terminal-themed ANSI blue (index 4 of the 256-colour range - see AbstractCellBufferedScreen's
  // colour-code Javadoc - renders as the literal "\033[34m" SGR code), not the ZX ink 1 RGB
  // constant: the live REPL prompt's own "\033[34m❯ \033[m" uses the terminal's own blue, which
  // can differ from ZX blue's fixed RGB under most terminal colour themes.
  private static final int MARKER_INK = 256 + 4;

  /**
   * Echoes an accepted line back as REPL/system chrome (see {@link VirtualScreen#systemMessage}),
   * syntax-highlighted the same way as JLine's live REPL-input highlighting and {@code LIST}.
   *
   * @param code the accepted line's text, with or without a leading line number.
   */
  private void echoAcceptedLine(String code) {
    if (screen == null) {
      return;
    }
    screen.systemMessage(
        () -> {
          screen.setInk(MARKER_INK);
          screen.print("❯ ");
          screen.setInk(-1);
          HighlightedLinePrinter.print(screen, code);
          screen.println();
        });
  }

  private boolean handleReplCommand(BazLangParser.ReplCommandContext ctx) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      programEditor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      final int lineNum = ExpressionEvaluator.toInt(executor.evalNum(edit.numExpr()));
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(
            ReportCode.INTEGER_OUT_OF_RANGE, 0, 1, "Line number out of range");
      }
      final var programLine = state.program().get(lineNum);
      if (input != null) {
        if (programLine != null) {
          input.prefillInput(lineNum + " " + programLine.sourceText());
        } else {
          input.prefillInput(lineNum + " ");
        }
      }
    } else if (ctx instanceof BazLangParser.ExitCmdContext) {
      return false;
    } else if (ctx instanceof BazLangParser.RenumCmdContext renum) {
      programEditor.executeRenum(renum.renumArgs());
    } else if (ctx instanceof BazLangParser.ReformatCmdContext reformat) {
      programEditor.executeReformat(reformat.lineRange());
    }
    return true;
  }

  private boolean handleImmediateStatement(String rawLine) {
    interpreter.executeImmediate(rawLine);
    return true;
  }
}
