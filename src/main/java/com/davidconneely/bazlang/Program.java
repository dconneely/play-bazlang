package com.davidconneely.bazlang;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Encapsulates the AST program map to prevent external mutation of the internal collection. */
public class Program {
  private final NavigableMap<Integer, ProgramLine> lines = new TreeMap<>();

  public boolean isEmpty() {
    return lines.isEmpty();
  }

  public void clear() {
    lines.clear();
  }

  public void put(int label, ProgramLine line) {
    lines.put(label, line);
  }

  public void putAll(Map<Integer, ProgramLine> program) {
    lines.putAll(program);
  }

  public void remove(int label) {
    lines.remove(label);
  }

  public ProgramLine get(int label) {
    return lines.get(label);
  }

  public boolean containsKey(int label) {
    return lines.containsKey(label);
  }

  public Integer firstKey() {
    return lines.isEmpty() ? null : lines.firstKey();
  }

  public Integer lastKey() {
    return lines.isEmpty() ? null : lines.lastKey();
  }

  public Integer higherKey(int label) {
    return lines.higherKey(label);
  }

  public Integer lowerKey(int label) {
    return lines.lowerKey(label);
  }

  public Integer ceilingKey(int label) {
    return lines.ceilingKey(label);
  }

  public Collection<ProgramLine> values() {
    return lines.values();
  }

  public Iterable<Map.Entry<Integer, ProgramLine>> entrySet() {
    return lines.entrySet();
  }

  public Iterable<Map.Entry<Integer, ProgramLine>> subMapEntries(
      int fromKey, boolean fromInclusive, int toKey, boolean toInclusive) {
    return lines.subMap(fromKey, fromInclusive, toKey, toInclusive).entrySet();
  }

  public void clearRange(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive) {
    lines.subMap(fromKey, fromInclusive, toKey, toInclusive).clear();
  }

  public int size() {
    return lines.size();
  }
}
