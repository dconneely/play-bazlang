package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.exec.ast.VarIdAllocator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Encapsulates the AST program map to prevent external mutation of the internal collection.
 *
 * <p>Also the authority for variable/array ids (implements {@link VarIdAllocator}): a compiled
 * {@code Program} - its {@code ProgramLine}s, their lowered/cached {@code Stmt} lists, and the ids
 * baked into those lists' AST nodes - can be handed from one {@code EvalState} to another, letting
 * a second session reuse the first's lowering work without re-parsing. Sessions must still take
 * turns (one {@code EvalState} at a time) - see {@link IdTable}'s Javadoc for exactly how far the
 * thread-safety here goes, and {@code docs/spec/architecture.md} for the rest of the gap.
 */
public class Program implements VarIdAllocator {
  /**
   * A name-to-id table with the reverse (id-to-name) direction alongside it, backed by thread-safe
   * collections - a first step towards true concurrent sharing, not a complete one: minting an id
   * (checking-then-inserting into both directions) is guarded by a {@link ReentrantLock} as a whole
   * (the same lock/unlock-in-{@code finally} shape as {@code JavaSoundSpeaker}/{@code
   * PlaySequencer}), so two threads can't race to assign two different ids to the same new name -
   * but nothing here makes {@link ProgramLine#getFlattenedStatements} itself safe to call from two
   * threads at once - see {@code docs/spec/architecture.md}.
   */
  private static final class IdTable {
    private final Map<String, Integer> ids = new ConcurrentHashMap<>();
    private final List<String> names = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    int idFor(String name) {
      return ids.computeIfAbsent(
          name,
          n -> {
            lock.lock();
            try {
              names.add(n);
              return names.size() - 1;
            } finally {
              lock.unlock();
            }
          });
    }

    Integer idIfPresent(String name) {
      return ids.get(name);
    }

    String nameFor(int id) {
      lock.lock();
      try {
        return names.get(id);
      } finally {
        lock.unlock();
      }
    }
  }

  private final NavigableMap<Integer, ProgramLine> lines = new TreeMap<>();
  private final IdTable numVarIds = new IdTable();
  private final IdTable numArrayIds = new IdTable();
  private final IdTable strVarIds = new IdTable();

  /** Create an empty program. */
  public Program() {}

  @Override
  public int numVarId(String name) {
    return numVarIds.idFor(name);
  }

  @Override
  public int numArrayId(String name) {
    return numArrayIds.idFor(name);
  }

  @Override
  public int strVarId(String name) {
    return strVarIds.idFor(name);
  }

  /**
   * The given scalar numeric variable name's id, without minting a new one if it isn't already
   * known.
   *
   * @param name the variable's name.
   * @return the id, or {@code null} if this name has never been assigned one.
   */
  public Integer numVarIdIfPresent(String name) {
    return numVarIds.idIfPresent(name);
  }

  /**
   * The given numeric array name's id, without minting a new one if it isn't already known.
   *
   * @param name the array's name.
   * @return the id, or {@code null} if this name has never been assigned one.
   */
  public Integer numArrayIdIfPresent(String name) {
    return numArrayIds.idIfPresent(name);
  }

  /**
   * The given string variable (scalar or array) name's id, without minting a new one if it isn't
   * already known.
   *
   * @param name the variable's name.
   * @return the id, or {@code null} if this name has never been assigned one.
   */
  public Integer strVarIdIfPresent(String name) {
    return strVarIds.idIfPresent(name);
  }

  /**
   * The scalar numeric variable name at the given id, the reverse of {@link #numVarId}.
   *
   * @param id the variable's id.
   * @return the name.
   */
  public String numVarName(int id) {
    return numVarIds.nameFor(id);
  }

  /**
   * The numeric array name at the given id, the reverse of {@link #numArrayId}.
   *
   * @param id the array's id.
   * @return the name.
   */
  public String numArrayName(int id) {
    return numArrayIds.nameFor(id);
  }

  /**
   * The string variable name at the given id, the reverse of {@link #strVarId}.
   *
   * @param id the variable's id.
   * @return the name.
   */
  public String strVarName(int id) {
    return strVarIds.nameFor(id);
  }

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
   * @param ids assigns/reuses variable ids for any line not already lowered.
   * @return the found address, or {@code null} if there is no such statement.
   */
  public EvalState.StatementAddress findFirstData(
      int fromLabel, AntlrParser parser, VarIdAllocator ids) {
    Integer label = lines.ceilingKey(fromLabel);
    while (label != null) {
      final var stmts = lines.get(label).getFlattenedStatements(parser, ids);
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
   * @param ids assigns/reuses variable ids for any line not already lowered.
   * @return the found address, or {@code null} if there is no such statement.
   */
  public EvalState.StatementAddress findMatchingNext(
      String forVar,
      int fromLabel,
      int fromStatementIndex,
      AntlrParser parser,
      VarIdAllocator ids) {
    Integer label = lines.ceilingKey(fromLabel); // == fromLabel itself when present
    int startIdx = fromStatementIndex;
    while (label != null) {
      final var stmts = lines.get(label).getFlattenedStatements(parser, ids);
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
