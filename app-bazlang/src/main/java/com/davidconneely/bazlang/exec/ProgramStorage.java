package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Handles persistence for BazLang programs: LOAD reads a source file (or classpath resource) and
 * replaces the current program; MERGE overlays a file's lines onto the current program without
 * clearing it; SAVE writes the current program to a file; VERIFY checks that a file's program text
 * matches the current program.
 */
public class ProgramStorage {
  private final EvalState state;
  private final AntlrParser parser;

  /**
   * Creates a storage backend.
   *
   * @param state the interpreter state to load into / save from.
   * @param parser the parser to use to parse loaded/merged source.
   */
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
    state.setProgram(parseSource(filename));
  }

  /**
   * Merges a BazLang program from the given filename into the current program: lines from the file
   * are added, replacing any existing lines with the same number, while all other existing lines
   * and all runtime state are left untouched (unlike LOAD, which replaces the whole program).
   *
   * @param filename the file path or {@code resource:/path/to/resource}
   * @throws ReportException if the file cannot be read or parsed
   */
  public void merge(String filename) {
    state.program().putAll(parseSource(filename));
  }

  /**
   * Verifies that the program text in the given file matches the current program exactly (line
   * numbers and source text, ignoring the immediate-mode line 0).
   *
   * @param filename the file path or {@code resource:/path/to/resource}
   * @throws ReportException with {@link ReportCode#TAPE_LOADING_ERROR} if the contents differ, or
   *     {@link ReportCode#INVALID_FILE_NAME} if the file cannot be read or parsed
   */
  public void verify(String filename) {
    final var fileProgram = parseSource(filename);
    if (!canonicalText(state.program().entrySet()).equals(canonicalText(fileProgram.entrySet()))) {
      throw codedException(ReportCode.TAPE_LOADING_ERROR, "Program does not match file");
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
      for (final var entry : state.program().entrySet()) {
        if (entry.getKey() == 0) {
          continue; // Skip immediate execution line
        }
        final var line = entry.getValue();
        writer.write(line.lineNumber() + " " + line.sourceText());
        writer.newLine();
      }
    } catch (IOException | InvalidPathException e) {
      // InvalidPathException (e.g. a ':' in an illegal position on Windows) is a RuntimeException,
      // not an IOException, but Path.of(filename) above is still inside this try's dynamic scope
      // (JLS 14.20.3 covers the try-with-resources resource specification too) - without catching
      // it here as well, a malformed filename would propagate uncaught past every caller.
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to save: " + e.getMessage());
    }
  }

  /** Reads (from file or {@code resource:} classpath) and parses a program source. */
  private NavigableMap<Integer, ProgramLine> parseSource(String filename) {
    try {
      final String source;
      if (filename.startsWith("resource:")) {
        final String resourcePath = filename.substring(9);
        try (var is = ProgramStorage.class.getResourceAsStream(resourcePath)) {
          if (is == null) {
            throw codedException(
                ReportCode.INVALID_FILE_NAME, "Resource not found: " + resourcePath);
          }
          source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
      } else {
        source = Files.readString(Path.of(filename));
      }
      return parser.parseProgramLines(source);
    } catch (IOException | InvalidPathException e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to load: " + e.getMessage());
    }
  }

  /** Canonical program text (line number + source, one per line), skipping immediate line 0. */
  private static String canonicalText(Iterable<Map.Entry<Integer, ProgramLine>> entries) {
    final var sb = new StringBuilder();
    for (final var entry : entries) {
      if (entry.getKey() == 0) {
        continue;
      }
      sb.append(entry.getKey()).append(' ').append(entry.getValue().sourceText()).append('\n');
    }
    return sb.toString();
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
