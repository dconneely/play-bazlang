package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.EvalState;
import com.davidconneely.bazlang.Interpreter;
import com.davidconneely.bazlang.ProgramLine;
import com.davidconneely.bazlang.ProgramManager;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.io.TerminalDisplay;
import com.davidconneely.repl.TerminalEngine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class TerminalDisplayGraphicsTest {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  private static final String CUBE_SRC = loadResource("/cube_test.bas");
  private static final String TETRAHEDRON_SRC = loadResource("/tetrahedron_test.bas");
  private static final String HANGMAN_SRC = loadResource("/hangman_test.bas");
  private static final String RACER_SRC = loadResource("/racer_test.bas");

  private static String loadResource(String name) {
    try (java.io.InputStream is = TerminalDisplayGraphicsTest.class.getResourceAsStream(name)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + name);
      }
      return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException("Failed to read resource: " + name, e);
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
      return 24;
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
  private TerminalDisplay display;

  private void runProgram(String source) {
    runProgram(source, new int[0]);
  }

  private void runProgram(String source, int... mockKeys) {
    Map<Integer, ProgramLine> program = PARSER.parseProgramLines(source);
    EvalState state = new EvalState();
    engine = new TestTerminalEngine();
    engine.setKeysToRead(mockKeys);
    display = new TerminalDisplay(engine);

    ProgramManager executor = new ProgramManager(state, display);
    Interpreter interpreter = new Interpreter(state, executor);
    try {
      interpreter.execute(program);
    } catch (ReportException e) {
      if (e.reportCode() != ReportCode.STOP_STATEMENT) {
        throw e;
      }
    } finally {
      display.forceFlush();
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
    // Run the game with keyboard inputs that win:
    // 'e', 'l', 'p', 'h', 'a', 'n', 't'
    runProgram(HANGMAN_SRC, 'e', 'l', 'p', 'h', 'a', 'n', 't');
    String output = engine.getOutput();
    System.out.println("--- HANGMAN RENDERED OUTPUT ---");
    System.out.println(output);
    System.out.println("--- END HANGMAN OUTPUT ---");
    assertTrue(output.contains("You win!"), "Output should contain the win message");
  }

  @Test
  void testHangmanRenderingLose() {
    // Run the game with keyboard inputs that lose:
    // 'x', 'y', 'z', 'w', 'q', 'v' (6 misses)
    runProgram(HANGMAN_SRC, 'x', 'y', 'z', 'w', 'q', 'v');
    String output = engine.getOutput();
    System.out.println("--- HANGMAN LOSE RENDERED OUTPUT ---");
    System.out.println(output);
    System.out.println("--- END HANGMAN LOSE OUTPUT ---");
    assertTrue(output.contains("You died!"), "Output should contain the lose message");
  }

  @Test
  void testInverseOverRendering() {
    runProgram("10 CLS\n" + "20 PLOT 0, 0\n" + "30 PLOT OVER 1; 0, 0\n");
    String output1 = engine.getOutput();
    assertTrue(
        !output1.contains("\u2596"), "Output must NOT contain quadrant block '▖' after OVER 1");

    runProgram("10 CLS\n" + "20 PLOT 0, 0\n" + "30 PLOT INVERSE 1; 0, 0\n");
    String output2 = engine.getOutput();
    assertTrue(
        !output2.contains("\u2596"), "Output must NOT contain quadrant block '▖' after INVERSE 1");
  }

  @Test
  void testPlotInverse() {
    runProgram(
        "10 CLS\n"
            + "20 PLOT 0, 0\n"
            + "30 PLOT OVER 1; 0, 0\n"
            + "40 PLOT 1, 0\n"
            + "50 PLOT INVERSE 1; 1, 0\n");
    String output = engine.getOutput();
    assertTrue(output.length() > 0);
  }

  @Test
  void testPlotRendering() {
    runProgram("10 CLS\n" + "20 PLOT 0, 0\n");
    String output = engine.getOutput();
    assertTrue(
        output.contains("\u2596"),
        "Output must contain lower-left quadrant block '▖' but was:\n" + output);
  }

  @Test
  void testRacerPreservesColors() {
    runProgram(RACER_SRC);
    String output = engine.getOutput();
    assertTrue(output.contains("forget your glasses"), "Output should contain the crash message");
    // Verify that the message retains the green grass color (RGB true-color containing 215 or 255)
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
