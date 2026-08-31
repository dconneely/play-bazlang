package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportExceptionTest {

  @Test
  void testExceptionFormat() {
    final var ex = new ReportException(ReportCode.RETURN_WITHOUT_GOSUB, 40, 1, "Test error");
    assertTrue(ex.format().contains("7"));
    assertTrue(ex.getMessage().contains("Test error"));
  }

  @Test
  void testExceptionGetters() {
    final var ex = new ReportException(ReportCode.SUBSCRIPT_WRONG, 100, 1, "Error");
    assertEquals(ReportCode.SUBSCRIPT_WRONG, ex.reportCode());
    assertEquals(100, ex.lineLabel());
  }
}
