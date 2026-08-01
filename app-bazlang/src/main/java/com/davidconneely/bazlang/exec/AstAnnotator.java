package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.math.BigInteger;

public final class AstAnnotator extends BazLangBaseVisitor<Void> {
  public static final AstAnnotator INSTANCE = new AstAnnotator();

  private final ThreadLocal<Integer> currentLineNumber = new ThreadLocal<>();

  private AstAnnotator() {}

  public void annotate(org.antlr.v4.runtime.tree.ParseTree tree, int lineNumber) {
    currentLineNumber.set(lineNumber);
    try {
      visit(tree);
    } finally {
      currentLineNumber.remove();
    }
  }

  /** Parses a BIN literal token text ("BIN 1010") to its numeric value. */
  static double parseBinLiteral(String tokenText, int lineNumber) {
    final String digits = tokenText.substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw new ReportException(
          ReportCode.NUMBER_TOO_BIG, lineNumber, "Binary literal exceeds 64 digits");
    }
    return new BigInteger(digits, 2).doubleValue();
  }

  /** Unquotes a STR_LITERAL token text (strips quotes, un-doubles embedded quotes). */
  static BStr parseStrLiteral(String tokenText) {
    return BStr.fromJavaString(
        tokenText.substring(1, tokenText.length() - 1).replace("\"\"", "\""));
  }

  @Override
  public Void visitNumLiteralExpr(NumLiteralExprContext ctx) {
    ctx.cachedNum = Double.parseDouble(ctx.NUM_LITERAL().getText());
    return super.visitNumLiteralExpr(ctx);
  }

  @Override
  public Void visitBinLiteralExpr(BinLiteralExprContext ctx) {
    ctx.cachedNum = parseBinLiteral(ctx.BIN_LITERAL().getText(), currentLineNumber.get());
    return super.visitBinLiteralExpr(ctx);
  }

  @Override
  public Void visitStrLiteralExpr(StrLiteralExprContext ctx) {
    ctx.cachedStr = parseStrLiteral(ctx.STR_LITERAL().getText());
    return super.visitStrLiteralExpr(ctx);
  }

  @Override
  public Void visitNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      ctx.cachedNum = Double.parseDouble(ctx.NUM_LITERAL().getText());
    } else if (ctx.BIN_LITERAL() != null) {
      ctx.cachedNum = parseBinLiteral(ctx.BIN_LITERAL().getText(), currentLineNumber.get());
    }
    return super.visitNumAtom(ctx);
  }

  @Override
  public Void visitStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      ctx.cachedStr = parseStrLiteral(ctx.STR_LITERAL().getText());
    }
    return super.visitStrAtom(ctx);
  }
}
