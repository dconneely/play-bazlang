package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.io.BazLangScreen;
import com.davidconneely.repl.ReplHandler;

public final class BazLangReplHandler implements ReplHandler {
  private final BazLangScreen screen;
  private final AntlrParser parser;
  private final EvalState state;
  private final ProgramManager executor;
  private final ProgramEditor programEditor;
  private final Interpreter interpreter;

  public BazLangReplHandler(
      BazLangScreen screen,
      AntlrParser parser,
      EvalState state,
      ProgramManager executor,
      ProgramEditor programEditor,
      Interpreter interpreter) {
    this.screen = screen;
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
        handleReplCommand(ctx);
      } else if (parsed instanceof AntlrParser.ParsedLine.Immediate(var _)) {
        if (screen != null) {
          screen.systemPrintln("❯ " + line.trim());
        }
        result = handleImmediateStatement(line);
      }

      // Success! Update last report info to OK with the current/last execution location
      state.setLastReportCode(ReportCode.OK);
      state.setLastReportLabel(state.currentLineLabel());
      state.setLastReportStatementIndex(state.currentStatementIndex());
      if (screen != null) {
        screen.setStatus(
            new ReportException(
                    ReportCode.OK,
                    state.lastReportLabel(),
                    state.lastReportStatementIndex(),
                    "Ready")
                .format());
      }
      return result;
    } catch (ReportException e) {
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      state.setLastReportStatementIndex(e.statementIndex());
      if (screen != null) {
        screen.setStatus(e.format());
      }
      if (e.reportCode() == ReportCode.STOP_STATEMENT) {
        return e.lineLabel() != 0;
      }
    }
    return true;
  }

  private boolean handleNumberedLine(int lineNumber, String statementText, String originalLine) {
    final String trimmed = originalLine.trim();
    if (trimmed.matches("^\\d+\\s*$")) {
      state.program().remove(lineNumber);
      if (screen != null) {
        screen.systemPrintln(lineNumber + " deleted");
      }
    } else {
      state.program().put(lineNumber, new ProgramLine(lineNumber, statementText));
      if (screen != null) {
        screen.systemPrintln("❯ " + trimmed);
      }
    }
    return true;
  }

  private void handleReplCommand(BazLangParser.ReplCommandContext ctx) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      programEditor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      final int lineNum = (int) executor.evalNum(edit.numExpr());
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      final var programLine = state.program().get(lineNum);
      if (screen != null) {
        if (programLine != null) {
          screen.prefillInput(lineNum + " " + programLine.sourceText());
        } else {
          screen.prefillInput(lineNum + " ");
        }
      }
    } else if (ctx instanceof BazLangParser.RenumCmdContext renum) {
      programEditor.executeRenum(renum.renumArgs());
    } else if (ctx instanceof BazLangParser.ReformatCmdContext reformat) {
      programEditor.executeReformat(reformat.lineRange());
    }
  }

  private boolean handleImmediateStatement(String rawLine) {
    interpreter.executeImmediate(rawLine);
    return true;
  }
}
