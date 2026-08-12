package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.exec.ast.AssignTarget;
import com.davidconneely.bazlang.exec.ast.AstFnDefinition;
import com.davidconneely.bazlang.exec.ast.AstProgramSupport;
import com.davidconneely.bazlang.exec.ast.NumExpr;
import com.davidconneely.bazlang.exec.ast.PrintElement;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.exec.ast.StrExpr;
import com.davidconneely.bazlang.exec.ast.StyleItem;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import com.davidconneely.cell.BrailleMode;
import com.davidconneely.cell.CellMode;
import com.davidconneely.cell.HalfCellMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.cell.SextantMode;
import com.davidconneely.repl.BreakException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Walks the typed {@link Stmt} AST via {@code switch} pattern matching, delegating expression
 * evaluation to {@link AstExpressionEvaluator}. Standalone (Phase 2 of {@code
 * localonly-plan-CUSTOM-AST.md}): not wired into the live interpreter, and — unlike the eventual
 * cutover shape — does not read/write {@code EvalState.program()} or {@code EvalState.fn()}/{@code
 * setFn()}. Instead it takes its own AST-flavoured program map (see {@link AstProgramSupport}) and
 * keeps its own {@code DEF FN} store (see {@link AstFnDefinition}), so this class can be built and
 * tested without touching what the still-live {@code StatementExecutor}/{@code ExpressionEvaluator}
 * depend on.
 *
 * <p><strong>Implements sub-phases 2a and 2b</strong> (2a: control flow and program data — {@code
 * CLEAR}/{@code NEW}/{@code LET}/{@code DIM}/{@code FOR}/{@code NEXT}/{@code GOTO}/{@code
 * GOSUB}/{@code RETURN}/{@code IF}/{@code CONT}/{@code STOP}/{@code RUN}/{@code DATA}/{@code
 * READ}/{@code RESTORE}/{@code DEF FN}/{@code REM}; 2b: I/O and graphics — {@code PRINT}/{@code
 * INPUT}/{@code PLOT}/{@code DRAW}/{@code CIRCLE}/{@code PLOTMODE}/the six style statements/ {@code
 * CLS}/{@code SCROLL}/{@code FAST}/{@code SLOW}/{@code PAUSE}). Program-management statements (2c:
 * {@code LOAD}/{@code SAVE}/{@code MERGE}/{@code VERIFY}/{@code LIST}/{@code RAND}) throw {@link
 * UnsupportedOperationException} for now; the {@code switch} below is exhaustive over {@link Stmt}
 * via its {@code default} arm specifically so adding a new {@link Stmt} case is a compile error
 * here until it's either implemented or explicitly deferred, not a silent gap.
 */
public class AstStatementExecutor {
  private final EvalState state;
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final AstExpressionEvaluator exprEvaluator;
  private final NavigableMap<Integer, List<Stmt>> program;
  private final Map<String, AstFnDefinition> fnDefs = new HashMap<>();

  public AstStatementExecutor(
      EvalState state,
      VirtualScreen screen,
      VirtualInput input,
      AstExpressionEvaluator exprEvaluator,
      NavigableMap<Integer, List<Stmt>> program) {
    this.state = state;
    this.screen = screen;
    this.input = input;
    this.exprEvaluator = exprEvaluator;
    this.program = program;
  }

  /**
   * The AST-flavoured program map this executor addresses {@code GOTO}/{@code NEXT}/{@code RESTORE}
   * targets against — separate from {@code EvalState.program()} (see class Javadoc).
   */
  public NavigableMap<Integer, List<Stmt>> program() {
    return program;
  }

  /**
   * This executor's own {@code DEF FN} store — separate from {@code EvalState.fn()} (see class
   * Javadoc and {@link AstFnDefinition}).
   */
  public Map<String, AstFnDefinition> fnDefs() {
    return fnDefs;
  }

