package com.davidconneely.bazlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReportCodeTest {

  @Test
  void testReportCodes() {
    assertEquals('1', ReportCode.NEXT_WITHOUT_FOR.getCode());
    assertEquals('7', ReportCode.RETURN_WITHOUT_GOSUB.getCode());
    assertEquals('C', ReportCode.NONSENSE_IN_BASIC.getCode());
  }

  @Test
  void testErrorMessages() {
    assertEquals("NEXT without FOR", ReportCode.NEXT_WITHOUT_FOR.getMessage());
    assertEquals("RETURN without GOSUB", ReportCode.RETURN_WITHOUT_GOSUB.getMessage());
  }
}
