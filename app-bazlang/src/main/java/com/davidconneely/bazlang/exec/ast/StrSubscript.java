package com.davidconneely.bazlang.exec.ast;

import java.util.List;

/**
 * A lowered {@code strSubscript}: zero or more array indices, optionally followed by a slice.
 * Shared between string-expression subscripting ({@link StrExpr.StrSubscriptExpr}) and string
 * assignment targets (statement AST, Phase 2). Mirrors the grammar rule of the same name.
 *
 * @param indices zero or more index expressions (e.g. {@code A$(1, 2)}); empty when only a slice is
 *     present (e.g. {@code A$(TO 5)})
 * @param slice the optional trailing slice (e.g. {@code A$(1 TO 5)}, {@code A$(TO 5)}); {@code
 *     null} when the subscript is indices-only
 */
public record StrSubscript(List<NumExpr> indices, StrSlice slice) {
  /**
   * A lowered {@code strSlice}: {@code start TO end}, with either bound optionally absent (e.g.
   * {@code TO 5}, {@code 1 TO}, {@code TO}).
   *
   * @param start the slice start, or {@code null} when absent
   * @param end the slice end, or {@code null} when absent
   */
  public record StrSlice(NumExpr start, NumExpr end) {}
}
