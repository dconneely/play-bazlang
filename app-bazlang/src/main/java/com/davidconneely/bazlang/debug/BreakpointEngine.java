package com.davidconneely.bazlang.debug;

import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.AstAnnotator;
import com.davidconneely.bazlang.exec.ExpressionEvaluator;
import com.davidconneely.bazlang.io.MockScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The AgentDebugger's breakpoint store and evaluation engine: persistent and one-shot breakpoints,
 * optional location filters, and the {@code CSC}/{@code ELAPSE}/{@code ?expr}/{@code EVERY} break
 * conditions (see docs/language_debugger.md).
 */
final class BreakpointEngine {

  enum ConditionType {
    NONE,
    VIEW,
    ELAPSE,
    EXPR,
    EVERY
  }

  record BreakCondition(
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

  /** Parses a condition string; returns null when it is not a valid condition. */
  static BreakCondition parseCondition(int line, int stmt, String cond, boolean persistent) {
    String upper = cond.toUpperCase();
    if (upper.startsWith("CSC")) {
      String quotedArg = cond.substring(3).trim();
      String text = QuotedArg.parse(quotedArg);
      if (text == null) {
        return null;
      }
      return new BreakCondition(line, stmt, ConditionType.VIEW, text, 0, persistent, 0, null);
    }
    if (upper.startsWith("ELAPSE")) {
      String rest = cond.substring(6).trim();
      if (rest.isEmpty()) {
        return null;
      }
      try {
        long ms = Long.parseLong(rest);
        return new BreakCondition(line, stmt, ConditionType.ELAPSE, null, ms, persistent, 0, null);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    if (cond.startsWith("?")) {
      String exprSource = cond.substring(1).trim();
      if (exprSource.isEmpty()) {
        return null;
      }
      return new BreakCondition(line, stmt, ConditionType.EXPR, exprSource, 0, persistent, 0, null);
    }
    if (upper.startsWith("EVERY")) {
      String rest = cond.substring(5).trim();
      if (rest.isEmpty()) {
        return null;
      }
      try {
        int n = Integer.parseInt(rest);
        if (n <= 0) {
          return null;
        }
        return new BreakCondition(
            line, stmt, ConditionType.EVERY, null, 0, persistent, n, new AtomicInteger());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  void add(BreakCondition brk) {
    activeBreaks.add(brk);
  }

  void clearPersistent() {
    activeBreaks.removeIf(BreakCondition::persistent);
  }

  void clearAt(int line, int stmt) {
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
                new AstAnnotator(0).visit(numCtx);
                yield eval.evalNum(numCtx) != 0.0;
              } catch (ReportException e) {
                try {
                  var strCtx = parser.parseStrExpr(brk.seeText());
                  new AstAnnotator(0).visit(strCtx);
                  yield !eval.evalStr(strCtx).isEmpty();
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
