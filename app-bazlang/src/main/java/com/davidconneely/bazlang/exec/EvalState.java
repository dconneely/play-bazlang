package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.exec.ast.Expr;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The program's memory - a thin facade over four cohesive collaborators: {@link VariableStore}
 * (numeric/string scalars and arrays, {@code DEF FN}), {@link ReturnStack} ({@code GOSUB}/{@code
 * RETURN}), {@link ProgramCounter} (execution position), and {@link DataCursor} ({@code READ}/
 * {@code RESTORE} position). Active {@code FOR} loops, the RNG, the last report, and default
 * styles/graphics-cursor state remain direct fields - see {@link #clear()} for exactly what each of
 * {@code NEW}/{@code CLEAR} resets. Where to jump or resume next is not state here at all - see
 * {@link Interpreter#resume(int, int)}.
 */
public class EvalState {
  public record NumArray(int[] dimensions, double[] data) {}

  public sealed interface StrVar {
    record Scalar(BStr value) implements StrVar {}

    record Array(int[] arrayDimensions, int stringLength, byte[] data) implements StrVar {}
  }

  public record FnDefinition(String name, List<String> params, Expr body) {}

  public record ForLoopData(double limit, double step, int loopPcLabel, int loopPcStatementIndex) {}

  public record StatementAddress(int lineLabel, int statementIndex) {}

  public record DataPointer(int lineLabel, int statementIndex, int expressionIndex) {}

  public record ReportState(ReportCode code, int lineLabel, int statementIndex) {}

  public static final class NumVarRef {
    public final String name;
    public double value;
    public boolean initialised;

    public NumVarRef(String name) {
      this.name = name;
    }
  }

  public static final class NumArrayRef {
    public final String name;
    public NumArray array;

    public NumArrayRef(String name) {
      this.name = name;
    }
  }

  public static final class StrVarRef {
    public final String name;
    public StrVar value;

    public StrVarRef(String name) {
      this.name = name;
    }
  }

  public static final class FnDefRef {
    public final String name;
    public FnDefinition def;

    public FnDefRef(String name) {
      this.name = name;
    }
  }

  private final Program program = new Program();
  private final VariableStore variables = new VariableStore();
  private final ReturnStack returnStack = new ReturnStack();
  private final ProgramCounter programCounter = new ProgramCounter();
  private final DataCursor dataCursor = new DataCursor();

  private final Map<String, ForLoopData> forLoops = new HashMap<>();
  private final Random random = new Random();

  private ReportState lastReport = new ReportState(ReportCode.OK, 0, 1);

  // Default ink/paper colour codes:
  // - -1: Default terminal colour
  // - 0..7: ZX Spectrum colour codes
  // - 8: Transparent/preserve existing cell colour
  // - 9: Contrast colour
  // - 256..511: xterm colour index + 256
  // - 2^24..2^25-1: 24-bit RGB colour value + 2^24
  private final StyleState defaultStyles = new StyleState();

  public Program program() {
    return program;
  }

  public StyleState defaultStyles() {
    return defaultStyles;
  }

  public int defaultInk() {
    return defaultStyles.ink();
  }

  public void setDefaultInk(int defaultInk) {
    defaultStyles.setInk(defaultInk);
  }

  public int defaultPaper() {
    return defaultStyles.paper();
  }

  public void setDefaultPaper(int defaultPaper) {
    defaultStyles.setPaper(defaultPaper);
  }

  public int defaultBright() {
    return defaultStyles.bright();
  }

  public void setDefaultBright(int defaultBright) {
    defaultStyles.setBright(defaultBright);
  }

  public int defaultFlash() {
    return defaultStyles.flash();
  }

  public void setDefaultFlash(int defaultFlash) {
    defaultStyles.setFlash(defaultFlash);
  }

  public int defaultInverse() {
    return defaultStyles.inverse();
  }

  public void setDefaultInverse(int defaultInverse) {
    defaultStyles.setInverse(defaultInverse);
  }

  public int defaultOver() {
    return defaultStyles.over();
  }

  public void setDefaultOver(int defaultOver) {
    defaultStyles.setOver(defaultOver);
  }

  private int graphicsCursorX = 0;
  private int graphicsCursorY = 0;

  public int graphicsCursorX() {
    return graphicsCursorX;
  }

  public int graphicsCursorY() {
    return graphicsCursorY;
  }

  public void setGraphicsCursorX(int x) {
    this.graphicsCursorX = x;
  }

  public void setGraphicsCursorY(int y) {
    this.graphicsCursorY = y;
  }

  public void setProgram(Map<Integer, ProgramLine> program) {
    this.program.clear();
    this.program.putAll(program);
  }

  public NumVarRef getOrAddNumVar(String name) {
    return variables.getOrAddNumVar(name);
  }

  public NumArrayRef getOrAddNumArray(String name) {
    return variables.getOrAddNumArray(name);
  }

  public StrVarRef getOrAddStrVar(String name) {
    return variables.getOrAddStrVar(name);
  }

  public FnDefRef getOrAddFnDef(String name) {
    return variables.getOrAddFnDef(name);
  }

  // ===== Numeric scalar variables =====

  public boolean hasNumVar(String name) {
    return variables.hasNumVar(name);
  }

  public double numVar(String name) {
    return variables.numVar(name);
  }

  public NumVarRef getNumVarRef(String name) {
    return variables.getNumVarRef(name);
  }

  public void setNumVar(String name, double val) {
    variables.setNumVar(name, val);
  }

  // ===== Numeric arrays =====

  public boolean hasNumArray(String name) {
    return variables.hasNumArray(name);
  }

  public NumArray numArray(String name) {
    return variables.numArray(name);
  }

  public void setNumArray(String name, NumArray arr) {
    variables.setNumArray(name, arr);
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned numeric array, for debugger inspection.
   */
  public Map<String, NumArray> numArraysSnapshot() {
    return variables.numArraysSnapshot();
  }

  // ===== String variables (Scalar and Array) =====

  public boolean hasStrVar(String name) {
    return variables.hasStrVar(name);
  }

  public StrVar strVar(String name) {
    return variables.strVar(name);
  }

  public void setStrVar(String name, StrVar val) {
    variables.setStrVar(name, val);
  }

  public Map<String, Double> variablesSnapshot() {
    return variables.variablesSnapshot();
  }

  public Map<String, String> stringVariablesSnapshot() {
    return variables.stringVariablesSnapshot();
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned string array, for debugger inspection.
   */
  public Map<String, StrVar.Array> strArraysSnapshot() {
    return variables.strArraysSnapshot();
  }

  // ===== Functions =====

  public boolean hasFn(String name) {
    return variables.hasFn(name);
  }

  public FnDefinition fn(String name) {
    return variables.fn(name);
  }

  public void setFn(String name, FnDefinition def) {
    variables.setFn(name, def);
  }

  /** A read-only, name-sorted snapshot of every defined {@code DEF FN}, for debugger inspection. */
  public Map<String, FnDefinition> fnDefinitionsSnapshot() {
    return variables.fnDefinitionsSnapshot();
  }

  public void removeNumVar(String name) {
    variables.removeNumVar(name);
  }

  public void removeStrVar(String name) {
    variables.removeStrVar(name);
  }

  // ===== FOR loop tracking =====

  public boolean hasForLoop(String name) {
    return forLoops.containsKey(name);
  }

  public ForLoopData forLoop(String name) {
    return forLoops.get(name);
  }

  public void setForLoop(String name, ForLoopData data) {
    forLoops.put(name, data);
  }

  /** A read-only, name-sorted snapshot of every active FOR loop, for debugger inspection. */
  public Map<String, ForLoopData> forLoopsSnapshot() {
    return Collections.unmodifiableSortedMap(new TreeMap<>(forLoops));
  }

  // ===== GOSUB return stack =====

  public boolean isReturnStackEmpty() {
    return returnStack.isEmpty();
  }

  /** The current GOSUB nesting depth, for {@code step over} to detect when a call has returned. */
  public int returnStackDepth() {
    return returnStack.depth();
  }

  public void pushReturn(StatementAddress loc) {
    returnStack.push(loc);
  }

  public StatementAddress popReturn() {
    return returnStack.pop();
  }

  /**
   * A read-only snapshot of the GOSUB return stack, innermost (most recently called) frame first,
   * for debugger inspection.
   */
  public List<StatementAddress> returnStackSnapshot() {
    return returnStack.snapshot();
  }

  // ===== Randomness =====

  public double nextRandom() {
    return random.nextDouble();
  }

  public void seedRandom(long seed) {
    random.setSeed(seed);
  }

  public boolean isRunning() {
    return programCounter.isRunning();
  }

  public void setRunning(boolean running) {
    programCounter.setRunning(running);
  }

  public int currentLineLabel() {
    return programCounter.currentLineLabel();
  }

  public void setCurrentLineLabel(int label) {
    programCounter.setCurrentLineLabel(label);
  }

  public int currentStatementIndex() {
    return programCounter.currentStatementIndex();
  }

  public void setCurrentStatementIndex(int index) {
    programCounter.setCurrentStatementIndex(index);
  }

  public ReportState lastReport() {
    return lastReport;
  }

  public void setLastReport(ReportState lastReport) {
    this.lastReport = lastReport;
  }

  public DataPointer dataPointer() {
    return dataCursor.get();
  }

  public void setDataPointer(DataPointer dataPointer) {
    dataCursor.set(dataPointer);
  }

  public void clear() {
    variables.clear();
    forLoops.clear();
    returnStack.clear();
    lastReport = new ReportState(ReportCode.OK, 0, 1);
    dataCursor.clear();
    defaultStyles.reset();
  }
}
