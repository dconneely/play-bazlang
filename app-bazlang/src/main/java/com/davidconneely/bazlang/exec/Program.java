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

  /** Create an empty program. */
  public Program() {}

  /**
   * Whether the program has no lines.
   *
   * @return {@code true} if empty.
   */
  public boolean isEmpty() {
    return lines.isEmpty();
  }

  /** Removes every line. */
  public void clear() {
    lines.clear();
  }

  /**
   * Adds or replaces a line.
   *
   * @param label the line number.
   * @param line the line's content.
   */
  public void put(int label, ProgramLine line) {
    lines.put(label, line);
  }

  /**
   * Adds or replaces every line in {@code program}.
   *
   * @param program the lines to add, keyed by line number.
   */
  public void putAll(Map<Integer, ProgramLine> program) {
    lines.putAll(program);
  }

  /**
   * Removes a line.
   *
   * @param label the line number to remove.
   */
  public void remove(int label) {
    lines.remove(label);
  }

  /**
   * The line at the given number.
   *
   * @param label the line number.
   * @return the line, or {@code null} if absent.
   */
  public ProgramLine get(int label) {
    return lines.get(label);
  }

  /**
   * Whether a line exists at the given number.
   *
   * @param label the line number.
   * @return {@code true} if present.
   */
  public boolean containsKey(int label) {
    return lines.containsKey(label);
  }

  /**
   * The lowest line number in the program.
   *
   * @return the line number, or {@code null} if the program is empty.
   */
  public Integer firstKey() {
    return lines.isEmpty() ? null : lines.firstKey();
  }

  /**
   * The highest line number in the program.
   *
   * @return the line number, or {@code null} if the program is empty.
   */
  public Integer lastKey() {
    return lines.isEmpty() ? null : lines.lastKey();
  }

  /**
   * The least line number strictly greater than {@code label}.
   *
   * @param label the line number to search after.
   * @return the next line number, or {@code null} if none.
   */
  public Integer higherKey(int label) {
    return lines.higherKey(label);
  }

  /**
   * The greatest line number strictly less than {@code label}.
   *
   * @param label the line number to search before.
   * @return the previous line number, or {@code null} if none.
   */
  public Integer lowerKey(int label) {
    return lines.lowerKey(label);
  }

  /**
   * The least line number greater than or equal to {@code label}.
   *
   * @param label the line number to search from.
   * @return the line number, or {@code null} if none.
   */
  public Integer ceilingKey(int label) {
    return lines.ceilingKey(label);
  }

  /**
   * Every line's content, in line-number order.
   *
   * @return the lines.
   */
  public Collection<ProgramLine> values() {
    return lines.values();
  }

  /**
   * Every line number/content pair, in line-number order.
   *
   * @return the entries.
   */
  public Iterable<Map.Entry<Integer, ProgramLine>> entrySet() {
    return lines.entrySet();
  }

  /**
   * The line number/content pairs within a range, in line-number order.
   *
   * @param fromKey the range's start line number.
   * @param fromInclusive whether {@code fromKey} itself is included.
   * @param toKey the range's end line number.
   * @param toInclusive whether {@code toKey} itself is included.
   * @return the entries.
   */
  public Iterable<Map.Entry<Integer, ProgramLine>> subMapEntries(
      int fromKey, boolean fromInclusive, int toKey, boolean toInclusive) {
    return lines.subMap(fromKey, fromInclusive, toKey, toInclusive).entrySet();
  }

  /**
   * The line number/content pairs within an inclusive range, in line-number order.
   *
   * @param fromKey the range's inclusive start line number.
   * @param toKey the range's inclusive end line number.
   * @return the entries.
   */
  public Iterable<Map.Entry<Integer, ProgramLine>> subMapEntries(int fromKey, int toKey) {
    return lines.subMap(fromKey, true, toKey, true).entrySet();
  }

  /**
   * Removes every line within a range.
   *
   * @param fromKey the range's start line number.
   * @param fromInclusive whether {@code fromKey} itself is included.
   * @param toKey the range's end line number.
   * @param toInclusive whether {@code toKey} itself is included.
   */
  public void clearRange(int fromKey, boolean fromInclusive, int toKey, boolean toInclusive) {
    lines.subMap(fromKey, fromInclusive, toKey, toInclusive).clear();
  }

  /**
   * Line count.
   *
   * @return the number of lines.
   */
  public int size() {
    return lines.size();
  }

  /**
   * Finds the first DATA statement at or after {@code fromLabel}, scanning flattened statements in
   * program order. Returns its address, or null if there is none.
   *
   * @param fromLabel the line to start searching from.
   * @param parser the parser to use to flatten each candidate line's statements.
   * @return the found address, or {@code null} if there is no such statement.
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
   * flattened statements in program order (deliberately including IF bodies - see docs/quirks.md
   * "FOR loop flat skip scan"). Returns its address, or null.
   *
   * @param forVar the loop variable's name.
   * @param fromLabel the line to start searching from.
   * @param fromStatementIndex the flat statement index within {@code fromLabel} to start from.
   * @param parser the parser to use to flatten each candidate line's statements.
   * @return the found address, or {@code null} if there is no such statement.
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
