package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.antlr.BazLangParser.ExpressionContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class EvalState {
  public record NumArray(int[] dimensions, double[] data) {}

  public sealed interface StrVar {
    record Scalar(BStr value) implements StrVar {}

    record Array(int[] arrayDimensions, int stringLength, byte[] data) implements StrVar {}
  }

  public record FnDefinition(String name, List<String> params, ExpressionContext body) {}

  public record ForLoopData(double limit, double step, int loopPcLabel, int loopPcStatementIndex) {}

  public record StatementAddress(int lineLabel, int statementIndex) {}

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
  private final Map<String, NumVarRef> numScalars = new HashMap<>();
  private final Map<String, NumArrayRef> numArrays = new HashMap<>();
  private final Map<String, StrVarRef> strVars = new HashMap<>();
  private final Map<String, FnDefRef> fnDefinitions = new HashMap<>();

  private final Map<String, ForLoopData> forLoops = new HashMap<>();
  private final Deque<StatementAddress> returnStack = new ArrayDeque<>();
  private final Random random = new Random();

  private int dataExpressionIndex = -1;
  private int dataLineLabel = -1;
  private int dataStatementIndex = -1;

  private boolean running = true;
  private int currentLineLabel = 0;
  private int currentStatementIndex = 1;
  private StatementAddress pendingJump = null;
  private ReportCode lastReportCode = ReportCode.OK;
  private int lastReportLabel = 0;
  private int lastReportStatementIndex = 1;

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
    return numScalars.computeIfAbsent(name, NumVarRef::new);
  }

  public NumArrayRef getOrAddNumArray(String name) {
    return numArrays.computeIfAbsent(name, NumArrayRef::new);
  }

  public StrVarRef getOrAddStrVar(String name) {
    return strVars.computeIfAbsent(name, StrVarRef::new);
  }

  public FnDefRef getOrAddFnDef(String name) {
    return fnDefinitions.computeIfAbsent(name, FnDefRef::new);
  }

  // ===== Numeric scalar variables =====

  public boolean hasNumVar(String name) {
    NumVarRef ref = numScalars.get(name);
    return ref != null && ref.initialised;
  }

  public double numVar(String name) {
    NumVarRef ref = numScalars.get(name);
    if (ref != null && ref.initialised) {
      return ref.value;
    }
    throw new IllegalArgumentException("Undefined variable: " + name);
  }

  public NumVarRef getNumVarRef(String name) {
    return numScalars.get(name);
  }

  public void setNumVar(String name, double val) {
    NumVarRef ref = getOrAddNumVar(name);
    ref.value = val;
    ref.initialised = true;
  }

  // ===== Numeric arrays =====

  public boolean hasNumArray(String name) {
    NumArrayRef ref = numArrays.get(name);
    return ref != null && ref.array != null;
  }

  public NumArray numArray(String name) {
    NumArrayRef ref = numArrays.get(name);
    return (ref != null) ? ref.array : null;
  }

  public void setNumArray(String name, NumArray arr) {
    NumArrayRef ref = getOrAddNumArray(name);
    ref.array = arr;
  }

  // ===== String variables (Scalar and Array) =====

  public boolean hasStrVar(String name) {
    StrVarRef ref = strVars.get(name);
    return ref != null && ref.value != null;
  }

  public StrVar strVar(String name) {
    StrVarRef ref = strVars.get(name);
    return (ref != null) ? ref.value : null;
  }

  public void setStrVar(String name, StrVar val) {
    StrVarRef ref = getOrAddStrVar(name);
    ref.value = val;
  }

  public Map<String, Double> getVariablesSnapshot() {
    Map<String, Double> result = new TreeMap<>();
    for (var entry : numScalars.entrySet()) {
      if (entry.getValue().initialised) {
        result.put(entry.getKey(), entry.getValue().value);
      }
    }
    return result;
  }

  public Map<String, String> getStringVariablesSnapshot() {
    Map<String, String> result = new TreeMap<>();
    for (var entry : strVars.entrySet()) {
      if (entry.getValue().value instanceof StrVar.Scalar scalar) {
        result.put(entry.getKey(), scalar.value().toJavaString());
      }
    }
    return result;
  }

  // ===== Functions =====

  public boolean hasFn(String name) {
    FnDefRef ref = fnDefinitions.get(name);
    return ref != null && ref.def != null;
  }

  public FnDefinition fn(String name) {
    FnDefRef ref = fnDefinitions.get(name);
    return (ref != null) ? ref.def : null;
  }

  public void setFn(String name, FnDefinition def) {
    FnDefRef ref = getOrAddFnDef(name);
    ref.def = def;
  }

  public void removeNumVar(String name) {
    NumVarRef ref = numScalars.get(name);
    if (ref != null) {
      ref.initialised = false;
    }
  }

  public void removeStrVar(String name) {
    StrVarRef ref = strVars.get(name);
    if (ref != null) {
      ref.value = null;
    }
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

  // ===== GOSUB return stack =====

  public boolean isReturnStackEmpty() {
    return returnStack.isEmpty();
  }

  public void pushReturn(StatementAddress loc) {
    returnStack.push(loc);
  }

  public StatementAddress popReturn() {
    return returnStack.pop();
  }

  // ===== Randomness =====

  public double nextRandom() {
    return random.nextDouble();
  }

  public void seedRandom(long seed) {
    random.setSeed(seed);
  }

  public boolean isRunning() {
    return running;
  }

  public void setRunning(boolean running) {
    this.running = running;
  }

  public int currentLineLabel() {
    return currentLineLabel;
  }

  public void setCurrentLineLabel(int label) {
    this.currentLineLabel = label;
    this.currentStatementIndex = 1; // reset on new line
  }

  public int currentStatementIndex() {
    return currentStatementIndex;
  }

  public void setCurrentStatementIndex(int index) {
    this.currentStatementIndex = index;
  }

  public Integer pendingJumpLabel() {
    return pendingJump != null ? pendingJump.lineLabel() : null;
  }

  public Integer pendingJumpStatementIndex() {
    return pendingJump != null ? pendingJump.statementIndex() : null;
  }

  public boolean hasPendingJump() {
    return pendingJump != null;
  }

  public void setPendingJumpLocation(int label, int statementIndex) {
    this.pendingJump = new StatementAddress(label, statementIndex);
  }

  public void clearPendingJump() {
    this.pendingJump = null;
  }

  public ReportCode lastReportCode() {
    return lastReportCode;
  }

  public void setLastReportCode(ReportCode code) {
    this.lastReportCode = code;
  }

  public int lastReportLabel() {
    return lastReportLabel;
  }

  public void setLastReportLabel(int label) {
    this.lastReportLabel = label;
  }

  public int lastReportStatementIndex() {
    return lastReportStatementIndex;
  }

  public void setLastReportStatementIndex(int index) {
    this.lastReportStatementIndex = index;
  }

  public int dataExpressionIndex() {
    return dataExpressionIndex;
  }

  public int dataLineLabel() {
    return dataLineLabel;
  }

  public int dataStatementIndex() {
    return dataStatementIndex;
  }

  public void setDataExpressionIndex(int index) {
    this.dataExpressionIndex = index;
  }

  public void setDataLineLabel(int label) {
    this.dataLineLabel = label;
  }

  public void setDataStatementIndex(int index) {
    this.dataStatementIndex = index;
  }

  public void clear() {
    for (NumVarRef ref : numScalars.values()) {
      ref.initialised = false;
    }
    for (NumArrayRef ref : numArrays.values()) {
      ref.array = null;
    }
    for (StrVarRef ref : strVars.values()) {
      ref.value = null;
    }
    for (FnDefRef ref : fnDefinitions.values()) {
      ref.def = null;
    }
    forLoops.clear();
    returnStack.clear();
    clearPendingJump();
    lastReportCode = ReportCode.OK;
    lastReportLabel = 0;
    dataExpressionIndex = -1;
    dataLineLabel = -1;
    dataStatementIndex = -1;
    defaultStyles.reset();
  }
}
