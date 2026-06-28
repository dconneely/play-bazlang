package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;

public class ProgramManager extends StatementExecutor {
  private static final AntlrParser PARSER = AntlrParser.INSTANCE;

  public ProgramManager(EvalState state, BazLangDisplay display) {
    super(
        state,
        display,
        new ProgramStorage(state, PARSER),
        new ExpressionEvaluator(state, display, PARSER));
  }

  @Override
  public Void visitContStmt(ContStmtContext ctx) {
    final int m = state.lastReportLabel();
    if (m <= 0) {
      return null;
    }
    if (state.lastReportCode() == ReportCode.STOP_STATEMENT
        || state.lastReportCode() == ReportCode.BREAK_INTO_PROGRAM) {
      state.setPendingJumpLocation(m, state.lastReportStatementIndex() + 1);
    } else {
      state.setPendingJumpLocation(m, state.lastReportStatementIndex());
    }
    return null;
  }

  @Override
  public Void visitForStmt(ForStmtContext ctx) {
    final String forVar = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    final double st = exprEvaluator.evalNum(ctx.numExpr(0));
    final double en = exprEvaluator.evalNum(ctx.numExpr(1));
    final double step = ctx.numExpr().size() > 2 ? exprEvaluator.evalNum(ctx.numExpr(2)) : 1.0;
    state.setNumVar(forVar, st);
    state.setForLoop(
        forVar,
        new EvalState.ForLoopData(
            en, step, state.currentLineLabel(), state.currentStatementIndex()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT.
      // On a Sinclair ZX Spectrum, the scan is a flat, linear pass through all statements in
      // program order, including those nested inside IF...THEN bodies. So `IF 0 THEN NEXT i` is
      // still found by the scan even though the condition is always false. We use
      // getFlattenedStatements() to replicate this behaviour, which is also consistent with the
      // flat statement indices used by Interpreter.resume().
      Integer searchLabel = state.currentLineLabel();
      int startIdx = state.currentStatementIndex() + 1;
      while (searchLabel != null) {
        final var line = state.program().get(searchLabel);
        if (line != null) {
          final var flatStmts = line.getFlattenedStatements(PARSER);
          int stmtIdx = 1;
          for (final var stmt : flatStmts) {
            if (stmtIdx >= startIdx
                && stmt instanceof NextStmtContext nextCtx
                && nextCtx.NUM_IDENTIFIER().getText().equalsIgnoreCase(forVar)) {
              state.setPendingJumpLocation(searchLabel, stmtIdx + 1);
              return null;
            }
            stmtIdx++;
          }
        }
        searchLabel = state.program().higherKey(searchLabel);
        startIdx = 1; // For subsequent lines, check all statements
      }
      throw new ReportException(
          ReportCode.FOR_WITHOUT_NEXT, state.currentLineLabel(), "FOR without NEXT");
    }
    return null;
  }

  @Override
  public Void visitGosubStmt(GosubStmtContext ctx) {
    state.pushReturn(
        new EvalState.JumpLocation(state.currentLineLabel(), state.currentStatementIndex() + 1));
    final int target = (int) Math.round(exprEvaluator.evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  @Override
  public Void visitGotoStmt(GotoStmtContext ctx) {
    final int target = (int) Math.round(exprEvaluator.evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  private void gotoLabel(int target) {
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          state.currentLineLabel(),
          "GO TO line label out of range");
    }
    // Prevent jumping to line 0 (the immediate statement buffer)
    final int searchTarget = Math.max(target, Limits.MIN_LINE_LABEL);
    final Integer label = state.program().ceilingKey(searchTarget);
    if (label != null) {
      state.setPendingJumpLocation(label, 1);
    } else {
      state.setRunning(false);
    }
  }

  @Override
  public Void visitNextStmt(NextStmtContext ctx) {
    final String forVar = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.hasForLoop(forVar)) {
      throw new ReportException(
          ReportCode.NEXT_WITHOUT_FOR, state.currentLineLabel(), "NEXT without FOR");
    }
    final var d = state.forLoop(forVar);
    if (!state.hasNumVar(forVar)) {
      throw new ReportException(
          ReportCode.VARIABLE_NOT_FOUND, state.currentLineLabel(), "Undefined loop variable");
    }
    final double nv = state.numVar(forVar) + d.step();
    state.setNumVar(forVar, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      state.setPendingJumpLocation(d.loopPcLabel(), d.loopPcStatementIndex() + 1);
    }
    return null;
  }

  @Override
  public Void visitReturnStmt(ReturnStmtContext ctx) {
    if (state.isReturnStackEmpty()) {
      throw new ReportException(
          ReportCode.RETURN_WITHOUT_GOSUB, state.currentLineLabel(), "RETURN without GOSUB");
    }
    final var gosubLoc = state.popReturn();
    state.setPendingJumpLocation(gosubLoc.lineLabel(), gosubLoc.statementIndex());
    return null;
  }

  @Override
  public Void visitRunStmt(RunStmtContext ctx) {
    final int target =
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
