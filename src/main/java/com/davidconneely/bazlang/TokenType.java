package com.davidconneely.bazlang;

import static com.davidconneely.bazlang.TokenClass.DELIM;
import static com.davidconneely.bazlang.TokenClass.FUNC;
import static com.davidconneely.bazlang.TokenClass.ID;
import static com.davidconneely.bazlang.TokenClass.KWD;
import static com.davidconneely.bazlang.TokenClass.OP;
import static com.davidconneely.bazlang.TokenClass.STMT;

public enum TokenType {
  IDENTIFIER(ID),
  NUM_LITERAL(ID),
  STR_LITERAL(ID),
  NEWLINE(DELIM),
  EOF(DELIM),
  LEFT_PAREN(DELIM),
  RIGHT_PAREN(DELIM),
  MULTIPLY(OP),
  POWER(OP),
  PLUS(OP),
  COMMA(DELIM),
  MINUS(OP),
  DIVIDE(OP),
  SEMICOLON(DELIM),
  LESS_THAN(OP),
  LESS_EQUAL(OP),
  NOT_EQUALS(OP),
  EQUALS(OP),
  GREATER_THAN(OP),
  GREATER_EQUAL(OP),
  ABS(FUNC),
  ACS(FUNC),
  AND(OP),
  ASN(FUNC),
  AT(KWD),
  ATN(FUNC),
  CHR_STR(FUNC),
  CLEAR(STMT),
  CLS(STMT),
  CODE(FUNC),
  CONT(STMT),
  COPY(STMT),
  COS(FUNC),
  DIM(STMT),
  EXP(FUNC),
  FAST(STMT),
  FOR(STMT),
  GOSUB(STMT),
  GOTO(STMT),
  IF(STMT),
  INKEY_STR(FUNC),
  INPUT(STMT),
  INT(FUNC),
  LEN(FUNC),
  LET(STMT),
  LLIST(STMT),
  LIST(STMT),
  LN(FUNC),
  LOAD(STMT),
  LPRINT(STMT),
  NEW(STMT),
  NEXT(STMT),
  NOT(OP),
  OR(OP),
  PAUSE(STMT),
  PEEK(FUNC),
  PI(FUNC),
  PLOT(STMT),
  POKE(STMT),
  PRINT(STMT),
  RAND(STMT),
  REM(STMT),
  RETURN(STMT),
  RND(FUNC),
  RUN(STMT),
  SAVE(STMT),
  SCROLL(STMT),
  SGN(FUNC),
  SIN(FUNC),
  SLOW(STMT),
  SQR(FUNC),
  STEP(KWD),
  STOP(STMT),
  STR_STR(FUNC),
  TAB(KWD),
  TAN(FUNC),
  THEN(KWD),
  TO(KWD),
  UNPLOT(STMT),
  USR(FUNC),
  VAL(FUNC);

  private final TokenClass tokenClass;

  TokenType(TokenClass tokenClass) {
    this.tokenClass = tokenClass;
  }

  public TokenClass tokenClass() {
    return tokenClass;
  }
}
