package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;
import com.davidconneely.bazlang.io.BrailleMode;
import com.davidconneely.bazlang.io.CellMode;
import com.davidconneely.bazlang.io.HalfCellMode;
import com.davidconneely.bazlang.io.PixelMode;
import com.davidconneely.bazlang.io.QuadrantMode;
import com.davidconneely.bazlang.io.SextantMode;
import com.davidconneely.repl.BreakException;
import com.davidconneely.repl.Display;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Executes BazLang from the ANTLR ParseTree. */
public class StatementExecutor extends BazLangBaseVisitor<Object> {
  protected final EvalState state;
  protected final BazLangDisplay display;
  protected final ProgramStorage storage;
  protected final ExpressionEvaluator exprEvaluator;

  public StatementExecutor(
      EvalState state,
      BazLangDisplay display,
      ProgramStorage storage,
      ExpressionEvaluator exprEvaluator) {
    this.state = state;
    this.display = display;
    this.storage = storage;
    this.exprEvaluator = exprEvaluator;
  }

  public BazLangDisplay display() {
    return display;
  }

  // ===== Statement Execution =====

  @Override
  public Object visitClearStmt(ClearStmtContext ctx) {
    state.clear();
    return null;
  }

  @Override
  public Object visitClsStmt(ClsStmtContext ctx) {
    display.cls();
    return null;
  }

  @Override
  public Object visitCopyStmt(CopyStmtContext ctx) {
    return null; // Not implemented
  }

  @Override
  public Object visitDimStmt(DimStmtContext ctx) {
    var dimDecl = ctx.dimDecl();
    String name =
        dimDecl.NUM_IDENTIFIER() != null
            ? dimDecl.NUM_IDENTIFIER().getText().toUpperCase()
            : dimDecl.STR_IDENTIFIER().getText().toUpperCase();
    boolean isStr = name.endsWith("$");
    List<Integer> dims = new ArrayList<>();
    for (var exprCtx : dimDecl.numExpr()) {
      dims.add((int) evalNum(exprCtx));
    }
    if (isStr) {
      state.removeStrVar(name);
      int flen = dims.removeLast();
      int total = 1;
      for (int d : dims) {
        total *= d;
      }
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      BStr[] elements = new BStr[total];
      BStr emptyStr = BStr.EMPTY.paddedOrTruncatedTo(flen);
      Arrays.fill(elements, emptyStr);
      state.setStrVar(name, new EvalState.StrVar.Array(dims, flen, elements));
    } else {
      int total = 1;
      for (int d : dims) {
        total *= d;
      }
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      state.setNumArray(name, new EvalState.NumArray(dims, new double[total]));
    }
    return null;
  }

  @Override
  public Object visitIfStmt(IfStmtContext ctx) {
    if (evalNum(ctx.numExpr()) != 0.0) {
      visit(ctx.statement());
    }
    return null;
  }

  @Override
  public Object visitInputStmt(InputStmtContext ctx) {
    var target = ctx.assignmentTarget();
    boolean isNumeric = target.NUM_IDENTIFIER() != null;
    Display.InputMode mode =
        isNumeric ? Display.InputMode.INPUT_NUMERIC : Display.InputMode.INPUT_STRING;
    String line = readInputLine(mode);

    if (isNumeric) {
      // INPUT evaluates the input as a numeric expression; retry on syntax errors
      while (true) {
        try {
          double val = exprEvaluator.evaluateNumericExpression(line.trim());
          assignNumTarget(target, val);
          break;
        } catch (ReportException e) {
          if (e.reportCode() != ReportCode.NONSENSE_IN_BASIC) {
            throw e; // Other errors (undefined variable, division by zero) end program
          }
          display.prefillInput(line);
          line = readInputLine("Syntax error in expression");
        }
      }
    } else {
      assignStrTarget(target, BStr.fromJavaString(line));
    }
    return null;
  }

