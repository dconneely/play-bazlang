package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The breakpoint store and evaluation engine used by the MCP server: persistent and one-shot
 * breakpoints, optional location filters, and the {@code CSC}/{@code ELAPSE}/{@code ?expr}/{@code
 * EVERY} break conditions (see docs/mcp_server.md). Public so it is reusable from the {@code
 * com.davidconneely.bazlang.mcp} package.
 */
public final class BreakpointEngine {

  public enum ConditionType {
    NONE,
    VIEW,
    ELAPSE,
    EXPR,
    EVERY
  }

  public record BreakCondition(
      int line,
      int stmt,
      ConditionType type,
      String seeText,
      long timeoutMs,
      boolean persistent,
      int everyN,
      AtomicInteger counter) {}

  private final AntlrParser parser;
  private final List<BreakCondition> activeBreaks = new ArrayList<>();
  private final AtomicLong continueStartMs = new AtomicLong(System.currentTimeMillis());

  BreakpointEngine(AntlrParser parser) {
    this.parser = parser;
  }

  public void add(BreakCondition brk) {
    activeBreaks.add(brk);
  }

  public void clearPersistent() {
    activeBreaks.removeIf(BreakCondition::persistent);
  }

  public void clearAt(int line, int stmt) {
    activeBreaks.removeIf(b -> b.line() == line && b.stmt() == stmt);
  }

  /** Restarts the {@code ELAPSE} clock (called when execution resumes). */
  void resetTimer() {
    continueStartMs.set(System.currentTimeMillis());
  }

  /**
   * Checks all breakpoints against the statement about to execute at (line, stmt). Returns the
   * first that fires (removing it if one-shot), or null. Breakpoints after the fired one are not
   * evaluated, so their {@code EVERY} counters do not advance.
   */
  BreakCondition checkFired(int line, int stmt, MockScreen mockScreen, ExpressionEvaluator eval) {
    List<BreakCondition> toCheck = new ArrayList<>(activeBreaks);
    BreakCondition firedBreak = null;
    for (BreakCondition brk : toCheck) {
      if (firedBreak != null) {
        continue;
      }
      boolean locMatch =
          (brk.line() == -1 || brk.line() == line) && (brk.stmt() == -1 || brk.stmt() == stmt);
      if (!locMatch) {
        continue;
      }
      boolean condMet =
          switch (brk.type()) {
            case NONE -> true;
            case VIEW -> ScreenText.containsText(mockScreen, brk.seeText());
            case ELAPSE -> (System.currentTimeMillis() - continueStartMs.get()) >= brk.timeoutMs();
            case EXPR -> {
              try {
                var numCtx = parser.parseNumExpr(brk.seeText());
                yield eval.evalNum(AstLowering.lowerNum(numCtx, 0)) != 0.0;
              } catch (ReportException e) {
                try {
                  var strCtx = parser.parseStrExpr(brk.seeText());
                  yield !eval.evalStr(AstLowering.lowerStr(strCtx, 0)).isEmpty();
                } catch (ReportException e2) {
                  yield false;
                }
              }
            }
            case EVERY -> brk.counter().incrementAndGet() % brk.everyN() == 0;
          };
      if (condMet) {
        firedBreak = brk;
      }
    }
    if (firedBreak != null && !firedBreak.persistent()) {
      activeBreaks.remove(firedBreak);
    }
    return firedBreak;
  }
}
