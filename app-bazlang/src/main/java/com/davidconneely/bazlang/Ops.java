package com.davidconneely.bazlang;

/** Cached operator codes for expression contexts; 0 means not yet resolved. */
public final class Ops {
  public static final int MUL = 1;
  public static final int DIV = 2;
  public static final int ADD = 3;
  public static final int SUB = 4;
  public static final int EQ = 5;
  public static final int NE = 6;
  public static final int LT = 7;
  public static final int LE = 8;
  public static final int GT = 9;
  public static final int GE = 10;

  private Ops() {}

  /** Maps an operator token text to its code. */
  public static int fromText(String op) {
    return switch (op) {
      case "*" -> MUL;
      case "/" -> DIV;
      case "+" -> ADD;
      case "-" -> SUB;
      case "=" -> EQ;
      case "<>" -> NE;
      case "<" -> LT;
      case "<=" -> LE;
      case ">" -> GT;
      case ">=" -> GE;
      default -> 0;
    };
  }
}
