package com.davidconneely.bazlang.edit;

import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangLexer;
import com.davidconneely.bazlang.antlr.BazLangParser;
import com.davidconneely.bazlang.exec.EvalState;
import com.davidconneely.bazlang.exec.Program;
import com.davidconneely.bazlang.exec.ProgramLine;
import com.davidconneely.bazlang.io.VirtualScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Handles program-editing REPL commands: DELETE, REFORMAT, and RENUM. These manipulate the
 * program's source text and line numbering but do not execute BASIC statements.
 */
public class ProgramEditor {
  private final EvalState state;
  private final VirtualScreen screen;
  private final AntlrParser parser;
  private final ToDoubleFunction<BazLangParser.NumExprContext> numEval;

  public ProgramEditor(
      EvalState state,
      VirtualScreen screen,
      AntlrParser parser,
      ToDoubleFunction<BazLangParser.NumExprContext> numEval) {
    this.state = state;
    this.screen = screen;
    this.parser = parser;
    this.numEval = numEval;
  }

  /** Execute DELETE command with parsed line range. */
  public void executeDelete(BazLangParser.LineRangeContext range) {
    final int[] bounds = parseDeleteReformatLineRange(range);
    state.program().clearRange(bounds[0], true, bounds[1], true);
  }

  /** Execute REFORMAT command with optional line range. */
  public void executeReformat(BazLangParser.LineRangeContext range) {
    int start = Limits.MIN_TARGET_LABEL;
    int end = Limits.MAX_TARGET_LABEL;
    if (range != null) {
      final int[] bounds = parseDeleteReformatLineRange(range);
      start = bounds[0];
      end = bounds[1];
    }

    final var toReformat = new ArrayList<Map.Entry<Integer, ProgramLine>>();
    for (final var entry : state.program().subMapEntries(start, true, end, true)) {
      toReformat.add(entry);
    }
    if (toReformat.isEmpty()) {
      return;
    }

    final var formatter = new ReformatVisitor();
    for (final var entry : toReformat) {
      final int lineNum = entry.getKey();
      final var line = entry.getValue();
      final var stmt = line.getStatements(parser);
      final String formattedSource = formatter.visit(stmt);
      state.program().put(lineNum, new ProgramLine(lineNum, formattedSource));
    }
  }

  /** Execute RENUM command with parsed arguments. */
  public void executeRenum(BazLangParser.RenumArgsContext args) {
    final var program = state.program();
    if (program.isEmpty()) {
      return;
    }

    final int[] parsedArgs = parseRenumArgs(program, args);
    final int newStart = parsedArgs[0];
    final int newStep = parsedArgs[1];
    final int oldStart = parsedArgs[2];
    final int oldEnd = parsedArgs[3];

    if (newStart < Limits.MIN_LINE_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE, 0, "New start must be >= " + Limits.MIN_LINE_LABEL);
    }
    if (newStep < 1) {
      throw new ReportException(ReportCode.INTEGER_OUT_OF_RANGE, 0, "Step must be >= 1");
    }

    final var toRenumber = new ArrayList<Map.Entry<Integer, ProgramLine>>();
    for (final var entry : program.subMapEntries(oldStart, true, oldEnd, true)) {
      toRenumber.add(entry);
    }
    if (toRenumber.isEmpty()) {
      return;
    }

