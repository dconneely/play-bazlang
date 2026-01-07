package com.davidconneely.bazlang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  public static void main(String[] args) {
    Display display = new Display();
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
      Lexer lexer = new Lexer(source);
      var tokens = lexer.tokenize();
      Parser parser = new Parser(tokens);
      var program = parser.parseProgram();
      Interpreter interpreter = new Interpreter();
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
    Evaluator evaluator = new Evaluator(state, display);
    Executor executor = new Executor(state, evaluator, display);
    Interpreter interpreter = new Interpreter(state, executor);
    display.println("BazLang REPL. Type 'STOP' or Ctrl+C to exit.");
    while (true) {
      String line = display.readln("\033[7m>\033[27m ");
      if (line == null) {
        break; // EOF
      }
      if (line.isBlank()) {
        continue;
      }
      try {
        Lexer lexer = new Lexer(line);
        var tokens = lexer.tokenize();
        // Filter out NEWLINE/EOF for checking token count
        var significantTokens =
            tokens.stream()
                .filter(t -> t.type() != TokenType.NEWLINE && t.type() != TokenType.EOF)
                .toList();
        if (significantTokens.isEmpty()) {
          continue;
        }
        if (significantTokens.getFirst().type() == TokenType.NUM_LITERAL) {
          // Line editing
          int label = Integer.parseInt(significantTokens.getFirst().rep());
          if (significantTokens.size() == 1) {
            // Deletion
            state.program().remove(label);
          } else {
            // Insertion/Update
            Parser parser = new Parser(tokens);
            var newLines = parser.parseProgram();
            state.program().putAll(newLines);
          }
        } else {
          // Immediate execution
          Parser parser = new Parser(tokens);
          Statement stmt = parser.parseReplStatement();
          if (stmt instanceof Statement.Stop) {
            break;
          }
          executor.executeStatement(stmt);
          if (stmt instanceof Statement.Cont
              || stmt instanceof Statement.Gosub
              || stmt instanceof Statement.Goto
              || stmt instanceof Statement.Run) {
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
}
