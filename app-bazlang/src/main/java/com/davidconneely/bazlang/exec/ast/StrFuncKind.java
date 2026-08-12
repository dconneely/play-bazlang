package com.davidconneely.bazlang.exec.ast;

/** One case per {@code strFunc} grammar alternative, resolved once at lowering time. */
public enum StrFuncKind {
  CHR_STR,
  INKEY_STR,
  SCREEN_STR,
  STR_STR,
  UCHR_STR,
  UINKEY_STR,
  USCREEN_STR,
  VAL_STR
}
