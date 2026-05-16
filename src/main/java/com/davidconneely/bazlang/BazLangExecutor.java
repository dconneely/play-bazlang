package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangLexer;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/** Executes BazLang from the ANTLR ParseTree. */
public class BazLangExecutor extends BazLangBaseVisitor<Object> {
  private static final AntlrParser PARSER = new AntlrParser();
  private final EvalState state;
  private final BazLangDisplay display;

  public BazLangExecutor(EvalState state, BazLangDisplay display) {
    this.state = state;
    this.display = display;
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
  public Object visitContStmt(ContStmtContext ctx) {
    int m = state.lastReportLabel();
    if (m <= 0) {
      return null;
    }
    if (state.lastReportCode() == ReportCode.STOP_STATEMENT) {
      state.setPendingJumpLabel(state.program().higherKey(m));
    } else {
      state.setPendingJumpLabel(m);
    }
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
      state.strVars().remove(name);
      int flen = dims.removeLast();
      int total = 1;
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
      int total = 1;
      for (int d : dims) {
        total *= d;
      }
      if (total <= 0 || total > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      state.numArrays().put(name, new EvalState.NumArray(dims, new double[total]));
    }
    return null;
  }

  @Override
  public Object visitFastStmt(FastStmtContext ctx) {
    return null; // Not implemented
  }

  @Override
  public Object visitForStmt(ForStmtContext ctx) {
    String var = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    double st = evalNum(ctx.numExpr(0));
    double en = evalNum(ctx.numExpr(1));
    double step = ctx.numExpr().size() > 2 ? evalNum(ctx.numExpr(2)) : 1.0;
    state.numScalars().put(var, st);
    state.forLoops().put(var, new EvalState.ForLoopData(en, step, state.currentLineLabel()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT
      Integer nextLabel = state.program().higherKey(state.currentLineLabel());
      while (nextLabel != null) {
        ProgramLine line = state.program().get(nextLabel);
        StatementContext stmt = line.getStatement(PARSER);
        if (stmt instanceof NextStmtContext nextCtx
            && nextCtx.NUM_IDENTIFIER().getText().equalsIgnoreCase(var)) {
          state.setPendingJumpLabel(state.program().higherKey(nextLabel));
          return null;
        }
        nextLabel = state.program().higherKey(nextLabel);
      }
      state.setRunning(false);
    }
    return null;
  }

  @Override
  public Object visitGosubStmt(GosubStmtContext ctx) {
    state.returnStack().push(state.currentLineLabel());
    int target = (int) Math.round(evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  @Override
  public Object visitGotoStmt(GotoStmtContext ctx) {
    int target = (int) Math.round(evalNum(ctx.numExpr()));
    gotoLabel(target);
    return null;
  }

  private void gotoLabel(int target) {
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "GOTO line label out of range");
    }
    Integer label = state.program().ceilingKey(target);
    if (label != null) {
      state.setPendingJumpLabel(label);
    } else {
      state.setRunning(false);
    }
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
          double val = evaluateNumericExpression(line.trim());
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
      assignStrTarget(target, line);
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
      String val = evalStr(expr.strExpr());
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

  /**
   * Parses lineRange for DELETE and REFORMAT: single number means "just that line"; requires at
   * least one number.
   */
  private int[] parseDeleteReformatLineRange(BazLangParser.LineRangeContext range) {
    if (range == null) {
      throw new ReportException(
          ReportCode.NONSENSE_IN_BASIC, 0, "Command requires at least one line number");
    }
    var nums = range.numExpr();
    if (nums.isEmpty() && range.TO() == null) {
      throw new ReportException(
          ReportCode.NONSENSE_IN_BASIC, 0, "Command requires at least one line number or TO");
    }
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
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
      // Just TO: start=MIN, end=MAX (already set)
    } else {
      // Command n (no TO): just that single line
      start = (int) evalNum(nums.getFirst());
      end = start;
    }
    return new int[] {start, end};
  }

  /** Execute DELETE command with parsed line range. */
  public void executeDelete(BazLangParser.LineRangeContext range) {
    int[] bounds = parseDeleteReformatLineRange(range);
    state.program().subMap(bounds[0], true, bounds[1], true).clear();
  }

  /** Execute REFORMAT command with optional line range. */
  public void executeReformat(BazLangParser.LineRangeContext range) {
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
    if (range != null) {
      int[] bounds = parseDeleteReformatLineRange(range);
      start = bounds[0];
      end = bounds[1];
    }

    NavigableMap<Integer, ProgramLine> toReformat = state.program().subMap(start, true, end, true);
    if (toReformat.isEmpty()) {
      return;
    }

    ReformatVisitor formatter = new ReformatVisitor();
    // Use a temporary map to avoid ConcurrentModificationException if we were iterating
    // differently,
    // though for TreeMap/NavigableMap subMap(..).entrySet() it might be safe to put back into the
    // main map.
    // Actually, put() into the main map while iterating over the subMap entrySet is generally fine.
    for (Map.Entry<Integer, ProgramLine> entry : toReformat.entrySet()) {
      int lineNum = entry.getKey();
      ProgramLine line = entry.getValue();
      StatementContext stmt = line.getStatement(PARSER);
      String formattedSource = formatter.visit(stmt);
      state.program().put(lineNum, new ProgramLine(lineNum, formattedSource));
    }
  }

  /** Execute RENUM command with parsed arguments. */
  public void executeRenum(BazLangParser.RenumArgsContext args) {
    NavigableMap<Integer, ProgramLine> program = state.program();
    if (program.isEmpty()) {
      return;
    }

    // Parse renumArgs: [new_start] [STEP new_step] [, [old_start] TO [old_end]]
    int newStart = 10;
    int newStep = 10;
    int oldStart = program.firstKey();
    int oldEnd = program.lastKey();

    if (args != null) {
      var nums = args.numExpr();
      int numIndex = 0;

      // First number is new_start (if present and not after STEP)
      if (!nums.isEmpty() && args.STEP() == null
          || !nums.isEmpty()
              && args.getText().toUpperCase().indexOf("STEP")
                  > args.getText().indexOf(nums.getFirst().getText())) {
        newStart = (int) evalNum(nums.get(numIndex++));
      }

      // STEP number
      if (args.STEP() != null && numIndex < nums.size()) {
        newStep = (int) evalNum(nums.get(numIndex++));
      }

      // After comma: [old_start] TO [old_end]
      if (args.TO() != null) {
        // Check if there's a number before TO
        String argsText = args.getText().toUpperCase();
        int commaPos = argsText.indexOf(',');
        int toPos = argsText.indexOf("TO");

        if (commaPos >= 0 && numIndex < nums.size()) {
          // Check if next number is before or after TO
          String numText = nums.get(numIndex).getText();
          int numPos = argsText.indexOf(numText, commaPos);
          if (numPos < toPos) {
            oldStart = (int) evalNum(nums.get(numIndex++));
          }
        }

        // Number after TO
        if (numIndex < nums.size()) {
          oldEnd = (int) evalNum(nums.get(numIndex));
        }
      }
    }

    // Validate
    if (newStart < Limits.MIN_LINE_LABEL) {
      throw codedException(
          ReportCode.INTEGER_OUT_OF_RANGE, "New start must be >= " + Limits.MIN_LINE_LABEL);
    }
    if (newStep < 1) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Step must be >= 1");
    }

    // Get lines to renumber
    NavigableMap<Integer, ProgramLine> toRenumber = program.subMap(oldStart, true, oldEnd, true);
    if (toRenumber.isEmpty()) {
      return;
    }

    int count = toRenumber.size();
    long newEndLong = (long) newStart + (long) (count - 1) * newStep;
    if (newEndLong > Limits.MAX_LINE_LABEL) {
      throw codedException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          "Renumbering would exceed max line number " + Limits.MAX_LINE_LABEL);
    }
    int newEnd = (int) newEndLong;

    // Check boundaries: preserve line order
    Integer lineBefore = program.lowerKey(oldStart);
    if (lineBefore != null && newStart <= lineBefore) {
      throw codedException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          "New start "
              + newStart
              + " must be greater than line "
              + lineBefore
              + " to preserve line order");
    }
    Integer lineAfter = program.higherKey(oldEnd);
    if (lineAfter != null && newEnd >= lineAfter) {
      throw codedException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          "New end " + newEnd + " must be less than line " + lineAfter + " to preserve line order");
    }

    // Build old->new mapping
    Map<Integer, Integer> mapping = new HashMap<>();
    int newNum = newStart;
    for (int oldNum : toRenumber.keySet()) {
      mapping.put(oldNum, newNum);
      newNum += newStep;
    }

    // Update GOTO/GOSUB targets in all lines
    updateGotoGosubTargets(program, mapping, oldStart, oldEnd);

    // Extract, remove, re-insert with new numbers
    Map<Integer, ProgramLine> extracted = new HashMap<>(toRenumber);
    toRenumber.clear();
    for (Map.Entry<Integer, ProgramLine> entry : extracted.entrySet()) {
      int oldLineNum = entry.getKey();
      int newLineNum = mapping.get(oldLineNum);
      ProgramLine oldLine = entry.getValue();
      program.put(newLineNum, new ProgramLine(newLineNum, oldLine.sourceText()));
    }
  }

  private void updateGotoGosubTargets(
      NavigableMap<Integer, ProgramLine> program,
      Map<Integer, Integer> mapping,
      int oldStart,
      int oldEnd) {
    Map<Integer, String> updates = new HashMap<>();

    for (Map.Entry<Integer, ProgramLine> entry : program.entrySet()) {
      int lineNum = entry.getKey();
      ProgramLine line = entry.getValue();
      String source = line.sourceText();
      String upperSource = source.toUpperCase();

      // Efficiency optimization: skip lines that definitely don't have GOTO/GOSUB
      if (!upperSource.contains("GOTO") && !upperSource.contains("GOSUB")) {
        continue;
      }

      // Use lexer to find GOTO/GOSUB keywords correctly (handles strings and comments)
      CharStream input = CharStreams.fromString(source);
      BazLangLexer lexer = new BazLangLexer(input);
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      tokens.fill();
      List<Token> tokenList = tokens.getTokens();

      StringBuilder newSource = new StringBuilder();
      int lastCopiedPos = 0;
      boolean modified = false;

      for (int i = 0; i < tokenList.size(); i++) {
        Token t = tokenList.get(i);
        if ((t.getType() == BazLangLexer.GOTO || t.getType() == BazLangLexer.GOSUB)
            && i + 1 < tokenList.size()) {
          // Check for literal line number following the keyword
          Token next = tokenList.get(i + 1);
          if (next.getType() == BazLangLexer.NUM_LITERAL) {
            double val = Double.parseDouble(next.getText());
            int target = (int) Math.round(val);

            Integer newTarget = null;
            if (mapping.containsKey(target)) {
              newTarget = mapping.get(target);
            } else if (target >= oldStart && target <= oldEnd) {
              // Target in range but doesn't exist - use ceiling key (what GOTO would find)
              Integer ceilingKey = program.ceilingKey(target);
              if (ceilingKey != null && mapping.containsKey(ceilingKey)) {
                newTarget = mapping.get(ceilingKey);
                display.println(
                    "Warning: Line "
                        + lineNum
                        + " references non-existent line "
                        + target
                        + ", updated to "
                        + newTarget);
              }
            }

            if (newTarget != null) {
              // Suffix-preserve replacement: only change the literal part
              newSource.append(source, lastCopiedPos, next.getStartIndex()).append(newTarget);
              lastCopiedPos = next.getStopIndex() + 1;
              modified = true;
            }
          }
        }
      }

      if (modified) {
        newSource.append(source.substring(lastCopiedPos));
        updates.put(lineNum, newSource.toString());
      }
    }

    for (Map.Entry<Integer, String> update : updates.entrySet()) {
      int lineNum = update.getKey();
      String newSource = update.getValue();
      program.put(lineNum, new ProgramLine(lineNum, newSource));
    }
  }

  @Override
  public Object visitLoadStmt(LoadStmtContext ctx) {
    String filename = evalStr(ctx.strExpr());
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
      state.setProgram(PARSER.parseProgramLines(source));
    } catch (IOException e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to load: " + e.getMessage());
    }
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
  public Object visitNextStmt(NextStmtContext ctx) {
    String var = ctx.NUM_IDENTIFIER().getText().toUpperCase();
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
      state.setPendingJumpLabel(state.program().higherKey(d.loopPc()));
    }
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
    state.random().setSeed(seed);
    return null;
  }

  @Override
  public Object visitRemStmt(RemStmtContext ctx) {
    return null;
  }

  @Override
  public Object visitReturnStmt(ReturnStmtContext ctx) {
    if (state.returnStack().isEmpty()) {
      throw codedException(ReportCode.RETURN_WITHOUT_GOSUB, "RETURN without GOSUB");
    }
    Integer gosubLine = state.returnStack().pop();
    state.setPendingJumpLabel(state.program().higherKey(gosubLine));
    return null;
  }

  @Override
  public Object visitRunStmt(RunStmtContext ctx) {
    int target =
        ctx.numExpr() != null ? (int) Math.round(evalNum(ctx.numExpr())) : Limits.MIN_TARGET_LABEL;
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "RUN line label out of range");
    }
    state.clear();
    gotoLabel(target);
    return null;
  }

  @Override
  public Object visitSaveStmt(SaveStmtContext ctx) {
    String filename = evalStr(ctx.strExpr());
    try (var writer = Files.newBufferedWriter(Path.of(filename))) {
      for (var entry : state.program().entrySet()) {
        ProgramLine line = entry.getValue();
        writer.write(line.lineNumber() + " " + line.sourceText());
        writer.newLine();
      }
    } catch (IOException e) {
      throw codedException(ReportCode.INVALID_FILE_NAME, "Failed to save: " + e.getMessage());
    }
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
    return ((Number) visit(ctx)).doubleValue();
  }

  private String evalStr(StrExprContext ctx) {
    return (String) visit(ctx);
  }

  private String evalPrintExpr(ExpressionContext ctx) {
    if (ctx.numExpr() != null) {
      return formatNum(evalNum(ctx.numExpr()));
    } else {
      return evalStr(ctx.strExpr());
    }
  }

  // Numeric expression visitors

  @Override
  public Double visitNumLiteralExpr(NumLiteralExprContext ctx) {
    return Double.parseDouble(ctx.NUM_LITERAL().getText());
  }

  @Override
  public Double visitNumVarExpr(NumVarExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.numScalars().containsKey(name)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
    return state.numScalars().get(name);
  }

  @Override
  public Double visitNumArrayExpr(NumArrayExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.numArrays().containsKey(name)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
    }
    EvalState.NumArray na = state.numArrays().get(name);
    List<Integer> indices = new ArrayList<>();
    for (var e : ctx.numExpr()) {
      indices.add((int) evalNum(e));
    }
    int idx = calculateArrayIndex(na.dimensions(), indices);
    return na.data()[idx];
  }

  @Override
  public Double visitNumParenExpr(NumParenExprContext ctx) {
    return evalNum(ctx.numExpr());
  }

  @Override
  public Double visitNumFuncCallExpr(NumFuncCallExprContext ctx) {
    return (Double) visit(ctx.numFunc());
  }

  @Override
  public Double visitNumPowerExpr(NumPowerExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    if (l < 0.0 && r != Math.floor(r)) {
      throw codedException(ReportCode.INVALID_ARGUMENT, "Negative base with non-integer exponent");
    }
    return requireFinite(Math.pow(l, r));
  }

  @Override
  public Double visitNumUnaryMinusExpr(NumUnaryMinusExprContext ctx) {
    return -evalNum(ctx.numExpr());
  }

  @Override
  public Double visitNumMulDivExpr(NumMulDivExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    if (op.equals("*")) {
      return requireFinite(l * r);
    } else {
      if (r == 0.0) {
        throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
      }
      return l / r;
    }
  }

  @Override
  public Double visitNumAddSubExpr(NumAddSubExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    return requireFinite(op.equals("+") ? l + r : l - r);
  }

  @Override
  public Double visitNumCompExpr(NumCompExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    return switch (op) {
      case "=" -> l == r ? 1.0 : 0.0;
      case "<>" -> l != r ? 1.0 : 0.0;
      case "<" -> l < r ? 1.0 : 0.0;
      case "<=" -> l <= r ? 1.0 : 0.0;
      case ">" -> l > r ? 1.0 : 0.0;
      case ">=" -> l >= r ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  @Override
  public Double visitStrCompExpr(StrCompExprContext ctx) {
    String l = evalStr(ctx.strExpr(0));
    String r = evalStr(ctx.strExpr(1));
    String op = ctx.getChild(1).getText();
    return switch (op) {
      case "=" -> l.equals(r) ? 1.0 : 0.0;
      case "<>" -> !l.equals(r) ? 1.0 : 0.0;
      case "<" -> l.compareTo(r) < 0 ? 1.0 : 0.0;
      case "<=" -> l.compareTo(r) <= 0 ? 1.0 : 0.0;
      case ">" -> l.compareTo(r) > 0 ? 1.0 : 0.0;
      case ">=" -> l.compareTo(r) >= 0 ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  @Override
  public Double visitNumNotExpr(NumNotExprContext ctx) {
    return evalNum(ctx.numExpr()) == 0.0 ? 1.0 : 0.0;
  }

  @Override
  public Double visitNumAndExpr(NumAndExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A AND B = A if B ≠ 0, 0 if B = 0
    return right != 0.0 ? left : 0.0;
  }

  @Override
  public Double visitNumOrExpr(NumOrExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A OR B = 1 if B ≠ 0, A if B = 0
    return right != 0.0 ? 1.0 : left;
  }

  // Numeric function visitors

  @Override
  public Double visitNumFunc(NumFuncContext ctx) {
    // Dispatch to specific function handling
    if (ctx.ABS() != null) {
      return Math.abs(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.ACS() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ACS requires argument in [-1, 1]");
      }
      return Math.acos(arg);
    }
    if (ctx.ASN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ASN requires argument in [-1, 1]");
      }
      return Math.asin(arg);
    }
    if (ctx.ATN() != null) {
      return Math.atan(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.CODE() != null) {
      return (double) evalStrAtom(ctx.strAtom()).codePointAt(0);
    }
    if (ctx.COS() != null) {
      return Math.cos(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.EXP() != null) {
      return requireFinite(Math.exp(evalNumAtom(ctx.numAtom())));
    }
    if (ctx.INT() != null) {
      return Math.floor(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.LEN() != null) {
      return (double) evalStrAtom(ctx.strAtom()).length();
    }
    if (ctx.LN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg <= 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "LN requires a positive argument");
      }
      return Math.log(arg);
    }
    if (ctx.PEEK() != null) {
      evalNumAtom(ctx.numAtom()); // consume arg
      return 0.0;
    }
    if (ctx.PI() != null) {
      return Math.PI;
    }
    if (ctx.RND() != null) {
      return state.random().nextDouble();
    }
    if (ctx.SGN() != null) {
      return Math.signum(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.SIN() != null) {
      return Math.sin(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.SQR() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg < 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "SQR requires a non-negative argument");
      }
      return Math.sqrt(arg);
    }
    if (ctx.TAN() != null) {
      return Math.tan(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.USR() != null) {
      evalNumAtom(ctx.numAtom()); // consume arg
      return 0.0;
    }
    if (ctx.VAL() != null) {
      String exprStr = evalStrAtom(ctx.strAtom()).trim();
      return evaluateNumericExpression(exprStr);
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown function");
  }

  private double evalNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      return Double.parseDouble(ctx.NUM_LITERAL().getText());
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      // Either simple variable or array subscript
      String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
      if (!ctx.numExpr().isEmpty()) {
        // Array subscript: NUM_IDENTIFIER ( numExpr, ... )
        if (!state.numArrays().containsKey(name)) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
        }
        EvalState.NumArray na = state.numArrays().get(name);
        List<Integer> indices = new ArrayList<>();
        for (var e : ctx.numExpr()) {
          indices.add((int) evalNum(e));
        }
        return na.data()[calculateArrayIndex(na.dimensions(), indices)];
      } else {
        // Simple variable
        if (!state.numScalars().containsKey(name)) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
        }
        return state.numScalars().get(name);
      }
    }
    if (!ctx.numExpr().isEmpty()) {
      // Parenthesized: ( numExpr )
      return evalNum(ctx.numExpr(0));
    }
    if (ctx.numFunc() != null) {
      return (Double) visit(ctx.numFunc());
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid numeric atom");
  }

  // String expression visitors

  @Override
  public String visitStrLiteralExpr(StrLiteralExprContext ctx) {
    String text = ctx.STR_LITERAL().getText();
    return text.substring(1, text.length() - 1); // Remove quotes
  }

  @Override
  public String visitStrVarExpr(StrVarExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    if (state.charArrays().containsKey(name)) {
      EvalState.CharArray ca = state.charArrays().get(name);
      if (ca.dimensions().isEmpty()) {
        return new String(ca.data());
      }
    }
    if (state.strVars().containsKey(name)) {
      return state.strVars().get(name);
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
  }

  @Override
  public String visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    var subscript = ctx.strSubscript();
    return evalStrSubscript(name, subscript);
  }

  private String evalStrSubscript(String name, StrSubscriptContext subscript) {
    // Parse indices and slice from subscript
    List<Integer> indices = new ArrayList<>();
    Integer sliceStart = null;
    Integer sliceEnd = null;
    boolean hasSlice = subscript.TO() != null;
    var numExprs = subscript.numExpr();
    if (hasSlice) {
      // Has `TO` - need to figure out which exprs are indices vs slice bounds
      String text = subscript.getText().toUpperCase();
      int toPos = text.indexOf("TO");
      // Count commas before `TO` to determine indices count
      String beforeTo = text.substring(0, toPos);
      int commaCount = (int) beforeTo.chars().filter(c -> c == ',').count();
      // Indices are the expressions before the slice
      for (int i = 0; i < commaCount && i < numExprs.size(); i++) {
        indices.add((int) evalNum(numExprs.get(i)));
      }
      // Remaining expressions are slice bounds
      int sliceExprStart = commaCount;
      if (sliceExprStart < numExprs.size()) {
        // Check if there's content before TO (slice start)
        String trimBefore = beforeTo.substring(beforeTo.lastIndexOf(',') + 1).trim();
        if (!trimBefore.isEmpty() || (commaCount == 0 && !beforeTo.isBlank())) {
          sliceStart = (int) evalNum(numExprs.get(sliceExprStart));
          sliceExprStart++;
        }
      }
      if (sliceExprStart < numExprs.size()) {
        sliceEnd = (int) evalNum(numExprs.get(sliceExprStart));
      }
    } else {
      // No TO - all expressions are indices
      for (var e : numExprs) {
        indices.add((int) evalNum(e));
      }
    }
    // Now evaluate the subscript based on variable type
    if (state.charArrays().containsKey(name)) {
      EvalState.CharArray ca = state.charArrays().get(name);
      int n = ca.dimensions().size();
      Integer charIndex = null;
      // Handle char index (extra index beyond array dimensions)
      if (indices.size() == n + 1) {
        charIndex = indices.removeLast();
      } else if (indices.size() != n && n == 0 && indices.size() == 1) {
        charIndex = indices.removeFirst();
      }
      int arrayIdx = calculateArrayIndex(ca.dimensions(), indices);
      int base = arrayIdx * ca.fixedStrLen();
      return sliceCharArray(ca.data(), base, ca.fixedStrLen(), charIndex, sliceStart, sliceEnd);
    }
    if (state.strVars().containsKey(name)) {
      String s = state.strVars().get(name);
      Integer charIndex = null;
      if (indices.size() == 1 && !hasSlice) {
        charIndex = indices.getFirst();
        indices.clear();
      }
      if (!indices.isEmpty()) {
        throw codedException(
            ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
      }
      return sliceString(s, charIndex, sliceStart, sliceEnd);
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
  }

  @Override
  public String visitStrParenExpr(StrParenExprContext ctx) {
    return evalStr(ctx.strExpr());
  }

  @Override
  public String visitStrConcatExpr(StrConcatExprContext ctx) {
    return evalStr(ctx.strExpr(0)) + evalStr(ctx.strExpr(1));
  }

  @Override
  public String visitStrAndExpr(StrAndExprContext ctx) {
    // str AND n = str if n ≠ 0, "" if n = 0
    String left = evalStr(ctx.strExpr());
    double right = evalNum(ctx.numExpr());
    return right != 0.0 ? left : "";
  }

  @Override
  public String visitStrFuncCallExpr(StrFuncCallExprContext ctx) {
    return (String) visit(ctx.strFunc());
  }

  @Override
  public String visitStrFunc(StrFuncContext ctx) {
    if (ctx.CHR_STR() != null) {
      int code = (int) evalNumAtom(ctx.numAtom());
      return new String(Character.toChars(code));
    }
    if (ctx.INKEY_STR() != null) {
      return display.inkey();
    }
    if (ctx.STR_STR() != null) {
      return formatNum(evalNumAtom(ctx.numAtom()));
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string function");
  }

  private String evalStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      String text = ctx.STR_LITERAL().getText();
      return text.substring(1, text.length() - 1);
    }
    if (ctx.strSubscript() != null) {
      String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
      return evalStrSubscript(name, ctx.strSubscript());
    }
    if (ctx.STR_IDENTIFIER() != null) {
      String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
      if (state.charArrays().containsKey(name)) {
        EvalState.CharArray ca = state.charArrays().get(name);
        if (ca.dimensions().isEmpty()) {
          return new String(ca.data());
        }
      }
      if (state.strVars().containsKey(name)) {
        return state.strVars().get(name);
      }
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
    if (ctx.strExpr() != null) {
      return evalStr(ctx.strExpr());
    }
    if (ctx.strFunc() != null) {
      return (String) visit(ctx.strFunc());
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string atom");
  }

  // ===== Assignment Helpers =====

  private void assignNumTarget(AssignmentTargetContext target, double val) {
    String name = target.NUM_IDENTIFIER().getText().toUpperCase();
    var numExprs = target.numExpr();
    if (numExprs.isEmpty()) {
      // Scalar
      state.numScalars().put(name, val);
    } else {
      // Array element
      if (!state.numArrays().containsKey(name)) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
      }
      EvalState.NumArray na = state.numArrays().get(name);
      List<Integer> indices = new ArrayList<>();
      for (var e : numExprs) {
        indices.add((int) evalNum(e));
      }
      int idx = calculateArrayIndex(na.dimensions(), indices);
      na.data()[idx] = val;
    }
  }

  private void assignStrTarget(AssignmentTargetContext target, String val) {
    String name = target.STR_IDENTIFIER().getText().toUpperCase();
    var subscript = target.strSubscript();
    if (subscript == null) {
      // Scalar assignment
      if (state.charArrays().containsKey(name)) {
        EvalState.CharArray ca = state.charArrays().get(name);
        applyStrAssignment(ca.data(), 0, ca.data().length, null, null, null, val);
        return;
      }
      state.strVars().put(name, val);
      return;
    }
    // Subscripted assignment - parse subscript
    List<Integer> indices = new ArrayList<>();
    Integer sliceStart = null;
    Integer sliceEnd = null;
    boolean hasSlice = subscript.TO() != null;
    var numExprs = subscript.numExpr();
    if (hasSlice) {
      String text = subscript.getText().toUpperCase();
      int toPos = text.indexOf("TO");
      String beforeTo = text.substring(0, toPos);
      int commaCount = (int) beforeTo.chars().filter(c -> c == ',').count();
      for (int i = 0; i < commaCount && i < numExprs.size(); i++) {
        indices.add((int) evalNum(numExprs.get(i)));
      }
      int sliceExprStart = commaCount;
      if (sliceExprStart < numExprs.size()) {
        String trimBefore = beforeTo.substring(beforeTo.lastIndexOf(',') + 1).trim();
        if (!trimBefore.isEmpty() || (commaCount == 0 && !beforeTo.isBlank())) {
          sliceStart = (int) evalNum(numExprs.get(sliceExprStart));
          sliceExprStart++;
        }
      }
      if (sliceExprStart < numExprs.size()) {
        sliceEnd = (int) evalNum(numExprs.get(sliceExprStart));
      }
    } else {
      for (var e : numExprs) {
        indices.add((int) evalNum(e));
      }
    }
    if (state.charArrays().containsKey(name)) {
      EvalState.CharArray ca = state.charArrays().get(name);
      int n = ca.dimensions().size();
      Integer charIndex = null;
      if (indices.size() == n + 1) {
        charIndex = indices.removeLast();
      } else if (indices.size() != n && n == 0 && indices.size() == 1) {
        charIndex = indices.removeFirst();
      }
      int arrayIdx = calculateArrayIndex(ca.dimensions(), indices);
      int base = arrayIdx * ca.fixedStrLen();
      applyStrAssignment(ca.data(), base, ca.fixedStrLen(), charIndex, sliceStart, sliceEnd, val);
    } else if (state.strVars().containsKey(name)) {
      String str = state.strVars().get(name);
      char[] chars = str.toCharArray();
      Integer charIndex = null;
      if (indices.size() == 1 && !hasSlice) {
        charIndex = indices.getFirst();
        indices.clear();
      }
      if (!indices.isEmpty()) {
        throw codedException(
            ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
      }
      applyStrAssignment(chars, 0, chars.length, charIndex, sliceStart, sliceEnd, val);
      state.strVars().put(name, new String(chars));
    } else {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
  }

  // ===== Utility Methods =====

  private int calculateArrayIndex(List<Integer> dimensions, List<Integer> indices) {
    int n = dimensions.size();
    if (indices.size() != n) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    int idx = 0;
    int m = 1;
    for (int i = n - 1; i >= 0; i--) {
      int sz = dimensions.get(i);
      int v = indices.get(i);
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  private String sliceCharArray(
      char[] data, int base, int len, Integer charIdx, Integer sliceStart, Integer sliceEnd) {
    int st = (charIdx != null ? charIdx : 1) + (sliceStart != null ? sliceStart - 1 : 0);
    int en =
        (charIdx != null ? charIdx : 1)
            + (sliceEnd != null ? sliceEnd - 1 : (charIdx != null ? 0 : len - 1));
    if (st < 1 || en > len || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    return new String(data, base + st - 1, en - st + 1);
  }

  private String sliceString(String s, Integer charIdx, Integer sliceStart, Integer sliceEnd) {
    int len = s.length();
    int st = (charIdx != null ? charIdx : 1) + (sliceStart != null ? sliceStart - 1 : 0);
    int en =
        (charIdx != null ? charIdx : 1)
            + (sliceEnd != null ? sliceEnd - 1 : (charIdx != null ? 0 : len - 1));
    if (st < 1 || en > len || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    return s.substring(st - 1, en);
  }

  private void applyStrAssignment(
      char[] data,
      int base,
      int len,
      Integer charIdx,
      Integer sliceStart,
      Integer sliceEnd,
      String val) {
    int st = (charIdx != null ? charIdx : 1) + (sliceStart != null ? sliceStart - 1 : 0);
    int en =
        (charIdx != null ? charIdx : 1)
            + (sliceEnd != null ? sliceEnd - 1 : (charIdx != null ? 0 : len - 1));
    if (st < 1 || en > len || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    for (int i = 0; i < (en - st + 1); i++) {
      data[base + st - 1 + i] = (i < val.length()) ? val.charAt(i) : ' ';
    }
  }

  /**
   * Evaluates a string as a numeric expression. Used by VAL function and INPUT for numeric
   * variables. Per ZX81 BASIC, this parses and evaluates the full expression.
   *
   * @param exprStr the expression string to evaluate
   * @return the numeric result
   * @throws ReportException if the expression is invalid
   */
  private double evaluateNumericExpression(String exprStr) {
    if (exprStr.isEmpty()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Empty expression");
    }
    BazLangParser.NumExprContext exprCtx = PARSER.parseNumExpr(exprStr);
    return evalNum(exprCtx);
  }

  private static final double ULP0 = 1e-39;

  /** Formats a number with up to 8 decimal digits, scientific notation for extreme values. */
  private String formatNum(double d) {
    if (Math.abs(d) < ULP0) {
      return "0";
    }
    if (Double.isNaN(d)) {
      return "NaN";
    }
    if (Double.isInfinite(d)) {
      return d > 0.0 ? "Infinity" : "-Infinity";
    }
    double abs = Math.abs(d);
    if (abs < 1e-5 || abs >= 1e13) {
      // Scientific notation for extreme values
      java.text.DecimalFormat df = new java.text.DecimalFormat("0.########E0");
      String result = df.format(d);
      // Add + sign for positive exponents (e.g., 1E15 -> 1E+15)
      int ePos = result.indexOf('E');
      if (ePos >= 0 && ePos + 1 < result.length() && result.charAt(ePos + 1) != '-') {
        result = result.substring(0, ePos + 1) + "+" + result.substring(ePos + 1);
      }
      return result;
    } else {
      // Normal decimal notation with up to 8 decimal places
      java.text.DecimalFormat df = new java.text.DecimalFormat("0.########");
      return df.format(d);
    }
  }

  private double requireFinite(double d) {
    if (!Double.isFinite(d)) {
      throw codedException(ReportCode.NUMBER_TOO_BIG, "Arithmetic overflow");
    }
    return d;
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), msg);
  }
}
