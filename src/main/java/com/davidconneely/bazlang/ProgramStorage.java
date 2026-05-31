package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles persistence for BazLang programs: LOAD reads a source file (or classpath resource) and
 * replaces the current program; SAVE writes the current program to a file.
 */
public class ProgramStorage {
  private final EvalState state;
  private final AntlrParser parser;

  public ProgramStorage(EvalState state, AntlrParser parser) {
    this.state = state;
    this.parser = parser;
  }

  /**
   * Loads a BazLang program from the given filename, replacing the current program. Filenames
   * starting with {@code resource:} are loaded from the classpath.
   *
   * @param filename the file path or {@code resource:/path/to/resource}
   * @throws ReportException if the file cannot be read or parsed
   */
  public void load(String filename) {
    try {
      String source;
      if (filename.startsWith("resource:")) {
        String resourcePath = filename.substring(9);
        try (var is = ProgramStorage.class.getResourceAsStream(resourcePath)) {
          if (is == null) {
            throw new ReportException(
                ReportCode.INVALID_FILE_NAME,
                state.currentLineLabel(),
                "Resource not found: " + resourcePath);
          }
          source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
      } else {
        source = Files.readString(Path.of(filename));
      }
      state.setProgram(parser.parseProgramLines(source));
    } catch (IOException e) {
      throw new ReportException(
          ReportCode.INVALID_FILE_NAME,
          state.currentLineLabel(),
          "Failed to load: " + e.getMessage());
    }
  }

  /**
   * Saves the current program to the given filename, one line per file line.
   *
   * @param filename the file path to write
   * @throws ReportException if the file cannot be written
   */
  public void save(String filename) {
    try (var writer = Files.newBufferedWriter(Path.of(filename))) {
      for (var entry : state.program().entrySet()) {
        ProgramLine line = entry.getValue();
        writer.write(line.lineNumber() + " " + line.sourceText());
        writer.newLine();
      }
    } catch (IOException e) {
      throw new ReportException(
          ReportCode.INVALID_FILE_NAME,
          state.currentLineLabel(),
          "Failed to save: " + e.getMessage());
    }
  }
}
