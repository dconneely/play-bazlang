package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.file.Files;
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

  @Test
  void testSaveRejectsASyntacticallyInvalidPathCleanly() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);
    state.setProgram(PARSER.parseProgramLines("10 PRINT \"HELLO\""));

    // A ':' anywhere but a Windows drive prefix makes Path.of(...) throw InvalidPathException —
    // a RuntimeException, not an IOException, so it must be caught explicitly or it propagates
    // uncaught past every caller instead of a clean report.
    final var ex = assertThrows(ReportException.class, () -> storage.save("bad:name.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, ex.reportCode());
  }

  @Test
  void testLoadRejectsASyntacticallyInvalidPathCleanly() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);

    final var ex = assertThrows(ReportException.class, () -> storage.load("bad:name.bas"));
    assertEquals(ReportCode.INVALID_FILE_NAME, ex.reportCode());
  }

  @Test
  void testMergeOverlaysWithoutClearing(@TempDir Path tempDir) throws IOException {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);
    state.setProgram(PARSER.parseProgramLines("10 PRINT \"A\"\n20 PRINT \"B\""));

    final var mergeFile = tempDir.resolve("merge.bas");
    Files.writeString(mergeFile, "20 PRINT \"NEW\"\n30 PRINT \"C\"\n");
    storage.merge(mergeFile.toString());

    assertEquals(3, state.program().size());
    assertEquals("PRINT \"A\"", state.program().get(10).sourceText()); // existing line kept
    assertEquals("PRINT \"NEW\"", state.program().get(20).sourceText()); // same-numbered replaced
    assertEquals("PRINT \"C\"", state.program().get(30).sourceText()); // new line added
  }

  @Test
  void testVerifyMatches(@TempDir Path tempDir) {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);
    state.setProgram(PARSER.parseProgramLines("10 PRINT \"HELLO\"\n20 GOTO 10"));

    final var file = tempDir.resolve("verify.bas");
    storage.save(file.toString());
    assertDoesNotThrow(() -> storage.verify(file.toString()));
  }

  @Test
  void testVerifyMismatchThrowsTapeError(@TempDir Path tempDir) throws IOException {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);
    state.setProgram(PARSER.parseProgramLines("10 PRINT \"HELLO\""));

    final var file = tempDir.resolve("verify.bas");
    Files.writeString(file, "10 PRINT \"DIFFERENT\"\n");
    final var ex = assertThrows(ReportException.class, () -> storage.verify(file.toString()));
    assertEquals(ReportCode.TAPE_LOADING_ERROR, ex.reportCode());
  }
}
