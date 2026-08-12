package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.Limits;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.exec.ast.AssignTarget;
import com.davidconneely.bazlang.exec.ast.AstFnDefinition;
import com.davidconneely.bazlang.exec.ast.AstProgramSupport;
import com.davidconneely.bazlang.exec.ast.NumExpr;
import com.davidconneely.bazlang.exec.ast.Stmt;
import com.davidconneely.bazlang.exec.ast.StrExpr;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Walks the typed {@link Stmt} AST via {@code switch} pattern matching, delegating expression
 * evaluation to {@link AstExpressionEvaluator}. Standalone (Phase 2 of {@code
 * localonly-plan-CUSTOM-AST.md}): not wired into the live interpreter, and — unlike the eventual
 * cutover shape — does not read/write {@code EvalState.program()} or {@code EvalState.fn()}/{@code
 * setFn()}. Instead it takes its own AST-flavoured program map (see {@link AstProgramSupport}) and
 * keeps its own {@code DEF FN} store (see {@link AstFnDefinition}), so this class can be built and
 * tested without touching what the still-live {@code StatementExecutor}/{@code ExpressionEvaluator}
 * depend on.
 *
 * <p><strong>Only implements sub-phase 2a</strong> (control flow and program data: {@code
 * CLEAR}/{@code NEW}/{@code LET}/{@code DIM}/{@code FOR}/{@code NEXT}/{@code GOTO}/{@code
 * GOSUB}/{@code RETURN}/{@code IF}/{@code CONT}/{@code STOP}/{@code RUN}/{@code DATA}/{@code
 * READ}/{@code RESTORE}/{@code DEF FN}, plus the trivial {@code REM} no-op). Every other statement
 * kind (I/O, graphics, program management — sub-phases 2b/2c) throws {@link
 * UnsupportedOperationException} for now; the {@code switch} below is exhaustive over {@link Stmt}
 * via its {@code default} arm specifically so adding a new {@link Stmt} case is a compile error
 * here until it's either implemented or explicitly deferred, not a silent gap.
 */
public class AstStatementExecutor {
  private final EvalState state;
  private final AstExpressionEvaluator exprEvaluator;
  private final NavigableMap<Integer, List<Stmt>> program;
  private final Map<String, AstFnDefinition> fnDefs = new HashMap<>();

  public AstStatementExecutor(
      EvalState state,
      AstExpressionEvaluator exprEvaluator,
      NavigableMap<Integer, List<Stmt>> program) {
    this.state = state;
    this.exprEvaluator = exprEvaluator;
    this.program = program;
  }

  /**
   * The AST-flavoured program map this executor addresses {@code GOTO}/{@code NEXT}/{@code RESTORE}
   * targets against — separate from {@code EvalState.program()} (see class Javadoc).
   */
  public NavigableMap<Integer, List<Stmt>> program() {
    return program;
  }

  /**
   * This executor's own {@code DEF FN} store — separate from {@code EvalState.fn()} (see class
   * Javadoc and {@link AstFnDefinition}).
   */
  public Map<String, AstFnDefinition> fnDefs() {
    return fnDefs;
  }

  public void execute(Stmt stmt) {
    switch (stmt) {
      case Stmt.ClearStmt _ -> state.clear();
      case Stmt.NewStmt _ -> {
        state.clear();
        program.clear();
      }
      case Stmt.LetStmt s -> executeLetStmt(s);
      case Stmt.DimStmt s -> executeDimStmt(s);
      case Stmt.ForStmt s -> executeForStmt(s);
      case Stmt.NextStmt s -> executeNextStmt(s);
      case Stmt.GotoStmt s -> gotoLabel((int) Math.round(exprEvaluator.evalNum(s.target())));
      case Stmt.GosubStmt s -> executeGosubStmt(s);
      case Stmt.ReturnStmt _ -> executeReturnStmt();
      case Stmt.IfStmt s -> executeIfStmt(s);
      case Stmt.ContStmt _ -> executeContStmt();
      case Stmt.StopStmt _ -> executeStopStmt();
      case Stmt.RunStmt s -> executeRunStmt(s);
      case Stmt.DataStmt _ -> {
        /* no-op: consumed by READ, not executed */
      }
      case Stmt.ReadStmt s -> executeReadStmt(s);
      case Stmt.RestoreStmt s -> executeRestoreStmt(s);
      case Stmt.DefFnStmt s -> executeDefFnStmt(s);
      case Stmt.RemStmt _ -> {
        /* no-op */
      }
      default ->
          throw new UnsupportedOperationException(
              stmt.getClass().getSimpleName() + " not yet implemented (Phase 2b/2c)");
    }
  }

