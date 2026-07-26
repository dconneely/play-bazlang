package com.davidconneely.bazlang.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end pin tests for the AgentDebugger stdin/stdout protocol documented in
 * docs/language_debugger.md. Each test spawns the debugger in a fresh JVM, feeds it a scripted
 * session, and asserts the exact response transcript — any change to these transcripts is a
 * protocol change and must be reflected in the documentation.
 */
class AgentDebuggerProtocolTest {

  private static List<String> runSession(String script) throws IOException, InterruptedException {
    final String javaExe =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    final var pb =
        new ProcessBuilder(
            javaExe, "-cp", System.getProperty("java.class.path"), AgentDebugger.class.getName());
    pb.redirectErrorStream(false);
    final Process proc = pb.start();
    proc.getOutputStream().write(script.getBytes(StandardCharsets.UTF_8));
    proc.getOutputStream().close();
    final String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "debugger did not exit within 30s");
    return stdout.lines().toList();
  }

  @Test
  void testBreakpointConditionEvalAndAssignment() throws Exception {
    final var lines =
        runSession(
            """
            >10 LET x = 0
            >20 LET x = x + 1
            >30 GO TO 20
            >LIST
            /SPB 20:1 ?x=3
            >RUN
            ?x
            !LET x = 10
            ?x
            /CPB
            /STOP
            """);
    assertEquals(
        List.of(
            "+READY",
            "+", // >10
            "+", // >20
            "+", // >30
            "+\"10 LET x = 0\\n20 LET x = x + 1\\n30 GO TO 20\"", // >LIST
            "+", // /SPB
            "+BREAK AT 20:1", // >RUN (deferred)
            "+3", // ?x
            "+", // !LET x = 10
            "+10", // ?x
            "+", // /CPB
            "+"), // /STOP
        lines);
  }

  @Test
  void testStopReportScreenReadAndGoValidation() throws Exception {
    final var lines =
        runSession(
            """
            >10 PRINT "hi"
            >20 STOP
            >30 PRINT "end"
            >RUN
            /RSC 0 0 1 9
            !LET x = 10
            ?x
            /GO
            """);
    assertEquals(
        List.of(
            "+READY",
            "+", // >10
            "+", // >20
            "+", // >30
            "+STOP 9 STOP statement, 20:1", // >RUN (deferred)
            "+\"hi{8}\\n{10}\"", // /RSC
            "+", // !LET
            "+10", // ?x
            "-/GO is only valid when paused at a breakpoint; use >RUN"),
        lines);
  }

  @Test
  void testErrorResponsesAndUnknownCommands() throws Exception {
    final var lines =
        runSession(
            """
            bogus
            /XYZ
            >XYZ
            /SPB nonsense
            >RUN
            """);
    assertEquals(
        List.of(
            "+READY",
            "-UNKNOWN COMMAND. Commands start with /, ?, !, or >",
            "-UNKNOWN / COMMAND. Allowed: /GO, /STOP, /SPB, /S1B, /CPB, /RSC, /PIQ, /SSD",
            "-UNKNOWN REPL COMMAND."
                + " Allowed: >n [stmt], >NEW, >LOAD \"path\", >LIST, >RUN, >GOTO n",
            "-Invalid condition — expected CSC \"<text>\","
                + " ELAPSE <ms>, ?<expression>, or EVERY <n>",
            "-no programme loaded; use >n stmt or >LOAD \"path\", then >RUN"),
        lines);
  }
}
