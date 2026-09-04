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
 * EVERY} break conditions (see docs/spec/mcp.md). Public so it is reusable from the {@code
 * com.davidconneely.bazlang.mcp} package.
 */
public final class BreakpointEngine {

  /** Which kind of break condition a {@link BreakCondition} carries, beyond a plain location. */
  public enum ConditionType {
    /** A plain location breakpoint, with no extra condition. */
    NONE,
    /** A {@code CSC} view-only marker; never itself pauses execution. */
    VIEW,
    /** An {@code ELAPSE} wall-clock condition. */
    ELAPSE,
    /** A {@code ?expr} conditional breakpoint. */
    EXPR,
    /** An {@code EVERY} counting breakpoint, firing every {@code everyN}th hit. */
    EVERY
  }

  /**
   * One registered breakpoint.
   *
   * @param line the line to break at.
   * @param stmt the flat statement index to break at.
   * @param type which kind of condition, beyond the plain location, this carries.
   * @param seeText for {@link ConditionType#VIEW}, the screen text to watch for; for {@link
   *     ConditionType#EXPR}, the condition expression's source text.
   * @param timeoutMs for {@link ConditionType#ELAPSE}, the wall-clock delay in milliseconds.
   * @param persistent whether this breakpoint survives past its first hit.
   * @param everyN for {@link ConditionType#EVERY}, how many hits between firings.
   * @param counter for {@link ConditionType#EVERY}, the running hit count.
   */
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

  /**
   * Registers a breakpoint.
   *
   * @param brk the breakpoint to add.
   */
  public void add(BreakCondition brk) {
    activeBreaks.add(brk);
  }

  /** Removes every registered breakpoint whose {@link BreakCondition#persistent()} is true. */
  public void clearPersistent() {
    activeBreaks.removeIf(BreakCondition::persistent);
  }

  /**
   * Removes every breakpoint registered at the given location.
   *
   * @param line the line to clear breakpoints at.
   * @param stmt the flat statement index to clear breakpoints at.
   */
  public void clearAt(int line, int stmt) {
    activeBreaks.removeIf(b -> b.line() == line && b.stmt() == stmt);
  }

  /**
   * A read-only snapshot of every currently-active breakpoint, in registration order.
   *
   * @return the snapshot.
   */
  public List<BreakCondition> list() {
    return List.copyOf(activeBreaks);
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
