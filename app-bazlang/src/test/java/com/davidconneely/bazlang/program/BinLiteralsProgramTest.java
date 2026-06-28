package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidconneely.bazlang.ReportException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Tests exercising BIN literal parsing and constraints. */
class BinLiteralsProgramTest extends BaseProgramTest {

  @Test
  void testBinLiterals() {
    final var state =
        runProgram(
            """
                10 LET A = BIN 1010
                20 LET B = BIN 1 0 1 0
                30 LET C = BIN 1111111111111111111111111111111111111111111111111111111111111111
                """);
    assertEquals(10.0, state.numVar("A"));
    assertEquals(10.0, state.numVar("B"));
    assertEquals(
        new BigInteger("1111111111111111111111111111111111111111111111111111111111111111", 2)
            .doubleValue(),
        state.numVar("C"));

    // Exceeds 64 digits
    assertThrows(
        ReportException.class,
        () ->
            runProgram(
                "10 LET A = BIN 1111111111111111111111111"
                    + "1111111111111111111111111111111111111111"));
  }
}
