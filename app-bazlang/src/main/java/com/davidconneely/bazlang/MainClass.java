package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.edit.ProgramEditor;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.StreamScreen;
import com.davidconneely.bazlang.io.TerminalScreen;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import com.davidconneely.repl.Repl;
import com.davidconneely.repl.jline.JLineTerminalEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  public static void main(String[] args) {
    int exitCode = 0;
    if (System.console() != null) {
      try (var term = new TerminalScreen(new JLineTerminalEngine())) {
        exitCode = dispatch(args, term, term);
      } catch (IOException ignored) {
      }
    } else {
      try (var screen = new StreamScreen()) {
        exitCode = dispatch(args, screen, screen);
      }
    }
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  private static int dispatch(String[] args, VirtualScreen screen, VirtualInput input) {
    if (args.length == 0) {
      return runRepl(screen, input);
    } else if (args.length == 1) {
      return runFile(args[0], screen, input);
    } else {
      screen.systemPrintln("Usage: java com.davidconneely.bazlang.MainClass [source-file]");
      return 1;
    }
  }

  private static int runFile(String sourceFile, VirtualScreen screen, VirtualInput input) {
    try {
      final String source = Files.readString(Path.of(sourceFile));
      final var program = PARSER.parseProgramLines(source);
      final var state = new EvalState();
      final var executor = new StatementExecutor(state, screen, input);
      final var interpreter = new Interpreter(state, executor);
      interpreter.execute(program);
      screen.waitForKey();
      return 0;
    } catch (IOException e) {
      screen.systemPrintln("Error reading file: " + e.getMessage());
      return 1;
    } catch (ReportException e) {
      if (e.reportCode() == ReportCode.STOP_STATEMENT) {
        screen.waitForKey();
        return 0;
      } else {
        screen.systemPrintln(e.format());
        return 1;
      }
    }
  }

  private static int runRepl(VirtualScreen screen, VirtualInput input) {
    final var state = new EvalState();
    final var executor = new StatementExecutor(state, screen, input);
    final var interpreter = new Interpreter(state, executor);
    final var editor = new ProgramEditor(state, screen, PARSER, executor::evalNum);
    screen.systemPrintln("BazLang REPL. Type 'STOP' or Ctrl+D at the prompt to exit.");
    final var handler =
        new InterpreterReplHandler(screen, input, PARSER, state, executor, editor, interpreter);
    Repl.loop(input, handler);
    return 0;
  }
}
