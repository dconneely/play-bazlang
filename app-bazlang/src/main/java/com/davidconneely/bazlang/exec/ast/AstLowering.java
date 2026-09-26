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
 * strTerm}/{@code strAtom}) to the typed {@link NumExpr}/{@link StrExpr} AST. Pure functions apart
 * from the small {@link VarIdAllocator} dependency: no {@code EvalState}, no direct dependency on
 * any concrete execution-state class - so lowering can assign a variable/array's immutable {@code
 * id} field (see {@link NumExpr} class Javadoc) at construction time using only that narrow
 * interface. This keeps this class usable both ahead-of-time (at {@code ProgramLine} parse time)
 * and for one-off runtime parses ({@code VAL}, {@code INPUT}) without needing an {@code EvalState}
 * to exist yet - callers that do have one (the common case) simply pass it, since {@code EvalState}
 * implements {@link VarIdAllocator}.
 *
 * <p>{@code lineNumber} is threaded through as a plain parameter (not a {@code ThreadLocal}, as the
 * retired {@code AstAnnotator} class did) purely so a {@code BIN} literal that overflows 64 digits
 * can report the line it came from; it is never used to resolve variables.
 */
public final class AstLowering {
  private AstLowering() {}

  /**
   * Lowers either half of the grammar's {@code expression} rule ({@code numExpr | strExpr}).
   *
   * @param ctx the parsed expression.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered {@link NumExpr} or {@link StrExpr}.
   */
  public static Expr lowerExpression(ExpressionContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.numExpr() != null) {
      return lowerNum(ctx.numExpr(), lineNumber, ids);
    }
    return lowerStr(ctx.strExpr(), lineNumber, ids);
  }

  // ===== Numeric expressions =====

  /**
   * Lowers a {@code numExpr} to the typed {@link NumExpr} AST.
   *
   * @param ctx the parsed numeric expression.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered node.
   */
  public static NumExpr lowerNum(NumExprContext ctx, int lineNumber, VarIdAllocator ids) {
    return switch (ctx) {
      case NumLiteralExprContext c ->
          new NumExpr.NumLiteral(parseNumLiteral(c.NUM_LITERAL().getText()));
      case BinLiteralExprContext c ->
          new NumExpr.NumLiteral(parseBinLiteral(c.BIN_LITERAL().getText(), lineNumber));
      case NumVarExprContext c -> {
        final String name = upper(c.NUM_IDENTIFIER().getText());
        yield new NumExpr.NumVarExpr(name, ids.numVarId(name));
      }
      case NumArrayExprContext c -> {
        final String name = upper(c.NUM_IDENTIFIER().getText());
        yield new NumExpr.NumArrayExpr(
            name, lowerNumList(c.numExpr(), lineNumber, ids), ids.numArrayId(name));
      }
      case NumParenExprContext c -> lowerNum(c.numExpr(), lineNumber, ids);
      case NumFuncCallExprContext c -> lowerNumFunc(c.numFunc(), lineNumber, ids);
      case FnNumCallExprContext c ->
          new NumExpr.FnNumCall(
              upper(c.NUM_IDENTIFIER().getText()), lowerExpressionList(c.args, lineNumber, ids));
      case NumPowerExprContext c ->
          new NumExpr.NumBinaryOp(
              Op.POW,
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case NumUnaryMinusExprContext c ->
          new NumExpr.NumUnaryMinus(lowerNum(c.numExpr(), lineNumber, ids));
      case NumMulDivExprContext c ->
          new NumExpr.NumBinaryOp(
              mulDivOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case NumAddSubExprContext c ->
          new NumExpr.NumBinaryOp(
              addSubOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case NumCompExprContext c ->
          new NumExpr.NumCompare(
              compOp(c.getChild(1).getText()),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case StrCompExprContext c ->
          new NumExpr.StrCompare(
              compOp(c.getChild(1).getText()),
              lowerStr(c.strTerm(0), lineNumber, ids),
              lowerStr(c.strTerm(1), lineNumber, ids));
      case NumNotExprContext c -> new NumExpr.NumNot(lowerNum(c.numExpr(), lineNumber, ids));
      case NumAndExprContext c ->
          new NumExpr.NumAnd(
              lowerNum(c.numExpr(0), lineNumber, ids), lowerNum(c.numExpr(1), lineNumber, ids));
      case NumOrExprContext c ->
          new NumExpr.NumOr(
              lowerNum(c.numExpr(0), lineNumber, ids), lowerNum(c.numExpr(1), lineNumber, ids));
      default -> throw new IllegalStateException("Unknown numExpr alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code numAtom} (a function argument without parens) to the same node types as {@link
   * #lowerNum(NumExprContext, int, VarIdAllocator)} - the atom/expr split is syntax-only.
   *
   * @param ctx the parsed numeric atom.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered node.
   */
  public static NumExpr lowerNum(NumAtomContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.NUM_LITERAL() != null) {
      return new NumExpr.NumLiteral(parseNumLiteral(ctx.NUM_LITERAL().getText()));
    }
    if (ctx.BIN_LITERAL() != null) {
      return new NumExpr.NumLiteral(parseBinLiteral(ctx.BIN_LITERAL().getText(), lineNumber));
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      final String name = upper(ctx.NUM_IDENTIFIER().getText());
      if (!ctx.numExpr().isEmpty()) {
        return new NumExpr.NumArrayExpr(
            name, lowerNumList(ctx.numExpr(), lineNumber, ids), ids.numArrayId(name));
      }
      return new NumExpr.NumVarExpr(name, ids.numVarId(name));
    }
    if (!ctx.numExpr().isEmpty()) {
      return lowerNum(ctx.numExpr(0), lineNumber, ids);
    }
    if (ctx.numFunc() != null) {
      return lowerNumFunc(ctx.numFunc(), lineNumber, ids);
    }
    throw new IllegalStateException("Unknown numAtom alternative: " + ctx.getText());
  }

  @SuppressWarnings("PMD.NcssCount") // One branch per grammar alternative, as expected.
  private static NumExpr.NumFuncCall lowerNumFunc(
      NumFuncContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.ABS() != null) {
      return numFuncOfAtom(NumFuncKind.ABS, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.ACS() != null) {
      return numFuncOfAtom(NumFuncKind.ACS, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.ASN() != null) {
      return numFuncOfAtom(NumFuncKind.ASN, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.ATTR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.ATTR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids)));
    }
    if (ctx.ATN() != null) {
      return numFuncOfAtom(NumFuncKind.ATN, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.CODE() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.CODE, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.COLOUR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.COLOUR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids),
              lowerNum(ctx.numExpr(2), lineNumber, ids)));
    }
    if (ctx.COS() != null) {
      return numFuncOfAtom(NumFuncKind.COS, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.EXP() != null) {
      return numFuncOfAtom(NumFuncKind.EXP, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.FRAMES() != null) {
      return noArgNumFunc(NumFuncKind.FRAMES);
    }
    if (ctx.INT() != null) {
      return numFuncOfAtom(NumFuncKind.INT, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.LEN() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.LEN, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.LN() != null) {
      return numFuncOfAtom(NumFuncKind.LN, ctx.numAtom(), lineNumber, ids);
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
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids)));
    }
    if (ctx.RND() != null) {
      return noArgNumFunc(NumFuncKind.RND);
    }
    if (ctx.SGN() != null) {
      return numFuncOfAtom(NumFuncKind.SGN, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.SIN() != null) {
      return numFuncOfAtom(NumFuncKind.SIN, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.SQR() != null) {
      return numFuncOfAtom(NumFuncKind.SQR, ctx.numAtom(), lineNumber, ids);
    }
    if (ctx.TAN() != null) {
      return numFuncOfAtom(NumFuncKind.TAN, ctx.numAtom(), lineNumber, ids);
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
          List.of(
              lowerStr(ctx.strExpr(), lineNumber, ids), lowerNum(ctx.numExpr(0), lineNumber, ids)));
    }
    if (ctx.UCODE() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.UCODE, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.ULEN() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.ULEN, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.VAL() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.VAL, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.XATTR() != null) {
      return new NumExpr.NumFuncCall(
          NumFuncKind.XATTR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids),
              lowerNum(ctx.numExpr(2), lineNumber, ids)));
    }
    throw new IllegalStateException("Unknown numFunc alternative: " + ctx.getText());
  }

  private static NumExpr.NumFuncCall numFuncOfAtom(
      NumFuncKind kind, NumAtomContext atom, int lineNumber, VarIdAllocator ids) {
    return new NumExpr.NumFuncCall(kind, List.of(lowerNum(atom, lineNumber, ids)));
  }

  private static NumExpr.NumFuncCall noArgNumFunc(NumFuncKind kind) {
    return new NumExpr.NumFuncCall(kind, List.of());
  }

  // ===== String expressions =====

  /**
   * Lowers a {@code strExpr} to the typed {@link StrExpr} AST.
   *
   * @param ctx the parsed string expression.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered node.
   */
  public static StrExpr lowerStr(StrExprContext ctx, int lineNumber, VarIdAllocator ids) {
    return switch (ctx) {
      case StrAndExprContext c ->
          new StrExpr.StrAnd(
              lowerStr(c.strTerm(), lineNumber, ids), lowerNum(c.numExpr(), lineNumber, ids));
      case StrTermExprContext c -> lowerStr(c.strTerm(), lineNumber, ids);
      default -> throw new IllegalStateException("Unknown strExpr alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code strTerm} (everything a {@code strExpr} can be except the top-level {@code AND}
   * - see {@code BazLang.g4}'s comment on the split) to the same node types as {@link
   * #lowerStr(StrExprContext, int, VarIdAllocator)}.
   *
   * @param ctx the parsed string term.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered node.
   */
  public static StrExpr lowerStr(StrTermContext ctx, int lineNumber, VarIdAllocator ids) {
    return switch (ctx) {
      case StrLiteralExprContext c ->
          new StrExpr.StrLiteral(parseStrLiteral(c.STR_LITERAL().getText()));
      case StrVarExprContext c -> {
        final String name = upper(c.STR_IDENTIFIER().getText());
        yield new StrExpr.StrVarExpr(name, ids.strVarId(name));
      }
      case StrSubscriptExprContext c -> {
        final String name = upper(c.STR_IDENTIFIER().getText());
        yield new StrExpr.StrSubscriptExpr(
            name, lowerStrSubscript(c.strSubscript(), lineNumber, ids), ids.strVarId(name));
      }
      case StrParenExprContext c -> lowerStr(c.strExpr(), lineNumber, ids);
      case StrConcatExprContext c ->
          new StrExpr.StrConcat(
              lowerStr(c.strTerm(0), lineNumber, ids), lowerStr(c.strTerm(1), lineNumber, ids));
      case StrFuncCallExprContext c -> lowerStrFunc(c.strFunc(), lineNumber, ids);
      case FnStrCallExprContext c ->
          new StrExpr.FnStrCall(
              upper(c.STR_IDENTIFIER().getText()), lowerExpressionList(c.args, lineNumber, ids));
      default -> throw new IllegalStateException("Unknown strTerm alternative: " + ctx.getClass());
    };
  }

  /**
   * Lowers a {@code strAtom} (a function argument without parens) to the same node types as {@link
   * #lowerStr(StrExprContext, int, VarIdAllocator)} - the atom/expr split is syntax-only.
   *
   * @param ctx the parsed string atom.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered node.
   */
  public static StrExpr lowerStr(StrAtomContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.STR_LITERAL() != null) {
      return new StrExpr.StrLiteral(parseStrLiteral(ctx.STR_LITERAL().getText()));
    }
    if (ctx.STR_IDENTIFIER() != null) {
      final String name = upper(ctx.STR_IDENTIFIER().getText());
      if (ctx.strSubscript() != null) {
        return new StrExpr.StrSubscriptExpr(
            name, lowerStrSubscript(ctx.strSubscript(), lineNumber, ids), ids.strVarId(name));
      }
      return new StrExpr.StrVarExpr(name, ids.strVarId(name));
    }
    if (ctx.strExpr() != null) {
      return lowerStr(ctx.strExpr(), lineNumber, ids);
    }
    if (ctx.strFunc() != null) {
      return lowerStrFunc(ctx.strFunc(), lineNumber, ids);
    }
    throw new IllegalStateException("Unknown strAtom alternative: " + ctx.getText());
  }

  private static StrExpr.StrFuncCall lowerStrFunc(
      StrFuncContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.CHR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.CHR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber, ids)));
    }
    if (ctx.INKEY_STR() != null) {
      return new StrExpr.StrFuncCall(StrFuncKind.INKEY_STR, List.of());
    }
    if (ctx.SCREEN_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.SCREEN_STR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids)));
    }
    if (ctx.STR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.STR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber, ids)));
    }
    if (ctx.TL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.TL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.UCHR_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.UCHR_STR, List.of(lowerNum(ctx.numAtom(), lineNumber, ids)));
    }
    if (ctx.UINKEY_STR() != null) {
      return new StrExpr.StrFuncCall(StrFuncKind.UINKEY_STR, List.of());
    }
    if (ctx.USCREEN_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.USCREEN_STR,
          List.of(
              lowerNum(ctx.numExpr(0), lineNumber, ids),
              lowerNum(ctx.numExpr(1), lineNumber, ids)));
    }
    if (ctx.UTL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.UTL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    if (ctx.VAL_STR() != null) {
      return new StrExpr.StrFuncCall(
          StrFuncKind.VAL_STR, List.of(lowerStr(ctx.strAtom(), lineNumber, ids)));
    }
    throw new IllegalStateException("Unknown strFunc alternative: " + ctx.getText());
  }

  /**
   * Lowers a {@code strSubscript}, shared between {@link StrExpr.StrSubscriptExpr} and string
   * assignment targets.
   *
   * @param ctx the parsed subscript/slice.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed.
   * @return the lowered subscript/slice.
   */
  public static StrSubscript lowerStrSubscript(
      StrSubscriptContext ctx, int lineNumber, VarIdAllocator ids) {
    final List<NumExpr> indices =
        ctx.indices != null ? lowerNumList(ctx.indices, lineNumber, ids) : List.of();
    StrSubscript.StrSlice slice = null;
    if (ctx.slice != null) {
      final NumExpr start =
          ctx.slice.start != null ? lowerNum(ctx.slice.start, lineNumber, ids) : null;
      final NumExpr end = ctx.slice.end != null ? lowerNum(ctx.slice.end, lineNumber, ids) : null;
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
   *
   * @param ctx the parsed statement list.
   * @param lineNumber the source line, for error reporting only.
   * @param ids assigns/reuses variable ids for the AST nodes constructed - see {@link
   *     ProgramLine#getFlattenedStatements} for why the same allocator must be used across every
   *     line of one programme.
   * @return the flattened statement list.
   */
  public static List<Stmt> lowerStatements(
      StatementsContext ctx, int lineNumber, VarIdAllocator ids) {
    final var flat = new ArrayList<Stmt>();
    flattenInto(lowerStatementList(ctx, lineNumber, ids), flat);
    return List.copyOf(flat);
  }

  private static List<Stmt> lowerStatementList(
      StatementsContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx == null) {
      return List.of();
    }
    return ctx.statement().stream().map(s -> lowerStatement(s, lineNumber, ids)).toList();
  }

  private static void flattenInto(List<Stmt> stmts, List<Stmt> flat) {
    for (final var stmt : stmts) {
      flat.add(stmt);
      if (stmt instanceof Stmt.IfStmt ifStmt) {
        flattenInto(ifStmt.body(), flat);
      }
    }
  }

  private static Stmt lowerStatement(StatementContext ctx, int lineNumber, VarIdAllocator ids) {
    return switch (ctx) {
      case AplayStmtContext c ->
          new Stmt.AplayStmt(c.strExpr().stream().map(e -> lowerStr(e, lineNumber, ids)).toList());
      case BeepStmtContext c ->
          new Stmt.BeepStmt(
              lowerNum(c.numExpr(0), lineNumber, ids), lowerNum(c.numExpr(1), lineNumber, ids));
      case BrightStmtContext c -> new Stmt.BrightStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case CircleStmtContext c ->
          new Stmt.CircleStmt(
              lowerStyleList(c.styleList(), lineNumber, ids),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids),
              lowerNum(c.numExpr(2), lineNumber, ids));
      case ClearStmtContext _ -> new Stmt.ClearStmt();
      case ClsStmtContext _ -> new Stmt.ClsStmt();
      case ContStmtContext _ -> new Stmt.ContStmt();
      case DataStmtContext c ->
          new Stmt.DataStmt(
              c.expression().stream().map(e -> lowerExpression(e, lineNumber, ids)).toList());
      case DefFnStmtContext c -> lowerDefFnStmt(c, lineNumber, ids);
      case DimStmtContext c -> lowerDimStmt(c.dimDecl(), lineNumber, ids);
      case DrawStmtContext c ->
          new Stmt.DrawStmt(
              lowerStyleList(c.styleList(), lineNumber, ids),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case FastStmtContext _ -> new Stmt.FastStmt();
      case FlashStmtContext c -> new Stmt.FlashStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case ForStmtContext c -> lowerForStmt(c, lineNumber, ids);
      case GosubStmtContext c -> new Stmt.GosubStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case GotoStmtContext c -> new Stmt.GotoStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case IfStmtContext c ->
          new Stmt.IfStmt(
              lowerNum(c.numExpr(), lineNumber, ids),
              lowerStatementList(c.statements(), lineNumber, ids));
      case InkStmtContext c -> new Stmt.InkStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case InputStmtContext c ->
          new Stmt.InputStmt(lowerAssignTarget(c.assignmentTarget(), lineNumber, ids));
      case InverseStmtContext c -> new Stmt.InverseStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case LetStmtContext c ->
          new Stmt.LetStmt(
              lowerAssignTarget(c.assignmentTarget(), lineNumber, ids),
              lowerExpression(c.expression(), lineNumber, ids));
      case ListStmtContext c -> new Stmt.ListStmt(lowerLineRange(c.lineRange(), lineNumber, ids));
      case LoadStmtContext c -> new Stmt.LoadStmt(lowerStr(c.strExpr(), lineNumber, ids));
      case MergeStmtContext c -> new Stmt.MergeStmt(lowerStr(c.strExpr(), lineNumber, ids));
      case NewStmtContext _ -> new Stmt.NewStmt();
      case NextStmtContext c -> new Stmt.NextStmt(upper(c.NUM_IDENTIFIER().getText()));
      case OverStmtContext c -> new Stmt.OverStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case PaperStmtContext c -> new Stmt.PaperStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case PauseStmtContext c -> new Stmt.PauseStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case PlayStmtContext c ->
          new Stmt.PlayStmt(c.strExpr().stream().map(e -> lowerStr(e, lineNumber, ids)).toList());
      case PlotStmtContext c ->
          new Stmt.PlotStmt(
              lowerStyleList(c.styleList(), lineNumber, ids),
              lowerNum(c.numExpr(0), lineNumber, ids),
              lowerNum(c.numExpr(1), lineNumber, ids));
      case PlotmodeStmtContext c -> new Stmt.PlotmodeStmt(lowerNum(c.numExpr(), lineNumber, ids));
      case PrintStmtContext c -> new Stmt.PrintStmt(lowerPrintList(c.printList(), lineNumber, ids));
      case RandStmtContext c ->
          new Stmt.RandStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber, ids) : null);
      case ReadStmtContext c ->
          new Stmt.ReadStmt(
              c.assignmentTarget().stream()
                  .map(t -> lowerAssignTarget(t, lineNumber, ids))
                  .toList());
      case RemStmtContext _ -> new Stmt.RemStmt();
      case RestoreStmtContext c ->
          new Stmt.RestoreStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber, ids) : null);
      case ReturnStmtContext _ -> new Stmt.ReturnStmt();
      case RunStmtContext c ->
          new Stmt.RunStmt(c.numExpr() != null ? lowerNum(c.numExpr(), lineNumber, ids) : null);
      case SaveStmtContext c -> new Stmt.SaveStmt(lowerStr(c.strExpr(), lineNumber, ids));
      case ScrollStmtContext _ -> new Stmt.ScrollStmt();
      case SlowStmtContext _ -> new Stmt.SlowStmt();
      case StopStmtContext _ -> new Stmt.StopStmt();
      case VerifyStmtContext c -> new Stmt.VerifyStmt(lowerStr(c.strExpr(), lineNumber, ids));
      default ->
          throw new IllegalStateException("Unknown statement alternative: " + ctx.getClass());
    };
  }

  private static Stmt.DefFnStmt lowerDefFnStmt(
      DefFnStmtContext ctx, int lineNumber, VarIdAllocator ids) {
    // Duplicate-parameter and body-type-mismatch validation stay in AstStatementExecutor, not
    // here: they need state.currentStatementIndex() for identical error attribution to today's
    // visitDefFnStmt, which lowering (a pure function, no EvalState) does not have access to.
    final String name = upper(ctx.name.getText());
    final List<String> params =
        ctx.params != null
            ? ctx.params.stream().map(Token::getText).map(AstLowering::upper).toList()
            : List.of();
    return new Stmt.DefFnStmt(name, params, lowerExpression(ctx.expression(), lineNumber, ids));
  }

  private static Stmt.DimStmt lowerDimStmt(DimDeclContext ctx, int lineNumber, VarIdAllocator ids) {
    final boolean isStr = ctx.STR_IDENTIFIER() != null;
    final String name =
        upper(isStr ? ctx.STR_IDENTIFIER().getText() : ctx.NUM_IDENTIFIER().getText());
    return new Stmt.DimStmt(name, isStr, lowerNumList(ctx.numExpr(), lineNumber, ids));
  }

  private static Stmt.ForStmt lowerForStmt(ForStmtContext ctx, int lineNumber, VarIdAllocator ids) {
    final String forVar = upper(ctx.NUM_IDENTIFIER().getText());
    final NumExpr start = lowerNum(ctx.numExpr(0), lineNumber, ids);
    final NumExpr end = lowerNum(ctx.numExpr(1), lineNumber, ids);
    final NumExpr step =
        ctx.numExpr().size() > 2
            ? lowerNum(ctx.numExpr(2), lineNumber, ids)
            : new NumExpr.NumLiteral(1.0);
    return new Stmt.ForStmt(forVar, start, end, step);
  }

  private static AssignTarget lowerAssignTarget(
      AssignmentTargetContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx.STR_IDENTIFIER() != null) {
      final String name = upper(ctx.STR_IDENTIFIER().getText());
      final StrSubscript subscript =
          ctx.strSubscript() != null
              ? lowerStrSubscript(ctx.strSubscript(), lineNumber, ids)
              : null;
      return new AssignTarget.StrTarget(name, subscript, ids.strVarId(name));
    }
    final String name = upper(ctx.NUM_IDENTIFIER().getText());
    if (!ctx.numExpr().isEmpty()) {
      return new AssignTarget.NumArrayTarget(
          name, lowerNumList(ctx.numExpr(), lineNumber, ids), ids.numArrayId(name));
    }
    return new AssignTarget.NumScalarTarget(name, ids.numVarId(name));
  }

  /**
   * Lowers a {@code lineRange}. Disambiguates the single-bound-with-{@code TO} case ("{@code n TO}"
   * vs. "{@code TO n}") from tree child order rather than {@code ctx.getText()} sniffing - see
   * {@link LineRange}'s class Javadoc.
   */
  private static LineRange lowerLineRange(
      LineRangeContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx == null) {
      return null;
    }
    final var nums = ctx.numExpr();
    if (ctx.TO() != null) {
      if (nums.size() == 2) {
        return new LineRange(
            lowerNum(nums.get(0), lineNumber, ids), lowerNum(nums.get(1), lineNumber, ids));
      }
      if (nums.size() == 1) {
        // Reference identity, not equals(): checking tree child order (is the numExpr node the
        // first child?), not value equality.
        @SuppressWarnings("PMD.CompareObjectsWithEquals")
        final boolean numComesFirst = ctx.getChild(0) == nums.get(0);
        return numComesFirst
            ? new LineRange(lowerNum(nums.get(0), lineNumber, ids), null)
            : new LineRange(null, lowerNum(nums.get(0), lineNumber, ids));
      }
      return new LineRange(null, null); // just "TO": whole program
    }
    // No TO: the only other alternative requires exactly one numExpr ("LIST n": n to end).
    return new LineRange(lowerNum(nums.getFirst(), lineNumber, ids), null);
  }

  private static List<StyleItem> lowerStyleList(
      StyleListContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx == null || ctx.styleItem().isEmpty()) {
      return List.of();
    }
    return ctx.styleItem().stream().map(s -> lowerStyleItem(s, lineNumber, ids)).toList();
  }

  private static StyleItem lowerStyleItem(
      StyleItemContext ctx, int lineNumber, VarIdAllocator ids) {
    if (ctx instanceof StyleBrightItemContext c) {
      return new StyleItem(StyleItem.StyleKind.BRIGHT, lowerNum(c.numExpr(), lineNumber, ids));
    }
    if (ctx instanceof StyleFlashItemContext c) {
      return new StyleItem(StyleItem.StyleKind.FLASH, lowerNum(c.numExpr(), lineNumber, ids));
    }
    if (ctx instanceof StyleInkItemContext c) {
      return new StyleItem(StyleItem.StyleKind.INK, lowerNum(c.numExpr(), lineNumber, ids));
    }
    if (ctx instanceof StyleInverseItemContext c) {
      return new StyleItem(StyleItem.StyleKind.INVERSE, lowerNum(c.numExpr(), lineNumber, ids));
    }
    if (ctx instanceof StyleOverItemContext c) {
      return new StyleItem(StyleItem.StyleKind.OVER, lowerNum(c.numExpr(), lineNumber, ids));
    }
    if (ctx instanceof StylePaperItemContext c) {
      return new StyleItem(StyleItem.StyleKind.PAPER, lowerNum(c.numExpr(), lineNumber, ids));
    }
    throw new IllegalStateException("Unknown styleItem alternative: " + ctx.getText());
  }

  private static List<PrintElement> lowerPrintList(
      PrintListContext ctx, int lineNumber, VarIdAllocator ids) {
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
                lowerNum(at.numExpr(0), lineNumber, ids),
                lowerNum(at.numExpr(1), lineNumber, ids)));
      } else if (child instanceof PrintTabItemContext tab) {
        elements.add(new PrintElement.TabItem(lowerNum(tab.numExpr(), lineNumber, ids)));
      } else if (child instanceof PrintStyleItemContext style) {
        elements.add(
            new PrintElement.StyleElement(lowerStyleItem(style.styleItem(), lineNumber, ids)));
      } else if (child instanceof PrintExprItemContext exprItem) {
        elements.add(
            new PrintElement.ValueItem(lowerExpression(exprItem.expression(), lineNumber, ids)));
      }
    }
    return List.copyOf(elements);
  }

  // ===== Shared helpers =====

  private static List<NumExpr> lowerNumList(
      List<NumExprContext> ctxs, int lineNumber, VarIdAllocator ids) {
    return ctxs.stream().map(c -> lowerNum(c, lineNumber, ids)).toList();
  }

  private static List<Expr> lowerExpressionList(
      List<ExpressionContext> ctxs, int lineNumber, VarIdAllocator ids) {
    if (ctxs == null) {
      return List.of();
    }
    return ctxs.stream().map(c -> lowerExpression(c, lineNumber, ids)).toList();
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
          ReportCode.NUMBER_TOO_BIG, lineNumber, 1, "Binary literal exceeds 64 digits");
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
