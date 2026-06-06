package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A visitor that returns a reformatted string representation of a BazLang statement. Keywords and
 * built-in function names are upper-cased, and whitespace is normalized.
 */
public class ReformatVisitor extends BazLangBaseVisitor<String> {

  @Override
  public String visitClearStmt(ClearStmtContext ctx) {
    return "CLEAR";
  }

  @Override
  public String visitClsStmt(ClsStmtContext ctx) {
    return "CLS";
  }

  @Override
  public String visitContStmt(ContStmtContext ctx) {
    return "CONT";
  }

  @Override
  public String visitCopyStmt(CopyStmtContext ctx) {
    return "COPY";
  }

  @Override
  public String visitDimStmt(DimStmtContext ctx) {
    return "DIM " + visit(ctx.dimDecl());
  }

  @Override
  public String visitDimDecl(DimDeclContext ctx) {
    if (ctx.NUM_IDENTIFIER() != null) {
      return ctx.NUM_IDENTIFIER().getText().toUpperCase()
          + "("
          + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
          + ")";
    } else {
      return ctx.STR_IDENTIFIER().getText().toUpperCase()
          + "("
          + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
          + ")";
    }
  }

  @Override
  public String visitFastStmt(FastStmtContext ctx) {
    return "FAST";
  }

  @Override
  public String visitForStmt(ForStmtContext ctx) {
    StringBuilder sb =
        new StringBuilder("FOR ")
            .append(ctx.NUM_IDENTIFIER().getText().toUpperCase())
            .append(" = ")
            .append(visit(ctx.numExpr(0)))
            .append(" TO ")
            .append(visit(ctx.numExpr(1)));
    if (ctx.STEP() != null && !isLiteralValue(ctx.numExpr(2), 1.0)) {
      sb.append(" STEP ").append(visit(ctx.numExpr(2)));
    }
    return sb.toString();
  }

  @Override
  public String visitGosubStmt(GosubStmtContext ctx) {
    return "GOSUB " + visit(ctx.numExpr());
  }

  @Override
  public String visitGotoStmt(GotoStmtContext ctx) {
    return "GOTO " + visit(ctx.numExpr());
  }

  @Override
  public String visitIfStmt(IfStmtContext ctx) {
    return "IF " + visit(ctx.numExpr()) + " THEN " + visit(ctx.statement());
  }

  @Override
  public String visitInputStmt(InputStmtContext ctx) {
    return "INPUT " + visit(ctx.assignmentTarget());
  }

  @Override
  public String visitLetStmt(LetStmtContext ctx) {
    return "LET " + visit(ctx.assignmentTarget()) + " = " + visit(ctx.expression());
  }

  @Override
  public String visitListStmt(ListStmtContext ctx) {
    String res = "LIST";
    if (ctx.lineRange() != null) {
      String range = visit(ctx.lineRange());
      if (!"0".equals(range)) {
        res += " " + range;
      }
    }
    return res;
  }

  @Override
  public String visitLListStmt(LListStmtContext ctx) {
    String res = "LLIST";
    if (ctx.lineRange() != null) {
      String range = visit(ctx.lineRange());
      if (!"0".equals(range)) {
        res += " " + range;
      }
    }
    return res;
  }

  @Override
  public String visitLineRange(LineRangeContext ctx) {
    var nums = ctx.numExpr();
    if (ctx.TO() != null) {
      if (nums.size() == 2) {
        return visit(nums.get(0)) + " TO " + visit(nums.get(1));
      } else if (nums.size() == 1) {
        if (ctx.getText().toUpperCase().startsWith("TO")) {
          return "TO " + visit(nums.getFirst());
        } else {
          return visit(nums.getFirst()) + " TO";
        }
      } else {
        return "TO";
      }
    } else {
      return visit(nums.getFirst());
    }
  }

  @Override
  public String visitLoadStmt(LoadStmtContext ctx) {
    return "LOAD " + visit(ctx.strExpr());
  }

  @Override
  public String visitLPrintStmt(LPrintStmtContext ctx) {
    String res = "LPRINT";
    if (ctx.printList() != null) {
      res += " " + visit(ctx.printList());
    }
    return res;
  }

  @Override
  public String visitNewStmt(NewStmtContext ctx) {
    return "NEW";
  }

  @Override
  public String visitNextStmt(NextStmtContext ctx) {
    return "NEXT " + ctx.NUM_IDENTIFIER().getText().toUpperCase();
  }

  @Override
  public String visitPauseStmt(PauseStmtContext ctx) {
    return "PAUSE " + visit(ctx.numExpr());
  }

