package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ExamplesParseTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  @Test
  void testAllExamplesParse() {
    Path examplesDir = Path.of("examples");
    try (Stream<Path> paths = Files.walk(examplesDir)) {
      paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".bas"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  Map<Integer, ProgramLine> program = PARSER.parseProgramLines(content);
                  assertNotNull(program, "Failed to parse example: " + p);
                  // Trigger lazy parsing of each line to ensure ANTLR successfully parses them
                  for (ProgramLine line : program.values()) {
                    line.getFlattenedStatements(PARSER);
                  }
                } catch (Exception e) {
                  fail("Exception parsing example " + p + ": " + e.getMessage(), e);
                }
              });
    } catch (IOException e) {
      fail("Failed to read examples directory: " + e.getMessage(), e);
    }
  }
}
