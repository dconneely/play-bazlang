package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.Display;
import com.davidconneely.bazlang.io.StreamDisplay;
import com.davidconneely.bazlang.io.TerminalDisplay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  private static final AntlrParser parser = new AntlrParser();

  public static void main(String[] args) {
    Display display;
    try {
      // Simple heuristic: if no console, use stream display
      if (System.console() == null) {
        display = new StreamDisplay();
      } else {
        display = new TerminalDisplay();
      }
    } catch (Exception e) {
      display = new StreamDisplay();
    }
    if (args.length == 0) {
      runRepl(display);
    } else if (args.length == 1) {
      runFile(args[0], display);
    } else {
      display.println("Usage: java com.davidconneely.bazlang.MainClass [source-file]");
      System.exit(1);
    }
  }

  private static void runFile(String sourceFile, Display display) {
    try {
      String source = Files.readString(Path.of(sourceFile));
      var program = parser.parseProgramLines(source);
      EvalState state = new EvalState();
      BazLangExecutor executor = new BazLangExecutor(state, display);
      Interpreter interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
    } catch (IOException e) {
      display.println("Error reading file: " + e.getMessage());
      System.exit(1);
    } catch (ReportException e) {
      display.println(e.prefix() + " " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      display.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void runRepl(Display display) {
    EvalState state = new EvalState();
    BazLangExecutor executor = new BazLangExecutor(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    display.println("BazLang REPL. Type 'STOP' or Ctrl+D at the prompt to exit.");
    while (true) {
      String line;
      try {
        line = display.readln("\033[7m>\033[27m ");
      } catch (Display.BreakException e) {
        // Ctrl+C at prompt: just reprint prompt
        continue;
      }
      if (line == null) {
        break; // EOF
      }
      if (line.isBlank()) {
        continue;
      }
      try {
        AntlrParser.ParsedLine parsed = parser.parseReplLine(line);
        if (parsed
            instanceof AntlrParser.ParsedLine.Numbered(int lineNumber, String statementText)) {
          // Line editing - check if it's just a line number (deletion)
          String trimmed = line.trim();
          if (trimmed.matches("^\\d+\\s*$")) {
            // Just a number - delete the line
            state.program().remove(lineNumber);
          } else {
            // Insertion/Update - store as ProgramLine with source text
            state.program().put(lineNumber, new ProgramLine(lineNumber, statementText));
          }
        } else if (parsed instanceof AntlrParser.ParsedLine.ReplCommand(var ctx)) {
          handleReplCommand(ctx, executor, display, state);
        } else if (parsed instanceof AntlrParser.ParsedLine.Immediate(StatementContext statement)) {
          if (statement instanceof StopStmtContext) {
            break;
          }
          executor.visit(statement);
          if (statement instanceof ContStmtContext
              || statement instanceof GosubStmtContext
              || statement instanceof GotoStmtContext
              || statement instanceof RunStmtContext) {
            interpreter.resume();
          }
        }
      } catch (ReportException e) {
        state.setLastReportCode(e.reportCode());
        state.setLastReportLabel(e.lineLabel());
        display.println(e.prefix() + " " + e.getMessage());
      } catch (Exception e) {
        display.println("Error: " + e.getMessage());
      }
    }
  }

  private static void handleReplCommand(
      BazLangParser.ReplCommandContext ctx,
      BazLangExecutor executor,
      Display display,
      EvalState state) {
    if (ctx instanceof BazLangParser.DeleteCmdContext delete) {
      executor.executeDelete(delete.lineRange());
    } else if (ctx instanceof BazLangParser.EditCmdContext edit) {
      int lineNum = (int) executor.evalNum(edit.numExpr());
      if (lineNum < Limits.MIN_LINE_LABEL || lineNum > Limits.MAX_LINE_LABEL) {
        throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Line number out of range");
      }
      ProgramLine programLine = state.program().get(lineNum);
      if (programLine != null) {
        display.prefillInput(lineNum + " " + programLine.sourceText());
      } else {
        display.prefillInput(lineNum + " ");
      }
    } else if (ctx instanceof BazLangParser.RenumCmdContext renum) {
      executor.executeRenum(renum.renumArgs());
    }
  }
}
