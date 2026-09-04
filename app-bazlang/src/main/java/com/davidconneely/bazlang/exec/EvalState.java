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
  /**
   * A dimensioned numeric array's shape and contents, flattened into one {@code double[]}.
   *
   * @param dimensions the array's declared size in each dimension.
   * @param data the array's contents, flattened in row-major order.
   */
  public record NumArray(int[] dimensions, double[] data) {}

  /** A string variable: either a plain scalar or a dimensioned, fixed-width array. */
  public sealed interface StrVar {
    /**
     * A scalar string variable.
     *
     * @param value the variable's current value.
     */
    record Scalar(BStr value) implements StrVar {}

    /**
     * A dimensioned string array, every element the same fixed byte width.
     *
     * @param arrayDimensions the array's declared size in each dimension.
     * @param stringLength the fixed byte width of every element.
     * @param data the array's contents, flattened in row-major order.
     */
    record Array(int[] arrayDimensions, int stringLength, byte[] data) implements StrVar {}
  }

  /**
   * A {@code DEF FN} definition.
   *
   * @param name the function's name.
   * @param params the function's parameter names.
   * @param body the single-expression function body.
   */
  public record FnDefinition(String name, List<String> params, Expr body) {}

  /**
   * The bookkeeping for one active {@code FOR} loop.
   *
   * @param limit the loop's terminating value.
   * @param step the amount to increment the loop variable by each iteration.
   * @param loopPcLabel the line to jump back to on {@code NEXT}.
   * @param loopPcStatementIndex the flat statement index to jump back to on {@code NEXT}.
   */
  public record ForLoopData(double limit, double step, int loopPcLabel, int loopPcStatementIndex) {}

  /**
   * A statement's flat address, for the GOSUB return stack.
   *
   * @param lineLabel the line number.
   * @param statementIndex the flat statement index within that line.
   */
  public record StatementAddress(int lineLabel, int statementIndex) {}

  /**
   * The current {@code READ}/{@code RESTORE} position: which {@code DATA} value is read next.
   *
   * @param lineLabel the line holding the next value.
   * @param statementIndex the flat statement index of the {@code DATA} statement holding it.
   * @param expressionIndex the value's position within that {@code DATA} statement's value list.
   */
  public record DataPointer(int lineLabel, int statementIndex, int expressionIndex) {}

  /**
   * The outcome of the most recently executed statement.
   *
   * @param code the report code; {@link ReportCode#OK} for no error.
   * @param lineLabel the line the report refers to.
   * @param statementIndex the flat statement index the report refers to.
   */
  public record ReportState(ReportCode code, int lineLabel, int statementIndex) {}

  /** A scalar numeric variable's lazily-populated reference cache; see {@link Expr} nodes. */
  public static final class NumVarRef {
    /** The variable's name. */
    public final String name;

    /** The variable's current value. */
    public double value;

    /** Whether this variable has been assigned a value yet. */
    public boolean initialised;

    /**
     * Create a reference for the named scalar variable.
     *
     * @param name the variable's name.
     */
    public NumVarRef(String name) {
      this.name = name;
    }
  }

  /** A numeric array variable's lazily-populated reference cache; see {@link Expr} nodes. */
  public static final class NumArrayRef {
    /** The array's name. */
    public final String name;

    /** The array's shape and contents, or {@code null} if not yet dimensioned. */
    public NumArray array;

    /**
     * Create a reference for the named array variable.
     *
     * @param name the array's name.
     */
    public NumArrayRef(String name) {
      this.name = name;
    }
  }

  /** A string variable's lazily-populated reference cache; see {@link Expr} nodes. */
  public static final class StrVarRef {
    /** The variable's name. */
    public final String name;

    /** The variable's current value. */
    public StrVar value;

    /**
     * Create a reference for the named string variable.
     *
     * @param name the variable's name.
     */
    public StrVarRef(String name) {
      this.name = name;
    }
  }

  /** A {@code DEF FN} function's lazily-populated reference cache. */
  public static final class FnDefRef {
    /** The function's name. */
    public final String name;

    /** The function's definition, or {@code null} if not yet defined. */
    public FnDefinition def;

    /**
     * Create a reference for the named function.
     *
     * @param name the function's name.
     */
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

  /** Create a fresh, empty state. */
  public EvalState() {}

  /**
   * The stored program.
   *
   * @return the program.
   */
  public Program program() {
    return program;
  }

  /**
   * The persistent default style settings (set by the six top-level style statements, as opposed to
   * a per-call {@code styleList}/print-item override).
   *
   * @return the default styles.
   */
  public StyleState defaultStyles() {
    return defaultStyles;
  }

  /**
   * Default ink (foreground) colour.
   *
   * @return the colour code.
   */
  public int defaultInk() {
    return defaultStyles.ink();
  }

  /**
   * Set the default ink (foreground) colour.
   *
   * @param defaultInk the new colour code.
   */
  public void setDefaultInk(int defaultInk) {
    defaultStyles.setInk(defaultInk);
  }

  /**
   * Default paper (background) colour.
   *
   * @return the colour code.
   */
  public int defaultPaper() {
    return defaultStyles.paper();
  }

  /**
   * Set the default paper (background) colour.
   *
   * @param defaultPaper the new colour code.
   */
  public void setDefaultPaper(int defaultPaper) {
    defaultStyles.setPaper(defaultPaper);
  }

  /**
   * Default brightness.
   *
   * @return the brightness value.
   */
  public int defaultBright() {
    return defaultStyles.bright();
  }

  /**
   * Set the default brightness.
   *
   * @param defaultBright the new brightness value.
   */
  public void setDefaultBright(int defaultBright) {
    defaultStyles.setBright(defaultBright);
  }

  /**
   * Default flash (blink) setting.
   *
   * @return the flash value.
   */
  public int defaultFlash() {
    return defaultStyles.flash();
  }

  /**
   * Set the default flash (blink) setting.
   *
   * @param defaultFlash the new flash value.
   */
  public void setDefaultFlash(int defaultFlash) {
    defaultStyles.setFlash(defaultFlash);
  }

  /**
   * Default inverse-video setting.
   *
   * @return the inverse value.
   */
  public int defaultInverse() {
    return defaultStyles.inverse();
  }

  /**
   * Set the default inverse-video setting.
   *
   * @param defaultInverse the new inverse value.
   */
  public void setDefaultInverse(int defaultInverse) {
    defaultStyles.setInverse(defaultInverse);
  }

  /**
   * Default overlay (XOR-plot) setting.
   *
   * @return the over value.
   */
  public int defaultOver() {
    return defaultStyles.over();
  }

  /**
   * Set the default overlay (XOR-plot) setting.
   *
   * @param defaultOver the new over value.
   */
  public void setDefaultOver(int defaultOver) {
    defaultStyles.setOver(defaultOver);
  }

  private int graphicsCursorX = 0;
  private int graphicsCursorY = 0;

  /**
   * The graphics cursor's current x-coordinate, used by {@code DRAW}'s relative movement.
   *
   * @return the pixel x-coordinate.
   */
  public int graphicsCursorX() {
    return graphicsCursorX;
  }

  /**
   * The graphics cursor's current y-coordinate, used by {@code DRAW}'s relative movement.
   *
   * @return the pixel y-coordinate.
   */
  public int graphicsCursorY() {
    return graphicsCursorY;
  }

  /**
   * Set the graphics cursor's x-coordinate.
   *
   * @param x the new pixel x-coordinate.
   */
  public void setGraphicsCursorX(int x) {
    this.graphicsCursorX = x;
  }

  /**
   * Set the graphics cursor's y-coordinate.
   *
   * @param y the new pixel y-coordinate.
   */
  public void setGraphicsCursorY(int y) {
    this.graphicsCursorY = y;
  }

  /**
   * Replaces the stored program's contents with the given lines.
   *
   * @param program the new program lines, keyed by line number.
   */
  public void setProgram(Map<Integer, ProgramLine> program) {
    this.program.clear();
    this.program.putAll(program);
  }

  /**
   * Returns the named scalar numeric variable's reference, creating it (uninitialised) if absent.
   *
   * @param name the variable's name.
   * @return the reference.
   */
  public NumVarRef getOrAddNumVar(String name) {
    return variables.getOrAddNumVar(name);
  }

  /**
   * Returns the named numeric array's reference, creating it (undimensioned) if absent.
   *
   * @param name the array's name.
   * @return the reference.
   */
  public NumArrayRef getOrAddNumArray(String name) {
    return variables.getOrAddNumArray(name);
  }

  /**
   * Returns the named string variable's reference, creating it (empty) if absent.
   *
   * @param name the variable's name.
   * @return the reference.
   */
  public StrVarRef getOrAddStrVar(String name) {
    return variables.getOrAddStrVar(name);
  }

  /**
   * Returns the named {@code DEF FN} function's reference, creating it (undefined) if absent.
   *
   * @param name the function's name.
   * @return the reference.
   */
  public FnDefRef getOrAddFnDef(String name) {
    return variables.getOrAddFnDef(name);
  }

  // ===== Numeric scalar variables =====

  /**
   * Whether the named scalar numeric variable has been assigned a value.
   *
   * @param name the variable's name.
   * @return {@code true} if assigned.
   */
  public boolean hasNumVar(String name) {
    return variables.hasNumVar(name);
  }

  /**
   * The named scalar numeric variable's value.
   *
   * @param name the variable's name.
   * @return the value.
   */
  public double numVar(String name) {
    return variables.numVar(name);
  }

  /**
   * The named scalar numeric variable's reference, without creating it if absent.
   *
   * @param name the variable's name.
   * @return the reference, or {@code null} if the variable doesn't exist.
   */
  public NumVarRef getNumVarRef(String name) {
    return variables.getNumVarRef(name);
  }

  /**
   * Set the named scalar numeric variable's value.
   *
   * @param name the variable's name.
   * @param val the new value.
   */
  public void setNumVar(String name, double val) {
    variables.setNumVar(name, val);
  }

  // ===== Numeric arrays =====

  /**
   * Whether the named numeric array has been dimensioned.
   *
   * @param name the array's name.
   * @return {@code true} if dimensioned.
   */
  public boolean hasNumArray(String name) {
    return variables.hasNumArray(name);
  }

  /**
   * The named numeric array's shape and contents.
   *
   * @param name the array's name.
   * @return the array.
   */
  public NumArray numArray(String name) {
    return variables.numArray(name);
  }

  /**
   * Set the named numeric array's shape and contents.
   *
   * @param name the array's name.
   * @param arr the new array contents.
   */
  public void setNumArray(String name, NumArray arr) {
    variables.setNumArray(name, arr);
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned numeric array, for debugger inspection.
   *
   * @return the snapshot.
   */
  public Map<String, NumArray> numArraysSnapshot() {
    return variables.numArraysSnapshot();
  }

  // ===== String variables (Scalar and Array) =====

  /**
   * Whether the named string variable has been assigned a value.
   *
   * @param name the variable's name.
   * @return {@code true} if assigned.
   */
  public boolean hasStrVar(String name) {
    return variables.hasStrVar(name);
  }

  /**
   * The named string variable's current value.
   *
   * @param name the variable's name.
   * @return the value.
   */
  public StrVar strVar(String name) {
    return variables.strVar(name);
  }

  /**
   * Set the named string variable's value.
   *
   * @param name the variable's name.
   * @param val the new value.
   */
  public void setStrVar(String name, StrVar val) {
    variables.setStrVar(name, val);
  }

  /**
   * A read-only, name-sorted snapshot of every scalar numeric variable's value, for debugger
   * inspection.
   *
   * @return the snapshot.
   */
  public Map<String, Double> variablesSnapshot() {
    return variables.variablesSnapshot();
  }

  /**
   * A read-only, name-sorted snapshot of every scalar string variable's value (as a Java string),
   * for debugger inspection.
   *
   * @return the snapshot.
   */
  public Map<String, String> stringVariablesSnapshot() {
    return variables.stringVariablesSnapshot();
  }

  /**
   * A read-only, name-sorted snapshot of every dimensioned string array, for debugger inspection.
   *
   * @return the snapshot.
   */
  public Map<String, StrVar.Array> strArraysSnapshot() {
    return variables.strArraysSnapshot();
  }

  // ===== Functions =====

  /**
   * Whether the named {@code DEF FN} function has been defined.
   *
   * @param name the function's name.
   * @return {@code true} if defined.
   */
  public boolean hasFn(String name) {
    return variables.hasFn(name);
  }

  /**
   * The named {@code DEF FN} function's definition.
   *
   * @param name the function's name.
   * @return the definition.
   */
  public FnDefinition fn(String name) {
    return variables.fn(name);
  }

  /**
   * Define the named {@code DEF FN} function.
   *
   * @param name the function's name.
   * @param def the new definition.
   */
  public void setFn(String name, FnDefinition def) {
    variables.setFn(name, def);
  }

  /**
   * A read-only, name-sorted snapshot of every defined {@code DEF FN}, for debugger inspection.
   *
   * @return the snapshot.
   */
  public Map<String, FnDefinition> fnDefinitionsSnapshot() {
    return variables.fnDefinitionsSnapshot();
  }

  /**
   * Removes the named scalar numeric variable, as if it had never been assigned.
   *
   * @param name the variable's name.
   */
  public void removeNumVar(String name) {
    variables.removeNumVar(name);
  }

  /**
   * Removes the named string variable, as if it had never been assigned.
   *
   * @param name the variable's name.
   */
  public void removeStrVar(String name) {
    variables.removeStrVar(name);
  }

  // ===== FOR loop tracking =====

  /**
   * Whether the named {@code FOR} loop is currently active.
   *
   * @param name the loop variable's name.
   * @return {@code true} if active.
   */
  public boolean hasForLoop(String name) {
    return forLoops.containsKey(name);
  }

  /**
   * The named active {@code FOR} loop's bookkeeping.
   *
   * @param name the loop variable's name.
   * @return the loop's bookkeeping.
   */
  public ForLoopData forLoop(String name) {
    return forLoops.get(name);
  }

  /**
   * Register or update an active {@code FOR} loop.
   *
   * @param name the loop variable's name.
   * @param data the loop's bookkeeping.
   */
  public void setForLoop(String name, ForLoopData data) {
    forLoops.put(name, data);
  }

  /**
   * A read-only, name-sorted snapshot of every active FOR loop, for debugger inspection.
   *
   * @return the snapshot.
   */
  public Map<String, ForLoopData> forLoopsSnapshot() {
    return Collections.unmodifiableSortedMap(new TreeMap<>(forLoops));
  }

  // ===== GOSUB return stack =====

  /**
   * Whether the GOSUB return stack is empty (no pending {@code RETURN}).
   *
   * @return {@code true} if empty.
   */
  public boolean isReturnStackEmpty() {
    return returnStack.isEmpty();
  }

  /**
   * The current GOSUB nesting depth, for {@code step over} to detect when a call has returned.
   *
   * @return the current nesting depth.
   */
  public int returnStackDepth() {
    return returnStack.depth();
  }

  /**
   * Pushes a return address onto the GOSUB stack.
   *
   * @param loc the address to return to.
   */
  public void pushReturn(StatementAddress loc) {
    returnStack.push(loc);
  }

  /**
   * Pops and returns the innermost return address, for {@code RETURN}.
   *
   * @return the address to resume at.
   */
  public StatementAddress popReturn() {
    return returnStack.pop();
  }

  /**
   * A read-only snapshot of the GOSUB return stack, innermost (most recently called) frame first,
   * for debugger inspection.
   *
   * @return the snapshot.
   */
  public List<StatementAddress> returnStackSnapshot() {
    return returnStack.snapshot();
  }

  // ===== Randomness =====

  /**
   * The next pseudorandom value from the {@code RAND()} builtin's RNG.
   *
   * @return a value in {@code [0.0, 1.0)}.
   */
  public double nextRandom() {
    return random.nextDouble();
  }

  /**
   * Reseeds the {@code RAND()} builtin's RNG.
   *
   * @param seed the new seed.
   */
  public void seedRandom(long seed) {
    random.setSeed(seed);
  }

  /**
   * Whether the interpreter is currently running (as opposed to paused/stopped).
   *
   * @return {@code true} if running.
   */
  public boolean isRunning() {
    return programCounter.isRunning();
  }

  /**
   * Set whether the interpreter is running.
   *
   * @param running the new running state.
   */
  public void setRunning(boolean running) {
    programCounter.setRunning(running);
  }

  /**
   * The line currently executing.
   *
   * @return the line number.
   */
  public int currentLineLabel() {
    return programCounter.currentLineLabel();
  }

  /**
   * Set the line currently executing.
   *
   * @param label the new line number.
   */
  public void setCurrentLineLabel(int label) {
    programCounter.setCurrentLineLabel(label);
  }

  /**
   * The flat statement index currently executing within {@link #currentLineLabel()}.
   *
   * @return the flat statement index.
   */
  public int currentStatementIndex() {
    return programCounter.currentStatementIndex();
  }

  /**
   * Set the flat statement index currently executing.
   *
   * @param index the new flat statement index.
   */
  public void setCurrentStatementIndex(int index) {
    programCounter.setCurrentStatementIndex(index);
  }

  /**
   * The outcome of the most recently executed statement.
   *
   * @return the last report.
   */
  public ReportState lastReport() {
    return lastReport;
  }

  /**
   * Set the outcome of the most recently executed statement.
   *
   * @param lastReport the new report.
   */
  public void setLastReport(ReportState lastReport) {
    this.lastReport = lastReport;
  }

  /**
   * The current {@code READ}/{@code RESTORE} position.
   *
   * @return the current data pointer.
   */
  public DataPointer dataPointer() {
    return dataCursor.get();
  }

  /**
   * Set the current {@code READ}/{@code RESTORE} position.
   *
   * @param dataPointer the new data pointer.
   */
  public void setDataPointer(DataPointer dataPointer) {
    dataCursor.set(dataPointer);
  }

  /**
   * Resets runtime state as {@code NEW}/{@code CLEAR} do: variables, {@code FOR} loops, the GOSUB
   * stack, the last report, the {@code DATA} pointer, and default styles. Does not touch the stored
   * program itself, the RNG's state, or the graphics cursor.
   */
  public void clear() {
    variables.clear();
    forLoops.clear();
    returnStack.clear();
    lastReport = new ReportState(ReportCode.OK, 0, 1);
    dataCursor.clear();
    defaultStyles.reset();
  }
}
