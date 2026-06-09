package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;
import com.davidconneely.cell.BrailleMode;
import com.davidconneely.cell.CellMode;
import com.davidconneely.cell.HalfCellMode;
import com.davidconneely.cell.PixelMode;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.cell.SextantMode;
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
  public Object visitDataStmt(DataStmtContext ctx) {
    return null;
  }

  @Override
  public Object visitDefFnStmt(DefFnStmtContext ctx) {
    String name = ctx.name.getText().toUpperCase();
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
    List<String> params = new ArrayList<>();
    if (ctx.params != null) {
      java.util.Set<String> paramSet = new java.util.HashSet<>();
      for (var p : ctx.params) {
        String pName = p.getText().toUpperCase();
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
    if (evalNum(ctx.numExpr()) == 0.0) {
      if (state.currentLineLabel() > 0) {
        Integer nextLabel = state.program().higherKey(state.currentLineLabel());
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

  private String readInputLine(String prompt) {
    try {
      String line = display.readln(prompt);
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
    Integer searchLine = state.program().ceilingKey(target);
    while (searchLine != null) {
      ProgramLine line = state.program().get(searchLine);
      List<StatementContext> stmts = line.getFlattenedStatements(AntlrParser.INSTANCE);
      int stmtIdx = 1;
      for (StatementContext stmt : stmts) {
        if (stmt instanceof DataStmtContext) {
          state.setDataLineLabel(searchLine);
          state.setDataStatementIndex(stmtIdx);
          state.setDataExpressionIndex(0);
          return;
        }
        stmtIdx++;
      }
      searchLine = state.program().higherKey(searchLine);
    }
    state.setDataLineLabel(Integer.MAX_VALUE);
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
    for (var entry : state.program().subMapEntries(range[0], true, range[1], true)) {
      ProgramLine line = entry.getValue();
      display.println(line.lineNumber() + " " + line.sourceText());
    }
    return null;
  }

  @Override
  public Object visitLListStmt(LListStmtContext ctx) {
    int[] range = parseListLineRange(ctx.lineRange());
    for (var entry : state.program().subMapEntries(range[0], true, range[1], true)) {
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
      for (int i = 0; i < printList.getChildCount(); i++) {
        var child = printList.getChild(i);
        if (child instanceof PrintItemContext item) {
          tp = executeLPrintItem(item, tp);
          suppressNewline = false;
        } else if (child instanceof PrintSepContext sep) {
          String sepText = sep.getText();
          if (sepText.equals(",")) {
            // Comma moves to next tab stop
            int nextTab = ((tp / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
            if (nextTab > tp) {
              display.lprint(" ".repeat(nextTab - tp));
              tp = nextTab;
            }
          } else if (sepText.equals("'")) {
            display.lprintln();
            tp = 0;
          }
          // Semicolon does nothing (items concatenate)
          suppressNewline = true;
        }
      }
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
      for (int i = 0; i < printList.getChildCount(); i++) {
        var child = printList.getChild(i);
        if (child instanceof PrintItemContext item) {
          tabPos = executePrintItem(item, tabPos);
          suppressNewline = false;
        } else if (child instanceof PrintSepContext sep) {
          String sepText = sep.getText();
          if (sepText.equals(",")) {
            // Comma moves to next tab stop
            int nextTab = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
            if (nextTab > tabPos) {
              display.print(" ".repeat(nextTab - tabPos));
              tabPos = display.currentCol();
            }
          } else if (sepText.equals("'")) {
            display.println();
            tabPos = 0;
          }
          // Semicolon does nothing (items concatenate)
          suppressNewline = true;
        }
      }
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
  public Object visitReadStmt(ReadStmtContext ctx) {
    for (var target : ctx.assignmentTarget()) {
      if (state.dataLineLabel() == -1) {
        restoreTo(0);
      }
      int lineLabel = state.dataLineLabel();
      if (lineLabel == Integer.MAX_VALUE) {
        throw codedException(ReportCode.OUT_OF_DATA, "Out of DATA");
      }
      ProgramLine line = state.program().get(lineLabel);
      if (line == null) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      List<StatementContext> stmts = line.getFlattenedStatements(AntlrParser.INSTANCE);
      int stmtIdx = state.dataStatementIndex();
      StatementContext stmt = stmts.get(stmtIdx - 1);
      if (!(stmt instanceof DataStmtContext dataCtx)) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      int exprIdx = state.dataExpressionIndex();
      ExpressionContext exprCtx = dataCtx.expression(exprIdx);

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
          Integer nextLine = state.program().higherKey(lineLabel);
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
        double val = evalNum(exprCtx.numExpr());
        assignNumTarget(target, val);
      } else {
        if (exprCtx.strExpr() == null) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string");
        }
        BStr val = evalStr(exprCtx.strExpr());
        assignStrTarget(target, val);
      }
    }
    return null;
  }

  @Override
  public Object visitRemStmt(RemStmtContext ctx) {
    return null;
  }

  @Override
  public Object visitRestoreStmt(RestoreStmtContext ctx) {
    int target = 0;
    if (ctx.numExpr() != null) {
      double val = evalNum(ctx.numExpr());
      target = (int) Math.round(val);
      if (target < 0 || target > Limits.MAX_TARGET_LABEL) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Line label out of range");
      }
    }
    restoreTo(target);
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
  public Object visitStopStmt(StopStmtContext ctx) {
    state.setRunning(false);
    throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
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
        if (!ca.arrayDimensions().isEmpty()) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
        }
        ca.elements()[0] = val.paddedOrTruncatedTo(ca.stringLength());
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
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
