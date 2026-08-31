package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangParser.NumExprContext;
import com.davidconneely.bazlang.exec.ast.AssignTarget;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.NumExpr;
import com.davidconneely.bazlang.exec.ast.PrintElement;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.exec.ast.StrExpr;
import com.davidconneely.bazlang.exec.ast.StyleItem;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import com.davidconneely.bazlang.io.VirtualSpeaker;
import com.davidconneely.bazlang.play.PlayParser;
import com.davidconneely.bazlang.play.PlaySource;
import com.davidconneely.cell.BrailleMode;
import com.davidconneely.cell.CellMode;
import com.davidconneely.cell.HalfCellMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.cell.SextantMode;
import com.davidconneely.repl.BreakException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Walks the typed {@link Stmt} AST via {@code switch} pattern matching, delegating expression
 * evaluation to {@link ExpressionEvaluator}. Replaced the original ANTLR-visitor-based executor at
 * the Phase 4 cutover (see {@code localonly-plan-CUSTOM-AST.md}).
 *
 * <p>{@code switch (stmt)} in {@link #execute} is exhaustive over the sealed {@link Stmt} with no
 * {@code default} arm, so adding a new {@code Stmt} case is a compile error here, not a silent gap.
 */
public class StatementExecutor {
  private final EvalState state;
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final VirtualSpeaker speaker;
  private final ProgramStorage storage;
  private final ExpressionEvaluator exprEvaluator;
  private final AntlrParser parser;

  public StatementExecutor(
      EvalState state, VirtualScreen screen, VirtualInput input, VirtualSpeaker speaker) {
    this(
        state,
        screen,
        input,
        speaker,
        new ProgramStorage(state, AntlrParser.INSTANCE),
        new ExpressionEvaluator(state, screen, input, AntlrParser.INSTANCE),
        AntlrParser.INSTANCE);
  }

  public StatementExecutor(
      EvalState state,
      VirtualScreen screen,
      VirtualInput input,
      VirtualSpeaker speaker,
      ProgramStorage storage,
      ExpressionEvaluator exprEvaluator,
      AntlrParser parser) {
    this.state = state;
    this.screen = screen;
    this.input = input;
    this.speaker = speaker;
    this.storage = storage;
    this.exprEvaluator = exprEvaluator;
    this.parser = parser;
  }

  public VirtualScreen screen() {
    return screen;
  }

  public VirtualInput input() {
    return input;
  }

  public VirtualSpeaker speaker() {
    return speaker;
  }

  public ExpressionEvaluator exprEvaluator() {
    return exprEvaluator;
  }

  /**
   * Evaluates an already-parsed ANTLR {@code numExpr} - used by {@code ProgramEditor} and the
   * {@code EDIT} REPL command, which (by design; see {@code localonly-plan-CUSTOM-AST.md} Phase 0)
   * still work directly off raw parse trees. Lowers fresh on every call, same "parse/lower fresh
   * each time" shape as {@code VAL}/{@code INPUT}.
   */
  public double evalNum(NumExprContext ctx) {
    return exprEvaluator.evalNum(AstLowering.lowerNum(ctx, state.currentLineLabel()));
  }

  public ControlFlow execute(Stmt stmt) {
    ControlFlow flow = ControlFlow.CONTINUE;
    switch (stmt) {
      case Stmt.ClearStmt _ -> state.clear();
      case Stmt.NewStmt _ -> {
        state.clear();
        state.program().clear();
      }
      case Stmt.LetStmt s -> executeLetStmt(s);
      case Stmt.DimStmt s -> executeDimStmt(s);
      case Stmt.ForStmt s -> flow = executeForStmt(s);
      case Stmt.NextStmt s -> flow = executeNextStmt(s);
      case Stmt.GotoStmt s -> flow = gotoLabel((int) Math.round(exprEvaluator.evalNum(s.target())));
      case Stmt.GosubStmt s -> flow = executeGosubStmt(s);
      case Stmt.ReturnStmt _ -> flow = executeReturnStmt();
      case Stmt.IfStmt s -> flow = executeIfStmt(s);
      case Stmt.ContStmt _ -> flow = executeContStmt();
      case Stmt.StopStmt _ -> executeStopStmt();
      case Stmt.RunStmt s -> flow = executeRunStmt(s);
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
      case Stmt.BeepStmt s -> executeBeepStmt(s);
      case Stmt.PlayStmt s -> executePlayStmt(s);
      case Stmt.AplayStmt s -> executeAplayStmt(s);
      case Stmt.RandStmt s -> executeRandStmt(s);
      case Stmt.ListStmt s -> executeListStmt(s);
      case Stmt.LoadStmt s -> storage.load(exprEvaluator.evalStr(s.fileName()).toJavaString());
      case Stmt.MergeStmt s -> storage.merge(exprEvaluator.evalStr(s.fileName()).toJavaString());
      case Stmt.SaveStmt s -> storage.save(exprEvaluator.evalStr(s.fileName()).toJavaString());
      case Stmt.VerifyStmt s -> storage.verify(exprEvaluator.evalStr(s.fileName()).toJavaString());
    }
    return flow;
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

  private ControlFlow executeForStmt(Stmt.ForStmt stmt) {
    final double st = exprEvaluator.evalNum(stmt.start());
    final double en = exprEvaluator.evalNum(stmt.end());
    final double step = exprEvaluator.evalNum(stmt.step());
    state.setNumVar(stmt.forVar(), st);
    state.setForLoop(
        stmt.forVar(),
        new EvalState.ForLoopData(
            en, step, state.currentLineLabel(), state.currentStatementIndex()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT (flat scan including IF bodies - see docs/quirks.md
      // "FOR loop flat skip scan").
      final var addr =
          state
              .program()
              .findMatchingNext(
                  stmt.forVar(),
                  state.currentLineLabel(),
                  state.currentStatementIndex() + 1,
                  parser);
      if (addr == null) {
        throw codedException(ReportCode.FOR_WITHOUT_NEXT, "FOR without NEXT");
      }
      return new ControlFlow.Jump(addr.lineLabel(), addr.statementIndex() + 1);
    }
    return ControlFlow.CONTINUE;
  }

  private ControlFlow executeNextStmt(Stmt.NextStmt stmt) {
    final String forVar = stmt.forVar();
    if (!state.hasForLoop(forVar)) {
      throw codedException(ReportCode.NEXT_WITHOUT_FOR, "NEXT without FOR");
    }
    final var d = state.forLoop(forVar);
    if (!state.hasNumVar(forVar)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined loop variable");
    }
    final double nv = state.numVar(forVar) + d.step();
    state.setNumVar(forVar, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      return new ControlFlow.Jump(d.loopPcLabel(), d.loopPcStatementIndex() + 1);
    }
    return ControlFlow.CONTINUE;
  }

  private ControlFlow gotoLabel(int target) {
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "GO TO line label out of range");
    }
    // Prevent jumping to line 0 (the immediate statement buffer)
    final int searchTarget = Math.max(target, Limits.MIN_LINE_LABEL);
    final Integer label = state.program().ceilingKey(searchTarget);
    return (label != null) ? new ControlFlow.Jump(label, 1) : ControlFlow.END_OF_PROGRAM;
  }

  private ControlFlow executeGosubStmt(Stmt.GosubStmt stmt) {
    state.pushReturn(
        new EvalState.StatementAddress(
            state.currentLineLabel(), state.currentStatementIndex() + 1));
    return gotoLabel((int) Math.round(exprEvaluator.evalNum(stmt.target())));
  }

  private ControlFlow executeReturnStmt() {
    if (state.isReturnStackEmpty()) {
      throw codedException(ReportCode.RETURN_WITHOUT_GOSUB, "RETURN without GOSUB");
    }
    final var gosubLoc = state.popReturn();
    return new ControlFlow.Jump(gosubLoc.lineLabel(), gosubLoc.statementIndex());
  }

  private ControlFlow executeIfStmt(Stmt.IfStmt stmt) {
    if (exprEvaluator.evalNum(stmt.condition()) != 0.0) {
      return ControlFlow.CONTINUE;
    }
    if (state.currentLineLabel() > 0) {
      final Integer nextLabel = state.program().higherKey(state.currentLineLabel());
      return (nextLabel != null) ? new ControlFlow.Jump(nextLabel, 1) : ControlFlow.END_OF_PROGRAM;
    }
    // Immediate mode: effectively skips the rest of the immediate line.
    return new ControlFlow.Jump(0, Integer.MAX_VALUE);
  }

  private ControlFlow executeContStmt() {
    final int m = state.lastReport().lineLabel();
    if (m <= 0) {
      // Confirmed against real ZX80/ZX81/ZX Spectrum hardware: CONT with nothing to continue (the
      // "0 OK, 0:1" state NEW/power-on leaves lastReport in) is a silent no-op, not an error.
      return ControlFlow.CONTINUE;
    }
    final int index =
        (state.lastReport().code() == ReportCode.STOP_STATEMENT
                || state.lastReport().code() == ReportCode.BREAK_INTO_PROGRAM)
            ? state.lastReport().statementIndex() + 1
            : state.lastReport().statementIndex();
    return new ControlFlow.Jump(m, index);
  }

  private void executeStopStmt() {
    state.setRunning(false);
    throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
  }

  private ControlFlow executeRunStmt(Stmt.RunStmt stmt) {
    final int target =
        stmt.target() != null
            ? (int) Math.round(exprEvaluator.evalNum(stmt.target()))
            : Limits.MIN_TARGET_LABEL;
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "RUN line label out of range");
    }
    state.clear();
    return gotoLabel(target);
  }

  // ===== DATA / READ / RESTORE =====

  private void restoreTo(int target) {
    final var addr = state.program().findFirstData(target, parser);
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
      final var line = state.program().get(lineLabel);
      if (line == null) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final var stmts = line.getFlattenedStatements(parser);
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
          final Integer nextLine = state.program().higherKey(lineLabel);
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
    if (stmt.params().stream().distinct().count() != stmt.params().size()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Duplicate parameter name");
    }
    state.setFn(name, new EvalState.FnDefinition(name, stmt.params(), stmt.body()));
  }

  // ===== Style statements =====

  /**
   * Shared shape of the six {@code INK}/{@code PAPER}/{@code BRIGHT}/{@code FLASH}/{@code
   * INVERSE}/{@code OVER} statements: evaluate, then update both the screen's active attribute and
   * {@code EvalState}'s persistent default (unlike an inline styleList/print-item setting, which
   * only ever touches the screen - see {@link StyleItem}'s class Javadoc).
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

  // ===== BEEP =====

  private void executeBeepStmt(Stmt.BeepStmt stmt) {
    final double duration = exprEvaluator.evalNum(stmt.duration());
    final double pitch = exprEvaluator.evalNum(stmt.pitch());
    final long totalMs = Math.max(0L, Math.round(duration * 1000.0));
    if (totalMs == 0L) {
      return;
    }
    // speaker.beep() is expected to start playback and return promptly (see VirtualSpeaker's
    // class doc) rather than block for the tone's duration, so this thread stays free to run the
    // same chunked wait/BREAK-poll loop PAUSE uses, rather than being stuck inside a blocking
    // audio write for the full duration with no chance to notice BREAK.
    speaker.beep(duration, pitch);
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
        speaker.stopBeep();
        state.setRunning(false);
        // Mirrors PAUSE: pressing BREAK mid-tone gives report code L (BREAK into program); CONT
        // then advances past the BEEP to the next statement.
        throw codedException(
            ReportCode.BREAK_INTO_PROGRAM, ReportCode.BREAK_INTO_PROGRAM.getMessage());
      }
    }
  }

  // ===== PLAY / APLAY =====

  // Seconds requested per pull from the PlaySource, matching BEEP/PAUSE's own 20ms chunk cadence.
  private static final double PLAY_CHUNK_SECONDS = 0.02;
  private static final long PLAY_CHUNK_MS = Math.round(PLAY_CHUNK_SECONDS * 1000.0);

  // A running background APLAY, bundled with enough to both target a per-channel update at it
  // (source) and stop it (stop/thread). null when no APLAY has ever run, or the last one already
  // fully replaced by a fresh PLAY/APLAY. isAlive() -- not just "was a stop requested" -- is what
  // executeAplayStmt checks before attempting a partial update: a session whose thread already
  // exited on its own (tune finished, or a prior stop already took effect) has nothing left
  // pulling from `source`, so replaceChannel-ing it would silently have no audible effect at all.
  private record AplaySession(Thread thread, PlaySource source, AtomicBoolean stop) {
    boolean isAlive() {
      return thread.isAlive();
    }
  }

  private volatile AplaySession activeAplay;

  /**
   * Whether a background APLAY session's thread is still alive. Package-visible purely as a test
   * hook: it's otherwise unobservable from outside that the thread deliberately never exits just
   * because it ran out of notes (see the comment where {@code frame.finished()} is handled in
   * {@link #startNewAplaySession}) - exiting there would race {@link #executeAplayStmt}'s own
   * {@code isAlive()} check and could silently drop a channel update targeted at this session.
   */
  boolean aplaySessionIsAlive() {
    final AplaySession current = activeAplay;
    return current != null && current.isAlive();
  }

  private List<String> playChannelStrings(List<StrExpr> channels) {
    return channels.stream().map(e -> exprEvaluator.evalStr(e).toJavaString()).toList();
  }

  /** Stops any background APLAY and silences whatever PLAY/APLAY audio is currently sounding. */
  public void stopBackgroundAudio() {
    final AplaySession current = activeAplay;
    if (current != null) {
      current.stop().set(true);
      current.thread().interrupt(); // wake it immediately rather than waiting out its own sleep
    }
    speaker.stopBeep();
    speaker.stopPlay();
  }

  // Swallows InterruptedException rather than propagating it: both updateLiveAplaySession and
  // stopBackgroundAudio use Thread.interrupt() purely as a "wake up now" signal (a replaced
  // channel should be heard promptly, not wait out whatever sleep this thread happens to already
  // be in; a stop should take effect promptly too) -- `stop` is the sole authoritative exit
  // signal for the loop this drives, checked every iteration regardless of why a sleep ended.
  private static void sleepIgnoringInterrupt(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // Intentionally ignored -- see method doc.
    }
  }

  /**
   * Pulls the next frame from {@code source} and renders it, taking roughly its own duration to do
   * so; returns {@code false} once the source has finished. With a real audio device that pacing
   * comes for free from the output line's backpressure (see {@code VirtualSpeaker.playFrame}); the
   * top-up sleep only does real work when there is no device -- a headless/no-op speaker returns
   * instantly, and without it these loops would spin flat out rather than advance the tune in
   * anything like real time. (Takes the {@code PlaySource} rather than an already-pulled frame so
   * the frame type itself is only ever a local {@code var} here -- see the ExcessiveImports note on
   * this class.)
   */
  private boolean renderNextFramePaced(PlaySource source) {
    final var frame = source.next(PLAY_CHUNK_SECONDS);
    if (frame.finished()) {
      return false;
    }
    final long targetMs = Math.max(0L, Math.round(frame.durationSeconds() * 1000.0));
    final long startNanos = System.nanoTime();
    speaker.playFrame(frame.a(), frame.b(), frame.c(), frame.durationSeconds());
    final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
    if (elapsedMs < targetMs) {
      sleepIgnoringInterrupt(targetMs - elapsedMs);
    }
    return true;
  }

  private void executePlayStmt(Stmt.PlayStmt stmt) {
    stopBackgroundAudio(); // a blocking PLAY takes over from any currently-running background APLAY
    final var source =
        PlayParser.buildSequencer(
            playChannelStrings(stmt.channels()),
            state.currentLineLabel(),
            state.currentStatementIndex());
    while (renderNextFramePaced(source)) {
      if (input.pollForBreak()) {
        speaker.stopPlay(); // cut short: discard whatever is still queued
        state.setRunning(false);
        // Mirrors BEEP/PAUSE: pressing BREAK mid-tune gives report code L (BREAK into program);
        // CONT then advances past the PLAY to the next statement.
        throw codedException(
            ReportCode.BREAK_INTO_PROGRAM, ReportCode.BREAK_INTO_PROGRAM.getMessage());
      }
    }
    // Reached the end naturally, so let the queued tail play out rather than flushing it -- an
    // unconditional stopPlay() here used to clip the final note of every completed PLAY.
    speaker.drainPlay();
  }

  private void executeAplayStmt(Stmt.AplayStmt stmt) {
    final List<String> given = playChannelStrings(stmt.channels());
    final AplaySession current = activeAplay;
    if (current != null && current.isAlive()) {
      updateLiveAplaySession(current, given);
      return;
    }
    startNewAplaySession(given);
  }

  /**
   * Targets a partial update at an already-running APLAY: {@code "-"} (trimmed) leaves that
   * channel's in-progress state - including an in-progress infinite repeat - completely untouched;
   * anything else replaces it. Any trailing channel not given at all is silenced, matching a fresh
   * call's own "omission silences" rule even against a live session.
   */
  private void updateLiveAplaySession(AplaySession session, List<String> given) {
    for (int i = 0; i < given.size(); i++) {
      final String channelDsl = given.get(i);
      if (!"-".equals(channelDsl.trim())) {
        session
            .source()
            .replaceChannel(i, channelDsl, state.currentLineLabel(), state.currentStatementIndex());
      }
    }
    for (int i = given.size(); i < 3; i++) {
      session
          .source()
          .replaceChannel(i, "", state.currentLineLabel(), state.currentStatementIndex());
    }
    // Wakes the background thread immediately rather than leaving it to discover the replaced
    // channel(s) whenever its current sleep happens to end (up to one PLAY_CHUNK_MS away, or
    // longer if it's mid-note) -- see startNewAplaySession's sleepIgnoringInterrupt for why this
    // is safe: every wait in that loop treats an interrupt as "wake early", never "stop". Without
    // this, two updates to the same channel landing within one chunk of each other could silently
    // drop the first one entirely (it's overwritten before the thread ever delivers it to the
    // speaker) -- observed as pong's paddle-touch click intermittently not playing at all.
    session.thread().interrupt();
  }

  private void startNewAplaySession(List<String> given) {
    final var source =
        PlayParser.buildSequencer(given, state.currentLineLabel(), state.currentStatementIndex());
    final AtomicBoolean stop = new AtomicBoolean(false);
    // Unlike PLAY, returns immediately - the rest of the BASIC program keeps running while this
    // background thread advances the tune, exactly the point of APLAY existing at all.
    final Thread player =
        new Thread(
            () -> {
              try {
                boolean wasSounding = false;
                while (!stop.get()) {
                  if (renderNextFramePaced(source)) {
                    wasSounding = true;
                  } else {
                    if (wasSounding) {
                      // Just reached the natural end of a sound: let its tail play, then park the
                      // output rather than leaving it running empty until the next trigger.
                      speaker.drainPlay();
                      wasSounding = false;
                    }
                    // Nothing playing right now, but this session stays alive rather than exiting
                    // -- exiting here would race executeAplayStmt's isAlive() check: a channel
                    // replace targeted at this (still nominally "live") session could land after
                    // the thread has already decided to exit but before it actually terminates,
                    // silently dropping that update since no thread is left to ever consume it
                    // (this was a real, intermittent "the click sometimes doesn't play" bug).
                    // Idle-polls at the same cadence rather than a longer sleep: fillPendingNotes()
                    // is O(1) once every channel is done, so this costs nothing while idle.
                    sleepIgnoringInterrupt(PLAY_CHUNK_MS);
                  }
                }
              } finally {
                speaker.stopPlay();
              }
            });
    player.setDaemon(true);
    player.setName("bazlang-aplay");
    activeAplay = new AplaySession(player, source, stop);
    player.start();
  }

  // ===== Program management =====

  private void executeRandStmt(Stmt.RandStmt stmt) {
    long seed = stmt.seed() != null ? Math.round(exprEvaluator.evalNum(stmt.seed())) : 0;
    // RAND with 0 or no argument seeds from system state
    if (seed == 0) {
      // Combine multiple entropy sources and mix with XorShift
      seed = System.nanoTime() ^ java.util.concurrent.ThreadLocalRandom.current().nextLong();
      seed ^= seed << 17;
      seed ^= seed >>> 31;
      seed ^= seed << 8;
    }
    state.seedRandom(seed);
  }

  private void executeListStmt(Stmt.ListStmt stmt) {
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
    final var range = stmt.range();
    if (range != null) {
      if (range.from() != null) {
        start = (int) exprEvaluator.evalNum(range.from());
      }
      if (range.to() != null) {
        end = (int) exprEvaluator.evalNum(range.to());
      }
    }
    for (final var entry : state.program().subMapEntries(start, true, end, true)) {
      final var line = entry.getValue();
      if (line.lineNumber() >= Limits.MIN_LINE_LABEL) {
        screen.println(line.lineNumber() + " " + line.sourceText());
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
