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

  public record CharArray(List<Integer> dimensions, int fixedStrLen, char[] data) {}

  public record ForLoopData(double limit, double step, int loopPc) {}

  private final NavigableMap<Integer, ProgramLine> program = new TreeMap<>();
  private final Map<String, Double> numScalars = new HashMap<>();
  private final Map<String, NumArray> numArrays = new HashMap<>();
  private final Map<String, String> strVars = new HashMap<>();
  private final Map<String, CharArray> charArrays = new HashMap<>();
  private final Map<String, ForLoopData> forLoops = new HashMap<>();
  private final Deque<Integer> returnStack = new ArrayDeque<>();
  private final Random random = new Random();

  private boolean running = true;
  private int currentLineLabel = 0;
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

  public Map<String, String> strVars() {
    return strVars;
  }

  public Map<String, CharArray> charArrays() {
    return charArrays;
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
    charArrays.clear();
    forLoops.clear();
    returnStack.clear();
    lastReportCode = ReportCode.OK;
    lastReportLabel = 0;
  }
}
