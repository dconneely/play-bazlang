package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramStorageTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  @Test
  void testSaveAndLoad(@TempDir Path tempDir) throws IOException {
    EvalState state = new EvalState();
    ProgramStorage storage = new ProgramStorage(state, PARSER);

    String source = "10 PRINT \"HELLO\"\n20 GOTO 10";
    Map<Integer, ProgramLine> originalProgram = PARSER.parseProgramLines(source);
    state.setProgram(originalProgram);

    Path saveFile = tempDir.resolve("test.bas");
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
    EvalState state = new EvalState();
    ProgramStorage storage = new ProgramStorage(state, PARSER);

    // This file doesn't actually exist in tests, but let's test a non-existent resource
    ReportException e =
        assertThrows(ReportException.class, () -> storage.load("resource:/missing.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, e.reportCode());
  }

  @Test
  void testSaveInvalidPath() {
    EvalState state = new EvalState();
    ProgramStorage storage = new ProgramStorage(state, PARSER);

    // Save to an invalid path that cannot be written
    ReportException e =
        assertThrows(
            ReportException.class, () -> storage.save("invalid_dir/that/doesnt/exist/test.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, e.reportCode());
  }
}
