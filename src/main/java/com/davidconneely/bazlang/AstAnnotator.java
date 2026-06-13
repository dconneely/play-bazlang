package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.math.BigInteger;

public class AstAnnotator extends BazLangBaseVisitor<Void> {
  private final int lineNumber;

  public AstAnnotator(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  @Override
  public Void visitNumLiteralExpr(NumLiteralExprContext ctx) {
    ctx.cachedNum = Double.parseDouble(ctx.NUM_LITERAL().getText());
    return super.visitNumLiteralExpr(ctx);
  }

  @Override
  public Void visitBinLiteralExpr(BinLiteralExprContext ctx) {
    String digits = ctx.BIN_LITERAL().getText().substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw new ReportException(
          ReportCode.NUMBER_TOO_BIG, lineNumber, "Binary literal exceeds 64 digits");
    }
    ctx.cachedNum = new BigInteger(digits, 2).doubleValue();
    return super.visitBinLiteralExpr(ctx);
  }

  @Override
  public Void visitNumVarExpr(NumVarExprContext ctx) {
    ctx.varName = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    return super.visitNumVarExpr(ctx);
  }

  @Override
  public Void visitNumArrayExpr(NumArrayExprContext ctx) {
    ctx.varName = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    return super.visitNumArrayExpr(ctx);
  }

  @Override
  public Void visitFnNumCallExpr(FnNumCallExprContext ctx) {
    ctx.varName = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    return super.visitFnNumCallExpr(ctx);
  }

  @Override
  public Void visitStrLiteralExpr(StrLiteralExprContext ctx) {
    String text = ctx.STR_LITERAL().getText();
    ctx.cachedStr = BStr.fromJavaString(text.substring(1, text.length() - 1).replace("\"\"", "\""));
    return super.visitStrLiteralExpr(ctx);
  }

  @Override
  public Void visitStrVarExpr(StrVarExprContext ctx) {
    ctx.varName = ctx.STR_IDENTIFIER().getText().toUpperCase();
    return super.visitStrVarExpr(ctx);
  }

  @Override
  public Void visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    ctx.varName = ctx.STR_IDENTIFIER().getText().toUpperCase();
    return super.visitStrSubscriptExpr(ctx);
  }

  @Override
  public Void visitFnStrCallExpr(FnStrCallExprContext ctx) {
    ctx.varName = ctx.STR_IDENTIFIER().getText().toUpperCase();
    return super.visitFnStrCallExpr(ctx);
  }

  @Override
  public Void visitNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      ctx.cachedNum = Double.parseDouble(ctx.NUM_LITERAL().getText());
    } else if (ctx.BIN_LITERAL() != null) {
      String digits = ctx.BIN_LITERAL().getText().substring(3).replaceAll("[ \t]", "");
      if (digits.length() > 64) {
        throw new ReportException(
            ReportCode.NUMBER_TOO_BIG, lineNumber, "Binary literal exceeds 64 digits");
      }
      ctx.cachedNum = new BigInteger(digits, 2).doubleValue();
    } else if (ctx.NUM_IDENTIFIER() != null) {
      ctx.varName = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    }
    return super.visitNumAtom(ctx);
  }

  @Override
  public Void visitStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      String text = ctx.STR_LITERAL().getText();
      ctx.cachedStr =
          BStr.fromJavaString(text.substring(1, text.length() - 1).replace("\"\"", "\""));
    } else if (ctx.STR_IDENTIFIER() != null) {
      ctx.varName = ctx.STR_IDENTIFIER().getText().toUpperCase();
    }
    return super.visitStrAtom(ctx);
  }
}