  // ===== LET / DIM =====

  private void executeLetStmt(Stmt.LetStmt stmt) {
    if (stmt.value() instanceof NumExpr numExpr) {
      assignNumTarget(stmt.target(), exprEvaluator.evalNum(numExpr));
    } else if (stmt.value() instanceof StrExpr strExpr) {
      if (!(stmt.target() instanceof AssignTarget.StrTarget strTarget)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric value");
      }
      assignStrTarget(strTarget, exprEvaluator.evalStr(strExpr));
    }
  }

  private void executeDimStmt(Stmt.DimStmt stmt) {
    final int numDims = stmt.dims().size();
    final int[] dims = new int[numDims];
    for (int i = 0; i < numDims; i++) {
      final int d = (int) exprEvaluator.evalNum(stmt.dims().get(i));
      if (d < 1) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      dims[i] = d;
    }
    if (stmt.isString()) {
      state.removeStrVar(stmt.name());
      final int flen = dims[numDims - 1];
      long total = 1;
      final int[] arrDims = new int[numDims - 1];
      System.arraycopy(dims, 0, arrDims, 0, numDims - 1);
      for (int i = 0; i < numDims - 1; i++) {
        total *= dims[i];
        if (total > Limits.MAX_ARRAY_ELEMENTS) {
          throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
        }
      }
      final long totalBytes = total * flen;
      if (totalBytes > Limits.MAX_ARRAY_ELEMENTS) {
        throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
      }
      final byte[] data = new byte[(int) totalBytes];
      Arrays.fill(data, (byte) 32); // Space padded by default
      state.setStrVar(stmt.name(), new EvalState.StrVar.Array(arrDims, flen, data));
    } else {
      long total = 1;
      for (int d : dims) {
        total *= d;
        if (total > Limits.MAX_ARRAY_ELEMENTS) {
          throw codedException(ReportCode.OUT_OF_MEMORY, "Array too large");
        }
      }
      state.setNumArray(stmt.name(), new EvalState.NumArray(dims, new double[(int) total]));
    }
  }

  // ===== Control flow =====

  private void executeForStmt(Stmt.ForStmt stmt) {
    final double st = exprEvaluator.evalNum(stmt.start());
    final double en = exprEvaluator.evalNum(stmt.end());
    final double step = exprEvaluator.evalNum(stmt.step());
    state.setNumVar(stmt.forVar(), st);
    state.setForLoop(
        stmt.forVar(),
        new EvalState.ForLoopData(
            en, step, state.currentLineLabel(), state.currentStatementIndex()));
    if ((step >= 0) ? (st > en) : (st < en)) {
      // Skip to matching NEXT (flat scan including IF bodies — see docs/quirks.md
      // "FOR loop flat skip scan").
      final var addr =
          AstProgramSupport.findMatchingNext(
              program, stmt.forVar(), state.currentLineLabel(), state.currentStatementIndex() + 1);
      if (addr == null) {
        throw new ReportException(
            ReportCode.FOR_WITHOUT_NEXT, state.currentLineLabel(), "FOR without NEXT");
      }
      state.setPendingJumpLocation(addr.lineLabel(), addr.statementIndex() + 1);
    }
  }

