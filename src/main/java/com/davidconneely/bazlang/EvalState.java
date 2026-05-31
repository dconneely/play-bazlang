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

  public record StrArray(List<Integer> dimensions, int fixedStrLen, byte[] data) {}

  public record ForLoopData(double limit, double step, int loopPc) {}

  private final NavigableMap<Integer, ProgramLine> program = new TreeMap<>();
  private final Map<String, Double> numScalars = new HashMap<>();
  private final Map<String, NumArray> numArrays = new HashMap<>();
  private final Map<String, BStr> strVars = new HashMap<>();
  private final Map<String, StrArray> strArrays = new HashMap<>();
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

  public Map<String, Double> numScalars() {
    return numScalars;
  }

  public Map<String, NumArray> numArrays() {
    return numArrays;
  }

  public Map<String, BStr> strVars() {
    return strVars;
  }

  public Map<String, StrArray> strArrays() {
    return strArrays;
  }

  public Map<String, ForLoopData> forLoops() {
    return forLoops;
  }

  public Deque<Integer> returnStack() {
    return returnStack;
  }

  public Random random() {
    return random;
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
    strArrays.clear();
    forLoops.clear();
    returnStack.clear();
    clearPendingJump();
    lastReportCode = ReportCode.OK;
    lastReportLabel = 0;
  }
}