  private String readInputLine(Display.InputMode mode) {
    try {
      String line = display.readln(mode);
      return line != null ? line : "";
    } catch (BreakException e) {
      state.setRunning(false);
      throw codedException(
          ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
    }
  }

  private String readInputLine(String prompt) {
    try {
      String line = display.readln(prompt);
      return line != null ? line : "";
    } catch (BreakException e) {
      state.setRunning(false);
      throw codedException(
          ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
    }
  }

  @Override
  public Object visitLetStmt(LetStmtContext ctx) {
    var target = ctx.assignmentTarget();
    var expr = ctx.expression();
    if (target.NUM_IDENTIFIER() != null) {
      double val = evalNum(expr.numExpr());
      assignNumTarget(target, val);
    } else {
      BStr val = evalStr(expr.strExpr());
      assignStrTarget(target, val);
    }
    return null;
  }

  @Override
  public Object visitListStmt(ListStmtContext ctx) {
    int[] range = parseListLineRange(ctx.lineRange());
    for (var entry : state.program().subMap(range[0], true, range[1], true).entrySet()) {
      ProgramLine line = entry.getValue();
      display.println(line.lineNumber() + " " + line.sourceText());
    }
    return null;
  }

  @Override
  public Object visitLListStmt(LListStmtContext ctx) {
    int[] range = parseListLineRange(ctx.lineRange());
    for (var entry : state.program().subMap(range[0], true, range[1], true).entrySet()) {
      ProgramLine line = entry.getValue();
      display.lprintln(line.lineNumber() + " " + line.sourceText());
    }
    return null;
  }

  /** Parses lineRange for LIST/LLIST: single number means "from n to end". */
  private int[] parseListLineRange(BazLangParser.LineRangeContext range) {
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
    if (range != null) {
      var nums = range.numExpr();
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
  public Object visitLoadStmt(LoadStmtContext ctx) {
    storage.load(evalStr(ctx.strExpr()).toJavaString());
    return null;
  }

  @Override
  public Object visitLPrintStmt(LPrintStmtContext ctx) {
    int tp = 0;
    boolean suppressNewline = false;
    var printList = ctx.printList();
    if (printList != null) {
      var items = printList.printItem();
      var seps = printList.printSep();
      for (int i = 0; i < items.size(); i++) {
        tp = executeLPrintItem(items.get(i), tp);
        // Process separator after this item (if any)
        if (i < seps.size()) {
          String sep = seps.get(i).getText();
          if (sep.equals(",")) {
            // Comma moves to next tab stop
            int nextTab = ((tp / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
            if (nextTab > tp) {
              display.lprint(" ".repeat(nextTab - tp));
              tp = nextTab;
            }
          }
          // Semicolon does nothing (items concatenate)
        }
      }
      // Trailing separator (semicolon or comma) suppresses newline
      int numBetweenSeps = items.size() - 1;
      suppressNewline = seps.size() > numBetweenSeps;
    }
    if (!suppressNewline) {
      display.lprintln();
    }
    return null;
  }

  private int executeLPrintItem(PrintItemContext item, int tp) {
    if (item instanceof PrintExprItemContext expr) {
      String s = evalPrintExpr(expr.expression());
      display.lprint(s);
      return tp + s.length();
    } else if (item instanceof PrintAtItemContext at) {
      return (int) evalNum(at.numExpr(1)); // col
    } else if (item instanceof PrintTabItemContext tab) {
      int t = (int) evalNum(tab.numExpr());
      if (t < 0) {
        t = ((tp / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
      }
      if (t > tp) {
        display.lprint(" ".repeat(t - tp));
      }
      return t;
    }
    return tp;
  }

  @Override
  public Object visitNewStmt(NewStmtContext ctx) {
    state.clear();
    state.program().clear();
    return null;
  }

  @Override
  public Object visitPauseStmt(PauseStmtContext ctx) {
    double frames = evalNum(ctx.numExpr());
    long totalMs = Math.max(0L, Math.round(frames * 20.0));
    display.forceFlush();
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
      if (display.pollForBreak()) {
        state.setRunning(false);
        throw codedException(
            ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
      }
    }
    return null;
  }

  @Override
  public Object visitPlotStmt(PlotStmtContext ctx) {
    int x = (int) evalNum(ctx.numExpr(0));
    int y = (int) evalNum(ctx.numExpr(1));
    try {
      display.plot(x, y);
    } catch (IllegalArgumentException e) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
    }
    return null;
  }

  @Override
  public Object visitPlotmodeStmt(PlotmodeStmtContext ctx) {
    int mode = (int) evalNum(ctx.numExpr());
    PixelMode pixelMode =
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
    display.setPlotMode(pixelMode);
    return null;
  }

  @Override
  public Object visitPrintStmt(PrintStmtContext ctx) {
    int tabPos = display.currentCol();
    boolean suppressNewline = false;
    var printList = ctx.printList();
    if (printList != null) {
      var items = printList.printItem();
      var seps = printList.printSep();
      for (int i = 0; i < items.size(); i++) {
        tabPos = executePrintItem(items.get(i), tabPos);
        // Process separator after this item (if any)
        if (i < seps.size()) {
          String sep = seps.get(i).getText();
          if (sep.equals(",")) {
            // Comma moves to next tab stop
            int nextTab = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
            if (nextTab > tabPos) {
              display.print(" ".repeat(nextTab - tabPos));
              tabPos = display.currentCol();
            }
          }
          // Semicolon does nothing (items concatenate)
        }
      }
      // Trailing separator (semicolon or comma) suppresses newline
      int numBetweenSeps = items.size() - 1;
      suppressNewline = seps.size() > numBetweenSeps;
    }
    if (!suppressNewline) {
      display.println();
    }
    display.flush(); // Ensure output is visible, including semicolon-terminated lines
    return null;
  }

  private int executePrintItem(PrintItemContext item, int tabPos) {
    if (item instanceof PrintExprItemContext expr) {
      String s = evalPrintExpr(expr.expression());
      display.print(s);
      return display.currentCol();
    } else if (item instanceof PrintAtItemContext at) {
      int row = (int) evalNum(at.numExpr(0));
      int col = (int) evalNum(at.numExpr(1));
      if (row < 0 || col < 0) {
        throw codedException(ReportCode.OUT_OF_SCREEN, "Screen out of bounds");
      }
      display.locate(row, col);
      return col;
    } else if (item instanceof PrintTabItemContext tab) {
      int t = (int) evalNum(tab.numExpr());
      if (t < 0) {
        t = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
      }
      if (t > tabPos) {
        display.print(" ".repeat(t - tabPos));
      }
      return display.currentCol();
    }
    return tabPos;
  }

  @Override
  public Object visitRandStmt(RandStmtContext ctx) {
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
  public Object visitRemStmt(RemStmtContext ctx) {
    return null;
  }

  @Override
  public Object visitSaveStmt(SaveStmtContext ctx) {
    storage.save(exprEvaluator.evalStr(ctx.strExpr()).toJavaString());
    return null;
  }

  @Override
  public Object visitScrollStmt(ScrollStmtContext ctx) {
    display.scroll();
    return null;
  }

  @Override
  public Object visitSlowStmt(SlowStmtContext ctx) {
    return null;
  }

  @Override
  public Object visitStopStmt(StopStmtContext ctx) {
    state.setRunning(false);
    if (state.currentLineLabel() > 0) {
      throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
    }
    return null;
  }

  @Override
  public Object visitUnplotStmt(UnplotStmtContext ctx) {
    int x = (int) evalNum(ctx.numExpr(0));
    int y = (int) evalNum(ctx.numExpr(1));
    try {
      display.unplot(x, y);
    } catch (IllegalArgumentException e) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
    }
    return null;
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
    String name = target.NUM_IDENTIFIER().getText().toUpperCase();
    var numExprs = target.numExpr();
    if (numExprs.isEmpty()) {
      // Scalar
      state.setNumVar(name, val);
    } else {
      // Array element
      if (!state.hasNumArray(name)) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
      }
      EvalState.NumArray na = state.numArray(name);
      List<Integer> indices = new ArrayList<>();
      for (var e : numExprs) {
        indices.add((int) evalNum(e));
      }
      int idx = exprEvaluator.calculateArrayIndex(na.dimensions(), indices);
      na.data()[idx] = val;
    }
  }

  private void assignStrTarget(AssignmentTargetContext target, BStr val) {
    String name = target.STR_IDENTIFIER().getText().toUpperCase();
    var subscript = target.strSubscript();
    if (subscript == null) {
      // Scalar assignment
      EvalState.StrVar var = state.strVar(name);
      if (var instanceof EvalState.StrVar.Array ca) {
        for (int i = 0; i < ca.elements().length; i++) {
          int offset = i * ca.stringLength();
          int remaining = Math.max(0, val.length() - offset);
          if (remaining == 0) {
            ca.elements()[i] = BStr.EMPTY.paddedOrTruncatedTo(ca.stringLength());
          } else if (remaining >= ca.stringLength()) {
            ca.elements()[i] = val.slice(offset + 1, offset + ca.stringLength());
          } else {
            ca.elements()[i] =
                val.slice(offset + 1, val.length()).paddedOrTruncatedTo(ca.stringLength());
          }
        }
        return;
      }
      state.setStrVar(name, new EvalState.StrVar.Scalar(val));
      return;
    }
    // Subscripted assignment - parse subscript
    ExpressionEvaluator.ParsedSubscript parsed = exprEvaluator.parseStrSubscript(subscript);
    List<Integer> indices = parsed.indices();
    Integer sliceStart = parsed.sliceStart();
    Integer sliceEnd = parsed.sliceEnd();
    boolean hasSlice = parsed.hasSlice();
    EvalState.StrVar var = state.strVar(name);
    if (var instanceof EvalState.StrVar.Array ca) {
      int n = ca.arrayDimensions().size();
      Integer byteIndex = null;
      if (indices.size() == n + 1) {
        byteIndex = indices.removeLast();
      } else if (indices.size() != n && n == 0 && indices.size() == 1) {
        byteIndex = indices.removeFirst();
      }
      int arrayIdx = exprEvaluator.calculateArrayIndex(ca.arrayDimensions(), indices);
      BStr elem = ca.elements()[arrayIdx];
      int[] bounds =
          exprEvaluator.calculateSliceBounds(ca.stringLength(), byteIndex, sliceStart, sliceEnd);
      ca.elements()[arrayIdx] = elem.withSlice(bounds[0], bounds[1], val);
    } else if (var instanceof EvalState.StrVar.Scalar scalar) {
      BStr str = scalar.value();
      Integer byteIndex = null;
      if (indices.size() == 1 && !hasSlice) {
        byteIndex = indices.getFirst();
        indices.clear();
      }
      if (!indices.isEmpty()) {
        throw codedException(
            ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
      }
      int[] bounds =
          exprEvaluator.calculateSliceBounds(str.length(), byteIndex, sliceStart, sliceEnd);
      state.setStrVar(name, new EvalState.StrVar.Scalar(str.withSlice(bounds[0], bounds[1], val)));
    } else {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), msg);
  }
}