  public void execute(Stmt stmt) {
    switch (stmt) {
      case Stmt.ClearStmt _ -> state.clear();
      case Stmt.NewStmt _ -> {
        state.clear();
        program.clear();
      }
      case Stmt.LetStmt s -> executeLetStmt(s);
      case Stmt.DimStmt s -> executeDimStmt(s);
      case Stmt.ForStmt s -> executeForStmt(s);
      case Stmt.NextStmt s -> executeNextStmt(s);
      case Stmt.GotoStmt s -> gotoLabel((int) Math.round(exprEvaluator.evalNum(s.target())));
      case Stmt.GosubStmt s -> executeGosubStmt(s);
      case Stmt.ReturnStmt _ -> executeReturnStmt();
      case Stmt.IfStmt s -> executeIfStmt(s);
      case Stmt.ContStmt _ -> executeContStmt();
      case Stmt.StopStmt _ -> executeStopStmt();
      case Stmt.RunStmt s -> executeRunStmt(s);
      case Stmt.DataStmt _ -> {
        /* no-op: consumed by READ, not executed */
      }
      case Stmt.ReadStmt s -> executeReadStmt(s);
      case Stmt.RestoreStmt s -> executeRestoreStmt(s);
      case Stmt.DefFnStmt s -> executeDefFnStmt(s);
      case Stmt.RemStmt _ -> {
        /* no-op */
      }
      case Stmt.ClsStmt _ -> screen.cls();
      case Stmt.ScrollStmt _ -> screen.scroll();
      case Stmt.FastStmt _ -> screen.setFastMode(true);
      case Stmt.SlowStmt _ -> screen.setFastMode(false);
      case Stmt.InkStmt s -> executeColourStmt(s.value(), screen::setInk, state::setDefaultInk);
      case Stmt.PaperStmt s ->
          executeColourStmt(s.value(), screen::setPaper, state::setDefaultPaper);
      case Stmt.BrightStmt s ->
          executeColourStmt(s.value(), screen::setBright, state::setDefaultBright);
      case Stmt.FlashStmt s ->
          executeColourStmt(s.value(), screen::setFlash, state::setDefaultFlash);
      case Stmt.InverseStmt s ->
          executeColourStmt(s.value(), screen::setInverse, state::setDefaultInverse);
      case Stmt.OverStmt s -> executeColourStmt(s.value(), screen::setOver, state::setDefaultOver);
      case Stmt.PlotmodeStmt s -> executePlotmodeStmt(s);
      case Stmt.PlotStmt s -> executePlotStmt(s);
      case Stmt.DrawStmt s -> executeDrawStmt(s);
      case Stmt.CircleStmt s -> executeCircleStmt(s);
      case Stmt.PrintStmt s -> executePrintStmt(s);
      case Stmt.InputStmt s -> executeInputStmt(s);
      case Stmt.PauseStmt s -> executePauseStmt(s);
      default ->
          throw new UnsupportedOperationException(
              stmt.getClass().getSimpleName() + " not yet implemented (Phase 2c)");
    }
  }

  // ===== LET / DIM =====

  private void executeLetStmt(Stmt.LetStmt stmt) {
    if (stmt.value() instanceof NumExpr numExpr) {
      assignNumTarget(stmt.target(), exprEvaluator.evalNum(numExpr));
    } else if (stmt.value() instanceof StrExpr strExpr) {
      if (!(stmt.target() instanceof AssignTarget.StrTarget strTarget)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric value");
      }
      assignStrTarget(strTarget, exprEvaluator.evalStr(strExpr));
    }
  }

