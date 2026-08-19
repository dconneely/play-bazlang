package com.davidconneely.bazlang.edit;

import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A visitor that returns a reformatted string representation of a BazLang statement. Keywords and
 * built-in function names are upper-cased, and whitespace is normalised.
 */
public class ReformatVisitor extends BazLangBaseVisitor<String> {
  @Override
  public String visitStatements(StatementsContext ctx) {
    return ctx.statement().stream().map(this::visit).collect(Collectors.joining(" : "));
  }

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
    return "CONTINUE";
  }

  @Override
  public String visitDimStmt(DimStmtContext ctx) {
    return "DIM " + visit(ctx.dimDecl());
  }

  @Override
  public String visitDimDecl(DimDeclContext ctx) {
    if (ctx.NUM_IDENTIFIER() != null) {
      return ctx.NUM_IDENTIFIER().getText().toLowerCase()
          + "("
          + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
          + ")";
    } else {
      return ctx.STR_IDENTIFIER().getText().toLowerCase()
          + "("
          + ctx.numExpr().stream().map(this::visit).collect(Collectors.joining(", "))
          + ")";
    }
  }

  @Override
  public String visitForStmt(ForStmtContext ctx) {
    final var sb =
        new StringBuilder("FOR ")
            .append(ctx.NUM_IDENTIFIER().getText().toLowerCase())
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
    return "GO SUB " + visit(ctx.numExpr());
  }

  @Override
  public String visitGotoStmt(GotoStmtContext ctx) {
    return "GO TO " + visit(ctx.numExpr());
  }

  @Override
  public String visitIfStmt(IfStmtContext ctx) {
    return "IF " + visit(ctx.numExpr()) + " THEN " + visit(ctx.statements());
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
      final String range = visit(ctx.lineRange());
      if (!"0".equals(range)) {
        res += " " + range;
      }
    }
    return res;
  }

  @Override
  public String visitLineRange(LineRangeContext ctx) {
    final var nums = ctx.numExpr();
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
  public String visitMergeStmt(MergeStmtContext ctx) {
    return "MERGE " + visit(ctx.strExpr());
  }

  @Override
  public String visitNewStmt(NewStmtContext ctx) {
    return "NEW";
  }

  @Override
  public String visitNextStmt(NextStmtContext ctx) {
    return "NEXT " + ctx.NUM_IDENTIFIER().getText().toLowerCase();
  }

  @Override
  public String visitPaperStmt(PaperStmtContext ctx) {
    return "PAPER " + visit(ctx.numExpr());
  }

  @Override
  public String visitInkStmt(InkStmtContext ctx) {
    return "INK " + visit(ctx.numExpr());
  }

  @Override
  public String visitBrightStmt(BrightStmtContext ctx) {
    return "BRIGHT " + visit(ctx.numExpr());
  }

  @Override
  public String visitFlashStmt(FlashStmtContext ctx) {
    return "FLASH " + visit(ctx.numExpr());
  }

  @Override
  public String visitInverseStmt(InverseStmtContext ctx) {
    return "INVERSE " + visit(ctx.numExpr());
  }

  @Override
  public String visitOverStmt(OverStmtContext ctx) {
    return "OVER " + visit(ctx.numExpr());
  }

  @Override
  public String visitPauseStmt(PauseStmtContext ctx) {
    return "PAUSE " + visit(ctx.numExpr());
  }

  @Override
  public String visitBeepStmt(BeepStmtContext ctx) {
    return "BEEP " + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1));
  }

  @Override
  public String visitPlayStmt(PlayStmtContext ctx) {
    return "PLAY " + ctx.strExpr().stream().map(this::visit).collect(Collectors.joining(", "));
  }

  @Override
  public String visitAplayStmt(AplayStmtContext ctx) {
    return "APLAY " + ctx.strExpr().stream().map(this::visit).collect(Collectors.joining(", "));
  }

  @Override
  public String visitPlotStmt(PlotStmtContext ctx) {
    return formatCommandWithStyles("PLOT", ctx.styleList())
        + " "
        + visit(ctx.numExpr(0))
        + ", "
        + visit(ctx.numExpr(1));
  }

  @Override
  public String visitDrawStmt(DrawStmtContext ctx) {
    return formatCommandWithStyles("DRAW", ctx.styleList())
        + " "
        + visit(ctx.numExpr(0))
        + ", "
        + visit(ctx.numExpr(1));
  }

  @Override
  public String visitCircleStmt(CircleStmtContext ctx) {
    return formatCommandWithStyles("CIRCLE", ctx.styleList())
        + " "
        + visit(ctx.numExpr(0))
        + ", "
        + visit(ctx.numExpr(1))
        + ", "
        + visit(ctx.numExpr(2));
  }

  @Override
  public String visitPlotmodeStmt(PlotmodeStmtContext ctx) {
    return "PLOTMODE " + visit(ctx.numExpr());
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
      final var child = ctx.getChild(i);
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
  public String visitStyleList(StyleListContext ctx) {
    final var sb = new StringBuilder();
    for (int i = 0; i < ctx.getChildCount(); i++) {
      final var child = ctx.getChild(i);
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
  public String visitStyleInkItem(StyleInkItemContext ctx) {
    return "INK " + visit(ctx.numExpr());
  }

  @Override
  public String visitStylePaperItem(StylePaperItemContext ctx) {
    return "PAPER " + visit(ctx.numExpr());
  }

  @Override
  public String visitStyleBrightItem(StyleBrightItemContext ctx) {
    return "BRIGHT " + visit(ctx.numExpr());
  }

  @Override
  public String visitStyleFlashItem(StyleFlashItemContext ctx) {
    return "FLASH " + visit(ctx.numExpr());
  }

  @Override
  public String visitStyleInverseItem(StyleInverseItemContext ctx) {
    return "INVERSE " + visit(ctx.numExpr());
  }

  @Override
  public String visitStyleOverItem(StyleOverItemContext ctx) {
    return "OVER " + visit(ctx.numExpr());
  }

  @Override
  public String visitPrintStyleItem(PrintStyleItemContext ctx) {
    return visit(ctx.styleItem());
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
    String res = "RANDOMIZE";
    if (ctx.numExpr() != null && !isLiteralValue(ctx.numExpr(), 0.0)) {
      res += " " + visit(ctx.numExpr());
    }
    return res;
  }

  @Override
  public String visitRemStmt(RemStmtContext ctx) {
    final String text = ctx.REM().getText();
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
  public String visitVerifyStmt(VerifyStmtContext ctx) {
    return "VERIFY " + visit(ctx.strExpr());
  }

  @Override
  public String visitScrollStmt(ScrollStmtContext ctx) {
    return "SCROLL";
  }

  @Override
  public String visitStopStmt(StopStmtContext ctx) {
    return "STOP";
  }

  private String formatCommandWithStyles(String command, StyleListContext styleList) {
    String res = command;
    if (styleList != null && styleList.getChildCount() > 0) {
      final String styles = visit(styleList);
      if (!styles.isEmpty()) {
        res += " " + styles.trim();
      }
    }
    return res;
  }

  @Override
  public String visitDataStmt(DataStmtContext ctx) {
    return "DATA " + ctx.expression().stream().map(this::visit).collect(Collectors.joining(", "));
  }

  @Override
  public String visitDefFnStmt(DefFnStmtContext ctx) {
    final String params =
        ctx.params != null
            ? ctx.params.stream()
                .map(org.antlr.v4.runtime.Token::getText)
                .map(String::toLowerCase)
                .collect(Collectors.joining(", "))
            : "";
    return "DEF FN "
        + ctx.name.getText().toLowerCase()
        + "("
        + params
        + ") = "
        + visit(ctx.expression());
  }

  @Override
  public String visitReadStmt(ReadStmtContext ctx) {
    return "READ "
        + ctx.assignmentTarget().stream().map(this::visit).collect(Collectors.joining(", "));
  }

  @Override
  public String visitRestoreStmt(RestoreStmtContext ctx) {
    return ctx.numExpr() != null ? "RESTORE " + visit(ctx.numExpr()) : "RESTORE";
  }

  @Override
  public String visitFastStmt(FastStmtContext ctx) {
    return "FAST";
  }

  @Override
  public String visitSlowStmt(SlowStmtContext ctx) {
    return "SLOW";
  }

  @Override
  public String visitAssignmentTarget(AssignmentTargetContext ctx) {
    if (ctx.STR_IDENTIFIER() != null) {
      String res = ctx.STR_IDENTIFIER().getText().toLowerCase();
      if (ctx.strSubscript() != null) {
        res += "(" + visit(ctx.strSubscript()) + ")";
      }
      return res;
    } else {
      String res = ctx.NUM_IDENTIFIER().getText().toLowerCase();
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
  public String visitBinLiteralExpr(BinLiteralExprContext ctx) {
    // Preserve BIN literals exactly as written (including any internal spaces).
    return ctx.BIN_LITERAL().getText().toUpperCase();
  }

  @Override
  public String visitNumVarExpr(NumVarExprContext ctx) {
    return ctx.NUM_IDENTIFIER().getText().toLowerCase();
  }

  @Override
  public String visitNumArrayExpr(NumArrayExprContext ctx) {
    return ctx.NUM_IDENTIFIER().getText().toLowerCase()
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
    // Normalise ** to ^ in reformatted output.
    return visit(ctx.numExpr(0)) + " ^ " + visit(ctx.numExpr(1));
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
    return ctx.STR_IDENTIFIER().getText().toLowerCase();
  }

  @Override
  public String visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    return ctx.STR_IDENTIFIER().getText().toLowerCase() + "(" + visit(ctx.strSubscript()) + ")";
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
    final var sb = new StringBuilder();
    if (ctx.indices != null && !ctx.indices.isEmpty()) {
      sb.append(ctx.indices.stream().map(this::visit).collect(Collectors.joining(", ")));
      if (ctx.slice != null) {
        sb.append(", ");
      }
    }
    if (ctx.slice != null) {
      if (ctx.slice.start != null) {
        sb.append(visit(ctx.slice.start)).append(' ');
      }
      sb.append("TO");
      if (ctx.slice.end != null) {
        sb.append(' ').append(visit(ctx.slice.end));
      }
    }
    return sb.toString();
  }

  @Override
  public String visitNumFunc(NumFuncContext ctx) {
    if (ctx.COLOUR() != null) {
      return "COLOUR("
          + visit(ctx.numExpr(0))
          + ", "
          + visit(ctx.numExpr(1))
          + ", "
          + visit(ctx.numExpr(2))
          + ")";
    }
    if (ctx.ATTR() != null) {
      return "ATTR(" + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1)) + ")";
    }
    if (ctx.XATTR() != null) {
      return "XATTR("
          + visit(ctx.numExpr(0))
          + ", "
          + visit(ctx.numExpr(1))
          + ", "
          + visit(ctx.numExpr(2))
          + ")";
    }
    if (ctx.FRAMES() != null) {
      return "FRAMES";
    }
    if (ctx.PI() != null) {
      return "PI";
    }
    if (ctx.PLOTH() != null) {
      return "PLOTH";
    }
    if (ctx.PLOTMODE() != null) {
      return "PLOTMODE";
    }
    if (ctx.PLOTW() != null) {
      return "PLOTW";
    }
    if (ctx.PLOTX() != null) {
      return "PLOTX";
    }
    if (ctx.PLOTY() != null) {
      return "PLOTY";
    }
    if (ctx.POINT() != null) {
      return "POINT(" + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1)) + ")";
    }
    if (ctx.RND() != null) {
      return "RND";
    }
    if (ctx.TEXTH() != null) {
      return "TEXTH";
    }
    if (ctx.TEXTW() != null) {
      return "TEXTW";
    }
    if (ctx.TEXTX() != null) {
      return "TEXTX";
    }
    if (ctx.TEXTY() != null) {
      return "TEXTY";
    }
    if (ctx.UCNEXT() != null) {
      return "UCNEXT(" + visit(ctx.strExpr()) + ", " + visit(ctx.numExpr(0)) + ")";
    }

    final String funcName = ctx.getChild(0).getText().toUpperCase();
    final String arg = visit(ctx.getChild(1));
    return funcName + " " + arg;
  }

  @Override
  public String visitStrFunc(StrFuncContext ctx) {
    if (ctx.INKEY_STR() != null) {
      return "INKEY$";
    }
    if (ctx.SCREEN_STR() != null) {
      return "SCREEN$(" + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1)) + ")";
    }
    if (ctx.UINKEY_STR() != null) {
      return "UINKEY$";
    }
    if (ctx.USCREEN_STR() != null) {
      return "USCREEN$(" + visit(ctx.numExpr(0)) + ", " + visit(ctx.numExpr(1)) + ")";
    }
    final String funcName = ctx.getChild(0).getText().toUpperCase();
    final String arg = visit(ctx.getChild(1));
    return funcName + " " + arg;
  }

  @Override
  public String visitNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      return ctx.NUM_LITERAL().getText();
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      if (ctx.numExpr().isEmpty()) {
        return ctx.NUM_IDENTIFIER().getText().toLowerCase();
      }
      return ctx.NUM_IDENTIFIER().getText().toLowerCase()
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
        return ctx.STR_IDENTIFIER().getText().toLowerCase();
      }
      return ctx.STR_IDENTIFIER().getText().toLowerCase() + "(" + visit(ctx.strSubscript()) + ")";
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
