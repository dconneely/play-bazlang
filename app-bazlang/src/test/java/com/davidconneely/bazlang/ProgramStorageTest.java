package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramStorageTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  @Test
  void testSaveAndLoad(@TempDir Path tempDir) {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);

    final String source = "10 PRINT \"HELLO\"\n20 GOTO 10";
    final var originalProgram = PARSER.parseProgramLines(source);
    state.setProgram(originalProgram);

    final var saveFile = tempDir.resolve("test.bas");
    storage.save(saveFile.toString());

    // Clear state and load
    state.program().clear();
    storage.load(saveFile.toString());

    assertEquals(2, state.program().size());
    assertEquals("PRINT \"HELLO\"", state.program().get(10).sourceText());
    assertEquals("GOTO 10", state.program().get(20).sourceText());
  }

  @Test
  void testLoadResource() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);

    // This file doesn't actually exist in tests, but let's test a non-existent resource
    final var ex = assertThrows(ReportException.class, () -> storage.load("resource:/missing.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, ex.reportCode());
  }

  @Test
  void testSaveInvalidPath() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);

    // Save to an invalid path that cannot be written
    final var ex =
        assertThrows(
            ReportException.class, () -> storage.save("invalid_dir/that/doesnt/exist/test.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, ex.reportCode());
  }
}