  private void executeNextStmt(Stmt.NextStmt stmt) {
    final String forVar = stmt.forVar();
    if (!state.hasForLoop(forVar)) {
      throw new ReportException(
          ReportCode.NEXT_WITHOUT_FOR, state.currentLineLabel(), "NEXT without FOR");
    }
    final var d = state.forLoop(forVar);
    if (!state.hasNumVar(forVar)) {
      throw new ReportException(
          ReportCode.VARIABLE_NOT_FOUND, state.currentLineLabel(), "Undefined loop variable");
    }
    final double nv = state.numVar(forVar) + d.step();
    state.setNumVar(forVar, nv);
    if (d.step() >= 0 ? nv <= d.limit() : nv >= d.limit()) {
      state.setPendingJumpLocation(d.loopPcLabel(), d.loopPcStatementIndex() + 1);
    }
  }

  private void gotoLabel(int target) {
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE,
          state.currentLineLabel(),
          "GO TO line label out of range");
    }
    // Prevent jumping to line 0 (the immediate statement buffer)
    final int searchTarget = Math.max(target, Limits.MIN_LINE_LABEL);
    final Integer label = program.ceilingKey(searchTarget);
    if (label != null) {
      state.setPendingJumpLocation(label, 1);
    } else {
      state.setRunning(false);
    }
  }

  private void executeGosubStmt(Stmt.GosubStmt stmt) {
    state.pushReturn(
        new EvalState.StatementAddress(
            state.currentLineLabel(), state.currentStatementIndex() + 1));
    gotoLabel((int) Math.round(exprEvaluator.evalNum(stmt.target())));
  }

  private void executeReturnStmt() {
    if (state.isReturnStackEmpty()) {
      throw new ReportException(
          ReportCode.RETURN_WITHOUT_GOSUB, state.currentLineLabel(), "RETURN without GOSUB");
    }
    final var gosubLoc = state.popReturn();
    state.setPendingJumpLocation(gosubLoc.lineLabel(), gosubLoc.statementIndex());
  }

  private void executeIfStmt(Stmt.IfStmt stmt) {
    if (exprEvaluator.evalNum(stmt.condition()) == 0.0) {
      if (state.currentLineLabel() > 0) {
        final Integer nextLabel = program.higherKey(state.currentLineLabel());
        if (nextLabel != null) {
          state.setPendingJumpLocation(nextLabel, 1);
        } else {
          state.setRunning(false); // End of program
        }
      } else {
        state.setPendingJumpLocation(
            0, Integer.MAX_VALUE); // effectively skips the rest of immediate line
      }
    }
  }

  private void executeContStmt() {
    final int m = state.lastReport().lineLabel();
    if (m <= 0) {
      return;
    }
    if (state.lastReport().code() == ReportCode.STOP_STATEMENT
        || state.lastReport().code() == ReportCode.BREAK_INTO_PROGRAM) {
      state.setPendingJumpLocation(m, state.lastReport().statementIndex() + 1);
    } else {
      state.setPendingJumpLocation(m, state.lastReport().statementIndex());
    }
  }

  private void executeStopStmt() {
    state.setRunning(false);
    throw codedException(ReportCode.STOP_STATEMENT, ReportCode.STOP_STATEMENT.getMessage());
  }

  private void executeRunStmt(Stmt.RunStmt stmt) {
    final int target =
        stmt.target() != null
            ? (int) Math.round(exprEvaluator.evalNum(stmt.target()))
            : Limits.MIN_TARGET_LABEL;
    if (target < Limits.MIN_TARGET_LABEL || target > Limits.MAX_TARGET_LABEL) {
      throw new ReportException(
          ReportCode.INTEGER_OUT_OF_RANGE, state.currentLineLabel(), "RUN line label out of range");
    }
    state.clear();
    gotoLabel(target);
  }

  // ===== DATA / READ / RESTORE =====

  private void restoreTo(int target) {
    final var addr = AstProgramSupport.findFirstData(program, target);
    if (addr != null) {
      state.setDataPointer(new EvalState.DataPointer(addr.lineLabel(), addr.statementIndex(), 0));
    } else {
      state.setDataPointer(new EvalState.DataPointer(Integer.MAX_VALUE, -1, -1));
    }
  }

  private void executeReadStmt(Stmt.ReadStmt stmt) {
    for (final var target : stmt.targets()) {
      if (state.dataPointer().lineLabel() == -1) {
        restoreTo(0);
      }
      final int lineLabel = state.dataPointer().lineLabel();
      if (lineLabel == Integer.MAX_VALUE) {
        throw codedException(ReportCode.OUT_OF_DATA, "Out of DATA");
      }
      final var stmts = program.get(lineLabel);
      if (stmts == null) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final int stmtIdx = state.dataPointer().statementIndex();
      if (!(stmts.get(stmtIdx - 1) instanceof Stmt.DataStmt dataStmt)) {
        throw codedException(ReportCode.STATEMENT_LOST, "Statement lost");
      }
      final int exprIdx = state.dataPointer().expressionIndex();
      final var exprVal = dataStmt.values().get(exprIdx);

      // Advance pointer before evaluating and assigning
      if (exprIdx + 1 < dataStmt.values().size()) {
        state.setDataPointer(new EvalState.DataPointer(lineLabel, stmtIdx, exprIdx + 1));
      } else {
        // Find next DATA statement in the current line
        boolean found = false;
        for (int i = stmtIdx + 1; i <= stmts.size(); i++) {
          if (stmts.get(i - 1) instanceof Stmt.DataStmt) {
            state.setDataPointer(new EvalState.DataPointer(lineLabel, i, 0));
            found = true;
            break;
          }
        }
        if (!found) {
          final Integer nextLine = program.higherKey(lineLabel);
          if (nextLine != null) {
            restoreTo(nextLine);
          } else {
            state.setDataPointer(new EvalState.DataPointer(Integer.MAX_VALUE, -1, -1));
          }
        }
      }

      // Evaluate and assign
      if (exprVal instanceof NumExpr numExpr) {
        if (target instanceof AssignTarget.StrTarget) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected number");
        }
        assignNumTarget(target, exprEvaluator.evalNum(numExpr));
      } else if (exprVal instanceof StrExpr strExpr) {
        if (!(target instanceof AssignTarget.StrTarget strTarget)) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string");
        }
        assignStrTarget(strTarget, exprEvaluator.evalStr(strExpr));
      }
    }
  }

  private void executeRestoreStmt(Stmt.RestoreStmt stmt) {
    int target = 0;
    if (stmt.target() != null) {
      final double val = exprEvaluator.evalNum(stmt.target());
      target = (int) Math.round(val);
      if (target < 0 || target > Limits.MAX_TARGET_LABEL) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Line label out of range");
      }
    }
    restoreTo(target);
  }

  // ===== DEF FN =====

  private void executeDefFnStmt(Stmt.DefFnStmt stmt) {
    final String name = stmt.name();
    if (name.endsWith("$")) {
      if (!(stmt.body() instanceof StrExpr)) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected string expression");
      }
    } else {
      if (!(stmt.body() instanceof NumExpr)) {
        throw codedException(
            ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric expression");
      }
    }
    final var paramSet = new HashSet<String>();
    for (final var p : stmt.params()) {
      if (!paramSet.add(p)) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "Duplicate parameter name: " + p);
      }
    }
    fnDefs.put(name, new AstFnDefinition(name, stmt.params(), stmt.body()));
  }

  // ===== Assignment helpers =====

  private void assignNumTarget(AssignTarget target, double val) {
    if (target instanceof AssignTarget.NumScalarTarget scalar) {
      var ref = scalar.ref;
      if (ref == null) {
        ref = state.getOrAddNumVar(scalar.name);
        scalar.ref = ref;
      }
      ref.value = val;
      ref.initialised = true;
    } else if (target instanceof AssignTarget.NumArrayTarget array) {
      var ref = array.ref;
      if (ref == null) {
        ref = state.getOrAddNumArray(array.name);
        array.ref = ref;
      }
      if (ref.array == null) {
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + array.name);
      }
      final var na = ref.array;
      final int count = array.indices.size();
      final int[] indices = new int[count];
      for (int i = 0; i < count; i++) {
        indices[i] = (int) exprEvaluator.evalNum(array.indices.get(i));
      }
      final int idx = exprEvaluator.calculateArrayIndex(na.dimensions(), indices, 0, count);
      na.data()[idx] = val;
    } else {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Type mismatch: expected numeric target");
    }
  }

  private void assignStrTarget(AssignTarget.StrTarget target, BStr val) {
    var ref = target.ref;
    if (ref == null) {
      ref = state.getOrAddStrVar(target.name);
      target.ref = ref;
    }
    final var subscript = target.subscript;
    if (subscript == null) {
      assignStrSubscriptNull(ref, val);
      return;
    }

    final int indicesCount = subscript.indices().size();
    final int[] indices = new int[indicesCount];
    for (int i = 0; i < indicesCount; i++) {
      indices[i] = (int) exprEvaluator.evalNum(subscript.indices().get(i));
    }
    int sliceStart = -1;
    int sliceEnd = -1;
    final boolean hasSlice = subscript.slice() != null;
    if (hasSlice) {
      if (subscript.slice().start() != null) {
        sliceStart = (int) exprEvaluator.evalNum(subscript.slice().start());
      }
      if (subscript.slice().end() != null) {
        sliceEnd = (int) exprEvaluator.evalNum(subscript.slice().end());
      }
    }

    final var strVar = ref.value;
    if (strVar instanceof EvalState.StrVar.Array ca) {
      assignStrArrayTarget(ca, val, indices, indicesCount, sliceStart, sliceEnd);
    } else if (strVar instanceof EvalState.StrVar.Scalar scalar) {
      assignStrScalarTarget(
          ref, scalar, val, hasSlice, indices, indicesCount, sliceStart, sliceEnd);
    } else {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + target.name);
    }
  }

  private void assignStrSubscriptNull(EvalState.StrVarRef ref, BStr val) {
    final var strVar = ref.value;
    if (strVar
        instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
      if (arrayDimensions.length != 0) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      final int copyLen = Math.min(stringLength, val.length());
      for (int i = 0; i < copyLen; i++) {
        data[i] = (byte) val.byteAt(i);
      }
      for (int i = copyLen; i < stringLength; i++) {
        data[i] = (byte) 32;
      }
    } else {
      ref.value = new EvalState.StrVar.Scalar(val.copy());
    }
  }

  private void assignStrArrayTarget(
      EvalState.StrVar.Array ca,
      BStr val,
      int[] indices,
      int indicesCount,
      int sliceStart,
      int sliceEnd) {
    final int n = ca.arrayDimensions().length;
    int byteIndex = -1;
    int count = indicesCount;
    if (count == n + 1) {
      byteIndex = indices[n];
      count--;
    }
    final int arrayIdx = exprEvaluator.calculateArrayIndex(ca.arrayDimensions(), indices, 0, count);

    final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, ca.stringLength());
    if (bounds == null) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }

    final int sliceLen = bounds.length();
    final int copyLen = Math.min(sliceLen, val.length());
    final int offset = arrayIdx * ca.stringLength() + (bounds.start() - 1);
    for (int i = 0; i < copyLen; i++) {
      ca.data()[offset + i] = (byte) val.byteAt(i);
    }
    for (int i = copyLen; i < sliceLen; i++) {
      ca.data()[offset + i] = (byte) 32;
    }
  }

  private void assignStrScalarTarget(
      EvalState.StrVarRef ref,
      EvalState.StrVar.Scalar scalar,
      BStr val,
      boolean hasSlice,
      int[] indices,
      int indicesCount,
      int sliceStart,
      int sliceEnd) {
    final var str = scalar.value();
    int byteIndex = -1;
    int count = indicesCount;
    if (count == 1 && !hasSlice) {
      byteIndex = indices[0];
      count--;
    }
    if (count > 0) {
      throw codedException(
          ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
    }

    final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, str.length());
    if (bounds == null) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }

    ref.value = new EvalState.StrVar.Scalar(str.withSlice(bounds.start(), bounds.end(), val));
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
