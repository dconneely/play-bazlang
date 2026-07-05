package com.davidconneely.bazlang.program;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.List;

/**
 * Developer scaffold for running BazLang BASIC games / programs, feeding keys, and inspecting the
 * resulting 2D CellBuffer grid (including quadrant characters). Useful for agents and developers to
 * debug behavior in a controlled mock environment.
 */
public final class BazLangDevelopmentScaffold {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private record RunResult(EvalState state, MockScreen screen) {}

  /** Prints the contents of the 25x80 cell buffer to stdout with frame borders. */
  static void printScreen(MockScreen screen) {
    System.out.println("┌" + "─".repeat(80) + "┐");
    for (int r = 0; r < 25; r++) {
      StringBuilder sb = new StringBuilder();
      sb.append('│');
      for (int c = 0; c < 80; c++) {
        int cp = screen.getScreenCodepoint(r, c);
        sb.appendCodePoint(cp);
      }
      sb.append('│');
      System.out.println(sb.toString());
    }
    System.out.println("└" + "─".repeat(80) + "┘");
  }

  /**
   * Runs a program with loop-safety protection: if the program is stuck in a busy-wait loop polling
   * for input (more than 200 consecutive empty polls), it will automatically force a 'n' key and
   * stop execution to prevent hanging.
   */
  private static RunResult runWithSafety(String source, List<BStr> inkey, List<BStr> uinkey) {
    final var program = PARSER.parseProgramLines(source);
    final var state = new EvalState();

    final var screen =
        new MockScreen(List.of()) {
          private int emptyPolls = 0;

          @Override
          public BStr inkey() {
            BStr val = super.inkey();
            if (val == BStr.EMPTY) {
              emptyPolls++;
              if (emptyPolls > 200) {
                state.setRunning(false);
                return BStr.fromJavaString("n");
              }
            } else {
              emptyPolls = 0;
            }
            return val;
          }

          @Override
          public BStr uinkey() {
            BStr val = super.uinkey();
            if (val == BStr.EMPTY) {
              emptyPolls++;
              if (emptyPolls > 200) {
                state.setRunning(false);
                return BStr.fromJavaString("n");
              }
            } else {
              emptyPolls = 0;
            }
            return val;
          }
        };

    for (var k : inkey) {
      screen.queueInkey(k);
    }
    for (var k : uinkey) {
      screen.queueUinkey(k);
    }

    final var executor = new ProgramManager(state, screen);
    final var interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        System.err.println("FAILED running program: " + e.format());
        System.err.println("=== SCREEN AT EXCEPTION ===");
        printScreen(screen);
        throw e;
      }
    }
    return new RunResult(state, screen);
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(
          "Usage: java BazLangDevelopmentScaffold " + "<file.bas | program_name> [queued_keys...]");
      return;
    }

    String inputPath = args[0];
    String source;
    java.nio.file.Path p = java.nio.file.Path.of(inputPath);

    if (!java.nio.file.Files.exists(p)) {
      // Try resolving as program_name inside default paths
      String programName = inputPath.endsWith(".bas") ? inputPath : inputPath + ".bas";
      p = java.nio.file.Path.of("src", "example", "bas", programName);
      if (!java.nio.file.Files.exists(p)) {
        p = java.nio.file.Path.of("app-bazlang", "src", "example", "bas", programName);
      }
    }

    try {
      source = java.nio.file.Files.readString(p);
    } catch (java.io.IOException e) {
      System.err.printf(
          "Could not load BASIC file from path '%s': %s%n", p.toAbsolutePath(), e.getMessage());
      return;
    }

    java.nio.file.Path fileNamePath = p.getFileName();
    String fileName = fileNamePath != null ? fileNamePath.toString() : "PROGRAM";

    System.out.printf("Running scaffold for program: %s%n", fileName);

    // Collect any remaining arguments as queued inkey inputs
    List<BStr> inkeys = new java.util.ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      inkeys.add(BStr.fromJavaString(args[i]));
    }

    var run = runWithSafety(source, inkeys, List.of());

    System.out.printf("=== %s CELL BUFFER GRAPHICS ===%n", fileName.toUpperCase());
    printScreen(run.screen());
  }
}
