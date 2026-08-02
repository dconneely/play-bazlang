package com.davidconneely.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplTest {

  @Test
  void testLoopExitsOnNull() {
    List<String> handled = new ArrayList<>();
    ReplReader reader =
        new ReplReader() {
          int count = 0;

          @Override
          public String readReplInput() {
            if (count++ == 0) {
              return "hello";
            }
            return null; // exit
          }
        };

    ReplHandler handler =
        line -> {
          handled.add(line);
          return true; // continue
        };

    Repl.loop(reader, handler);
    assertEquals(1, handled.size());
    assertEquals("hello", handled.get(0));
  }

  @Test
  void testLoopExitsOnHandlerFalse() {
    List<String> handled = new ArrayList<>();
    ReplReader reader = () -> "command";

    ReplHandler handler =
        line -> {
          handled.add(line);
          return false; // exit immediately
        };

    Repl.loop(reader, handler);
    assertEquals(1, handled.size());
    assertEquals("command", handled.get(0));
  }

  @Test
  void testLoopHandlesBreakException() {
    List<String> handled = new ArrayList<>();
    ReplReader reader =
        new ReplReader() {
          int count = 0;

          @Override
          public String readReplInput() {
            count++;
            if (count == 1) {
              throw new BreakException();
            }
            if (count == 2) {
              return "second";
            }
            return null;
          }
        };

    ReplHandler handler =
        line -> {
          handled.add(line);
          return true;
        };

    Repl.loop(reader, handler);
    assertEquals(1, handled.size());
    assertEquals("second", handled.get(0));
  }

  @Test
  void testLoopIgnoresBlankLines() {
    List<String> handled = new ArrayList<>();
    ReplReader reader =
        new ReplReader() {
          int count = 0;

          @Override
          public String readReplInput() {
            count++;
            if (count == 1) {
              return "  \t ";
            }
            if (count == 2) {
              return "real";
            }
            return null;
          }
        };

    ReplHandler handler =
        line -> {
          handled.add(line);
          return true;
        };

    Repl.loop(reader, handler);
    assertEquals(1, handled.size());
    assertEquals("real", handled.get(0));
  }
}
