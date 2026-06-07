package com.davidconneely.bazlang;

public enum ReportCode {
  OK('0', "OK"),
  NEXT_WITHOUT_FOR('1', "NEXT without FOR"),
  VARIABLE_NOT_FOUND('2', "Variable not found"),
  SUBSCRIPT_WRONG('3', "Subscript wrong"),
  OUT_OF_MEMORY('4', "Out of memory"),
  OUT_OF_SCREEN('5', "Out of screen"),
  NUMBER_TOO_BIG('6', "Number too big"),
  RETURN_WITHOUT_GOSUB('7', "RETURN without GOSUB"),
  END_OF_FILE('8', "End of file"),
  STOP_STATEMENT('9', "STOP statement"),
  INVALID_ARGUMENT('A', "Invalid argument"),
  INTEGER_OUT_OF_RANGE('B', "Integer out of range"),
  NONSENSE_IN_BASIC('C', "Nonsense in BASIC"),
  BREAK_CONT_REPEATS('D', "BREAK - CONT repeats"),
  OUT_OF_DATA('E', "Out of DATA"),
  INVALID_FILE_NAME('F', "Invalid file name"),
  NO_ROOM_FOR_LINE('G', "No room for line"),
  STOP_IN_INPUT('H', "STOP in INPUT"),
  FOR_WITHOUT_NEXT('I', "FOR without NEXT"),
  INVALID_COLOUR('K', "Invalid colour"),
  BREAK_INTO_PROGRAM('L', "BREAK into program"),
  RAMTOP_NO_GOOD('M', "RAMTOP no good"),
  STATEMENT_LOST('N', "Statement lost"),
  FN_WITHOUT_DEF('P', "FN without DEF"),
  PARAMETER_ERROR('Q', "Parameter error"),
  TAPE_LOADING_ERROR('R', "Tape loading error");

  private final char code;
  private final String message;

  ReportCode(char code, String message) {
    this.code = code;
    this.message = message;
  }

  public char getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
