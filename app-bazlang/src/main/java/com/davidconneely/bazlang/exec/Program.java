package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ast.Stmt;
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

  public Iterable<Map.Entry<Integer, ProgramLine>> subMapEntries(int fromKey, int toKey) {
    return lines.subMap(fromKey, true, toKey, true).entrySet();
  }

  public void clearRange(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive) {
    lines.subMap(fromKey, fromInclusive, toKey, toInclusive).clear();
  }

  public int size() {
    return lines.size();
  }

  /**
   * Finds the first DATA statement at or after {@code fromLabel}, scanning flattened statements in
   * program order. Returns its address, or null if there is none.
   */
  public EvalState.StatementAddress findFirstData(int fromLabel, AntlrParser parser) {
    Integer label = lines.ceilingKey(fromLabel);
    while (label != null) {
      final var stmts = lines.get(label).getFlattenedStatements(parser);
      for (int i = 1; i <= stmts.size(); i++) {
        if (stmts.get(i - 1) instanceof Stmt.DataStmt) {
          return new EvalState.StatementAddress(label, i);
        }
      }
      label = lines.higherKey(label);
    }
    return null;
  }

  /**
   * Finds the first {@code NEXT forVar} at or after (fromLabel, fromStatementIndex), scanning
   * flattened statements in program order (deliberately including IF bodies — see docs/quirks.md
   * "FOR loop flat skip scan"). Returns its address, or null.
   */
  public EvalState.StatementAddress findMatchingNext(
      String forVar, int fromLabel, int fromStatementIndex, AntlrParser parser) {
    Integer label = lines.ceilingKey(fromLabel); // == fromLabel itself when present
    int startIdx = fromStatementIndex;
    while (label != null) {
      final var stmts = lines.get(label).getFlattenedStatements(parser);
      for (int i = startIdx; i <= stmts.size(); i++) {
        if (stmts.get(i - 1) instanceof Stmt.NextStmt(String next)
            && next.equalsIgnoreCase(forVar)) {
          return new EvalState.StatementAddress(label, i);
        }
      }
      label = lines.higherKey(label);
      startIdx = 1;
    }
    return null;
  }
}
