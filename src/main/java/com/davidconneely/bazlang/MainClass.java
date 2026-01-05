package com.davidconneely.bazlang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainClass {
  public static void main(String[] args) {
    Terminal terminal = new Terminal();
    if (args.length == 0) {
      runRepl(terminal);
    } else if (args.length == 1) {
      runFile(args[0], terminal);
    } else {
      terminal.println("Usage: java com.davidconneely.bazlang.MainClass [source-file]");
      System.exit(1);
    }
  }

  private static void runFile(String sourceFile, Terminal terminal) {
    try {
      String source = Files.readString(Path.of(sourceFile));
      Lexer lexer = new Lexer(source);
      var tokens = lexer.tokenize();
      Parser parser = new Parser(tokens);
      var program = parser.parseProgram();
      Interpreter interpreter = new Interpreter();
      interpreter.execute(program);
    } catch (IOException e) {
      terminal.println("Error reading file: " + e.getMessage());
      System.exit(1);
    } catch (ReportException e) {
      terminal.println(e.prefix() + " " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      terminal.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void runRepl(Terminal terminal) {
    MachineState state = new MachineState();
    Evaluator evaluator = new Evaluator(state, terminal);
    Executor executor = new Executor(state, evaluator, terminal);
    Interpreter interpreter = new Interpreter(state, executor);
    terminal.println("BazLang REPL. Type 'STOP' or Ctrl+C to exit.");
    while (true) {
      String line = terminal.readln("\033[7m>\033[27m ");
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
        if (significantTokens.getFirst().type() == TokenType.NUMERIC_LITERAL) {
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
          if (stmt instanceof Statement.Run
              || stmt instanceof Statement.Goto
              || stmt instanceof Statement.Gosub
              || stmt instanceof Statement.Cont) {
            interpreter.resume();
          }
        }
      } catch (ReportException e) {
        state.setLastReportCode(e.reportCode());
        state.setLastReportLabel(e.lineLabel());
        terminal.println(e.prefix() + " " + e.getMessage());
      } catch (Exception e) {
        terminal.println("Error: " + e.getMessage());
      }
    }
  }
}
