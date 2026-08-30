package com.davidconneely.bazlang.exec.ast;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * Lowers ANTLR expression parse trees ({@code numExpr}/{@code numAtom}/{@code strExpr}/{@code
 * strTerm}/{@code strAtom}) to the typed {@link NumExpr}/{@link StrExpr} AST. Pure functions: no
 * {@code EvalState}, so lowering never resolves a variable/array reference - those stay lazily
 * resolved on first evaluation (see {@link NumExpr} class Javadoc), which keeps this class usable
 * both ahead-of-time (at {@code ProgramLine} parse time) and for one-off runtime parses ({@code
 * VAL}, {@code INPUT}) without needing an {@code EvalState} to exist yet.
 *
 * <p>{@code lineNumber} is threaded through as a plain parameter (not a {@code ThreadLocal}, as the
 * retired {@code AstAnnotator} class did) purely so a {@code BIN} literal that overflows 64 digits
 * can report the line it came from; it is never used to resolve variables.
 */
public final class AstLowering {
  private AstLowering() {}

  /** Lowers either half of the grammar's {@code expression} rule ({@code numExpr | strExpr}). */
  public static Expr lowerExpression(ExpressionContext ctx, int lineNumber) {
    if (ctx.numExpr() != null) {
      return lowerNum(ctx.numExpr(), lineNumber);
    }
    return lowerStr(ctx.strExpr(), lineNumber);
  }

  // ===== Numeric expressions =====

