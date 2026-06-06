package com.davidconneely.bazlang;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class EvalState {
  public record NumArray(List<Integer> dimensions, double[] data) {}

  public sealed interface StrVar {
    record Scalar(BStr value) implements StrVar {}

    record Array(List<Integer> arrayDimensions, int stringLength, BStr[] elements)
        implements StrVar {}
  }

  public record ForLoopData(double limit, double step, int loopPc) {}

  private final NavigableMap<Integer, ProgramLine> program = new TreeMap<>();
  private final Map<String, Double> numScalars = new HashMap<>();
  private final Map<String, NumArray> numArrays = new HashMap<>();
  private final Map<String, StrVar> strVars = new HashMap<>();
  private final Map<String, ForLoopData> forLoops = new HashMap<>();
  private final Deque<Integer> returnStack = new ArrayDeque<>();
  private final Random random = new Random();

  private boolean running = true;
  private int currentLineLabel = 0;
  private Integer pendingJumpLabel = null;
  private boolean hasPendingJump = false;
  private ReportCode lastReportCode = ReportCode.OK;
  private int lastReportLabel = 0;

  public NavigableMap<Integer, ProgramLine> program() {
    return program;
  }

  public void setProgram(Map<Integer, ProgramLine> program) {
    this.program.clear();
    this.program.putAll(program);
  }

  // ===== Numeric scalar variables =====

  public boolean hasNumVar(String name) {
    return numScalars.containsKey(name);
  }

  public Double numVar(String name) {
    return numScalars.get(name);
  }

  public void setNumVar(String name, double val) {
    numScalars.put(name, val);
  }

  // ===== Numeric arrays =====

  public boolean hasNumArray(String name) {
    return numArrays.containsKey(name);
  }

  public NumArray numArray(String name) {
    return numArrays.get(name);
  }

  public void setNumArray(String name, NumArray arr) {
    numArrays.put(name, arr);
  }

  // ===== String variables (Scalar and Array) =====

  public boolean hasStrVar(String name) {
    return strVars.containsKey(name);
  }

  public StrVar strVar(String name) {
    return strVars.get(name);
  }

  public void setStrVar(String name, StrVar val) {
    strVars.put(name, val);
  }

  public void removeStrVar(String name) {
    strVars.remove(name);
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

  public void pushReturn(int lineLabel) {
    returnStack.push(lineLabel);
  }

  public int popReturn() {
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
  }

  public Integer pendingJumpLabel() {
    return pendingJumpLabel;
  }

  public boolean hasPendingJump() {
    return hasPendingJump;
  }

  public void setPendingJumpLabel(Integer label) {
    this.pendingJumpLabel = label;
    this.hasPendingJump = true;
  }

  public void clearPendingJump() {
    this.pendingJumpLabel = null;
    this.hasPendingJump = false;
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

  public void clear() {
    numScalars.clear();
    numArrays.clear();
    strVars.clear();
    forLoops.clear();
    returnStack.clear();
    clearPendingJump();
    lastReportCode = ReportCode.OK;
    lastReportLabel = 0;
  }
}
