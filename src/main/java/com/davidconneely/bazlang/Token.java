package com.davidconneely.bazlang;

public record Token(TokenType type, String rep) {
  @Override
  public String toString() {
    return String.format("%s(%s)", type, rep);
  }
}
