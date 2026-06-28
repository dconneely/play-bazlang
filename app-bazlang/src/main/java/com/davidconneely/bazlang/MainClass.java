package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.BazLangDisplay;
import com.davidconneely.bazlang.io.StreamDisplay;
import com.davidconneely.bazlang.io.TerminalDisplay;
import com.davidconneely.repl.Repl;
import com.davidconneely.repl.jline.JLineTerminalEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  public static void main(String[] args) {
    try (var display = createDisplay()) {
      if (args.length == 0) {
        runRepl(display);
      } else if (args.length == 1) {
        runFile(args[0], display);
      } else {
        display.systemPrintln("Usage: java com.davidconneely.bazlang.MainClass [source-file]");
        System.exit(1);
      }
    }
  }

  private static BazLangDisplay createDisplay() {
    try {
      if (System.console() == null) {
        return new StreamDisplay();
      } else {
        return new TerminalDisplay(new JLineTerminalEngine());
      }
    } catch (IOException e) {
      return new StreamDisplay();
    }
  }

  private static void runFile(String sourceFile, BazLangDisplay display) {
    try {
      final String source = Files.readString(Path.of(sourceFile));
      final var program = PARSER.parseProgramLines(source);
      final var state = new EvalState();
      final var executor = new ProgramManager(state, display);
      final var interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
      display.waitForKey();
    } catch (IOException e) {
      display.systemPrintln("Error reading file: " + e.getMessage());
      System.exit(1);
    } catch (ReportException e) {
      if (e.reportCode() == ReportCode.STOP_STATEMENT) {
        display.waitForKey();
      } else {
        display.systemPrintln(e.format());
        System.exit(1);
      }
    }
  }

  private static void runRepl(BazLangDisplay display) {
    final var state = new EvalState();
    final var executor = new ProgramManager(state, display);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, display, PARSER, executor::evalNum);
    display.systemPrintln("BazLang REPL. Type 'STOP' or Ctrl+D at the prompt to exit.");
    final var handler = new BazLangReplHandler(PARSER, state, executor, editor, interpreter);
    new Repl().run(display, handler);
  }
}
