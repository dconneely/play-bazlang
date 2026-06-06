package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.repl.ReplHandler;
import com.davidconneely.repl.Shell;

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
      if (parsed instanceof AntlrParser.ParsedLine.Numbered(int lineNumber, String statementText)) {
        return handleNumberedLine(lineNumber, statementText, line, ui);
      } else if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
        handleReplCommand(ctx, ui);
        return true;
      } else if (parsed instanceof AntlrParser.ParsedLine.Immediate(StatementContext statement)) {
        return handleImmediateStatement(statement);
      }
    } catch (ReportException e) {
      state.setLastReportCode(e.reportCode());
      state.setLastReportLabel(e.lineLabel());
      ui.setStatus(e.prefix() + " " + e.getMessage());
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
      ui.systemPrintln(trimmed);
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

  private boolean handleImmediateStatement(StatementContext statement) {
    if (statement instanceof StopStmtContext) {
      return false;
    }
    executor.visit(statement);
    if (statement instanceof ContStmtContext
        || statement instanceof GosubStmtContext
        || statement instanceof GotoStmtContext
        || statement instanceof RunStmtContext) {
      interpreter.resume();
    }
    return true;
  }
}
