package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;

public class ProgramManager extends StatementExecutor {
  private static final AntlrParser PARSER = new AntlrParser();

  public ProgramManager(EvalState state, BazLangDisplay display) {
    super(
        state,
        display,
        new ProgramStorage(state, PARSER),
        new ExpressionEvaluator(state, display, PARSER));
  }

  @Override
  public Object visitContStmt(ContStmtContext ctx) {
    int m = state.lastReportLabel();
    if (m <= 0) {
      return null;
    }
    if (state.lastReportCode() == ReportCode.STOP_STATEMENT) {
      state.setPendingJumpLabel(state.program().higherKey(m));
    } else {
      state.setPendingJumpLabel(m);
    }
    return null;
  }

  @Override
  public Object visitForStmt(ForStmtContext ctx) {
    String var = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    double st = exprEvaluator.evalNum(ctx.numExpr(0));
    double en = exprEvaluator.evalNum(ctx.numExpr(1));
    double step = ctx.numExpr().size() > 2 ? exprEvaluator.evalNum(ctx.numExpr(2)) : 1.0;
    state.setNumVar(var, st);
    state.setForLoop(var, new EvalState.ForLoopData(en, step, state.currentLineLabel()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT
      Integer nextLabel = state.program().higherKey(state.currentLineLabel());
      while (nextLabel != null) {
        ProgramLine line = state.program().get(nextLabel);
        StatementContext stmt = line.getStatement(PARSER);
        if (stmt instanceof NextStmtContext nextCtx
            && nextCtx.NUM_IDENTIFIER().getText().equalsIgnoreCase(var)) {
          state.setPendingJumpLabel(state.program().higherKey(nextLabel));
          return null;
        }
        nextLabel = state.program().higherKey(nextLabel);
      }
      state.setRunning(false);
    }
    return null;
  }

  @Override
  public Object visitGosubStmt(GosubStmtContext ctx) {
    state.pushReturn(state.currentLineLabel());
    int target = (int) Math.round(exprEvaluator.evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  @Override
  public Object visitGotoStmt(GotoStmtContext ctx) {
    int target = (int) Math.round(exprEvaluator.evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  private void gotoLabel(int target) {
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          state.currentLineLabel(),
          "GOTO line label out of range");
    }
    Integer label = state.program().ceilingKey(target);
    if (label != null) {
      state.setPendingJumpLabel(label);
    } else {
      state.setRunning(false);
    }
  }

  @Override
  public Object visitNextStmt(NextStmtContext ctx) {
    String var = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.hasForLoop(var)) {
      throw new ReportException(
          ReportCode.NEXT_WITHOUT_FOR, state.currentLineLabel(), "NEXT without FOR");
    }
    EvalState.ForLoopData d = state.forLoop(var);
    if (!state.hasNumVar(var)) {
      throw new ReportException(
          ReportCode.VARIABLE_NOT_FOUND, state.currentLineLabel(), "Undefined loop variable");
    }
    double nv = state.numVar(var) + d.step();
    state.setNumVar(var, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      state.setPendingJumpLabel(state.program().higherKey(d.loopPc()));
    }
    return null;
  }

  @Override
  public Object visitReturnStmt(ReturnStmtContext ctx) {
    if (state.isReturnStackEmpty()) {
      throw new ReportException(
          ReportCode.RETURN_WITHOUT_GOSUB, state.currentLineLabel(), "RETURN without GOSUB");
    }
    Integer gosubLine = state.popReturn();
    state.setPendingJumpLabel(state.program().higherKey(gosubLine));
    return null;
  }

  @Override
  public Object visitRunStmt(RunStmtContext ctx) {
    int target =
        ctx.numExpr() != null
            ? (int) Math.round(exprEvaluator.evalNum(ctx.numExpr()))
            : Limits.MIN_TARGET_LABEL;
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE, state.currentLineLabel(), "RUN line label out of range");
    }
    state.clear();
    gotoLabel(target);
    return null;
  }
}