  @Override
  public String visitPlotStmt(PlotStmtContext ctx) {
    return "PLOT " + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitPokeStmt(PokeStmtContext ctx) {
    return "POKE " + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitPrintStmt(PrintStmtContext ctx) {
    String res = "PRINT";
    if (ctx.printList() != null) {
      res += " " + visit(ctx.printList());
    }
    return res;
  }

  @Override
  public String visitPrintList(PrintListContext ctx) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ctx.getChildCount(); i++) {
      var child = ctx.getChild(i);
      if (child instanceof PrintSepContext) {
        sb.append(child.getText()).append(' ');
      } else if (child instanceof TerminalNode) {
        sb.append(child.getText());
      } else {
        sb.append(visit(child));
      }
    }
    return sb.toString();
  }

  @Override
  public String visitPrintAtItem(PrintAtItemContext ctx) {
    return "AT " + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitPrintTabItem(PrintTabItemContext ctx) {
    return "TAB " + visit(ctx.numExpr());
  }

  @Override
  public String visitPrintExprItem(PrintExprItemContext ctx) {
    return visit(ctx.expression());
  }

  @Override
  public String visitRandStmt(RandStmtContext ctx) {
    String res = "RAND";
    if (ctx.numExpr() != null && !isLiteralValue(ctx.numExpr(), 0.0)) {
      res += " " + visit(ctx.numExpr());
    }
    return res;
  }

  @Override
  public String visitRemStmt(RemStmtContext ctx) {
    String text = ctx.REM().getText();
    return "REM" + text.substring(3);
  }

  @Override
  public String visitReturnStmt(ReturnStmtContext ctx) {
    return "RETURN";
  }

  @Override
  public String visitRunStmt(RunStmtContext ctx) {
    String res = "RUN";
    if (ctx.numExpr() != null && !isLiteralValue(ctx.numExpr(), 0.0)) {
      res += " " + visit(ctx.numExpr());
    }
    return res;
  }

  @Override
  public String visitSaveStmt(SaveStmtContext ctx) {
    return "SAVE " + visit(ctx.strExpr());
  }

  @Override
  public String visitScrollStmt(ScrollStmtContext ctx) {
    return "SCROLL";
  }

  @Override
  public String visitSlowStmt(SlowStmtContext ctx) {
    return "SLOW";
  }

  @Override
  public String visitStopStmt(StopStmtContext ctx) {
    return "STOP";
  }

  @Override
  public String visitUnplotStmt(UnplotStmtContext ctx) {
    return "UNPLOT " + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitAssignmentTarget(AssignmentTargetContext ctx) {
    if (ctx.STR_IDENTIFIER() != null) {
      String res = ctx.STR_IDENTIFIER().getText().toUpperCase();
      if (ctx.strSubscript() != null) {
        res += "(" + visit(ctx.strSubscript()) + ")";
      }
      return res;
    } else {
      String res = ctx.NUM_IDENTIFIER().getText().toUpperCase();
      if (!ctx.numExpr().isEmpty()) {
        res +=
            "(" + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", ")) + ")";
      }
      return res;
    }
  }

  @Override
  public String visitNumLiteralExpr(NumLiteralExprContext ctx) {
    return ctx.NUM_LITERAL().getText();
  }

  @Override
  public String visitNumVarExpr(NumVarExprContext ctx) {
    return ctx.NUM_IDENTIFIER().getText().toUpperCase();
  }

  @Override
  public String visitNumArrayExpr(NumArrayExprContext ctx) {
    return ctx.NUM_IDENTIFIER().getText().toUpperCase()
        + "("
        + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
        + ")";
  }

  @Override
  public String visitNumParenExpr(NumParenExprContext ctx) {
    return "(" + visit(ctx.numExpr()) + ")";
  }

  @Override
  public String visitNumFuncCallExpr(NumFuncCallExprContext ctx) {
    return visit(ctx.numFunc());
  }

