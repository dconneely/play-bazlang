package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.BazLangScreen;
import com.davidconneely.bazlang.io.StreamScreen;
import com.davidconneely.bazlang.io.TerminalScreen;
import com.davidconneely.repl.Repl;
import com.davidconneely.repl.jline.JLineTerminalEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  public static void main(String[] args) {
    try (var screen = createScreen()) {
      if (args.length == 0) {
        runRepl(screen);
      } else if (args.length == 1) {
        runFile(args[0], screen);
      } else {
        screen.systemPrintln("Usage: java com.davidconneely.bazlang.MainClass [source-file]");
        System.exit(1);
      }
    }
  }

  private static BazLangScreen createScreen() {
    try {
      if (System.console() == null) {
        return new StreamScreen();
      } else {
        return new TerminalScreen(new JLineTerminalEngine());
      }
    } catch (IOException e) {
      return new StreamScreen();
    }
  }

  private static void runFile(String sourceFile, BazLangScreen screen) {
    try {
      final String source = Files.readString(Path.of(sourceFile));
      final var program = PARSER.parseProgramLines(source);
      final var state = new EvalState();
      final var executor = new ProgramManager(state, screen);
      final var interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
      screen.waitForKey();
    } catch (IOException e) {
      screen.systemPrintln("Error reading file: " + e.getMessage());
      System.exit(1);
    } catch (ReportException e) {
      if (e.reportCode() == ReportCode.STOP_STATEMENT) {
        screen.waitForKey();
      } else {
        screen.systemPrintln(e.format());
        System.exit(1);
      }
    }
  }

  private static void runRepl(BazLangScreen screen) {
    final var state = new EvalState();
    final var executor = new ProgramManager(state, screen);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    screen.systemPrintln("BazLang REPL. Type 'STOP' or Ctrl+D at the prompt to exit.");
    final var handler =
        new BazLangReplHandler(screen, PARSER, state, executor, editor, interpreter);
    new Repl().run(screen, handler);
  }
}
