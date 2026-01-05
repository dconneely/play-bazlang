package com.davidconneely.bazlang;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class MachineState {
  public record NumericArray(List<Integer> dimensions, double[] data) {}

  public record CharacterArray(List<Integer> dimensions, int fixedStringLength, char[] data) {}

  public record ForLoopData(double limit, double step, int loopPc) {}

  private final NavigableMap<Integer, Statement> program = new TreeMap<>();
  private final Map<String, Double> numericScalars = new HashMap<>();
  private final Map<String, NumericArray> numericArrays = new HashMap<>();
  private final Map<String, String> variableLengthStrings = new HashMap<>();
  private final Map<String, CharacterArray> characterArrays = new HashMap<>();
  private final Map<String, ForLoopData> forLoops = new HashMap<>();
  private final Deque<Integer> returnStack = new ArrayDeque<>();
  private final Random random = new Random();

  private boolean running = true;
  private int currentLineLabel = 0;
  private ReportCode lastReportCode = ReportCode.OK;
  private int lastReportLabel = 0;

  public NavigableMap<Integer, Statement> program() {
    return program;
  }

  public void setProgram(Map<Integer, Statement> program) {
    this.program.clear();
    this.program.putAll(program);
  }

  public Map<String, Double> numericScalars() {
    return numericScalars;
  }

  public Map<String, NumericArray> numericArrays() {
    return numericArrays;
  }

  public Map<String, String> variableLengthStrings() {
    return variableLengthStrings;
  }

  public Map<String, CharacterArray> characterArrays() {
    return characterArrays;
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
    numericScalars.clear();
    numericArrays.clear();
    variableLengthStrings.clear();
    characterArrays.clear();
    forLoops.clear();
    returnStack.clear();
    lastReportCode = ReportCode.OK;
    lastReportLabel = 0;
  }
}
