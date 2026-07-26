package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import com.davidconneely.cell.BrailleMode;
import com.davidconneely.cell.CellMode;
import com.davidconneely.cell.HalfCellMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.cell.SextantMode;
import com.davidconneely.repl.BreakException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Supplier;

/** Executes BazLang from the ANTLR ParseTree. */
public class StatementExecutor extends BazLangBaseVisitor<Void> {
  private final EvalState state;
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final ProgramStorage storage;
  private final ExpressionEvaluator exprEvaluator;
  private final AntlrParser parser;

  public StatementExecutor(EvalState state, VirtualScreen screen, VirtualInput input) {
    this(
        state,
        screen,
        input,
        new ProgramStorage(state, AntlrParser.INSTANCE),
        new ExpressionEvaluator(state, screen, input, AntlrParser.INSTANCE),
        AntlrParser.INSTANCE);
  }

  public StatementExecutor(
      EvalState state,
      VirtualScreen screen,
      VirtualInput input,
      ProgramStorage storage,
      ExpressionEvaluator exprEvaluator,
      AntlrParser parser) {
    this.state = state;
    this.screen = screen;
    this.input = input;
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

  public ExpressionEvaluator getExprEvaluator() {
    return exprEvaluator;
  }

  // ===== Statement Execution =====

  @Override
  public Void visitClearStmt(ClearStmtContext ctx) {
    state.clear();
    return null;
  }

  @Override
  public Void visitClsStmt(ClsStmtContext ctx) {
    screen.cls();
    return null;
  }

  @Override
  public Void visitDataStmt(DataStmtContext ctx) {
    return null;
  }

  @Override
  public Void visitDefFnStmt(DefFnStmtContext ctx) {
    final String name = ctx.name.getText().toUpperCase();
    if (name.endsWith("$")) {
      if (ctx.expression().strExpr() == null) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string expression");
      }
    } else {
      if (ctx.expression().numExpr() == null) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric expression");
      }
    }
    final var params = new ArrayList<String>();
    if (ctx.params != null) {
      final var paramSet = new HashSet<String>();
      for (final var p : ctx.params) {
        final String pName = p.getText().toUpperCase();
        if (!paramSet.add(pName)) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Duplicate parameter name: " + pName);
        }
        params.add(pName);
      }
    }
    state.setFn(name, new EvalState.FnDefinition(name, params, ctx.expression()));
    return null;
  }

  @Override
  public Void visitDimStmt(DimStmtContext ctx) {
    final var dimDecl = ctx.dimDecl();
    final String name =
        dimDecl.NUM_IDENTIFIER() != null
            ? dimDecl.NUM_IDENTIFIER().getText().toUpperCase()
            : dimDecl.STR_IDENTIFIER().getText().toUpperCase();
    final boolean isStr = name.endsWith("$");
    final int numDims = dimDecl.numExpr().size();
    final int[] dims = new int[numDims];
    for (int i = 0; i < numDims; i++) {
      final int d = (int) evalNum(dimDecl.numExpr(i));
      if (d < 1) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      dims[i] = d;
    }
    if (isStr) {
      state.removeStrVar(name);
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
      state.setStrVar(name, new EvalState.StrVar.Array(arrDims, flen, data));
    } else {
      long total = 1;
      for (int d : dims) {
        total *= d;
        if (total > Limits.MAX_ARRAY_ELEMENTS) {
          throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
        }
      }
      state.setNumArray(name, new EvalState.NumArray(dims, new double[(int) total]));
    }
    return null;
  }

  @Override
  public Void visitDrawStmt(DrawStmtContext ctx) {
    withRestoredStyles(
        () -> {
          applyStyleList(ctx.styleList());
          final int dx = (int) Math.round(evalNum(ctx.numExpr(0)));
          final int dy = (int) Math.round(evalNum(ctx.numExpr(1)));
          drawLine(
              state.graphicsCursorX(),
              state.graphicsCursorY(),
              state.graphicsCursorX() + dx,
              state.graphicsCursorY() + dy);
        });
    return null;
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

  @Override
  public Void visitFastStmt(FastStmtContext ctx) {
    screen.setFastMode(true);
    return null;
  }

  @Override
  public Void visitIfStmt(IfStmtContext ctx) {
    if (evalNum(ctx.numExpr()) == 0.0) {
      if (state.currentLineLabel() > 0) {
        final Integer nextLabel = state.program().higherKey(state.currentLineLabel());
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
    return null;
  }

  @Override
  public Void visitInputStmt(InputStmtContext ctx) {
    final var target = ctx.assignmentTarget();
    final boolean isNumeric = target.NUM_IDENTIFIER() != null;
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
      assignStrTarget(target, BStr.fromJavaString(line));
    }
    return null;
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

  private void restoreTo(int target) {
    final var addr = state.program().findFirstData(target, parser);
    if (addr != null) {
      state.setDataLineLabel(addr.lineLabel());
      state.setDataStatementIndex(addr.statementIndex());
      state.setDataExpressionIndex(0);
    } else {
      state.setDataLineLabel(Integer.MAX_VALUE);
    }
  }

  @Override
  public Void visitLetStmt(LetStmtContext ctx) {
    final var target = ctx.assignmentTarget();
    final var expr = ctx.expression();
    if (target.NUM_IDENTIFIER() != null) {
      final double val = evalNum(expr.numExpr());
      assignNumTarget(target, val);
    } else {
      final var val = evalStr(expr.strExpr());
      assignStrTarget(target, val);
    }
    return null;
  }

  @Override
  public Void visitListStmt(ListStmtContext ctx) {
    final int[] range = parseListLineRange(ctx.lineRange());
    for (final var entry : state.program().subMapEntries(range[0], true, range[1], true)) {
      final var line = entry.getValue();
      if (line.lineNumber() >= Limits.MIN_LINE_LABEL) {
        screen.println(line.lineNumber() + " " + line.sourceText());
      }
    }
    return null;
  }

  /** Parses lineRange for LIST: single number means "from n to end". */
  private int[] parseListLineRange(BazLangParser.LineRangeContext range) {
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
    if (range != null) {
      final var nums = range.numExpr();
      if (range.TO() != null) {
        if (nums.size() == 2) {
          start = (int) evalNum(nums.get(0));
          end = (int) evalNum(nums.get(1));
        } else if (nums.size() == 1) {
          if (range.getText().toUpperCase().startsWith("TO")) {
            end = (int) evalNum(nums.getFirst());
          } else {
            start = (int) evalNum(nums.getFirst());
          }
        }
        // TO with no numbers: start=MIN, end=MAX (already set)
      } else if (nums.size() == 1) {
        // LIST n (no TO): from n to end
        start = (int) evalNum(nums.getFirst());
        // end stays MAX
      }
    }
    return new int[] {start, end};
  }

  @Override
  public Void visitLoadStmt(LoadStmtContext ctx) {
    storage.load(evalStr(ctx.strExpr()).toJavaString());
    return null;
  }

  @Override
  public Void visitNewStmt(NewStmtContext ctx) {
    state.clear();
    state.program().clear();
    return null;
  }

  @Override
  public Void visitPauseStmt(PauseStmtContext ctx) {
    final double frames = evalNum(ctx.numExpr());
    final long totalMs = Math.max(0L, Math.round(frames * 20.0));
    screen.forceFlush();
    long remaining = totalMs;
    while (remaining > 0) {
      long chunk = Math.min(remaining, 20L);
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
    return null;
  }

  @Override
  public Void visitPlotStmt(PlotStmtContext ctx) {
    withRestoredStyles(
        () -> {
          applyStyleList(ctx.styleList());
          final var exprs = ctx.numExpr();
          try {
            if (exprs == null || exprs.isEmpty()) {
              screen.plot(state.graphicsCursorX(), state.graphicsCursorY());
            } else if (exprs.size() == 2) {
              final int x = (int) evalNum(ctx.numExpr(0));
              final int y = (int) evalNum(ctx.numExpr(1));
              screen.plot(x, y);
              state.setGraphicsCursorX(x);
              state.setGraphicsCursorY(y);
            }
          } catch (IllegalArgumentException e) {
            throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
          }
        });
    return null;
  }

  @Override
  public Void visitPlotmodeStmt(PlotmodeStmtContext ctx) {
    final int mode = (int) evalNum(ctx.numExpr());
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
    return null;
  }

  @Override
  public Void visitPrintStmt(PrintStmtContext ctx) {
    withRestoredStyles(
        () -> {
          int tabPos = screen.currentCol();
          boolean suppressNewline = false;
          final var printList = ctx.printList();
          if (printList != null) {
            for (int i = 0; i < printList.getChildCount(); i++) {
              final var child = printList.getChild(i);
              if (child instanceof PrintItemContext item) {
                tabPos = executePrintItem(item, tabPos);
                suppressNewline = false;
              } else if (child instanceof PrintSepContext sep) {
                final String sepText = sep.getText();
                if (sepText.equals(",")) {
                  // Comma moves to next tab stop
                  final int nextTab = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
                  if (nextTab > tabPos) {
                    screen.print(" ".repeat(nextTab - tabPos));
                    tabPos = screen.currentCol();
                  }
                } else if (sepText.equals("'")) {
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
    return null;
  }

  private int executePrintItem(PrintItemContext item, int tabPos) {
    if (item instanceof PrintExprItemContext expr) {
      final String s = evalPrintExpr(expr.expression());
      screen.print(s);
      return screen.currentCol();
    } else if (item instanceof PrintAtItemContext at) {
      final int row = (int) evalNum(at.numExpr(0));
      final int col = (int) evalNum(at.numExpr(1));
      if (row < 0 || col < 0) {
        throw codedException(ReportCode.OUT_OF_SCREEN, "Screen out of bounds");
      }
      screen.locate(row, col);
      return col;
    } else if (item instanceof PrintTabItemContext tab) {
      int t = (int) evalNum(tab.numExpr());
      if (t < 0) {
        t = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
      }
      if (t > tabPos) {
        screen.print(" ".repeat(t - tabPos));
      }
      return screen.currentCol();
    } else if (item instanceof PrintStyleItemContext style) {
      applyStyleItem(style.styleItem());
      return tabPos;
    }
    return tabPos;
  }

  private void applyStyleList(StyleListContext ctx) {
    if (ctx == null) {
      return;
    }
    for (final var style : ctx.styleItem()) {
      applyStyleItem(style);
    }
  }

  private void applyStyleItem(StyleItemContext style) {
    if (style instanceof StyleInkItemContext ink) {
      screen.setInk((int) evalNum(ink.numExpr()));
    } else if (style instanceof StylePaperItemContext paper) {
      screen.setPaper((int) evalNum(paper.numExpr()));
    } else if (style instanceof StyleBrightItemContext bright) {
      screen.setBright((int) evalNum(bright.numExpr()));
    } else if (style instanceof StyleFlashItemContext flash) {
      screen.setFlash((int) evalNum(flash.numExpr()));
    } else if (style instanceof StyleInverseItemContext inverse) {
      screen.setInverse((int) evalNum(inverse.numExpr()));
    } else if (style instanceof StyleOverItemContext over) {
      screen.setOver((int) evalNum(over.numExpr()));
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

  @Override
  public Void visitRandStmt(RandStmtContext ctx) {
    long seed;
    if (ctx.numExpr() != null) {
      seed = Math.round(evalNum(ctx.numExpr()));
    } else {
      seed = 0;
    }
    // RAND with 0 or no argument seeds from system state
    if (seed == 0) {
      // Combine multiple entropy sources and mix with XorShift
      seed = System.nanoTime() ^ ((long) new Object().hashCode() << 32 | new Object().hashCode());
      seed ^= seed << 17;
      seed ^= seed >>> 31;
      seed ^= seed << 8;
    }
    state.seedRandom(seed);
    return null;
  }

  @Override
  public Void visitReadStmt(ReadStmtContext ctx) {
    for (final var target : ctx.assignmentTarget()) {
      if (state.dataLineLabel() == -1) {
        restoreTo(0);
      }
      final int lineLabel = state.dataLineLabel();
      if (lineLabel == Integer.MAX_VALUE) {
        throw codedException(ReportCode.OUT_OF_DATA, "Out of DATA");
      }
      final var line = state.program().get(lineLabel);
      if (line == null) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final var stmts = line.getFlattenedStatements(parser);
      final int stmtIdx = state.dataStatementIndex();
      final var stmt = stmts.get(stmtIdx - 1);
      if (!(stmt instanceof DataStmtContext dataCtx)) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final int exprIdx = state.dataExpressionIndex();
      final var exprCtx = dataCtx.expression(exprIdx);

      // Advance pointer before evaluating and assigning
      if (exprIdx + 1 < dataCtx.expression().size()) {
        state.setDataExpressionIndex(exprIdx + 1);
      } else {
        // Find next DATA statement in the current line
        boolean found = false;
        for (int i = stmtIdx + 1; i <= stmts.size(); i++) {
          if (stmts.get(i - 1) instanceof DataStmtContext) {
            state.setDataStatementIndex(i);
            state.setDataExpressionIndex(0);
            found = true;
            break;
          }
        }
        if (!found) {
          final Integer nextLine = state.program().higherKey(lineLabel);
          if (nextLine != null) {
            restoreTo(nextLine);
          } else {
            state.setDataLineLabel(Integer.MAX_VALUE);
          }
        }
      }

      // Evaluate and assign
      if (target.NUM_IDENTIFIER() != null) {
        if (exprCtx.numExpr() == null) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected number");
        }
        final double val = evalNum(exprCtx.numExpr());
        assignNumTarget(target, val);
      } else {
        if (exprCtx.strExpr() == null) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string");
        }
        final var val = evalStr(exprCtx.strExpr());
        assignStrTarget(target, val);
      }
    }
    return null;
  }

  @Override
  public Void visitRemStmt(RemStmtContext ctx) {
    return null;
  }

  @Override
  public Void visitRestoreStmt(RestoreStmtContext ctx) {
    int target = 0;
    if (ctx.numExpr() != null) {
      final double val = evalNum(ctx.numExpr());
      target = (int) Math.round(val);
      if (target < 0 || target > Limits.MAX_TARGET_LABEL) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Line label out of range");
      }
    }
    restoreTo(target);
    return null;
  }

  @Override
  public Void visitSaveStmt(SaveStmtContext ctx) {
    storage.save(exprEvaluator.evalStr(ctx.strExpr()).toJavaString());
    return null;
  }

  @Override
  public Void visitScrollStmt(ScrollStmtContext ctx) {
    screen.scroll();
    return null;
  }

  @Override
  public Void visitSlowStmt(SlowStmtContext ctx) {
    screen.setFastMode(false);
    return null;
  }

  @Override
  public Void visitStopStmt(StopStmtContext ctx) {
    state.setRunning(false);
    throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
  }

  // ===== Expression Evaluation =====

  public double evalNum(NumExprContext ctx) {
    return exprEvaluator.evalNum(ctx);
  }

  private BStr evalStr(StrExprContext ctx) {
    return exprEvaluator.evalStr(ctx);
  }

  private String evalPrintExpr(ExpressionContext ctx) {
    return exprEvaluator.evalPrintExpr(ctx);
  }

  // ===== Assignment Helpers =====

  private void assignNumTarget(AssignmentTargetContext target, double val) {
    final String name = target.NUM_IDENTIFIER().getText().toUpperCase();
    final var numExprs = target.numExpr();
    if (numExprs.isEmpty()) {
      // Scalar
      var ref = (EvalState.NumVarRef) target.varRef;
      if (ref == null) {
        ref = state.getOrAddNumVar(name);
        target.varRef = ref;
      }
      ref.value = val;
      ref.initialised = true;
    } else {
      // Array element
      var ref = (EvalState.NumArrayRef) target.varRef;
      if (ref == null) {
        ref = state.getOrAddNumArray(name);
        target.varRef = ref;
      }
      if (ref.array == null) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
      }
      final var na = ref.array;
      final int count = numExprs.size();
      final int[] indices = new int[count];
      for (int i = 0; i < count; i++) {
        indices[i] = (int) evalNum(numExprs.get(i));
      }
      final int idx = exprEvaluator.calculateArrayIndex(na.dimensions(), indices, 0, count);
      na.data()[idx] = val;
    }
  }

  private void assignStrTarget(AssignmentTargetContext target, BStr val) {
    final String name = target.STR_IDENTIFIER().getText().toUpperCase();
    var ref = (EvalState.StrVarRef) target.varRef;
    if (ref == null) {
      ref = state.getOrAddStrVar(name);
      target.varRef = ref;
    }
    final var subscript = target.strSubscript();
    if (subscript == null) {
      assignStrSubscriptNull(ref, val);
      return;
    }

    final int indicesCount = subscript.indices != null ? subscript.indices.size() : 0;
    final int[] indices = new int[indicesCount];
    if (indicesCount > 0) {
      for (int i = 0; i < indicesCount; i++) {
        indices[i] = (int) evalNum(subscript.indices.get(i));
      }
    }
    int sliceStart = -1;
    int sliceEnd = -1;
    final boolean hasSlice = subscript.slice != null;
    if (hasSlice) {
      if (subscript.slice.start != null) {
        sliceStart = (int) evalNum(subscript.slice.start);
      }
      if (subscript.slice.end != null) {
        sliceEnd = (int) evalNum(subscript.slice.end);
      }
    }

    final var strVar = ref.value;
    if (strVar instanceof EvalState.StrVar.Array ca) {
      assignStrArrayTarget(ca, val, indices, indicesCount, sliceStart, sliceEnd);
    } else if (strVar instanceof EvalState.StrVar.Scalar scalar) {
      assignStrScalarTarget(
          ref, scalar, val, hasSlice, indices, indicesCount, sliceStart, sliceEnd);
    } else {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
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

  @Override
  public Void visitInkStmt(InkStmtContext ctx) {
    final int colour = (int) evalNum(ctx.numExpr());
    state.setDefaultInk(colour);
    screen.setInk(colour);
    return null;
  }

  @Override
  public Void visitPaperStmt(PaperStmtContext ctx) {
    final int colour = (int) evalNum(ctx.numExpr());
    state.setDefaultPaper(colour);
    screen.setPaper(colour);
    return null;
  }

  @Override
  public Void visitBrightStmt(BrightStmtContext ctx) {
    final int bright = (int) evalNum(ctx.numExpr());
    state.setDefaultBright(bright);
    screen.setBright(bright);
    return null;
  }

  @Override
  public Void visitFlashStmt(FlashStmtContext ctx) {
    final int flash = (int) evalNum(ctx.numExpr());
    state.setDefaultFlash(flash);
    screen.setFlash(flash);
    return null;
  }

  @Override
  public Void visitInverseStmt(InverseStmtContext ctx) {
    final int inverse = (int) evalNum(ctx.numExpr());
    state.setDefaultInverse(inverse);
    screen.setInverse(inverse);
    return null;
  }

  @Override
  public Void visitOverStmt(OverStmtContext ctx) {
    final int over = (int) evalNum(ctx.numExpr());
    state.setDefaultOver(over);
    screen.setOver(over);
    return null;
  }

  // ===== Program flow statements =====

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
      // Skip to matching NEXT (flat scan including IF bodies — see docs/quirks.md
      // "FOR loop flat skip scan").
      final var addr =
          state
              .program()
              .findMatchingNext(
                  forVar, state.currentLineLabel(), state.currentStatementIndex() + 1, parser);
      if (addr == null) {
        throw new ReportException(
            ReportCode.FOR_WITHOUT_NEXT, state.currentLineLabel(), "FOR without NEXT");
      }
      state.setPendingJumpLocation(addr.lineLabel(), addr.statementIndex() + 1);
    }
    return null;
  }

  @Override
  public Void visitGosubStmt(GosubStmtContext ctx) {
    state.pushReturn(
        new EvalState.StatementAddress(
            state.currentLineLabel(), state.currentStatementIndex() + 1));
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

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
