package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExampleParseTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  @Test
  void testAllExamplesParse() {
    final var exampleDir = Path.of("app-bazlang", "src", "example", "bas");
    try (var paths = Files.walk(exampleDir)) {
      paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".bas"))
          .forEach(
              p -> {
                try {
                  final String content = Files.readString(p);
                  final var program = PARSER.parseProgramLines(content);
                  assertNotNull(program, "Failed to parse example: " + p);
                  // Trigger lazy parsing of each line to ensure ANTLR successfully parses them
                  for (final var line : program.values()) {
                    line.getFlattenedStatements(PARSER);
                  }
                } catch (IOException | ReportException e) {
                  fail("Exception parsing example " + p + ": " + e.getMessage(), e);
                }
              });
    } catch (IOException e) {
      fail("Failed to read example directory: " + e.getMessage(), e);
    }
  }
}
