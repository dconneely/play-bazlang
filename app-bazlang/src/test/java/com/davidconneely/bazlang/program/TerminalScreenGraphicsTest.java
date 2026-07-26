package com.davidconneely.bazlang.program;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.Interpreter;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.exec.StatementExecutor;
import com.davidconneely.bazlang.io.TerminalScreen;
import com.davidconneely.repl.TerminalEngine;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class TerminalScreenGraphicsTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private static final String CUBE_SRC = loadResource("/cube_test.bas");
  private static final String TETRAHEDRON_SRC = loadResource("/tetrahedron_test.bas");
  private static final String HANGMAN_SRC = loadResource("/hangman_test.bas");
  private static final String RACER_SRC = loadResource("/racer_test.bas");

  private static String loadResource(String name) {
    try (var is = TerminalScreenGraphicsTest.class.getResourceAsStream(name)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + name);
      }
      return new String(is.readAllBytes(), UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read resource: " + name, e);
    }
  }

  static class TestTerminalEngine implements TerminalEngine {
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    private int[] keys = new int[0];
    private int keyIndex = 0;
    private boolean lastWasKey = false;

    void setKeysToRead(int... keys) {
      this.keys = keys == null ? new int[0] : keys.clone();
      this.keyIndex = 0;
      this.lastWasKey = false;
    }

    @Override
    public int getRows() {
      return 25;
    }

    @Override
    public int getColumns() {
      return 80;
    }

    @Override
    public PrintWriter writer() {
      return pw;
    }

    @Override
    public int readKey(long timeoutMs) {
      if (lastWasKey) {
        lastWasKey = false;
        return -1;
      }
      if (keyIndex < keys.length) {
        lastWasKey = true;
        return keys[keyIndex++];
      }
      return -1;
    }

    @Override
    public String readLine(String prompt, String prefill) {
      return "";
    }

    @Override
    public void setInputHeightListener(IntConsumer listener) {}

    @Override
    public void forceRedrawFromCursor() {}

    @Override
    public void onInterrupt(Runnable handler) {}

    @Override
    public void onResize(Runnable handler) {}

    @Override
    public void close() {}

    String getOutput() {
      return sw.toString();
    }
  }

  private TestTerminalEngine engine;

  private void runProgram(String source) {
    runProgram(source, new int[0]);
  }

  private void runProgram(String source, int... mockKeys) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();
    engine = new TestTerminalEngine();
    engine.setKeysToRead(mockKeys);
    TerminalScreen screen = new TerminalScreen(engine);

    StatementExecutor executor = new StatementExecutor(state, screen, screen);
    Interpreter interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    } finally {
      screen.forceFlush();
    }
  }

  @Test
  void testCubeRendering() {
    runProgram(CUBE_SRC);
    String output = engine.getOutput();
    System.out.println("--- CUBE RENDERED OUTPUT ---");
    System.out.println(output);
    System.out.println("--- END CUBE OUTPUT ---");
    boolean hasBraille = false;
    for (int i = 0; i < output.length(); i++) {
      char c = output.charAt(i);
      if (c > 0x2800 && c <= 0x28FF) {
        hasBraille = true;
        break;
      }
    }
    assertTrue(
        hasBraille,
        "Output must contain Braille characters for the cube's wireframe but was:\n" + output);
  }

  @Test
  void testHangmanRendering() {
    runProgram(HANGMAN_SRC, 'e', 'l', 'p', 'h', 'a', 'n', 't');
    String output = engine.getOutput();
    System.out.println("--- HANGMAN RENDERED OUTPUT ---");
    System.out.println(output);
    System.out.println("--- END HANGMAN OUTPUT ---");
    assertTrue(output.contains("You win!"), "Output should contain the win message");
  }

  @Test
  void testHangmanRenderingLose() {
    runProgram(HANGMAN_SRC, 'x', 'y', 'z', 'w', 'q', 'v');
    String output = engine.getOutput();
    System.out.println("--- HANGMAN LOSE RENDERED OUTPUT ---");
    System.out.println(output);
    System.out.println("--- END HANGMAN LOSE OUTPUT ---");
    assertTrue(output.contains("You died!"), "Output should contain the lose message");
  }

  @Test
  void testInverseOverRendering() {
    runProgram(
        """
        10 CLS
        20 PLOT 0, 0
        30 PLOT OVER 1; 0, 0
        """);
    String output1 = engine.getOutput();
    assertFalse(output1.contains("▖"), "Output must NOT contain quadrant block '▖' after OVER 1");

    runProgram(
        """
        10 CLS
        20 PLOT 0, 0
        30 PLOT INVERSE 1; 0, 0
        """);
    String output2 = engine.getOutput();
    assertFalse(
        output2.contains("▖"), "Output must NOT contain quadrant block '▖' after INVERSE 1");
  }

  @Test
  void testPlotInverse() {
    runProgram(
        """
            10 CLS
            20 PLOT 0, 0
            30 PLOT OVER 1; 0, 0
            40 PLOT 1, 0
            50 PLOT INVERSE 1; 1, 0
            """);
    String output = engine.getOutput();
    assertFalse(output.isEmpty());
  }

  @Test
  void testPlotRendering() {
    runProgram(
        """
        10 CLS
        20 PLOT 0, 0
        """);
    String output = engine.getOutput();
    assertTrue(
        output.contains("▖"),
        "Output must contain lower-left quadrant block '▖' but was:\n" + output);
  }

  @Test
  void testRacerPreservesColours() {
    runProgram(RACER_SRC);
    String output = engine.getOutput();
    assertTrue(output.contains("forget your glasses"), "Output should contain the crash message");
    assertTrue(
        output.contains(";215;") || output.contains(";255;"),
        "Output should contain true-colour ANSI codes representing preserved colours, but was:\n"
            + output);
  }

  @Test
  void testTetrahedronRendering() {
    runProgram(TETRAHEDRON_SRC);
    String output = engine.getOutput();
    boolean hasBraille = false;
    for (int i = 0; i < output.length(); i++) {
      char c = output.charAt(i);
      if (c > 0x2800 && c <= 0x28FF) {
        hasBraille = true;
        break;
      }
    }
    assertTrue(
        hasBraille,
        "Output must contain Braille characters for the tetrahedron's wireframe but was:\n"
            + output);
  }
}
