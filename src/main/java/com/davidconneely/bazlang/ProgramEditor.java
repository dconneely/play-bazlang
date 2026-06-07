package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangLexer;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.antlr.BazLangParser.StatementsContext;
import com.davidconneely.bazlang.io.BazLangDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Handles program-editing REPL commands: DELETE, REFORMAT, and RENUM. These manipulate the
 * program's source text and line numbering but do not execute BASIC statements.
 */
public class ProgramEditor {
  private final EvalState state;
  private final BazLangDisplay display;
  private final AntlrParser parser;
  private final ToDoubleFunction<BazLangParser.NumExprContext> numEval;

  public ProgramEditor(
      EvalState state,
      BazLangDisplay display,
      AntlrParser parser,
      ToDoubleFunction<BazLangParser.NumExprContext> numEval) {
    this.state = state;
    this.display = display;
    this.parser = parser;
    this.numEval = numEval;
  }

  /** Execute DELETE command with parsed line range. */
  public void executeDelete(BazLangParser.LineRangeContext range) {
    int[] bounds = parseDeleteReformatLineRange(range);
    state.program().clearRange(bounds[0], true, bounds[1], true);
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

    List<Map.Entry<Integer, ProgramLine>> toReformat = new ArrayList<>();
    for (Map.Entry<Integer, ProgramLine> entry :
        state.program().subMapEntries(start, true, end, true)) {
      toReformat.add(entry);
    }
    if (toReformat.isEmpty()) {
      return;
    }

    ReformatVisitor formatter = new ReformatVisitor();
    for (Map.Entry<Integer, ProgramLine> entry : toReformat) {
      int lineNum = entry.getKey();
      ProgramLine line = entry.getValue();
      StatementsContext stmt = line.getStatements(parser);
      String formattedSource = formatter.visit(stmt);
      state.program().put(lineNum, new ProgramLine(lineNum, formattedSource));
    }
  }

  /** Execute RENUM command with parsed arguments. */
  public void executeRenum(BazLangParser.RenumArgsContext args) {
    Program program = state.program();
    if (program.isEmpty()) {
      return;
    }

    int[] parsedArgs = parseRenumArgs(program, args);
    int newStart = parsedArgs[0];
    int newStep = parsedArgs[1];
    int oldStart = parsedArgs[2];
    int oldEnd = parsedArgs[3];

    if (newStart < Limits.MIN_LINE_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE, 0, "New start must be >= " + Limits.MIN_LINE_LABEL);
    }
    if (newStep < 1) {
      throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Step must be >= 1");
    }

    List<Map.Entry<Integer, ProgramLine>> toRenumber = new ArrayList<>();
    for (var entry : program.subMapEntries(oldStart, true, oldEnd, true)) {
      toRenumber.add(entry);
    }
    if (toRenumber.isEmpty()) {
      return;
    }

    int count = toRenumber.size();
    long newEndLong = (long) newStart + (long) (count - 1) * newStep;
    if (newEndLong > Limits.MAX_LINE_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          0,
          "Renumbering would exceed max line number " + Limits.MAX_LINE_LABEL);
    }
    int newEnd = (int) newEndLong;

    Integer lineBefore = program.lowerKey(oldStart);
    if (lineBefore != null && newStart <= lineBefore) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          0,
          "New start "
              + newStart
              + " must be greater than line "
              + lineBefore
              + " to preserve line order");
    }
    Integer lineAfter = program.higherKey(oldEnd);
    if (lineAfter != null && newEnd >= lineAfter) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          0,
          "New end " + newEnd + " must be less than line " + lineAfter + " to preserve line order");
    }

    Map<Integer, Integer> mapping = new HashMap<>();
    int newNum = newStart;
    for (var entry : toRenumber) {
      mapping.put(entry.getKey(), newNum);
      newNum += newStep;
    }

    updateGotoGosubTargets(program, mapping, oldStart, oldEnd);

    Map<Integer, ProgramLine> extracted = new HashMap<>();
    for (var entry : toRenumber) {
      extracted.put(entry.getKey(), entry.getValue());
    }
    program.clearRange(oldStart, true, oldEnd, true);
    for (Map.Entry<Integer, ProgramLine> entry : extracted.entrySet()) {
      int oldLineNum = entry.getKey();
      int newLineNum = mapping.get(oldLineNum);
      ProgramLine oldLine = entry.getValue();
      program.put(newLineNum, new ProgramLine(newLineNum, oldLine.sourceText()));
    }
  }

  private void updateGotoGosubTargets(
      Program program, Map<Integer, Integer> mapping, int oldStart, int oldEnd) {
    Map<Integer, String> updates = new HashMap<>();

    for (Map.Entry<Integer, ProgramLine> entry : program.entrySet()) {
      int lineNum = entry.getKey();
      ProgramLine line = entry.getValue();
      String source = line.sourceText();
      String upperSource = source.toUpperCase();

      if (!upperSource.contains("GOTO") && !upperSource.contains("GOSUB")) {
        continue;
      }

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
          Token next = tokenList.get(i + 1);
          if (next.getType() == BazLangLexer.NUM_LITERAL) {
            double val = Double.parseDouble(next.getText());
            int target = (int) Math.round(val);

            Integer newTarget = null;
            if (mapping.containsKey(target)) {
              newTarget = mapping.get(target);
            } else if (target >= oldStart && target <= oldEnd) {
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

  /**
   * Parses a line range for DELETE and REFORMAT: a single number means "just that line"; at least
   * one number is required.
   */
  private int[] parseRenumArgs(Program program, BazLangParser.RenumArgsContext args) {
    int newStart = 10;
    int newStep = 10;
    int oldStart = program.firstKey();
    int oldEnd = program.lastKey();

    if (args != null) {
      var nums = args.numExpr();
      int numIndex = 0;

      if (!nums.isEmpty() && args.STEP() == null
          || !nums.isEmpty()
              && args.getText().toUpperCase().indexOf("STEP")
                  > args.getText().indexOf(nums.getFirst().getText())) {
        newStart = (int) numEval.applyAsDouble(nums.get(numIndex++));
      }

      if (args.STEP() != null && numIndex < nums.size()) {
        newStep = (int) numEval.applyAsDouble(nums.get(numIndex++));
      }

      if (args.TO() != null) {
        String argsText = args.getText().toUpperCase();
        int commaPos = argsText.indexOf(',');
        int toPos = argsText.indexOf("TO");

        if (commaPos >= 0 && numIndex < nums.size()) {
          String numText = nums.get(numIndex).getText();
          int numPos = argsText.indexOf(numText, commaPos);
          if (numPos < toPos) {
            oldStart = (int) numEval.applyAsDouble(nums.get(numIndex++));
          }
        }

        if (numIndex < nums.size()) {
          oldEnd = (int) numEval.applyAsDouble(nums.get(numIndex));
        }
      }
    }
    return new int[] {newStart, newStep, oldStart, oldEnd};
  }

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
        start = (int) numEval.applyAsDouble(nums.get(0));
        end = (int) numEval.applyAsDouble(nums.get(1));
      } else if (nums.size() == 1) {
        if (range.getText().toUpperCase().startsWith("TO")) {
          end = (int) numEval.applyAsDouble(nums.getFirst());
        } else {
          start = (int) numEval.applyAsDouble(nums.getFirst());
        }
      }
      // Just TO: start=MIN, end=MAX (already set)
    } else {
      // Command n (no TO): just that single line
      start = (int) numEval.applyAsDouble(nums.getFirst());
      end = start;
    }
    return new int[] {start, end};
  }
}