    final int count = toRenumber.size();
    final long newEndLong = (long) newStart + (long) (count - 1) * newStep;
    if (newEndLong > Limits.MAX_LINE_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          0,
          "Renumbering would exceed max line number " + Limits.MAX_LINE_LABEL);
    }
    final int newEnd = (int) newEndLong;

    final Integer lineBefore = program.lowerKey(oldStart);
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
    final Integer lineAfter = program.higherKey(oldEnd);
    if (lineAfter != null && newEnd >= lineAfter) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          0,
          "New end " + newEnd + " must be less than line " + lineAfter + " to preserve line order");
    }

    final var mapping = new HashMap<Integer, Integer>();
    int newNum = newStart;
    for (final var entry : toRenumber) {
      mapping.put(entry.getKey(), newNum);
      newNum += newStep;
    }

    updateLineReferences(program, mapping, oldStart, oldEnd);

    final var extracted = new HashMap<Integer, ProgramLine>();
    for (final var entry : toRenumber) {
      extracted.put(entry.getKey(), entry.getValue());
    }
    program.clearRange(oldStart, true, oldEnd, true);
    for (final var entry : extracted.entrySet()) {
      final int oldLineNum = entry.getKey();
      final int newLineNum = mapping.get(oldLineNum);
      final var oldLine = entry.getValue();
      program.put(newLineNum, new ProgramLine(newLineNum, oldLine.sourceText()));
    }
  }

  private void updateLineReferences(
      Program program, Map<Integer, Integer> mapping, int oldStart, int oldEnd) {
    final var updates = new HashMap<Integer, String>();

    for (final var entry : program.entrySet()) {
      final int lineNum = entry.getKey();
      final var line = entry.getValue();
      final String source = line.sourceText();
      final String upperSource = source.toUpperCase();

      if (!upperSource.contains("GOTO")
          && !upperSource.contains("GOSUB")
          && !upperSource.contains("RESTORE")
          && !upperSource.contains("RUN")
          && !(upperSource.contains("GO")
              && (upperSource.contains("TO") || upperSource.contains("SUB")))) {
        continue;
      }

      final String updatedSource =
          updateLineTargets(lineNum, source, program, mapping, oldStart, oldEnd);
      if (updatedSource != null) {
        updates.put(lineNum, updatedSource);
      }
    }

    for (final var update : updates.entrySet()) {
      final int lineNum = update.getKey();
      program.put(lineNum, new ProgramLine(lineNum, update.getValue()));
    }
  }

  /**
   * Re-lexes the source line to rewrite literal jump targets (e.g. GOTO 100). Note: Computed
   * targets (like GOTO n+10) are silently left unchanged.
   */
  private String updateLineTargets(
      int lineNum,
      String source,
      Program program,
      Map<Integer, Integer> mapping,
      int oldStart,
      int oldEnd) {
    final var input = CharStreams.fromString(source);
    final var lexer = new BazLangLexer(input);
    final var tokens = new CommonTokenStream(lexer);
    tokens.fill();
    final var tokenList = tokens.getTokens();

    final var newSource = new StringBuilder();
    int lastCopiedPos = 0;
    boolean modified = false;

    int i = 0;
    while (i < tokenList.size()) {
      final var t = tokenList.get(i);
      Token targetToken = null;
      int skip = 1;

      if ((t.getType() == BazLangLexer.GOTO
              || t.getType() == BazLangLexer.GOSUB
              || t.getType() == BazLangLexer.RESTORE
              || t.getType() == BazLangLexer.RUN)
          && i + 1 < tokenList.size()) {
        final var next = tokenList.get(i + 1);
        if (next.getType() == BazLangLexer.NUM_LITERAL) {
          targetToken = next;
          skip = 2;
        }
      } else if (t.getType() == BazLangLexer.GO && i + 2 < tokenList.size()) {
        final var next1 = tokenList.get(i + 1);
        if (next1.getType() == BazLangLexer.TO || next1.getType() == BazLangLexer.SUB) {
          final var next2 = tokenList.get(i + 2);
          if (next2.getType() == BazLangLexer.NUM_LITERAL) {
            targetToken = next2;
            skip = 3;
          }
        }
      }

      if (targetToken != null) {
        final double val = Double.parseDouble(targetToken.getText());
        final int target = (int) Math.round(val);

        Integer newTarget = null;
        if (mapping.containsKey(target)) {
          newTarget = mapping.get(target);
        } else if (target >= oldStart && target <= oldEnd) {
          final Integer ceilingKey = program.ceilingKey(target);
          if (ceilingKey != null && mapping.containsKey(ceilingKey)) {
            newTarget = mapping.get(ceilingKey);
            final Integer newLineNum = mapping.getOrDefault(lineNum, lineNum);
            screen.println(
                "Warning: Line "
                    + lineNum
                    + " (now "
                    + newLineNum
                    + ") references non-existent line "
                    + target
                    + ", updated to "
                    + ceilingKey
                    + " (now "
                    + newTarget
                    + ")");
          }
        }

        if (newTarget != null) {
          newSource.append(source, lastCopiedPos, targetToken.getStartIndex()).append(newTarget);
          lastCopiedPos = targetToken.getStopIndex() + 1;
          modified = true;
        }
      }
      i += skip;
    }

    if (modified) {
      newSource.append(source.substring(lastCopiedPos));
      return newSource.toString();
    }
    return null;
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
      boolean seenStep = false;
      boolean seenComma = false;
      boolean seenTo = false;

      for (int i = 0; i < args.getChildCount(); i++) {
        var child = args.getChild(i);
        if (child.equals(args.STEP())) {
          seenStep = true;
        } else if (child.getText().equals(",")) {
          seenComma = true;
        } else if (child.equals(args.TO())) {
          seenTo = true;
        } else if (child instanceof BazLangParser.NumExprContext) {
          int val = (int) numEval.applyAsDouble((BazLangParser.NumExprContext) child);
          if (!seenStep && !seenComma) {
            newStart = val;
          } else if (seenStep && !seenComma) {
            newStep = val;
          } else if (seenComma && !seenTo) {
            oldStart = val;
          } else if (seenTo) {
            oldEnd = val;
          }
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
    final var nums = range.numExpr();
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
