package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementContext;
import com.davidconneely.repl.ReplHandler;
import com.davidconneely.repl.Shell;
import java.util.List;

public final class BazLangReplHandler implements ReplHandler {
  private final AntlrParser parser;
  private final EvalState state;
  private final ProgramManager executor;
  private final ProgramEditor programEditor;
  private final Interpreter interpreter;

  public BazLangReplHandler(
      AntlrParser parser,
      EvalState state,
      ProgramManager executor,
      ProgramEditor programEditor,
      Interpreter interpreter) {
    this.parser = parser;
    this.state = state;
    this.executor = executor;
    this.programEditor = programEditor;
    this.interpreter = interpreter;
  }

  @Override
  public boolean handleReplInput(String line, Shell ui) {
    try {
      AntlrParser.ParsedLine parsed = parser.parseReplLine(line);
      boolean result = true;
      if (parsed instanceof AntlrParser.ParsedLine.Numbered(int lineNumber, String statementText)) {
        // Reset current execution position on program modification
        state.setCurrentLineLabel(0);
        state.setCurrentStatementIndex(1);
        result = handleNumberedLine(lineNumber, statementText, line, ui);
      } else if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
        // REPL command is immediate execution at 0:1
        state.setCurrentLineLabel(0);
        state.setCurrentStatementIndex(1);
        if (ui != null) {
          ui.systemPrintln("❯ " + line.trim());
        }
        handleReplCommand(ctx, ui);
      } else if (parsed instanceof AntlrParser.ParsedLine.Immediate(var _)) {
        if (ui != null) {
          ui.systemPrintln("❯ " + line.trim());
        }
        result = handleImmediateStatement(line);
      }

      // Success! Update last report info to OK with the current/last execution location
      state.setLastReportCode(ReportCode.OK);
      state.setLastReportLabel(state.currentLineLabel());
      state.setLastReportStatementIndex(state.currentStatementIndex());
      if (ui != null) {
        ui.setStatus(
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
      if (ui != null) {
        ui.setStatus(e.format());
      }
      if (e.reportCode() == ReportCode.STOP_STATEMENT) {
        return e.lineLabel() != 0;
      }
    }
    return true;
  }

  private boolean handleNumberedLine(
      int lineNumber, String statementText, String originalLine, Shell ui) {
    String trimmed = originalLine.trim();
    if (trimmed.matches("^\\d+\\s*$")) {
      state.program().remove(lineNumber);
      ui.systemPrintln(lineNumber + " deleted");
    } else {
      state.program().put(lineNumber, new ProgramLine(lineNumber, statementText));
      ui.systemPrintln("❯ " + trimmed);
    }
    return true;
  }

  private void handleReplCommand(BazLangParser.ReplCommandContext ctx, Shell ui) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      programEditor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      int lineNum = (int) executor.evalNum(edit.numExpr());
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      ProgramLine programLine = state.program().get(lineNum);
      if (programLine != null) {
        ui.prefillInput(lineNum + " " + programLine.sourceText());
      } else {
        ui.prefillInput(lineNum + " ");
      }
    } else if (ctx instanceof BazLangParser.RenumCmdContext renum) {
      programEditor.executeRenum(renum.renumArgs());
    } else if (ctx instanceof BazLangParser.ReformatCmdContext reformat) {
      programEditor.executeReformat(reformat.lineRange());
    }
  }

  private boolean handleImmediateStatement(String rawLine) {
    state.setRunning(true);
    ProgramLine dummyLine = new ProgramLine(0, rawLine);
    int index = 1;
    List<StatementContext> flatStmts = dummyLine.getFlattenedStatements(parser);
    for (var stmt : flatStmts) {
      state.setCurrentLineLabel(0);
      state.setCurrentStatementIndex(index);
      executor.visit(stmt);
      if (state.hasPendingJump() || !state.isRunning()) {
        break;
      }
      index++;
    }
    // Handle returning to immediate mode from loops/subroutines
    while (state.hasPendingJump()
        && state.pendingJumpLabel() != null
        && state.pendingJumpLabel() == 0) {
      int startIndex = state.pendingJumpStatementIndex();
      state.clearPendingJump();
      index = 1;
      for (var stmt : flatStmts) {
        if (index >= startIndex) {
          state.setCurrentLineLabel(0);
          state.setCurrentStatementIndex(index);
          executor.visit(stmt);
          if (state.hasPendingJump() || !state.isRunning()) {
            break;
          }
        }
        index++;
      }
      // If a jump occurred to > 0 (like RUN or GO TO), resume the interpreter
      if (state.hasPendingJump()
          && state.pendingJumpLabel() != null
          && state.pendingJumpLabel() > 0) {
        interpreter.resume();
      }
    }
    // Check if the FIRST set of statements caused a jump to > 0
    if (state.hasPendingJump()
        && state.pendingJumpLabel() != null
        && state.pendingJumpLabel() > 0) {
      interpreter.resume();
    }
    state.setRunning(false);
    return true;
  }
}
