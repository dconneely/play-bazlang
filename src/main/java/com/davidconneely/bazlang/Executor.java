package com.davidconneely.bazlang;

import com.davidconneely.bazlang.io.Display;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Executor {
  private final EvalState state;
  private final Evaluator evaluator;
  private final Display display;

  public Executor(EvalState state, Evaluator evaluator, Display display) {
    this.state = state;
    this.evaluator = evaluator;
    this.display = display;
  }

  public Display terminal() {
    return display;
  }

  public void executeStatement(final Statement stmt) {
    switch (stmt) {
      case Statement.Clear _ -> executeClear();
      case Statement.Cls _ -> executeCls();
      case Statement.Cont _ -> executeCont();
      case Statement.Copy _ -> {}
      case Statement.Dim dim -> executeDim(dim);
      case Statement.Fast _ -> {}
      case Statement.For forStmt -> executeFor(forStmt);
      case Statement.Gosub gosub -> executeGosub(gosub);
      case Statement.Goto gotoStmt -> executeGoto(gotoStmt);
      case Statement.If ifStmt -> executeIf(ifStmt);
      case Statement.Input input -> executeInput(input);
      case Statement.Let let -> executeLet(let);
      case Statement.ListStmt list -> executeList(list);
      case Statement.LList llist -> executeLList(llist);
      case Statement.Load load -> executeLoad(load);
      case Statement.LPrint lprint -> executeLPrint(lprint);
      case Statement.New _ -> executeNew();
      case Statement.Next next -> executeNext(next);
      case Statement.Pause pause -> executePause(pause);
      case Statement.Plot plot -> executePlot(plot);
      case Statement.Poke _ -> {}
      case Statement.Print print -> executePrint(print);
      case Statement.Rand rand -> executeRand(rand);
      case Statement.Rem _ -> {}
      case Statement.Return _ -> executeReturn();
      case Statement.Run run -> executeRun(run);
      case Statement.Save save -> executeSave(save);
      case Statement.Scroll _ -> executeScroll();
      case Statement.Slow _ -> {}
      case Statement.Stop _ -> executeStop();
      case Statement.Unplot unplot -> executeUnplot(unplot);
      default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown statement");
    }
  }

  private void executeClear() {
    state.clear();
  }

  private void executeCls() {
    display.cls();
  }

  private void executeCont() {
    int m = state.lastReportLabel();
    if (m <= 0) return;
    if (state.lastReportCode() == ReportCode.STOP_STATEMENT) {
      state.setCurrentLineLabel(m);
    } else {
      Integer lower = state.program().lowerKey(m);
      state.setCurrentLineLabel(lower != null ? lower : -1);
    }
  }

  private void executeDim(Statement.Dim dim) {
    String name = dim.variable();
    boolean isStr = name.endsWith("$");
    List<Integer> dims = new ArrayList<>();
    for (Expression.NumExpr e : dim.dimensions()) {
      dims.add((int) evaluator.evaluateNumExpr(e));
    }
    if (isStr) {
      if (state.charArrays().containsKey(name)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Already DIMensioned");
      }
      state.strVars().remove(name);
      int flen = dims.removeLast(), total = 1;
      for (int d : dims) {
        total *= d;
      }
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      char[] data = new char[total * flen];
      Arrays.fill(data, ' ');
      state.charArrays().put(name, new EvalState.CharArray(dims, flen, data));
    } else {
      if (state.numArrays().containsKey(name)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Already DIMensioned");
      }
      int total = 1;
      for (int d : dims) total *= d;
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      state.numArrays().put(name, new EvalState.NumArray(dims, new double[total]));
    }
  }

  private void executeFor(Statement.For forStmt) {
    double st = evaluator.evaluateNumExpr(forStmt.start());
    double en = evaluator.evaluateNumExpr(forStmt.end());
    double step = evaluator.evaluateNumExpr(forStmt.step());
    String var = forStmt.variable();
    state.numScalars().put(var, st);
    state.forLoops().put(var, new EvalState.ForLoopData(en, step, state.currentLineLabel()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      Integer nextLabel = state.program().higherKey(state.currentLineLabel());
      while (nextLabel != null) {
        if (state.program().get(nextLabel) instanceof Statement.Next(String variable)
            && variable.equalsIgnoreCase(var)) {
          state.setCurrentLineLabel(nextLabel);
          return;
        }
        nextLabel = state.program().higherKey(nextLabel);
      }
      state.setRunning(false);
    }
  }

  private void executeGosub(Statement.Gosub gosub) {
    state.returnStack().push(state.currentLineLabel());
    executeGoto(new Statement.Goto(gosub.targetLabel()));
  }

  private void executeGoto(Statement.Goto gotoStmt) {
    int target = (int) Math.round(evaluator.evaluateNumExpr(gotoStmt.targetLabel()));
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "GOTO line label out of range");
    }
    Integer label = state.program().ceilingKey(target);
    if (label != null) {
      Integer lower = state.program().lowerKey(label);
      state.setCurrentLineLabel(lower != null ? lower : -1);
    } else {
      // GOTO beyond the last line.
      state.setRunning(false);
    }
  }

  private void executeIf(Statement.If ifStmt) {
    if (evaluator.evaluateNumExpr(ifStmt.condition()) != 0.0) {
      executeStatement(ifStmt.thenStmt());
    }
  }

  private void executeInput(Statement.Input input) {
    String line;
    try {
      line = display.readln("");
    } catch (Display.BreakException e) {
      // Ctrl+C at INPUT: interrupt execution
      state.setRunning(false);
      throw codedException(
          ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
    }

    if (line == null) {
      // EOF during INPUT treated as empty string or should it abort?
      // Standard BASIC behavior on EOF isn't well defined, but empty string is safe.
      line = "";
    }

    if (input.target() instanceof Expression.NumExpr nt) {
      double val;
      try {
        val = Double.parseDouble(line);
      } catch (Exception e) {
        val = 0.0;
      }
      assignNum(nt, val);
    } else if (input.target() instanceof Expression.StrExpr st) {
      assignStr(st, line);
    }
  }

  private void executeLet(Statement.Let let) {
    if (let.target() instanceof Expression.NumExpr nt) {
      double val = evaluator.evaluateNumExpr((Expression.NumExpr) let.value());
      assignNum(nt, val);
    } else if (let.target() instanceof Expression.StrExpr st) {
      String val = evaluator.evaluateStrExpr((Expression.StrExpr) let.value());
      assignStr(st, val);
    }
  }

  private void executeList(Statement.ListStmt list) {
    int start = (int) evaluator.evaluateNumExpr(list.startLabel());
    int end = (int) evaluator.evaluateNumExpr(list.endLabel());
    for (var entry : state.program().subMap(start, true, end, true).entrySet()) {
      display.println(entry.getKey() + " " + decompileStatement(entry.getValue()));
    }
  }

  private void executeLList(Statement.LList llist) {
    int start = (int) evaluator.evaluateNumExpr(llist.startLabel());
    int end = (int) evaluator.evaluateNumExpr(llist.endLabel());
    for (var entry : state.program().subMap(start, true, end, true).entrySet()) {
      display.lprintln(entry.getKey() + " " + decompileStatement(entry.getValue()));
    }
  }

  private void executeLoad(Statement.Load load) {
    String filename = evaluator.evaluateStrExpr(load.filename());
    try {
      String source;
      if (filename.startsWith("resource:")) {
        String resourcePath = filename.substring(9);
        try (var is = MainClass.class.getResourceAsStream(resourcePath)) {
          if (is == null) {
            throw codedException(
                ReportCode.INVALID_FILE_NAME, "Resource not found: " + resourcePath);
          }
          source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
      } else {
        source = Files.readString(Path.of(filename));
      }
      Lexer lexer = new Lexer(source);
      var tokens = lexer.tokenize();
      Parser parser = new Parser(tokens);
      state.setProgram(parser.parseProgram());
      // state.clear(); // Clear variables after load
    } catch (Exception e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to load: " + e.getMessage());
    }
  }

  private void executeLPrint(Statement.LPrint lprint) {
    int tp = 0;
    for (PrintItem item : lprint.items()) {
      switch (item) {
        case PrintItem.Expr e -> {
          String s = evaluator.evaluatePrintItemExpr(e.expr());
          display.lprint(s);
          tp += s.length();
        }
        case PrintItem.At at -> tp = (int) evaluator.evaluateNumExpr(at.col());
        case PrintItem.Tab tab -> {
          int t = (int) evaluator.evaluateNumExpr(tab.col());
          if (t < 0) {
            t = ((tp / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
          }
          if (t > tp) {
            display.lprint(" ".repeat(t - tp));
            tp = t;
          }
        }
      }
    }
    if (lprint.newline()) {
      display.lprintln();
    }
  }

  private void executeNew() {
    state.clear();
    state.program().clear();
  }

  private void executeNext(Statement.Next next) {
    String var = next.variable();
    if (!state.forLoops().containsKey(var)) {
      throw codedException(ReportCode.NEXT_WITHOUT_FOR, "NEXT without FOR");
    }
    EvalState.ForLoopData d = state.forLoops().get(var);
    if (!state.numScalars().containsKey(var)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined loop variable");
    }
    double nv = state.numScalars().get(var) + d.step();
    state.numScalars().put(var, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      state.setCurrentLineLabel(d.loopPc());
    }
  }

  private void executePause(Statement.Pause pause) {
    long frames = Math.round(evaluator.evaluateNumExpr(pause.frames()));
    for (long i = 0; i < frames; i++) {
      try {
        Thread.sleep(20L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      if (display.pollForBreak()) {
        state.setRunning(false);
        throw codedException(
            ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
      }
    }
  }

  private void executePlot(Statement.Plot plot) {
    int x = (int) evaluator.evaluateNumExpr(plot.x()),
        y = (int) evaluator.evaluateNumExpr(plot.y());
    try {
      display.plot(x, y);
    } catch (IllegalArgumentException e) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
    }
  }

  private void executePrint(Statement.Print print) {
    int tabPos = display.currentCol();
    for (PrintItem item : print.items()) {
      switch (item) {
        case PrintItem.Expr expr -> {
          String s = evaluator.evaluatePrintItemExpr(expr.expr());
          display.print(s);
          tabPos = display.currentCol();
        }
        case PrintItem.At at -> {
          int row = (int) evaluator.evaluateNumExpr(at.row()),
              col = (int) evaluator.evaluateNumExpr(at.col());
          if (row < 0 || col < 0) {
            throw codedException(ReportCode.OUT_OF_SCREEN, "Screen out of bounds");
          }
          display.locate(row, col);
          tabPos = col;
        }
        case PrintItem.Tab tab -> {
          int t = (int) evaluator.evaluateNumExpr(tab.col());
          if (t < 0) {
            t = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
          }
          if (t > tabPos) {
            display.print(" ".repeat(t - tabPos));
            tabPos = display.currentCol();
          }
        }
      }
    }
    if (print.newline()) {
      display.println();
    }
  }

  private void executeRand(Statement.Rand rand) {
    state.random().setSeed(Math.round(evaluator.evaluateNumExpr(rand.seed())));
  }

  private void executeReturn() {
    if (state.returnStack().isEmpty()) {
      throw codedException(ReportCode.RETURN_WITHOUT_GOSUB, "RETURN without GOSUB");
    }
    state.setCurrentLineLabel(state.returnStack().pop());
  }

  private void executeRun(Statement.Run run) {
    int target = (int) Math.round(evaluator.evaluateNumExpr(run.targetLabel()));
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "RUN line label out of range");
    }
    state.clear();
    executeGoto(new Statement.Goto(run.targetLabel()));
  }

  private void executeSave(Statement.Save save) {
    String filename = evaluator.evaluateStrExpr(save.filename());
    try (var writer = Files.newBufferedWriter(Path.of(filename))) {
      for (var entry : state.program().entrySet()) {
        writer.write(entry.getKey() + " " + decompileStatement(entry.getValue()));
        writer.newLine();
      }
    } catch (Exception e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to save: " + e.getMessage());
    }
  }

  private void executeScroll() {
    display.scroll();
  }

  private void executeStop() {
    state.setRunning(false);
    if (state.currentLineLabel() > 0) {
      throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
    }
  }

  private void executeUnplot(Statement.Unplot unplot) {
    int x = (int) evaluator.evaluateNumExpr(unplot.x()),
        y = (int) evaluator.evaluateNumExpr(unplot.y());
    try {
      display.unplot(x, y);
    } catch (IllegalArgumentException e) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, e.getMessage());
    }
  }

  private void assignNum(Expression.NumExpr target, double val) {
    if (target instanceof Expression.NumExpr.ScalarRef s) {
      state.numScalars().put(s.name(), val);
    } else if (target instanceof Expression.NumExpr.SubscriptRef s) {
      if (!state.numArrays().containsKey(s.name())) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      EvalState.NumArray na = state.numArrays().get(s.name());
      int idx = evaluator.calculateArrayIndex(na.dimensions(), s.indices());
      na.data()[idx] = val;
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid numeric assignment target");
    }
  }

  private void assignStr(Expression.StrExpr target, String val) {
    if (target instanceof Expression.StrExpr.ScalarRef s) {
      String name = s.name();
      if (state.charArrays().containsKey(name)) {
        EvalState.CharArray ca = state.charArrays().get(name);
        int fullLen = ca.data().length;
        applyStrAssignment((i, c) -> ca.data()[i] = c, fullLen, null, null, val);
        return;
      }
      state.strVars().put(name, val);
    } else if (target instanceof Expression.StrExpr.SubscriptRef s) {
      String name = s.name();
      if (state.charArrays().containsKey(name)) {
        EvalState.CharArray ca = state.charArrays().get(name);
        int n = ca.dimensions().size();
        List<Expression.NumExpr> indices = s.indices();
        Expression.NumExpr ci = null;
        if (indices.size() == n + 1) {
          ci = indices.get(n);
          indices = indices.subList(0, n);
        } else if (indices.size() != n) {
          if (n == 0) {
            if (indices.size() == 1) {
              ci = indices.getFirst();
              indices = List.of();
            } else if (!indices.isEmpty()) {
              throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
            }
          } else {
            throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
          }
        }
        int idx = evaluator.calculateArrayIndex(ca.dimensions(), indices);
        int base = idx * ca.fixedStrLen();
        applyStrAssignment((i, c) -> ca.data()[base + i] = c, ca.fixedStrLen(), ci, s.slice(), val);
      } else if (state.strVars().containsKey(name)) {
        String str = state.strVars().get(name);
        StringBuilder sb = new StringBuilder(str);
        if (s.indices().isEmpty()) {
          applyStrAssignment(sb::setCharAt, sb.length(), null, s.slice(), val);
        } else if (s.indices().size() == 1 && s.slice() == null) {
          applyStrAssignment(sb::setCharAt, sb.length(), s.indices().getFirst(), null, val);
        } else {
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        state.strVars().put(name, sb.toString());
      } else {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string assignment target");
    }
  }

  private interface StrStore {
    void set(int index, char c);
  }

  private void applyStrAssignment(
      StrStore store, int fullLen, Expression.NumExpr ci, Expression.Slice sl, String val) {
    Evaluator.Range r = evaluator.calculateSliceRange(fullLen, ci, sl);
    for (int i = 0; i < (r.end() - r.start() + 1); i++) {
      store.set(r.start() + i - 1, (i < val.length()) ? val.charAt(i) : ' ');
    }
  }

  private String decompileStatement(Statement stmt) {
    return switch (stmt) {
      case Statement.Clear _ -> "CLEAR";
      case Statement.Cls _ -> "CLS";
      case Statement.Cont _ -> "CONT";
      case Statement.Copy _ -> "COPY";
      case Statement.Dim dim ->
          "DIM " + dim.variable() + "(" + decompileNumExprs(dim.dimensions()) + ")";
      case Statement.Fast _ -> "FAST";
      case Statement.For forStmt -> {
        String base =
            "FOR "
                + forStmt.variable()
                + " = "
                + decompileNumExpr(forStmt.start())
                + " TO "
                + decompileNumExpr(forStmt.end());
        if (forStmt.step() instanceof Expression.NumExpr.Literal l && l.value() == 1.0) {
          yield base;
        } else {
          yield base + " STEP " + decompileNumExpr(forStmt.step());
        }
      }
      case Statement.Gosub gosub -> "GOSUB " + decompileNumExpr(gosub.targetLabel());
      case Statement.Goto gotoStmt -> "GOTO " + decompileNumExpr(gotoStmt.targetLabel());
      case Statement.If ifStmt ->
          "IF "
              + decompileNumExpr(ifStmt.condition())
              + " THEN "
              + decompileStatement(ifStmt.thenStmt());
      case Statement.Input input -> "INPUT " + decompileLValue(input.target());
      case Statement.Let let ->
          "LET " + decompileLValue(let.target()) + " = " + decompileExpression(let.value());
      case Statement.ListStmt list -> {
        boolean defStart =
            list.startLabel() instanceof Expression.NumExpr.Literal l
                && l.value() == Limits.MIN_TARGET_LABEL;
        boolean defEnd =
            list.endLabel() instanceof Expression.NumExpr.Literal l
                && l.value() == Limits.MAX_TARGET_LABEL;
        if (defStart && defEnd) yield "LIST";
        if (defEnd) yield "LIST " + decompileNumExpr(list.startLabel());
        if (defStart) yield "LIST ," + decompileNumExpr(list.endLabel());
        yield "LIST "
            + decompileNumExpr(list.startLabel())
            + ","
            + decompileNumExpr(list.endLabel());
      }
      case Statement.LList llist -> {
        boolean defStart =
            llist.startLabel() instanceof Expression.NumExpr.Literal l
                && l.value() == Limits.MIN_TARGET_LABEL;
        boolean defEnd =
            llist.endLabel() instanceof Expression.NumExpr.Literal l
                && l.value() == Limits.MAX_TARGET_LABEL;
        if (defStart && defEnd) yield "LLIST";
        if (defEnd) yield "LLIST " + decompileNumExpr(llist.startLabel());
        if (defStart) yield "LLIST ," + decompileNumExpr(llist.endLabel());
        yield "LLIST "
            + decompileNumExpr(llist.startLabel())
            + ","
            + decompileNumExpr(llist.endLabel());
      }
      case Statement.LPrint lprint ->
          "LPRINT " + decompilePrintItems(lprint.items()) + (lprint.newline() ? "" : ";");
      case Statement.New _ -> "NEW";
      case Statement.Next next -> "NEXT " + next.variable();
      case Statement.Pause pause -> "PAUSE " + decompileNumExpr(pause.frames());
      case Statement.Plot plot ->
          "PLOT " + decompileNumExpr(plot.x()) + ", " + decompileNumExpr(plot.y());
      case Statement.Poke poke ->
          "POKE " + decompileNumExpr(poke.address()) + ", " + decompileNumExpr(poke.data());
      case Statement.Print print ->
          "PRINT " + decompilePrintItems(print.items()) + (print.newline() ? "" : ";");
      case Statement.Rand rand -> {
        if (rand.seed() instanceof Expression.NumExpr.Literal l && l.value() == 0.0) {
          yield "RAND";
        }
        yield "RAND " + decompileNumExpr(rand.seed());
      }
      case Statement.Rem rem -> "REM " + rem.comment();
      case Statement.Return _ -> "RETURN";
      case Statement.Run run -> {
        if (run.targetLabel() instanceof Expression.NumExpr.Literal l
            && l.value() == (double) Limits.MIN_TARGET_LABEL) {
          yield "RUN";
        }
        yield "RUN " + decompileNumExpr(run.targetLabel());
      }
      case Statement.Save save -> "SAVE " + decompileStrExpr(save.filename());
      case Statement.Scroll _ -> "SCROLL";
      case Statement.Slow _ -> "SLOW";
      case Statement.Stop _ -> "STOP";
      case Statement.Unplot unplot ->
          "UNPLOT " + decompileNumExpr(unplot.x()) + ", " + decompileNumExpr(unplot.y());
      default -> "";
    };
  }

  private String decompileLValue(Expression target) {
    if (target instanceof Expression.NumExpr n) {
      return decompileNumExpr(n);
    } else if (target instanceof Expression.StrExpr s) {
      return decompileStrExpr(s);
    } else {
      return "";
    }
  }

  private String decompileExpression(Expression expr) {
    if (expr instanceof Expression.NumExpr n) {
      return decompileNumExpr(n);
    } else if (expr instanceof Expression.StrExpr s) {
      return decompileStrExpr(s);
    } else {
      return "";
    }
  }

  private String decompileNumExprs(List<Expression.NumExpr> exprs) {
    return exprs.stream().map(this::decompileNumExpr).collect(Collectors.joining(","));
  }

  private String decompilePrintItems(List<PrintItem> items) {
    StringBuilder sb = new StringBuilder();
    for (PrintItem item : items) {
      if (!sb.isEmpty()
          && !(item instanceof PrintItem.Tab
              && ((PrintItem.Tab) item).col() instanceof Expression.NumExpr.Literal l
              && l.value() == -1)) {
        // Only add generic separator if not a comma-tab
        // But logic is complex for ; and ,
        // Simplified:
      }
      switch (item) {
        case PrintItem.Expr e -> {
          if (!sb.isEmpty()) {
            sb.append(";");
          }
          if (e.expr() instanceof Expression.NumExpr n) {
            sb.append(decompileNumExpr(n));
          } else if (e.expr() instanceof Expression.StrExpr s) {
            sb.append(decompileStrExpr(s));
          }
        }
        case PrintItem.At at -> {
          if (!sb.isEmpty()) {
            sb.append(";");
          }
          sb.append("AT ")
              .append(decompileNumExpr(at.row()))
              .append(",")
              .append(decompileNumExpr(at.col()));
        }
        case PrintItem.Tab tab -> {
          if (tab.col() instanceof Expression.NumExpr.Literal l && l.value() == -1) {
            sb.append(",");
          } else {
            if (!sb.isEmpty()) {
              sb.append(";");
            }
            sb.append("TAB ").append(decompileNumExpr(tab.col()));
          }
        }
      }
    }
    return sb.toString();
  }

  private String decompileNumExpr(Expression.NumExpr expr) {
    // Simplified decompilation - mostly just for debugging/LIST
    // A real decompiler would need to handle precedence parentheses
    return switch (expr) {
      case Expression.NumExpr.Literal nl -> evaluator.formatNum(nl.value());
      case Expression.NumExpr.ScalarRef sr -> sr.name();
      case Expression.NumExpr.SubscriptRef sr ->
          sr.name() + "(" + decompileNumExprs(sr.indices()) + ")";
      case Expression.NumExpr.BinaryOp bo ->
          decompileNumExpr(bo.left())
              + " "
              + getRep(bo.operator())
              + " "
              + decompileNumExpr(bo.right());
      case Expression.NumExpr.UnaryOp uo ->
          getRep(uo.operator()) + " " + decompileNumExpr(uo.operand());
      case Expression.NumExpr.NumFunc fc ->
          getRep(fc.func()) + "(" + decompileNumExpr(fc.argument()) + ")";
      case Expression.NumExpr.StrFunc fc ->
          getRep(fc.func()) + "(" + decompileStrExpr(fc.argument()) + ")";
      case Expression.NumExpr.NullFunc nc -> getRep(nc.func());
      case Expression.NumExpr.NumComp nc ->
          decompileNumExpr(nc.left())
              + " "
              + getRep(nc.operator())
              + " "
              + decompileNumExpr(nc.right());
      case Expression.NumExpr.StrComp sc ->
          decompileStrExpr(sc.left())
              + " "
              + getRep(sc.operator())
              + " "
              + decompileStrExpr(sc.right());
    };
  }

  private String decompileStrExpr(Expression.StrExpr expr) {
    return switch (expr) {
      case Expression.StrExpr.Literal sl -> "\"" + sl.value() + "\"";
      case Expression.StrExpr.ScalarRef sr -> sr.name();
      case Expression.StrExpr.SubscriptRef sr -> {
        StringBuilder sb = new StringBuilder(sr.name()).append("(");
        if (!sr.indices().isEmpty()) {
          sb.append(decompileNumExprs(sr.indices()));
        }
        if (sr.slice() != null) {
          if (!sr.indices().isEmpty()) sb.append(", ");
          if (sr.slice().start() != null) sb.append(decompileNumExpr(sr.slice().start()));
          sb.append(" TO ");
          if (sr.slice().end() != null) sb.append(decompileNumExpr(sr.slice().end()));
        }
        sb.append(")");
        yield sb.toString();
      }
      case Expression.StrExpr.StrConcat sc ->
          decompileStrExpr(sc.left()) + " + " + decompileStrExpr(sc.right());
      case Expression.StrExpr.NumFunc nf ->
          getRep(nf.func()) + "(" + decompileNumExpr(nf.argument()) + ")";
      case Expression.StrExpr.NullFunc nf -> getRep(nf.func());
    };
  }

  private String getRep(TokenType type) {
    return switch (type) {
      case ABS -> "ABS";
      case ACS -> "ACS";
      case AND -> "AND";
      case ASN -> "ASN";
      case ATN -> "ATN";
      case CHR_STR -> "CHR$";
      case CODE -> "CODE";
      case COS -> "COS";
      case DIVIDE -> "/";
      case EQUALS -> "=";
      case EXP -> "EXP";
      case GREATER_EQUAL -> ">=";
      case GREATER_THAN -> ">";
      case INKEY_STR -> "INKEY$";
      case INT -> "INT";
      case LEN -> "LEN";
      case LESS_EQUAL -> "<=";
      case LESS_THAN -> "<";
      case LN -> "LN";
      case MINUS -> "-";
      case MULTIPLY -> "*";
      case NOT -> "NOT";
      case NOT_EQUALS -> "<>";
      case OR -> "OR";
      case PEEK -> "PEEK";
      case PI -> "PI";
      case PLUS -> "+";
      case POWER -> "**";
      case RND -> "RND";
      case SGN -> "SGN";
      case SIN -> "SIN";
      case SQR -> "SQR";
      case STR_STR -> "STR$";
      case TAN -> "TAN";
      case USR -> "USR";
      case VAL -> "VAL";
      default -> "";
    };
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), msg);
  }
}