  public static NumExpr lowerNum(NumExprContext ctx, int lineNumber) {
    return switch (ctx) {
      case NumLiteralExprContext c ->
          new NumExpr.NumLiteral(parseNumLiteral(c.NUM_LITERAL().getText()));
      case BinLiteralExprContext c ->
          new NumExpr.NumLiteral(parseBinLiteral(c.BIN_LITERAL().getText(), lineNumber));
      case NumVarExprContext c -> new NumExpr.NumVarExpr(upper(c.NUM_IDENTIFIER().getText()));
      case NumArrayExprContext c ->
          new NumExpr.NumArrayExpr(
              upper(c.NUM_IDENTIFIER().getText()), lowerNumList(c.numExpr(), lineNumber));
      case NumParenExprContext c -> lowerNum(c.numExpr(), lineNumber);
      case NumFuncCallExprContext c -> lowerNumFunc(c.numFunc(), lineNumber);
      case FnNumCallExprContext c ->
          new NumExpr.FnNumCall(
              upper(c.NUM_IDENTIFIER().getText()), lowerExpressionList(c.args, lineNumber));
      case NumPowerExprContext c ->
          new NumExpr.NumBinaryOp(
              Op.POW, lowerNum(c.numExpr(0), lineNumber), lowerNum(c.numExpr(1), lineNumber));
      case NumUnaryMinusExprContext c ->
          new NumExpr.NumUnaryMinus(lowerNum(c.numExpr(), lineNumber));
      case NumMulDivExprContext c ->
          new NumExpr.NumBinaryOp(
              mulDivOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber));
      case NumAddSubExprContext c ->
          new NumExpr.NumBinaryOp(
              addSubOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber));
      case NumCompExprContext c ->
          new NumExpr.NumCompare(
              compOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber));
      case StrCompExprContext c ->
          new NumExpr.StrCompare(
              compOp(c.getChild(1).getText()),
              lowerStr(c.strTerm(0), lineNumber),
              lowerStr(c.strTerm(1), lineNumber));
      case NumNotExprContext c -> new NumExpr.NumNot(lowerNum(c.numExpr(), lineNumber));
      case NumAndExprContext c ->
          new NumExpr.NumAnd(
              lowerNum(c.numExpr(0), lineNumber), lowerNum(c.numExpr(1), lineNumber));
      case NumOrExprContext c ->
          new NumExpr.NumOr(lowerNum(c.numExpr(0), lineNumber), lowerNum(c.numExpr(1), lineNumber));
      default -> throw new IllegalStateException("Unknown numExpr alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code numAtom} (a function argument without parens) to the same node types as {@link
   * #lowerNum(NumExprContext, int)} - the atom/expr split is syntax-only.
   */
  public static NumExpr lowerNum(NumAtomContext ctx, int lineNumber) {
    if (ctx.NUM_LITERAL() != null) {
      return new NumExpr.NumLiteral(parseNumLiteral(ctx.NUM_LITERAL().getText()));
    }
    if (ctx.BIN_LITERAL() != null) {
      return new NumExpr.NumLiteral(parseBinLiteral(ctx.BIN_LITERAL().getText(), lineNumber));
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      final String name = upper(ctx.NUM_IDENTIFIER().getText());
      if (!ctx.numExpr().isEmpty()) {
        return new NumExpr.NumArrayExpr(name, lowerNumList(ctx.numExpr(), lineNumber));
      }
      return new NumExpr.NumVarExpr(name);
    }
    if (!ctx.numExpr().isEmpty()) {
      return lowerNum(ctx.numExpr(0), lineNumber);
    }
    if (ctx.numFunc() != null) {
      return lowerNumFunc(ctx.numFunc(), lineNumber);
    }
    throw new IllegalStateException("Unknown numAtom alternative: " + ctx.getText());
  }

  @SuppressWarnings("PMD.NcssCount") // One branch per grammar alternative, as expected.
  private static NumExpr.NumFuncCall lowerNumFunc(NumFuncContext ctx, int lineNumber) {
    if (ctx.ABS() != null) {
      return numFuncOfAtom(NumFuncKind.ABS, ctx.numAtom(), lineNumber);
    }
    if (ctx.ACS() != null) {
      return numFuncOfAtom(NumFuncKind.ACS, ctx.numAtom(), lineNumber);
    }
    if (ctx.ASN() != null) {
      return numFuncOfAtom(NumFuncKind.ASN, ctx.numAtom(), lineNumber);
    }
    if (ctx.ATTR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.ATTR,
          List.of(lowerNum(ctx.numExpr(0), lineNumber), lowerNum(ctx.numExpr(1), lineNumber)));
    }
    if (ctx.ATN() != null) {
      return numFuncOfAtom(NumFuncKind.ATN, ctx.numAtom(), lineNumber);
    }
    if (ctx.CODE() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.CODE, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.COLOUR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.COLOUR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber),
              lowerNum(ctx.numExpr(1), lineNumber),
              lowerNum(ctx.numExpr(2), lineNumber)));
    }
    if (ctx.COS() != null) {
      return numFuncOfAtom(NumFuncKind.COS, ctx.numAtom(), lineNumber);
    }
    if (ctx.EXP() != null) {
      return numFuncOfAtom(NumFuncKind.EXP, ctx.numAtom(), lineNumber);
    }
    if (ctx.FRAMES() != null) {
      return noArgNumFunc(NumFuncKind.FRAMES);
    }
    if (ctx.INT() != null) {
      return numFuncOfAtom(NumFuncKind.INT, ctx.numAtom(), lineNumber);
    }
    if (ctx.LEN() != null) {
      return new NumExpr.NumFuncCall(NumFuncKind.LEN, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.LN() != null) {
      return numFuncOfAtom(NumFuncKind.LN, ctx.numAtom(), lineNumber);
    }
    if (ctx.PI() != null) {
      return noArgNumFunc(NumFuncKind.PI);
    }
    if (ctx.PLOTH() != null) {
      return noArgNumFunc(NumFuncKind.PLOTH);
    }
    if (ctx.PLOTMODE() != null) {
      return noArgNumFunc(NumFuncKind.PLOTMODE);
    }
    if (ctx.PLOTW() != null) {
      return noArgNumFunc(NumFuncKind.PLOTW);
    }
    if (ctx.PLOTX() != null) {
      return noArgNumFunc(NumFuncKind.PLOTX);
    }
    if (ctx.PLOTY() != null) {
      return noArgNumFunc(NumFuncKind.PLOTY);
    }
    if (ctx.POINT() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.POINT,
          List.of(lowerNum(ctx.numExpr(0), lineNumber), lowerNum(ctx.numExpr(1), lineNumber)));
    }
    if (ctx.RND() != null) {
      return noArgNumFunc(NumFuncKind.RND);
    }
    if (ctx.SGN() != null) {
      return numFuncOfAtom(NumFuncKind.SGN, ctx.numAtom(), lineNumber);
    }
    if (ctx.SIN() != null) {
      return numFuncOfAtom(NumFuncKind.SIN, ctx.numAtom(), lineNumber);
    }
    if (ctx.SQR() != null) {
      return numFuncOfAtom(NumFuncKind.SQR, ctx.numAtom(), lineNumber);
    }
    if (ctx.TAN() != null) {
      return numFuncOfAtom(NumFuncKind.TAN, ctx.numAtom(), lineNumber);
    }
    if (ctx.TEXTH() != null) {
      return noArgNumFunc(NumFuncKind.TEXTH);
    }
    if (ctx.TEXTW() != null) {
      return noArgNumFunc(NumFuncKind.TEXTW);
    }
    if (ctx.TEXTX() != null) {
      return noArgNumFunc(NumFuncKind.TEXTX);
    }
    if (ctx.TEXTY() != null) {
      return noArgNumFunc(NumFuncKind.TEXTY);
    }
    if (ctx.UCNEXT() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.UCNEXT,
          List.of(lowerStr(ctx.strExpr(), lineNumber), lowerNum(ctx.numExpr(0), lineNumber)));
    }
    if (ctx.UCODE() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.UCODE, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.ULEN() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.ULEN, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.VAL() != null) {
      return new NumExpr.NumFuncCall(NumFuncKind.VAL, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.XATTR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.XATTR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber),
              lowerNum(ctx.numExpr(1), lineNumber),
              lowerNum(ctx.numExpr(2), lineNumber)));
    }
    throw new IllegalStateException("Unknown numFunc alternative: " + ctx.getText());
  }

  private static NumExpr.NumFuncCall numFuncOfAtom(
      NumFuncKind kind, NumAtomContext atom, int lineNumber) {
    return new NumExpr.NumFuncCall(kind, List.of(lowerNum(atom, lineNumber)));
  }

  private static NumExpr.NumFuncCall noArgNumFunc(NumFuncKind kind) {
    return new NumExpr.NumFuncCall(kind, List.of());
  }

  // ===== String expressions =====

  public static StrExpr lowerStr(StrExprContext ctx, int lineNumber) {
    return switch (ctx) {
      case StrAndExprContext c ->
          new StrExpr.StrAnd(lowerStr(c.strTerm(), lineNumber), lowerNum(c.numExpr(), lineNumber));
      case StrTermExprContext c -> lowerStr(c.strTerm(), lineNumber);
      default -> throw new IllegalStateException("Unknown strExpr alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code strTerm} (everything a {@code strExpr} can be except the top-level {@code AND}
   * - see {@code BazLang.g4}'s comment on the split) to the same node types as {@link
   * #lowerStr(StrExprContext, int)}.
   */
  public static StrExpr lowerStr(StrTermContext ctx, int lineNumber) {
    return switch (ctx) {
      case StrLiteralExprContext c ->
          new StrExpr.StrLiteral(parseStrLiteral(c.STR_LITERAL().getText()));
      case StrVarExprContext c -> new StrExpr.StrVarExpr(upper(c.STR_IDENTIFIER().getText()));
      case StrSubscriptExprContext c ->
          new StrExpr.StrSubscriptExpr(
              upper(c.STR_IDENTIFIER().getText()), lowerStrSubscript(c.strSubscript(), lineNumber));
      case StrParenExprContext c -> lowerStr(c.strExpr(), lineNumber);
      case StrConcatExprContext c ->
          new StrExpr.StrConcat(
              lowerStr(c.strTerm(0), lineNumber), lowerStr(c.strTerm(1), lineNumber));
      case StrFuncCallExprContext c -> lowerStrFunc(c.strFunc(), lineNumber);
      case FnStrCallExprContext c ->
          new StrExpr.FnStrCall(
              upper(c.STR_IDENTIFIER().getText()), lowerExpressionList(c.args, lineNumber));
      default -> throw new IllegalStateException("Unknown strTerm alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code strAtom} (a function argument without parens) to the same node types as {@link
   * #lowerStr(StrExprContext, int)} - the atom/expr split is syntax-only.
   */
  public static StrExpr lowerStr(StrAtomContext ctx, int lineNumber) {
    if (ctx.STR_LITERAL() != null) {
      return new StrExpr.StrLiteral(parseStrLiteral(ctx.STR_LITERAL().getText()));
    }
    if (ctx.STR_IDENTIFIER() != null) {
      final String name = upper(ctx.STR_IDENTIFIER().getText());
      if (ctx.strSubscript() != null) {
        return new StrExpr.StrSubscriptExpr(
            name, lowerStrSubscript(ctx.strSubscript(), lineNumber));
      }
      return new StrExpr.StrVarExpr(name);
    }
    if (ctx.strExpr() != null) {
      return lowerStr(ctx.strExpr(), lineNumber);
    }
    if (ctx.strFunc() != null) {
      return lowerStrFunc(ctx.strFunc(), lineNumber);
    }
    throw new IllegalStateException("Unknown strAtom alternative: " + ctx.getText());
  }

  private static StrExpr.StrFuncCall lowerStrFunc(StrFuncContext ctx, int lineNumber) {
    if (ctx.CHR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.CHR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber)));
    }
    if (ctx.INKEY_STR() != null) {
      return new StrExpr.StrFuncCall(StrFuncKind.INKEY_STR, List.of());
    }
    if (ctx.SCREEN_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.SCREEN_STR,
          List.of(lowerNum(ctx.numExpr(0), lineNumber), lowerNum(ctx.numExpr(1), lineNumber)));
    }
    if (ctx.STR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.STR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber)));
    }
    if (ctx.TL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.TL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.UCHR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.UCHR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber)));
    }
    if (ctx.UINKEY_STR() != null) {
      return new StrExpr.StrFuncCall(StrFuncKind.UINKEY_STR, List.of());
    }
    if (ctx.USCREEN_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.USCREEN_STR,
          List.of(lowerNum(ctx.numExpr(0), lineNumber), lowerNum(ctx.numExpr(1), lineNumber)));
    }
    if (ctx.UTL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.UTL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    if (ctx.VAL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.VAL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber)));
    }
    throw new IllegalStateException("Unknown strFunc alternative: " + ctx.getText());
  }

  /**
   * Lowers a {@code strSubscript}, shared between {@link StrExpr.StrSubscriptExpr} and (Phase 2)
   * string assignment targets.
   */
  public static StrSubscript lowerStrSubscript(StrSubscriptContext ctx, int lineNumber) {
    final List<NumExpr> indices =
        ctx.indices != null ? lowerNumList(ctx.indices, lineNumber) : List.of();
    StrSubscript.StrSlice slice = null;
    if (ctx.slice != null) {
      final NumExpr start = ctx.slice.start != null ? lowerNum(ctx.slice.start, lineNumber) : null;
      final NumExpr end = ctx.slice.end != null ? lowerNum(ctx.slice.end, lineNumber) : null;
      slice = new StrSubscript.StrSlice(start, end);
    }
    return new StrSubscript(indices, slice);
  }

  // ===== Statements =====

  /**
   * Lowers a {@code statements} rule to a single <em>flat</em> {@code List<Stmt>}: {@code IfStmt}
   * bodies are recursively inlined right after the {@code IfStmt} itself, exactly as {@code
   * ProgramLine.flatten()} does today (the "flat skip-scan" quirk - see {@link Stmt}'s class
   * Javadoc). This is the list a {@code ProgramLine}/{@code Interpreter} walks for execution;
   * {@link Stmt.IfStmt#body()} itself holds the un-flattened nested form.
   */
  public static List<Stmt> lowerStatements(StatementsContext ctx, int lineNumber) {
    final var flat = new ArrayList<Stmt>();
    flattenInto(lowerStatementList(ctx, lineNumber), flat);
    return List.copyOf(flat);
  }

  private static List<Stmt> lowerStatementList(StatementsContext ctx, int lineNumber) {
    if (ctx == null) {
      return List.of();
    }
    return ctx.statement().stream().map(s -> lowerStatement(s, lineNumber)).toList();
  }

  private static void flattenInto(List<Stmt> stmts, List<Stmt> flat) {
    for (final var stmt : stmts) {
      flat.add(stmt);
      if (stmt instanceof Stmt.IfStmt ifStmt) {
        flattenInto(ifStmt.body(), flat);
      }
    }
  }

  private static Stmt lowerStatement(StatementContext ctx, int lineNumber) {
    return switch (ctx) {
      case AplayStmtContext c ->
          new Stmt.AplayStmt(c.strExpr().stream().map(e -> lowerStr(e, lineNumber)).toList());
      case BeepStmtContext c ->
          new Stmt.BeepStmt(lowerNum(c.numExpr(0), lineNumber), lowerNum(c.numExpr(1), lineNumber));
      case BrightStmtContext c -> new Stmt.BrightStmt(lowerNum(c.numExpr(), lineNumber));
      case CircleStmtContext c ->
          new Stmt.CircleStmt(
              lowerStyleList(c.styleList(), lineNumber),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber),
              lowerNum(c.numExpr(2), lineNumber));
      case ClearStmtContext _ -> new Stmt.ClearStmt();
      case ClsStmtContext _ -> new Stmt.ClsStmt();
      case ContStmtContext _ -> new Stmt.ContStmt();
      case DataStmtContext c ->
          new Stmt.DataStmt(
              c.expression().stream().map(e -> lowerExpression(e, lineNumber)).toList());
      case DefFnStmtContext c -> lowerDefFnStmt(c, lineNumber);
      case DimStmtContext c -> lowerDimStmt(c.dimDecl(), lineNumber);
      case DrawStmtContext c ->
          new Stmt.DrawStmt(
              lowerStyleList(c.styleList(), lineNumber),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber));
      case FastStmtContext _ -> new Stmt.FastStmt();
      case FlashStmtContext c -> new Stmt.FlashStmt(lowerNum(c.numExpr(), lineNumber));
      case ForStmtContext c -> lowerForStmt(c, lineNumber);
      case GosubStmtContext c -> new Stmt.GosubStmt(lowerNum(c.numExpr(), lineNumber));
      case GotoStmtContext c -> new Stmt.GotoStmt(lowerNum(c.numExpr(), lineNumber));
      case IfStmtContext c ->
          new Stmt.IfStmt(
              lowerNum(c.numExpr(), lineNumber), lowerStatementList(c.statements(), lineNumber));
      case InkStmtContext c -> new Stmt.InkStmt(lowerNum(c.numExpr(), lineNumber));
      case InputStmtContext c ->
          new Stmt.InputStmt(lowerAssignTarget(c.assignmentTarget(), lineNumber));
      case InverseStmtContext c -> new Stmt.InverseStmt(lowerNum(c.numExpr(), lineNumber));
      case LetStmtContext c ->
          new Stmt.LetStmt(
              lowerAssignTarget(c.assignmentTarget(), lineNumber),
              lowerExpression(c.expression(), lineNumber));
      case ListStmtContext c -> new Stmt.ListStmt(lowerLineRange(c.lineRange(), lineNumber));
      case LoadStmtContext c -> new Stmt.LoadStmt(lowerStr(c.strExpr(), lineNumber));
      case MergeStmtContext c -> new Stmt.MergeStmt(lowerStr(c.strExpr(), lineNumber));
      case NewStmtContext _ -> new Stmt.NewStmt();
      case NextStmtContext c -> new Stmt.NextStmt(upper(c.NUM_IDENTIFIER().getText()));
      case OverStmtContext c -> new Stmt.OverStmt(lowerNum(c.numExpr(), lineNumber));
      case PaperStmtContext c -> new Stmt.PaperStmt(lowerNum(c.numExpr(), lineNumber));
      case PauseStmtContext c -> new Stmt.PauseStmt(lowerNum(c.numExpr(), lineNumber));
      case PlayStmtContext c ->
          new Stmt.PlayStmt(c.strExpr().stream().map(e -> lowerStr(e, lineNumber)).toList());
      case PlotStmtContext c ->
          new Stmt.PlotStmt(
              lowerStyleList(c.styleList(), lineNumber),
              lowerNum(c.numExpr(0), lineNumber),
              lowerNum(c.numExpr(1), lineNumber));
      case PlotmodeStmtContext c -> new Stmt.PlotmodeStmt(lowerNum(c.numExpr(), lineNumber));
      case PrintStmtContext c -> new Stmt.PrintStmt(lowerPrintList(c.printList(), lineNumber));
      case RandStmtContext c ->
          new Stmt.RandStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber) : null);
      case ReadStmtContext c ->
          new Stmt.ReadStmt(
              c.assignmentTarget().stream().map(t -> lowerAssignTarget(t, lineNumber)).toList());
      case RemStmtContext _ -> new Stmt.RemStmt();
      case RestoreStmtContext c ->
          new Stmt.RestoreStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber) : null);
      case ReturnStmtContext _ -> new Stmt.ReturnStmt();
      case RunStmtContext c ->
          new Stmt.RunStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber) : null);
      case SaveStmtContext c -> new Stmt.SaveStmt(lowerStr(c.strExpr(), lineNumber));
      case ScrollStmtContext _ -> new Stmt.ScrollStmt();
      case SlowStmtContext _ -> new Stmt.SlowStmt();
      case StopStmtContext _ -> new Stmt.StopStmt();
      case VerifyStmtContext c -> new Stmt.VerifyStmt(lowerStr(c.strExpr(), lineNumber));
      default ->
          throw new IllegalStateException("Unknown statement alternative: " + ctx.getClass());
    };
  }

  private static Stmt.DefFnStmt lowerDefFnStmt(DefFnStmtContext ctx, int lineNumber) {
    // Duplicate-parameter and body-type-mismatch validation stay in AstStatementExecutor, not
    // here: they need state.currentStatementIndex() for identical error attribution to today's
    // visitDefFnStmt, which lowering (a pure, EvalState-free function) does not have access to.
    final String name = upper(ctx.name.getText());
    final List<String> params =
        ctx.params != null
            ? ctx.params.stream().map(Token::getText).map(AstLowering::upper).toList()
            : List.of();
    return new Stmt.DefFnStmt(name, params, lowerExpression(ctx.expression(), lineNumber));
  }

  private static Stmt.DimStmt lowerDimStmt(DimDeclContext ctx, int lineNumber) {
    final boolean isStr = ctx.STR_IDENTIFIER() != null;
    final String name =
        upper(isStr ? ctx.STR_IDENTIFIER().getText() : ctx.NUM_IDENTIFIER().getText());
    return new Stmt.DimStmt(name, isStr, lowerNumList(ctx.numExpr(), lineNumber));
  }

  private static Stmt.ForStmt lowerForStmt(ForStmtContext ctx, int lineNumber) {
    final String forVar = upper(ctx.NUM_IDENTIFIER().getText());
    final NumExpr start = lowerNum(ctx.numExpr(0), lineNumber);
    final NumExpr end = lowerNum(ctx.numExpr(1), lineNumber);
    final NumExpr step =
        ctx.numExpr().size() > 2
            ? lowerNum(ctx.numExpr(2), lineNumber)
            : new NumExpr.NumLiteral(1.0);
    return new Stmt.ForStmt(forVar, start, end, step);
  }

  private static AssignTarget lowerAssignTarget(AssignmentTargetContext ctx, int lineNumber) {
    if (ctx.STR_IDENTIFIER() != null) {
      final String name = upper(ctx.STR_IDENTIFIER().getText());
      final StrSubscript subscript =
          ctx.strSubscript() != null ? lowerStrSubscript(ctx.strSubscript(), lineNumber) : null;
      return new AssignTarget.StrTarget(name, subscript);
    }
    final String name = upper(ctx.NUM_IDENTIFIER().getText());
    if (!ctx.numExpr().isEmpty()) {
      return new AssignTarget.NumArrayTarget(name, lowerNumList(ctx.numExpr(), lineNumber));
    }
    return new AssignTarget.NumScalarTarget(name);
  }

  /**
   * Lowers a {@code lineRange}. Disambiguates the single-bound-with-{@code TO} case ("{@code n TO}"
   * vs. "{@code TO n}") from tree child order rather than {@code ctx.getText()} sniffing - see
   * {@link LineRange}'s class Javadoc.
   */
  private static LineRange lowerLineRange(LineRangeContext ctx, int lineNumber) {
    if (ctx == null) {
      return null;
    }
    final var nums = ctx.numExpr();
    if (ctx.TO() != null) {
      if (nums.size() == 2) {
        return new LineRange(lowerNum(nums.get(0), lineNumber), lowerNum(nums.get(1), lineNumber));
      }
      if (nums.size() == 1) {
        // Reference identity, not equals(): checking tree child order (is the numExpr node the
        // first child?), not value equality.
        @SuppressWarnings("PMD.CompareObjectsWithEquals")
        final boolean numComesFirst = ctx.getChild(0) == nums.get(0);
        return numComesFirst
            ? new LineRange(lowerNum(nums.get(0), lineNumber), null)
            : new LineRange(null, lowerNum(nums.get(0), lineNumber));
      }
      return new LineRange(null, null); // just "TO": whole program
    }
    // No TO: the only other alternative requires exactly one numExpr ("LIST n": n to end).
    return new LineRange(lowerNum(nums.getFirst(), lineNumber), null);
  }

  private static List<StyleItem> lowerStyleList(StyleListContext ctx, int lineNumber) {
    if (ctx == null || ctx.styleItem().isEmpty()) {
      return List.of();
    }
    return ctx.styleItem().stream().map(s -> lowerStyleItem(s, lineNumber)).toList();
  }

  private static StyleItem lowerStyleItem(StyleItemContext ctx, int lineNumber) {
    if (ctx instanceof StyleBrightItemContext c) {
      return new StyleItem(StyleItem.StyleKind.BRIGHT, lowerNum(c.numExpr(), lineNumber));
    }
    if (ctx instanceof StyleFlashItemContext c) {
      return new StyleItem(StyleItem.StyleKind.FLASH, lowerNum(c.numExpr(), lineNumber));
    }
    if (ctx instanceof StyleInkItemContext c) {
      return new StyleItem(StyleItem.StyleKind.INK, lowerNum(c.numExpr(), lineNumber));
    }
    if (ctx instanceof StyleInverseItemContext c) {
      return new StyleItem(StyleItem.StyleKind.INVERSE, lowerNum(c.numExpr(), lineNumber));
    }
    if (ctx instanceof StyleOverItemContext c) {
      return new StyleItem(StyleItem.StyleKind.OVER, lowerNum(c.numExpr(), lineNumber));
    }
    if (ctx instanceof StylePaperItemContext c) {
      return new StyleItem(StyleItem.StyleKind.PAPER, lowerNum(c.numExpr(), lineNumber));
    }
    throw new IllegalStateException("Unknown styleItem alternative: " + ctx.getText());
  }

  private static List<PrintElement> lowerPrintList(PrintListContext ctx, int lineNumber) {
    if (ctx == null) {
      return List.of();
    }
    final var elements = new ArrayList<PrintElement>();
    for (int i = 0; i < ctx.getChildCount(); i++) {
      final var child = ctx.getChild(i);
      if (child instanceof PrintSepContext sep) {
        elements.add(new PrintElement.Sep(sep.getText().charAt(0)));
      } else if (child instanceof PrintAtItemContext at) {
        elements.add(
            new PrintElement.AtItem(
                lowerNum(at.numExpr(0), lineNumber), lowerNum(at.numExpr(1), lineNumber)));
      } else if (child instanceof PrintTabItemContext tab) {
        elements.add(new PrintElement.TabItem(lowerNum(tab.numExpr(), lineNumber)));
      } else if (child instanceof PrintStyleItemContext style) {
        elements.add(new PrintElement.StyleElement(lowerStyleItem(style.styleItem(), lineNumber)));
      } else if (child instanceof PrintExprItemContext exprItem) {
        elements.add(
            new PrintElement.ValueItem(lowerExpression(exprItem.expression(), lineNumber)));
      }
    }
    return List.copyOf(elements);
  }

  // ===== Shared helpers =====

  private static List<NumExpr> lowerNumList(List<NumExprContext> ctxs, int lineNumber) {
    return ctxs.stream().map(c -> lowerNum(c, lineNumber)).toList();
  }

  private static List<Expr> lowerExpressionList(List<ExpressionContext> ctxs, int lineNumber) {
    if (ctxs == null) {
      return List.of();
    }
    return ctxs.stream().map(c -> lowerExpression(c, lineNumber)).toList();
  }

  private static String upper(String text) {
    return text.toUpperCase(Locale.ROOT);
  }

  private static Op mulDivOp(String text) {
    return "*".equals(text) ? Op.MUL : Op.DIV;
  }

  private static Op addSubOp(String text) {
    return "+".equals(text) ? Op.ADD : Op.SUB;
  }

  private static Op compOp(String text) {
    return switch (text) {
      case "=" -> Op.EQ;
      case "<>" -> Op.NE;
      case "<" -> Op.LT;
      case "<=" -> Op.LE;
      case ">" -> Op.GT;
      case ">=" -> Op.GE;
      default -> throw new IllegalStateException("Unknown comparison operator: " + text);
    };
  }

  private static double parseNumLiteral(String tokenText) {
    return Double.parseDouble(tokenText);
  }

  /**
   * Parses a BIN literal token text ("BIN 1010") to its numeric value. Mirrored the retired {@code
   * AstAnnotator.parseBinLiteral}; kept as its own copy here rather than shared, since {@code
   * AstAnnotator} no longer exists.
   */
  private static double parseBinLiteral(String tokenText, int lineNumber) {
    final String digits = tokenText.substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw new ReportException(
          ReportCode.NUMBER_TOO_BIG, lineNumber, "Binary literal exceeds 64 digits");
    }
    return new BigInteger(digits, 2).doubleValue();
  }

  /**
   * Unquotes a STR_LITERAL token text (strips quotes, un-doubles embedded quotes). Mirrored the
   * retired {@code AstAnnotator.parseStrLiteral}; kept as its own copy for the same reason as
   * {@link #parseBinLiteral}.
   */
  private static BStr parseStrLiteral(String tokenText) {
    return BStr.fromJavaString(
        tokenText.substring(1, tokenText.length() - 1).replace("\"\"", "\""));
  }
}