  private void executeDimStmt(Stmt.DimStmt stmt) {
    final int numDims = stmt.dims().size();
    final int[] dims = new int[numDims];
    for (int i = 0; i < numDims; i++) {
      final int d = (int) exprEvaluator.evalNum(stmt.dims().get(i));
      if (d < 1) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      dims[i] = d;
    }
    if (stmt.isString()) {
      state.removeStrVar(stmt.name());
      final int flen = dims[numDims - 1];
      long total = 1;
      final int[] arrDims = new int[numDims - 1];
      System.arraycopy(dims, 0, arrDims, 0, numDims - 1);
      for (int i = 0; i < numDims - 1; i++) {
        total *= dims[i];
        if (total > Limits.MAX_ARRAY_ELEMENTS) {
          throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
        }
      }
      final long totalBytes = total * flen;
      if (totalBytes > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      final byte[] data = new byte[(int) totalBytes];
      Arrays.fill(data, (byte) 32); // Space padded by default
      state.setStrVar(stmt.name(), new EvalState.StrVar.Array(arrDims, flen, data));
    } else {
      long total = 1;
      for (int d : dims) {
        total *= d;
        if (total > Limits.MAX_ARRAY_ELEMENTS) {
          throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
        }
      }
      state.setNumArray(stmt.name(), new EvalState.NumArray(dims, new double[(int) total]));
    }
  }

  // ===== Control flow =====

  private void executeForStmt(Stmt.ForStmt stmt) {
    final double st = exprEvaluator.evalNum(stmt.start());
    final double en = exprEvaluator.evalNum(stmt.end());
    final double step = exprEvaluator.evalNum(stmt.step());
    state.setNumVar(stmt.forVar(), st);
    state.setForLoop(
        stmt.forVar(),
        new EvalState.ForLoopData(
            en, step, state.currentLineLabel(), state.currentStatementIndex()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT (flat scan including IF bodies — see docs/quirks.md
      // "FOR loop flat skip scan").
      final var addr =
          AstProgramSupport.findMatchingNext(
              program, stmt.forVar(), state.currentLineLabel(), state.currentStatementIndex() + 1);
      if (addr == null) {
        throw new ReportException(
            ReportCode.FOR_WITHOUT_NEXT, state.currentLineLabel(), "FOR without NEXT");
      }
      state.setPendingJumpLocation(addr.lineLabel(), addr.statementIndex() + 1);
    }
  }

  private void executeNextStmt(Stmt.NextStmt stmt) {
    final String forVar = stmt.forVar();
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
    final Integer label = program.ceilingKey(searchTarget);
    if (label != null) {
      state.setPendingJumpLocation(label, 1);
    } else {
      state.setRunning(false);
    }
  }

  private void executeGosubStmt(Stmt.GosubStmt stmt) {
    state.pushReturn(
        new EvalState.StatementAddress(
            state.currentLineLabel(), state.currentStatementIndex() + 1));
    gotoLabel((int) Math.round(exprEvaluator.evalNum(stmt.target())));
  }

  private void executeReturnStmt() {
    if (state.isReturnStackEmpty()) {
      throw new ReportException(
          ReportCode.RETURN_WITHOUT_GOSUB, state.currentLineLabel(), "RETURN without GOSUB");
    }
    final var gosubLoc = state.popReturn();
    state.setPendingJumpLocation(gosubLoc.lineLabel(), gosubLoc.statementIndex());
  }

  private void executeIfStmt(Stmt.IfStmt stmt) {
    if (exprEvaluator.evalNum(stmt.condition()) == 0.0) {
      if (state.currentLineLabel() > 0) {
        final Integer nextLabel = program.higherKey(state.currentLineLabel());
        if (nextLabel != null) {
          state.setPendingJumpLocation(nextLabel, 1);
        } else {
          state.setRunning(false); // End of program
        }
      } else {
        state.setPendingJumpLocation(
            0, Integer.MAX_VALUE); // effectively skips the rest of immediate line
      }
    }
  }

  private void executeContStmt() {
    final int m = state.lastReport().lineLabel();
    if (m <= 0) {
      return;
    }
    if (state.lastReport().code() == ReportCode.STOP_STATEMENT
        || state.lastReport().code() == ReportCode.BREAK_INTO_PROGRAM) {
      state.setPendingJumpLocation(m, state.lastReport().statementIndex() + 1);
    } else {
      state.setPendingJumpLocation(m, state.lastReport().statementIndex());
    }
  }

  private void executeStopStmt() {
    state.setRunning(false);
    throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
  }

  private void executeRunStmt(Stmt.RunStmt stmt) {
    final int target =
        stmt.target() != null
            ? (int) Math.round(exprEvaluator.evalNum(stmt.target()))
            : Limits.MIN_TARGET_LABEL;
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE, state.currentLineLabel(), "RUN line label out of range");
    }
    state.clear();
    gotoLabel(target);
  }

  // ===== DATA / READ / RESTORE =====

  private void restoreTo(int target) {
    final var addr = AstProgramSupport.findFirstData(program, target);
    if (addr != null) {
      state.setDataPointer(new EvalState.DataPointer(addr.lineLabel(), addr.statementIndex(), 0));
    } else {
      state.setDataPointer(new EvalState.DataPointer(Integer.MAX_VALUE, -1, -1));
    }
  }

  private void executeReadStmt(Stmt.ReadStmt stmt) {
    for (final var target : stmt.targets()) {
      if (state.dataPointer().lineLabel() == -1) {
        restoreTo(0);
      }
      final int lineLabel = state.dataPointer().lineLabel();
      if (lineLabel == Integer.MAX_VALUE) {
        throw codedException(ReportCode.OUT_OF_DATA, "Out of DATA");
      }
      final var stmts = program.get(lineLabel);
      if (stmts == null) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final int stmtIdx = state.dataPointer().statementIndex();
      if (!(stmts.get(stmtIdx - 1) instanceof Stmt.DataStmt dataStmt)) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final int exprIdx = state.dataPointer().expressionIndex();
      final var exprVal = dataStmt.values().get(exprIdx);

      // Advance pointer before evaluating and assigning
      if (exprIdx + 1 < dataStmt.values().size()) {
        state.setDataPointer(new EvalState.DataPointer(lineLabel, stmtIdx, exprIdx + 1));
      } else {
        // Find next DATA statement in the current line
        boolean found = false;
        for (int i = stmtIdx + 1; i <= stmts.size(); i++) {
          if (stmts.get(i - 1) instanceof Stmt.DataStmt) {
            state.setDataPointer(new EvalState.DataPointer(lineLabel, i, 0));
            found = true;
            break;
          }
        }
        if (!found) {
          final Integer nextLine = program.higherKey(lineLabel);
          if (nextLine != null) {
            restoreTo(nextLine);
          } else {
            state.setDataPointer(new EvalState.DataPointer(Integer.MAX_VALUE, -1, -1));
          }
        }
      }

      // Evaluate and assign
      if (exprVal instanceof NumExpr numExpr) {
        if (target instanceof AssignTarget.StrTarget) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected number");
        }
        assignNumTarget(target, exprEvaluator.evalNum(numExpr));
      } else if (exprVal instanceof StrExpr strExpr) {
        if (!(target instanceof AssignTarget.StrTarget strTarget)) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string");
        }
        assignStrTarget(strTarget, exprEvaluator.evalStr(strExpr));
      }
    }
  }

  private void executeRestoreStmt(Stmt.RestoreStmt stmt) {
    int target = 0;
    if (stmt.target() != null) {
      final double val = exprEvaluator.evalNum(stmt.target());
      target = (int) Math.round(val);
      if (target < 0 || target > Limits.MAX_TARGET_LABEL) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Line label out of range");
      }
    }
    restoreTo(target);
  }

  // ===== DEF FN =====

  private void executeDefFnStmt(Stmt.DefFnStmt stmt) {
    final String name = stmt.name();
    if (name.endsWith("$")) {
      if (!(stmt.body() instanceof StrExpr)) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string expression");
      }
    } else {
      if (!(stmt.body() instanceof NumExpr)) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric expression");
      }
    }
    final var paramSet = new HashSet<String>();
    for (final var p : stmt.params()) {
      if (!paramSet.add(p)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Duplicate parameter name: " + p);
      }
    }
    fnDefs.put(name, new AstFnDefinition(name, stmt.params(), stmt.body()));
  }

  // ===== Style statements =====

  /**
   * Shared shape of the six {@code INK}/{@code PAPER}/{@code BRIGHT}/{@code FLASH}/{@code
   * INVERSE}/{@code OVER} statements: evaluate, then update both the screen's active attribute and
   * {@code EvalState}'s persistent default (unlike an inline styleList/print-item setting, which
   * only ever touches the screen — see {@link StyleItem}'s class Javadoc).
   */
  private void executeColourStmt(NumExpr value, IntConsumer screenSetter, IntConsumer stateSetter) {
    final int colour = (int) exprEvaluator.evalNum(value);
    stateSetter.accept(colour);
    screenSetter.accept(colour);
  }

  private void applyStyleList(List<StyleItem> styles) {
    for (final var style : styles) {
      applyStyleItem(style);
    }
  }

  // Checkstyle's MissingSwitchDefault doesn't recognise enum-switch exhaustiveness and requires a
  // default; PMD's ExhaustiveSwitchHasDefault then flags that same default as redundant. Keep it
  // (Checkstyle wins) and suppress the PMD complaint.
  @SuppressWarnings("PMD.ExhaustiveSwitchHasDefault")
  private void applyStyleItem(StyleItem style) {
    final int value = (int) exprEvaluator.evalNum(style.value());
    switch (style.kind()) {
      case INK -> screen.setInk(value);
      case PAPER -> screen.setPaper(value);
      case BRIGHT -> screen.setBright(value);
      case FLASH -> screen.setFlash(value);
      case INVERSE -> screen.setInverse(value);
      case OVER -> screen.setOver(value);
      default -> throw new IllegalStateException("Unknown style kind: " + style.kind());
    }
  }

  private void withRestoredStyles(Runnable action) {
    // Behaviour-identical to saving and re-applying the six values individually: the defaults
    // cannot change during the action, because style *statements* are what change defaults and
    // they cannot occur inside a PRINT/PLOT item list.
    state.defaultStyles().applyTo(screen);
    try {
      action.run();
    } finally {
      state.defaultStyles().applyTo(screen);
    }
  }

  // ===== Graphics =====

  private void executePlotmodeStmt(Stmt.PlotmodeStmt stmt) {
    final int mode = (int) exprEvaluator.evalNum(stmt.mode());
    final var pixelMode =
        switch (mode) {
          case 1 -> CellMode.INSTANCE;
          case 2 -> HalfCellMode.INSTANCE;
          case 4 -> QuadrantMode.INSTANCE;
          case 6 -> SextantMode.INSTANCE;
          case 8 -> BrailleMode.INSTANCE;
          default ->
              throw codedException(
                  ReportCode.INVALID_ARGUMENT, "Invalid PLOTMODE (use 1, 2, 4, 6, or 8)");
        };
    screen.setPlotMode(pixelMode);
  }

  private void executePlotStmt(Stmt.PlotStmt stmt) {
    withRestoredStyles(
        () -> {
          applyStyleList(stmt.styles());
          try {
            final int x = (int) exprEvaluator.evalNum(stmt.x());
            final int y = (int) exprEvaluator.evalNum(stmt.y());
            screen.plot(x, y);
            state.setGraphicsCursorX(x);
            state.setGraphicsCursorY(y);
          } catch (IllegalArgumentException e) {
            throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
          }
        });
  }

  private void executeDrawStmt(Stmt.DrawStmt stmt) {
    withRestoredStyles(
        () -> {
          applyStyleList(stmt.styles());
          final int dx = (int) Math.round(exprEvaluator.evalNum(stmt.dx()));
          final int dy = (int) Math.round(exprEvaluator.evalNum(stmt.dy()));
          drawLine(
              state.graphicsCursorX(),
              state.graphicsCursorY(),
              state.graphicsCursorX() + dx,
              state.graphicsCursorY() + dy);
        });
  }

  private void drawLine(int startX, int startY, int endX, int endY) {
    int x1 = startX;
    int y1 = startY;
    int diffX = Math.abs(endX - x1);
    int diffY = -Math.abs(endY - y1);
    int sx = x1 < endX ? 1 : -1;
    int sy = y1 < endY ? 1 : -1;
    int err = diffX + diffY;

    while (true) {
      screen.plot(x1, y1);
      if (x1 == endX && y1 == endY) {
        break;
      }
      final int e2 = 2 * err;
      if (e2 >= diffY) {
        err += diffY;
        x1 += sx;
      }
      if (e2 <= diffX) {
        err += diffX;
        y1 += sy;
      }
    }
    state.setGraphicsCursorX(endX);
    state.setGraphicsCursorY(endY);
  }

  private void executeCircleStmt(Stmt.CircleStmt stmt) {
    withRestoredStyles(
        () -> {
          applyStyleList(stmt.styles());
          final int cx = (int) Math.round(exprEvaluator.evalNum(stmt.cx()));
          final int cy = (int) Math.round(exprEvaluator.evalNum(stmt.cy()));
          final int r = (int) Math.round(exprEvaluator.evalNum(stmt.radius()));
          try {
            drawCircle(cx, cy, r);
          } catch (IllegalArgumentException e) {
            throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
          }
          // Like PLOT, CIRCLE leaves the graphics cursor at its centre.
          state.setGraphicsCursorX(cx);
          state.setGraphicsCursorY(cy);
        });
  }

  private void drawCircle(int cx, int cy, int r) {
    if (r <= 0) {
      screen.plot(cx, cy); // Degenerate circle: a single point at the centre.
      return;
    }
    // Midpoint (Bresenham) circle algorithm: compute one octant and mirror to the other seven.
    int x = r;
    int y = 0;
    int err = 1 - r;
    while (x >= y) {
      plotCircleOctants(cx, cy, x, y);
      y++;
      if (err < 0) {
        err += 2 * y + 1;
      } else {
        x--;
        err += 2 * (y - x) + 1;
      }
    }
  }

  private void plotCircleOctants(int cx, int cy, int x, int y) {
    screen.plot(cx + x, cy + y);
    screen.plot(cx - x, cy + y);
    screen.plot(cx + x, cy - y);
    screen.plot(cx - x, cy - y);
    screen.plot(cx + y, cy + x);
    screen.plot(cx - y, cy + x);
    screen.plot(cx + y, cy - x);
    screen.plot(cx - y, cy - x);
  }

  // ===== PRINT =====

  private void executePrintStmt(Stmt.PrintStmt stmt) {
    withRestoredStyles(
        () -> {
          int tabPos = screen.currentCol();
          boolean suppressNewline = false;
          for (final var item : stmt.items()) {
            switch (item) {
              case PrintElement.ValueItem v -> {
                screen.print(exprEvaluator.evalPrintExpr(v.value()));
                tabPos = screen.currentCol();
                suppressNewline = false;
              }
              case PrintElement.AtItem at -> {
                final int row = (int) exprEvaluator.evalNum(at.row());
                final int col = (int) exprEvaluator.evalNum(at.col());
                if (row < 0 || col < 0) {
                  throw codedException(ReportCode.OUT_OF_SCREEN, "Screen out of bounds");
                }
                screen.locate(row, col);
                tabPos = col;
                suppressNewline = false;
              }
              case PrintElement.TabItem tab -> {
                int t = (int) exprEvaluator.evalNum(tab.col());
                if (t < 0) {
                  t = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
                }
                if (t > tabPos) {
                  screen.print(" ".repeat(t - tabPos));
                }
                tabPos = screen.currentCol();
                suppressNewline = false;
              }
              case PrintElement.StyleElement style -> applyStyleItem(style.style());
              case PrintElement.Sep sep -> {
                if (sep.text() == ',') {
                  // Comma moves to next tab stop
                  final int nextTab = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
                  if (nextTab > tabPos) {
                    screen.print(" ".repeat(nextTab - tabPos));
                    tabPos = screen.currentCol();
                  }
                } else if (sep.text() == '\'') {
                  screen.println();
                  tabPos = 0;
                }
                // Semicolon does nothing (items concatenate)
                suppressNewline = true;
              }
            }
          }
          if (!suppressNewline) {
            screen.println();
          }
          screen.flush(); // Ensure output is visible, including semicolon-terminated lines
        });
  }

  // ===== INPUT =====

  private void executeInputStmt(Stmt.InputStmt stmt) {
    final var target = stmt.target();
    final boolean isNumeric =
        target instanceof AssignTarget.NumScalarTarget
            || target instanceof AssignTarget.NumArrayTarget;
    final var mode =
        isNumeric ? VirtualInput.InputMode.INPUT_NUMERIC : VirtualInput.InputMode.INPUT_STRING;
    String line = readInputLine(mode);

    if (isNumeric) {
      // INPUT evaluates the input as a numeric expression; retry on syntax errors
      while (true) {
        try {
          final double val = exprEvaluator.evaluateNumericExpression(line.trim());
          assignNumTarget(target, val);
          break;
        } catch (ReportException e) {
          if (!input.isInteractive() || e.reportCode() != ReportCode.NONSENSE_IN_BASIC) {
            throw e; // Other errors (undefined variable, etc.) or non-interactive screens
          }
          input.prefillInput(line);
          line = readInputLine("Syntax error in expression");
        }
      }
    } else {
      assignStrTarget((AssignTarget.StrTarget) target, BStr.fromJavaString(line));
    }
  }

  private String readInputLine(VirtualInput.InputMode mode) {
    return checkedInputLine(() -> input.readln(mode));
  }

  private String readInputLine(String prompt) {
    return checkedInputLine(() -> input.readln(prompt));
  }

  private String checkedInputLine(Supplier<String> reader) {
    try {
      final String line = reader.get();
      if (line != null && line.trim().equalsIgnoreCase("STOP")) {
        state.setRunning(false);
        throw codedException(ReportCode.STOP_IN_INPUT, ReportCode.STOP_IN_INPUT.getMessage());
      }
      return line != null ? line : "";
    } catch (BreakException e) {
      state.setRunning(false);
      throw codedException(ReportCode.STOP_IN_INPUT, ReportCode.STOP_IN_INPUT.getMessage());
    }
  }

  // ===== PAUSE =====

  private void executePauseStmt(Stmt.PauseStmt stmt) {
    final double frames = exprEvaluator.evalNum(stmt.frames());
    final long totalMs = Math.max(0L, Math.round(frames * 20.0));
    screen.forceFlush();
    long remaining = totalMs;
    while (remaining > 0) {
      final long chunk = Math.min(remaining, 20L);
      try {
        Thread.sleep(chunk);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      remaining -= chunk;
      if (input.pollForBreak()) {
        state.setRunning(false);
        // On a Sinclair ZX Spectrum, pressing BREAK during PAUSE gives report code L (BREAK into
        // program). CONT then advances past the PAUSE to the next statement.
        throw codedException(
            ReportCode.BREAK_INTO_PROGRAM, ReportCode.BREAK_INTO_PROGRAM.getMessage());
      }
    }
  }

  // ===== Assignment helpers =====

  private void assignNumTarget(AssignTarget target, double val) {
    if (target instanceof AssignTarget.NumScalarTarget scalar) {
      var ref = scalar.ref;
      if (ref == null) {
        ref = state.getOrAddNumVar(scalar.name);
        scalar.ref = ref;
      }
      ref.value = val;
      ref.initialised = true;
    } else if (target instanceof AssignTarget.NumArrayTarget array) {
      var ref = array.ref;
      if (ref == null) {
        ref = state.getOrAddNumArray(array.name);
        array.ref = ref;
      }
      if (ref.array == null) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + array.name);
      }
      final var na = ref.array;
      final int count = array.indices.size();
      final int[] indices = new int[count];
      for (int i = 0; i < count; i++) {
        indices[i] = (int) exprEvaluator.evalNum(array.indices.get(i));
      }
      final int idx = exprEvaluator.calculateArrayIndex(na.dimensions(), indices, 0, count);
      na.data()[idx] = val;
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric target");
    }
  }

  private void assignStrTarget(AssignTarget.StrTarget target, BStr val) {
    var ref = target.ref;
    if (ref == null) {
      ref = state.getOrAddStrVar(target.name);
      target.ref = ref;
    }
    final var subscript = target.subscript;
    if (subscript == null) {
      assignStrSubscriptNull(ref, val);
      return;
    }

    final int indicesCount = subscript.indices().size();
    final int[] indices = new int[indicesCount];
    for (int i = 0; i < indicesCount; i++) {
      indices[i] = (int) exprEvaluator.evalNum(subscript.indices().get(i));
    }
    int sliceStart = -1;
    int sliceEnd = -1;
    final boolean hasSlice = subscript.slice() != null;
    if (hasSlice) {
      if (subscript.slice().start() != null) {
        sliceStart = (int) exprEvaluator.evalNum(subscript.slice().start());
      }
      if (subscript.slice().end() != null) {
        sliceEnd = (int) exprEvaluator.evalNum(subscript.slice().end());
      }
    }

    final var strVar = ref.value;
    if (strVar instanceof EvalState.StrVar.Array ca) {
      assignStrArrayTarget(ca, val, indices, indicesCount, sliceStart, sliceEnd);
    } else if (strVar instanceof EvalState.StrVar.Scalar scalar) {
      assignStrScalarTarget(
          ref, scalar, val, hasSlice, indices, indicesCount, sliceStart, sliceEnd);
    } else {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + target.name);
    }
  }

  private void assignStrSubscriptNull(EvalState.StrVarRef ref, BStr val) {
    final var strVar = ref.value;
    if (strVar
        instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
      if (arrayDimensions.length != 0) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      final int copyLen = Math.min(stringLength, val.length());
      for (int i = 0; i < copyLen; i++) {
        data[i] = (byte) val.byteAt(i);
      }
      for (int i = copyLen; i < stringLength; i++) {
        data[i] = (byte) 32;
      }
    } else {
      ref.value = new EvalState.StrVar.Scalar(val.copy());
    }
  }

  private void assignStrArrayTarget(
      EvalState.StrVar.Array ca,
      BStr val,
      int[] indices,
      int indicesCount,
      int sliceStart,
      int sliceEnd) {
    final int n = ca.arrayDimensions().length;
    int byteIndex = -1;
    int count = indicesCount;
    if (count == n + 1) {
      byteIndex = indices[n];
      count--;
    }
    final int arrayIdx = exprEvaluator.calculateArrayIndex(ca.arrayDimensions(), indices, 0, count);

    final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, ca.stringLength());
    if (bounds == null) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }

    final int sliceLen = bounds.length();
    final int copyLen = Math.min(sliceLen, val.length());
    final int offset = arrayIdx * ca.stringLength() + (bounds.start() - 1);
    for (int i = 0; i < copyLen; i++) {
      ca.data()[offset + i] = (byte) val.byteAt(i);
    }
    for (int i = copyLen; i < sliceLen; i++) {
      ca.data()[offset + i] = (byte) 32;
    }
  }

  private void assignStrScalarTarget(
      EvalState.StrVarRef ref,
      EvalState.StrVar.Scalar scalar,
      BStr val,
      boolean hasSlice,
      int[] indices,
      int indicesCount,
      int sliceStart,
      int sliceEnd) {
    final var str = scalar.value();
    int byteIndex = -1;
    int count = indicesCount;
    if (count == 1 && !hasSlice) {
      byteIndex = indices[0];
      count--;
    }
    if (count > 0) {
      throw codedException(
          ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
    }

    final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, str.length());
    if (bounds == null) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }

    ref.value = new EvalState.StrVar.Scalar(str.withSlice(bounds.start(), bounds.end(), val));
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