  @Override
  public String visitNumPowerExpr(NumPowerExprContext ctx) {
    return visit(ctx.numExpr(0)) + " ** " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitNumUnaryMinusExpr(NumUnaryMinusExprContext ctx) {
    return "-" + visit(ctx.numExpr());
  }

  @Override
  public String visitNumMulDivExpr(NumMulDivExprContext ctx) {
    return visit(ctx.numExpr(0)) + " " + ctx.getChild(1).getText() + " " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitNumAddSubExpr(NumAddSubExprContext ctx) {
    return visit(ctx.numExpr(0)) + " " + ctx.getChild(1).getText() + " " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitNumCompExpr(NumCompExprContext ctx) {
    return visit(ctx.numExpr(0)) + " " + ctx.getChild(1).getText() + " " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitStrCompExpr(StrCompExprContext ctx) {
    return visit(ctx.strExpr(0)) + " " + ctx.getChild(1).getText() + " " + visit(ctx.strExpr(1));
  }

  @Override
  public String visitNumNotExpr(NumNotExprContext ctx) {
    return "NOT " + visit(ctx.numExpr());
  }

  @Override
  public String visitNumAndExpr(NumAndExprContext ctx) {
    return visit(ctx.numExpr(0)) + " AND " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitNumOrExpr(NumOrExprContext ctx) {
    return visit(ctx.numExpr(0)) + " OR " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitStrLiteralExpr(StrLiteralExprContext ctx) {
    return ctx.STR_LITERAL().getText();
  }

  @Override
  public String visitStrVarExpr(StrVarExprContext ctx) {
    return ctx.STR_IDENTIFIER().getText().toUpperCase();
  }

  @Override
  public String visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    return ctx.STR_IDENTIFIER().getText().toUpperCase() + "(" + visit(ctx.strSubscript()) + ")";
  }

  @Override
  public String visitStrParenExpr(StrParenExprContext ctx) {
    return "(" + visit(ctx.strExpr()) + ")";
  }

  @Override
  public String visitStrConcatExpr(StrConcatExprContext ctx) {
    return visit(ctx.strExpr(0)) + " + " + visit(ctx.strExpr(1));
  }

  @Override
  public String visitStrFuncCallExpr(StrFuncCallExprContext ctx) {
    return visit(ctx.strFunc());
  }

  @Override
  public String visitStrAndExpr(StrAndExprContext ctx) {
    return visit(ctx.strExpr()) + " AND " + visit(ctx.numExpr());
  }

  @Override
  public String visitStrSubscript(StrSubscriptContext ctx) {
    StringBuilder sb = new StringBuilder();
    var numExprs = ctx.numExpr();
    String text = ctx.getText().toUpperCase();
    int toPos = text.indexOf("TO");

    if (toPos >= 0) {
      // Logic from ExpressionEvaluator.evalStrSubscript but for formatting
      int commaCount = (int) text.substring(0, toPos).chars().filter(c -> c == ',').count();
      for (int i = 0; i < commaCount; i++) {
        sb.append(visit(numExprs.get(i))).append(", ");
      }
      int sliceExprStart = commaCount;
      boolean hasStart = !text.substring(text.lastIndexOf(',', toPos) + 1, toPos).isBlank();
      if (hasStart) {
        sb.append(visit(numExprs.get(sliceExprStart++)));
      }
      sb.append(" TO ");
      if (sliceExprStart < numExprs.size()) {
        sb.append(visit(numExprs.get(sliceExprStart)));
      }
    } else {
      sb.append(numExprs.stream().map(this::visit).collect(Collectors.joining(", ")));
    }
    return sb.toString();
  }

  @Override
  public String visitNumFunc(NumFuncContext ctx) {
    if (ctx.PI() != null) {
      return "PI";
    }
    if (ctx.RND() != null) {
      return "RND";
    }

    String funcName = ctx.getChild(0).getText().toUpperCase();
    String arg = visit(ctx.getChild(1));
    return funcName + " " + arg;
  }

  @Override
  public String visitStrFunc(StrFuncContext ctx) {
    if (ctx.INKEY_STR() != null) {
      return "INKEY$";
    }
    String funcName = ctx.getChild(0).getText().toUpperCase();
    String arg = visit(ctx.getChild(1));
    return funcName + " " + arg;
  }

  @Override
  public String visitNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      return ctx.NUM_LITERAL().getText();
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      if (ctx.numExpr().isEmpty()) {
        return ctx.NUM_IDENTIFIER().getText().toUpperCase();
      }
      return ctx.NUM_IDENTIFIER().getText().toUpperCase()
          + "("
          + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
          + ")";
    }
    if (ctx.numExpr().size() == 1 && ctx.getChild(0).getText().equals("(")) {
      return "(" + visit(ctx.numExpr(0)) + ")";
    }
    if (ctx.numFunc() != null) {
      return visit(ctx.numFunc());
    }
    return super.visitNumAtom(ctx);
  }

  @Override
  public String visitStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      return ctx.STR_LITERAL().getText();
    }
    if (ctx.STR_IDENTIFIER() != null) {
      if (ctx.strSubscript() == null) {
        return ctx.STR_IDENTIFIER().getText().toUpperCase();
      }
      return ctx.STR_IDENTIFIER().getText().toUpperCase() + "(" + visit(ctx.strSubscript()) + ")";
    }
    if (ctx.strExpr() != null) {
      return "(" + visit(ctx.strExpr()) + ")";
    }
    if (ctx.strFunc() != null) {
      return visit(ctx.strFunc());
    }
    return super.visitStrAtom(ctx);
  }

  private boolean isLiteralValue(NumExprContext ctx, double target) {
    if (ctx instanceof NumLiteralExprContext literal) {
      try {
        return Double.parseDouble(literal.NUM_LITERAL().getText()) == target;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }
}
