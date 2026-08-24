package com.davidconneely.bazlang.exec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramStorageTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  /**
   * A path that is syntactically invalid on <em>every</em> platform. A POSIX pathname cannot
   * contain NUL (they are NUL-terminated C strings) and Windows rejects it too, so {@code Path.of}
   * throws {@link java.nio.file.InvalidPathException} everywhere. Built by concatenating {@code
   * (char) 0} rather than writing an escape, so no NUL byte is ever stored in this source file.
   *
   * <p>This deliberately does not use {@code "bad:name.bas"}, which is invalid only on Windows: on
   * Linux and macOS {@code :} is an ordinary filename character, so {@code save} silently succeeded
   * (leaving a stray file behind in the working directory) and {@code load} merely hit
   * file-not-found - passing for the wrong reason. That version passed on Windows and failed CI on
   * the other two platforms.
   */
  private static final String INVALID_PATH_ON_EVERY_PLATFORM = "bad" + (char) 0 + "name.bas";

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
  void theInvalidPathFixtureIsGenuinelyInvalidOnThisPlatform() {
    // Guards the premise of the two tests below, which is the part that cannot be checked from a
    // single machine: they only exercise the InvalidPathException route if Path.of actually
    // rejects this string on whichever platform is running. A previous fixture was rejected on
    // Windows but accepted on Linux and macOS, so the tests passed locally while silently testing
    // nothing (save) or the wrong thing (load) in CI. If this ever fails, fix the fixture -- the
    // production code is not the thing at fault.
    assertThrows(InvalidPathException.class, () -> Path.of(INVALID_PATH_ON_EVERY_PLATFORM));
  }

  @Test
  void testSaveRejectsASyntacticallyInvalidPathCleanly() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);
    state.setProgram(PARSER.parseProgramLines("10 PRINT \"HELLO\""));

    // Path.of(...) throws InvalidPathException for a syntactically invalid path - a
    // RuntimeException, not an IOException, so it must be caught explicitly or it propagates
    // uncaught past every caller instead of producing a clean report.
    final var ex =
        assertThrows(ReportException.class, () -> storage.save(INVALID_PATH_ON_EVERY_PLATFORM));
    assertEquals(ReportCode.INVALID_FILE_NAME, ex.reportCode());
  }

  @Test
  void testLoadRejectsASyntacticallyInvalidPathCleanly() {
    final var state = new EvalState();
    final var storage = new ProgramStorage(state, PARSER);

    // Must be an invalid *path*, not merely a missing file: a nonexistent name would take the
    // IOException route instead and leave the InvalidPathException handling untested.
    final var ex =
        assertThrows(ReportException.class, () -> storage.load(INVALID_PATH_ON_EVERY_PLATFORM));
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
