package com.davidconneely.bazlang;

/**
 * Error codes and messages. Note: This list mirrors the original ZX Spectrum report table exactly,
 * which is why there is intentionally no code 'J'.
 */
public enum ReportCode {
  /** No error. */
  OK('0', "OK"),
  /** {@code NEXT} referenced a loop variable with no matching active {@code FOR}. */
  NEXT_WITHOUT_FOR('1', "NEXT without FOR"),
  /** A variable was read before ever being assigned. */
  VARIABLE_NOT_FOUND('2', "Variable not found"),
  /** An array index or string slice was out of bounds. */
  SUBSCRIPT_WRONG('3', "Subscript wrong"),
  /**
   * A resource limit was exceeded: deeply nested expressions, an oversized array, or recursive
   * {@code DEF FN}.
   */
  OUT_OF_MEMORY('4', "Out of memory"),
  /** {@code AT}/{@code PLOT}/... addressed a row or column outside the screen. */
  OUT_OF_SCREEN('5', "Out of screen"),
  /** A numeric result overflowed. */
  NUMBER_TOO_BIG('6', "Number too big"),
  /** {@code RETURN} executed with no matching {@code GOSUB} on the call stack. */
  RETURN_WITHOUT_GOSUB('7', "RETURN without GOSUB"),
  /** Reached the end of a file being read. */
  END_OF_FILE('8', "End of file"),
  /** A {@code STOP} statement halted the program deliberately. */
  STOP_STATEMENT('9', "STOP statement"),
  /** A built-in function or statement received an invalid argument. */
  INVALID_ARGUMENT('A', "Invalid argument"),
  /** An integer argument (e.g. a line number) was outside its valid range. */
  INTEGER_OUT_OF_RANGE('B', "Integer out of range"),
  /** A syntax error. */
  NONSENSE_IN_BASIC('C', "Nonsense in BASIC"),
  /** Reserved for ZX Spectrum report-table fidelity; not currently raised by this interpreter. */
  BREAK_CONT_REPEATS('D', "BREAK - CONT repeats"),
  /** {@code READ} ran out of {@code DATA} values to consume. */
  OUT_OF_DATA('E', "Out of DATA"),
  /** {@code LOAD}/{@code SAVE}/... was given an invalid file name. */
  INVALID_FILE_NAME('F', "Invalid file name"),
  /** Reserved for ZX Spectrum report-table fidelity; not currently raised by this interpreter. */
  NO_ROOM_FOR_LINE('G', "No room for line"),
  /** The user broke out of an {@code INPUT} prompt (typed {@code STOP} or pressed Ctrl+C). */
  STOP_IN_INPUT('H', "STOP in INPUT"),
  /** A {@code FOR} loop has no matching {@code NEXT}. */
  FOR_WITHOUT_NEXT('I', "FOR without NEXT"),
  /** Reserved for ZX Spectrum report-table fidelity; not currently raised by this interpreter. */
  INVALID_COLOUR('K', "Invalid colour"),
  /** The user pressed BREAK (Ctrl+C) while the program was running. */
  BREAK_INTO_PROGRAM('L', "BREAK into program"),
  /** Reserved for ZX Spectrum report-table fidelity; not currently raised by this interpreter. */
  RAMTOP_NO_GOOD('M', "RAMTOP no good"),
  /** Internal error: the program storage no longer contains an expected line or statement. */
  STATEMENT_LOST('N', "Statement lost"),
  /** A user-defined function ({@code FN}) was called before its {@code DEF FN} was executed. */
  FN_WITHOUT_DEF('P', "FN without DEF"),
  /** A {@code DEF FN} call passed the wrong number or type of arguments. */
  PARAMETER_ERROR('Q', "Parameter error"),
  /** {@code VERIFY} found the file's contents didn't match the current program. */
  TAPE_LOADING_ERROR('R', "Tape loading error");

  private final char code;
  private final String message;

  ReportCode(char code, String message) {
    this.code = code;
    this.message = message;
  }

  /**
   * The single-character ZX Spectrum report code.
   *
   * @return the code character.
   */
  public char getCode() {
    return code;
  }

  /**
   * The human-readable report message.
   *
   * @return the message.
   */
  public String getMessage() {
    return message;
  }
}
