package com.davidconneely.bazlang;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Executor {
  private final MachineState state;
  private final Evaluator evaluator;
  private final Terminal terminal;

  public Executor(MachineState state, Evaluator evaluator, Terminal terminal) {
    this.state = state;
    this.evaluator = evaluator;
    this.terminal = terminal;
  }

  public Terminal terminal() {
    return terminal;
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

  private void executeSave(Statement.Save save) {
    String filename = evaluator.evaluateStringExpression(save.filename());
    try (var writer = Files.newBufferedWriter(Path.of(filename))) {
      for (var entry : state.program().entrySet()) {
        writer.write(entry.getKey() + " " + decompileStatement(entry.getValue()));
        writer.newLine();
      }
    } catch (Exception e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to save: " + e.getMessage());
    }
  }

  private void executeClear() {
    state.clear();
  }

  private void executeCls() {
    terminal.cls();
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
    for (Expression.Numeric e : dim.dimensions()) {
      dims.add((int) evaluator.evaluateNumericExpression(e));
    }
    if (isStr) {
      if (state.characterArrays().containsKey(name)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Already DIMensioned");
      }
      state.variableLengthStrings().remove(name);
      int flen = dims.removeLast(), total = 1;
      for (int d : dims) {
        total *= d;
      }
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      char[] data = new char[total * flen];
      Arrays.fill(data, ' ');
      state.characterArrays().put(name, new MachineState.CharacterArray(dims, flen, data));
    } else {
      if (state.numericArrays().containsKey(name)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Already DIMensioned");
      }
      int total = 1;
      for (int d : dims) total *= d;
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      state.numericArrays().put(name, new MachineState.NumericArray(dims, new double[total]));
    }
  }

  private void executeFor(Statement.For forStmt) {
    double st = evaluator.evaluateNumericExpression(forStmt.start());
    double en = evaluator.evaluateNumericExpression(forStmt.end());
    double step = evaluator.evaluateNumericExpression(forStmt.step());
    String var = forStmt.variable();
    state.numericScalars().put(var, st);
    state.forLoops().put(var, new MachineState.ForLoopData(en, step, state.currentLineLabel()));
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
    int target = (int) Math.round(evaluator.evaluateNumericExpression(gotoStmt.targetLabel()));
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
    if (evaluator.evaluateNumericExpression(ifStmt.condition()) != 0.0) {
      executeStatement(ifStmt.thenStatement());
    }
  }

  private void executeInput(Statement.Input input) {
    String line = terminal.readln("");
    if (input.target() instanceof Expression.Numeric nt) {
      double val;
      try {
        val = Double.parseDouble(line);
      } catch (Exception e) {
        val = 0.0;
      }
      assignNumeric(nt, val);
    } else if (input.target() instanceof Expression.String st) {
      assignString(st, line);
    }
  }

  private void executeLet(Statement.Let let) {
    if (let.target() instanceof Expression.Numeric nt) {
      double val = evaluator.evaluateNumericExpression((Expression.Numeric) let.value());
      assignNumeric(nt, val);
    } else if (let.target() instanceof Expression.String st) {
      String val = evaluator.evaluateStringExpression((Expression.String) let.value());
      assignString(st, val);
    }
  }

  private void assignNumeric(Expression.Numeric target, double val) {
    if (target instanceof Expression.Numeric.ScalarRef s) {
      state.numericScalars().put(s.name(), val);
    } else if (target instanceof Expression.Numeric.SubscriptRef s) {
      if (!state.numericArrays().containsKey(s.name())) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      MachineState.NumericArray na = state.numericArrays().get(s.name());
      int idx = evaluator.calculateArrayIndex(na.dimensions(), s.indices());
      na.data()[idx] = val;
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid numeric assignment target");
    }
  }

  private void assignString(Expression.String target, String val) {
    if (target instanceof Expression.String.ScalarRef s) {
      String name = s.name();
      if (state.characterArrays().containsKey(name)) {
        MachineState.CharacterArray ca = state.characterArrays().get(name);
        int fullLen = ca.data().length;
        applyStringAssignment((i, c) -> ca.data()[i] = c, fullLen, null, null, val);
        return;
      }
      state.variableLengthStrings().put(name, val);
    } else if (target instanceof Expression.String.SubscriptRef s) {
      String name = s.name();
      if (state.characterArrays().containsKey(name)) {
        MachineState.CharacterArray ca = state.characterArrays().get(name);
        int n = ca.dimensions().size();
        List<Expression.Numeric> indices = s.indices();
        Expression.Numeric ci = null;
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
        int base = idx * ca.fixedStringLength();
        applyStringAssignment(
            (i, c) -> ca.data()[base + i] = c, ca.fixedStringLength(), ci, s.slice(), val);
      } else if (state.variableLengthStrings().containsKey(name)) {
        String str = state.variableLengthStrings().get(name);
        StringBuilder sb = new StringBuilder(str);
        if (s.indices().isEmpty()) {
          applyStringAssignment(sb::setCharAt, sb.length(), null, s.slice(), val);
        } else if (s.indices().size() == 1 && s.slice() == null) {
          applyStringAssignment(sb::setCharAt, sb.length(), s.indices().getFirst(), null, val);
        } else {
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        state.variableLengthStrings().put(name, sb.toString());
      } else {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string assignment target");
    }
  }

  private void executeList(Statement.ListStmt list) {
    int start = (int) evaluator.evaluateNumericExpression(list.start());
    int end = (int) evaluator.evaluateNumericExpression(list.end());
    for (var entry : state.program().subMap(start, true, end, true).entrySet()) {
      terminal.println(entry.getKey() + " " + decompileStatement(entry.getValue()));
    }
  }

  private void executeLList(Statement.LList llist) {
    int start = (int) evaluator.evaluateNumericExpression(llist.start());
    int end = (int) evaluator.evaluateNumericExpression(llist.end());
    for (var entry : state.program().subMap(start, true, end, true).entrySet()) {
      terminal.lprintln(entry.getKey() + " " + decompileStatement(entry.getValue()));
    }
  }

  private void executeLoad(Statement.Load load) {
    String filename = evaluator.evaluateStringExpression(load.filename());
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
          terminal.lprint(s);
          tp += s.length();
        }
        case PrintItem.At at -> tp = (int) evaluator.evaluateNumericExpression(at.col());
        case PrintItem.Tab tab -> {
          int t = (int) evaluator.evaluateNumericExpression(tab.col());
          if (t < 0) {
            t = ((tp / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
          }
          if (t > tp) {
            terminal.lprint(" ".repeat(t - tp));
            tp = t;
          }
        }
      }
    }
    if (lprint.newline()) {
      terminal.lprintln();
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
    MachineState.ForLoopData d = state.forLoops().get(var);
    if (!state.numericScalars().containsKey(var)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined loop variable");
    }
    double nv = state.numericScalars().get(var) + d.step();
    state.numericScalars().put(var, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      state.setCurrentLineLabel(d.loopPc());
    }
  }

  private void executePause(Statement.Pause pause) {
    long frames = Math.round(evaluator.evaluateNumericExpression(pause.frames()));
    for (long i = 0; i < frames; i++) {
      try {
        Thread.sleep(20L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      if (terminal.checkInterrupt()) {
        state.setRunning(false);
        throw codedException(
            ReportCode.BREAK_CONT_REPEATS, ReportCode.BREAK_CONT_REPEATS.getMessage());
      }
    }
  }

  private void executePlot(Statement.Plot plot) {
    int x = (int) evaluator.evaluateNumericExpression(plot.x()),
        y = (int) evaluator.evaluateNumericExpression(plot.y());
    terminal.plot(x, y);
  }

  private void executePrint(Statement.Print print) {
    int tabPos = terminal.currentCol();
    for (PrintItem item : print.items()) {
      switch (item) {
        case PrintItem.Expr expr -> {
          String s = evaluator.evaluatePrintItemExpr(expr.expr());
          terminal.print(s);
          tabPos = terminal.currentCol();
        }
        case PrintItem.At at -> {
          int row = (int) evaluator.evaluateNumericExpression(at.row()),
              col = (int) evaluator.evaluateNumericExpression(at.col());
          if (row < 0 || col < 0) {
            throw codedException(ReportCode.OUT_OF_SCREEN, "Screen out of bounds");
          }
          terminal.moveCursor(row, col);
          tabPos = col;
        }
        case PrintItem.Tab tab -> {
          int t = (int) evaluator.evaluateNumericExpression(tab.col());
          if (t < 0) {
            t = ((tabPos / Limits.TAB_WIDTH) + 1) * Limits.TAB_WIDTH;
          }
          if (t > tabPos) {
            terminal.print(" ".repeat(t - tabPos));
            tabPos = terminal.currentCol();
          }
        }
      }
    }
    if (print.newline()) {
      terminal.println();
    }
  }

  private void executeRand(Statement.Rand rand) {
    state.random().setSeed(Math.round(evaluator.evaluateNumericExpression(rand.seed())));
  }

  private void executeReturn() {
    if (state.returnStack().isEmpty()) {
      throw codedException(ReportCode.RETURN_WITHOUT_GOSUB, "RETURN without GOSUB");
    }
    state.setCurrentLineLabel(state.returnStack().pop());
  }

  private void executeRun(Statement.Run run) {
    int target = (int) Math.round(evaluator.evaluateNumericExpression(run.targetLabel()));
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "RUN line label out of range");
    }
    state.clear();
    executeGoto(new Statement.Goto(run.targetLabel()));
  }

  private void executeScroll() {
    terminal.scroll();
  }

  private void executeStop() {
    state.setRunning(false);
    if (state.currentLineLabel() > 0) {
      throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
    }
  }

  private void executeUnplot(Statement.Unplot unplot) {
    int x = (int) evaluator.evaluateNumericExpression(unplot.x()),
        y = (int) evaluator.evaluateNumericExpression(unplot.y());
    terminal.unplot(x, y);
  }

  private interface StringStore {
    void set(int index, char c);
  }

  private void applyStringAssignment(
      StringStore store, int fullLen, Expression.Numeric ci, Expression.Slice sl, String val) {
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
          "DIM " + dim.variable() + "(" + decompileNumericExpressions(dim.dimensions()) + ")";
      case Statement.Fast _ -> "FAST";
      case Statement.For forStmt ->
          "FOR "
              + forStmt.variable()
              + " = "
              + decompileNumeric(forStmt.start())
              + " TO "
              + decompileNumeric(forStmt.end())
              + " STEP "
              + decompileNumeric(forStmt.step());
      case Statement.Gosub gosub -> "GOSUB " + decompileNumeric(gosub.targetLabel());
      case Statement.Goto gotoStmt -> "GOTO " + decompileNumeric(gotoStmt.targetLabel());
      case Statement.If ifStmt ->
          "IF "
              + decompileNumeric(ifStmt.condition())
              + " THEN "
              + decompileStatement(ifStmt.thenStatement());
      case Statement.Input input -> "INPUT " + decompileLValue(input.target());
      case Statement.Let let ->
          "LET " + decompileLValue(let.target()) + " = " + decompileExpression(let.value());
      case Statement.ListStmt list ->
          "LIST " + decompileNumeric(list.start()) + ", " + decompileNumeric(list.end());
      case Statement.LList llist ->
          "LLIST " + decompileNumeric(llist.start()) + ", " + decompileNumeric(llist.end());
      case Statement.LPrint lprint ->
          "LPRINT " + decompilePrintItems(lprint.items()) + (lprint.newline() ? "" : ";");
      case Statement.New _ -> "NEW";
      case Statement.Next next -> "NEXT " + next.variable();
      case Statement.Pause pause -> "PAUSE " + decompileNumeric(pause.frames());
      case Statement.Plot plot ->
          "PLOT " + decompileNumeric(plot.x()) + ", " + decompileNumeric(plot.y());
      case Statement.Poke poke ->
          "POKE " + decompileNumeric(poke.address()) + ", " + decompileNumeric(poke.data());
      case Statement.Print print ->
          "PRINT " + decompilePrintItems(print.items()) + (print.newline() ? "" : ";");
      case Statement.Rand rand -> "RAND " + decompileNumeric(rand.seed());
      case Statement.Rem rem -> "REM " + rem.comment();
      case Statement.Return _ -> "RETURN";
      case Statement.Run run -> "RUN " + decompileNumeric(run.targetLabel());
      case Statement.Save save -> "SAVE " + decompileString(save.filename());
      case Statement.Scroll _ -> "SCROLL";
      case Statement.Slow _ -> "SLOW";
      case Statement.Stop _ -> "STOP";
      case Statement.Unplot unplot ->
          "UNPLOT " + decompileNumeric(unplot.x()) + ", " + decompileNumeric(unplot.y());
      default -> "";
    };
  }

  private String decompileLValue(Expression target) {
    if (target instanceof Expression.Numeric n) {
      return decompileNumeric(n);
    } else if (target instanceof Expression.String s) {
      return decompileString(s);
    } else {
      return "";
    }
  }

  private String decompileExpression(Expression expr) {
    if (expr instanceof Expression.Numeric n) {
      return decompileNumeric(n);
    } else if (expr instanceof Expression.String s) {
      return decompileString(s);
    } else {
      return "";
    }
  }

  private String decompileNumericExpressions(List<Expression.Numeric> exprs) {
    return exprs.stream().map(this::decompileNumeric).collect(Collectors.joining(","));
  }

  private String decompilePrintItems(List<PrintItem> items) {
    StringBuilder sb = new StringBuilder();
    for (PrintItem item : items) {
      if (!sb.isEmpty()
          && !(item instanceof PrintItem.Tab
              && ((PrintItem.Tab) item).col() instanceof Expression.Numeric.Literal l
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
          if (e.expr() instanceof Expression.Numeric n) {
            sb.append(decompileNumeric(n));
          } else if (e.expr() instanceof Expression.String s) {
            sb.append(decompileString(s));
          }
        }
        case PrintItem.At at -> {
          if (!sb.isEmpty()) {
            sb.append(";");
          }
          sb.append("AT ")
              .append(decompileNumeric(at.row()))
              .append(",")
              .append(decompileNumeric(at.col()));
        }
        case PrintItem.Tab tab -> {
          if (tab.col() instanceof Expression.Numeric.Literal l && l.value() == -1) {
            sb.append(",");
          } else {
            if (!sb.isEmpty()) {
              sb.append(";");
            }
            sb.append("TAB ").append(decompileNumeric(tab.col()));
          }
        }
      }
    }
    return sb.toString();
  }

  private String decompileNumeric(Expression.Numeric expr) {
    // Simplified decompilation - mostly just for debugging/LIST
    // A real decompiler would need to handle precedence parentheses
    return switch (expr) {
      case Expression.Numeric.Literal nl -> evaluator.formatNumber(nl.value());
      case Expression.Numeric.ScalarRef sr -> sr.name();
      case Expression.Numeric.SubscriptRef sr ->
          sr.name() + "(" + decompileNumericExpressions(sr.indices()) + ")";
      case Expression.Numeric.BinaryOp bo ->
          decompileNumeric(bo.left())
              + " "
              + getRep(bo.operator())
              + " "
              + decompileNumeric(bo.right());
      case Expression.Numeric.UnaryOp uo ->
          getRep(uo.operator()) + " " + decompileNumeric(uo.operand());
      case Expression.Numeric.FuncCall fc ->
          getRep(fc.func()) + "(" + decompileNumeric(fc.argument()) + ")";
      case Expression.Numeric.FuncCallStr fc ->
          getRep(fc.func()) + "(" + decompileString(fc.argument()) + ")";
      case Expression.Numeric.NullaryCall nc -> getRep(nc.func());
      case Expression.Numeric.NumericComparison nc ->
          decompileNumeric(nc.left())
              + " "
              + getRep(nc.operator())
              + " "
              + decompileNumeric(nc.right());
      case Expression.Numeric.StringComparison sc ->
          decompileString(sc.left())
              + " "
              + getRep(sc.operator())
              + " "
              + decompileString(sc.right());
    };
  }

  private String decompileString(Expression.String expr) {
    return switch (expr) {
      case Expression.String.Literal sl -> "\"" + sl.value() + "\"";
      case Expression.String.ScalarRef sr -> sr.name();
      case Expression.String.SubscriptRef sr -> {
        StringBuilder sb = new StringBuilder(sr.name()).append("(");
        if (!sr.indices().isEmpty()) {
          sb.append(decompileNumericExpressions(sr.indices()));
        }
        if (sr.slice() != null) {
          if (!sr.indices().isEmpty()) sb.append(", ");
          if (sr.slice().start() != null) sb.append(decompileNumeric(sr.slice().start()));
          sb.append(" TO ");
          if (sr.slice().end() != null) sb.append(decompileNumeric(sr.slice().end()));
        }
        sb.append(")");
        yield sb.toString();
      }
      case Expression.String.Concatenation sc ->
          decompileString(sc.left()) + " + " + decompileString(sc.right());
      case Expression.String.FuncCall fc ->
          getRep(fc.func()) + "(" + decompileNumeric(fc.argument()) + ")";
      case Expression.String.NullaryCall nc -> getRep(nc.func());
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
