package com.davidconneely.bazlang.exec;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.exec.ast.AstLowering;
import com.davidconneely.bazlang.exec.ast.Expr;
import com.davidconneely.bazlang.exec.ast.NumExpr;
import com.davidconneely.bazlang.exec.ast.StrExpr;
import com.davidconneely.bazlang.exec.ast.StrFuncKind;
import com.davidconneely.bazlang.exec.ast.StrSubscript;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Walks the typed {@link NumExpr}/{@link StrExpr} AST directly via {@code switch} pattern matching
 * and returns {@code double}/{@link BStr} directly from {@link #evalNum}/{@link #evalStr} - no
 * {@code numResult}/{@code strResult} side fields, unlike the original ANTLR-visitor-based
 * evaluator this replaced at the Phase 4 cutover (see {@code localonly-plan-CUSTOM-AST.md}).
 */
public class ExpressionEvaluator {
  private final EvalState state;
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final AntlrParser parser;

  private final int[] indexStack = new int[256];
  private int indexStackPtr = 0;

  public ExpressionEvaluator(
      EvalState state, VirtualScreen screen, VirtualInput input, AntlrParser parser) {
    this.state = state;
    this.screen = screen;
    this.input = input;
    this.parser = parser;
  }

  public VirtualScreen screen() {
    return screen;
  }

  /**
   * Formats an {@link Expr} for {@code PRINT}: a numeric value via {@link
   * ExpressionEvaluator#formatNum}, a string value as-is.
   */
  public String evalPrintExpr(Expr expr) {
    if (expr instanceof NumExpr numExpr) {
      return formatNum(evalNum(numExpr));
    }
    return evalStr((StrExpr) expr).toJavaString();
  }

  // ===== Numeric expressions =====

  public double evalNum(NumExpr expr) {
    return switch (expr) {
      case NumExpr.NumLiteral n -> n.value();
      case NumExpr.NumVarExpr v -> evalNumVar(v);
      case NumExpr.NumArrayExpr a -> evalNumArray(a);
      case NumExpr.NumFuncCall f -> evalNumFuncCall(f);
      case NumExpr.FnNumCall f -> evalFnNumCall(f);
      case NumExpr.NumBinaryOp b -> evalNumBinaryOp(b);
      case NumExpr.NumUnaryMinus u -> -evalNum(u.operand());
      case NumExpr.NumCompare c -> evalNumCompare(c);
      case NumExpr.StrCompare c -> evalStrCompare(c);
      case NumExpr.NumNot n -> evalNum(n.operand()) == 0.0 ? 1.0 : 0.0;
      case NumExpr.NumAnd a -> evalNumAnd(a);
      case NumExpr.NumOr o -> evalNumOr(o);
    };
  }

  private double evalNumVar(NumExpr.NumVarExpr v) {
    var ref = v.ref;
    if (ref == null) {
      ref = state.getOrAddNumVar(v.name);
      v.ref = ref;
    }
    if (!ref.initialised) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + ref.name);
    }
    return ref.value;
  }

  private double evalNumArray(NumExpr.NumArrayExpr a) {
    var ref = a.ref;
    if (ref == null) {
      ref = state.getOrAddNumArray(a.name);
      a.ref = ref;
    }
    if (ref.array == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + ref.name);
    }
    final var na = ref.array;
    final int count = a.indices.size();
    final int ptr = this.indexStackPtr;
    if (indexStackPtr + count > indexStack.length) {
      throw codedException(ReportCode.OUT_OF_MEMORY, "Expression too deeply nested");
    }
    this.indexStackPtr += count;
    try {
      for (int i = 0; i < count; i++) {
        indexStack[ptr + i] = (int) evalNum(a.indices.get(i));
      }
      final int idx = calculateArrayIndex(na.dimensions(), indexStack, ptr, count);
      return na.data()[idx];
    } finally {
      this.indexStackPtr = ptr;
    }
  }

  private double evalNumBinaryOp(NumExpr.NumBinaryOp b) {
    final double l = evalNum(b.left());
    final double r = evalNum(b.right());
    return switch (b.op()) {
      case MUL -> requireFinite(l * r);
      case DIV -> {
        if (r == 0.0) {
          throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
        }
        yield requireFinite(l / r);
      }
      case ADD -> requireFinite(l + r);
      case SUB -> requireFinite(l - r);
      case POW -> {
        if (l < 0.0 && r != Math.floor(r)) {
          throw codedException(
              ReportCode.INVALID_ARGUMENT, "Negative base with non-integer exponent");
        }
        yield requireFinite(Math.pow(l, r));
      }
      default -> throw new IllegalStateException("Not a binary arithmetic operator: " + b.op());
    };
  }

  private double evalNumCompare(NumExpr.NumCompare c) {
    final double l = evalNum(c.left());
    final double r = evalNum(c.right());
    return switch (c.op()) {
      case EQ -> l == r ? 1.0 : 0.0;
      case NE -> l != r ? 1.0 : 0.0;
      case LT -> l < r ? 1.0 : 0.0;
      case LE -> l <= r ? 1.0 : 0.0;
      case GT -> l > r ? 1.0 : 0.0;
      case GE -> l >= r ? 1.0 : 0.0;
      default -> throw new IllegalStateException("Not a comparison operator: " + c.op());
    };
  }

  private double evalStrCompare(NumExpr.StrCompare c) {
    final var l = evalStr(c.left());
    final var r = evalStr(c.right());
    return switch (c.op()) {
      case EQ -> l.equals(r) ? 1.0 : 0.0;
      case NE -> !l.equals(r) ? 1.0 : 0.0;
      case LT -> l.compareTo(r) < 0 ? 1.0 : 0.0;
      case LE -> l.compareTo(r) <= 0 ? 1.0 : 0.0;
      case GT -> l.compareTo(r) > 0 ? 1.0 : 0.0;
      case GE -> l.compareTo(r) >= 0 ? 1.0 : 0.0;
      default -> throw new IllegalStateException("Not a comparison operator: " + c.op());
    };
  }

  private double evalNumAnd(NumExpr.NumAnd a) {
    final double left = evalNum(a.left());
    final double right = evalNum(a.right());
    // A AND B = A if B != 0, 0 if B = 0
    return right != 0.0 ? left : 0.0;
  }

  private double evalNumOr(NumExpr.NumOr o) {
    final double left = evalNum(o.left());
    final double right = evalNum(o.right());
    // A OR B = 1 if B != 0, A if B = 0
    return right != 0.0 ? 1.0 : left;
  }

  private double evalNumFuncCall(NumExpr.NumFuncCall call) {
    final var args = call.args();
    return switch (call.kind()) {
      case ABS -> Math.abs(argNum(args, 0));
      case ACS -> {
        final double arg = argNum(args, 0);
        if (Math.abs(arg) > 1.0) {
          throw codedException(ReportCode.INVALID_ARGUMENT, "ACS requires argument in [-1, 1]");
        }
        yield Math.acos(arg);
      }
      case ASN -> {
        final double arg = argNum(args, 0);
        if (Math.abs(arg) > 1.0) {
          throw codedException(ReportCode.INVALID_ARGUMENT, "ASN requires argument in [-1, 1]");
        }
        yield Math.asin(arg);
      }
      case ATTR -> {
        final int row = (int) Math.round(argNum(args, 0));
        final int col = (int) Math.round(argNum(args, 1));
        if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
          throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
        }
        yield screen.getScreenAttributes(row, col);
      }
      case ATN -> Math.atan(argNum(args, 0));
      case CODE -> {
        final var s = argStr(args, 0);
        // Sinclair ZX BASIC `PRINT CODE ""` shows `0`
        yield s.isEmpty() ? 0 : s.byteAt(0);
      }
      case COLOUR -> {
        final int r = (int) Math.round(argNum(args, 0));
        final int g = (int) Math.round(argNum(args, 1));
        final int b = (int) Math.round(argNum(args, 2));
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
          throw codedException(
              ReportCode.INVALID_ARGUMENT, "COLOUR components must be between 0 and 255");
        }
        final int y = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        yield 16_777_216.0 + y;
      }
      case COS -> Math.cos(argNum(args, 0));
      case EXP -> requireFinite(Math.exp(argNum(args, 0)));
      case FRAMES -> System.currentTimeMillis() / 20.0;
      case INT -> Math.floor(argNum(args, 0));
      case LEN -> argStr(args, 0).length();
      case LN -> {
        final double arg = argNum(args, 0);
        if (arg <= 0.0) {
          throw codedException(ReportCode.INVALID_ARGUMENT, "LN requires a positive argument");
        }
        yield Math.log(arg);
      }
      case PI -> Math.PI;
      case PLOTH -> screen.plotHeight();
      case PLOTMODE -> screen.plotMode();
      case PLOTW -> screen.plotWidth();
      case PLOTX -> state.graphicsCursorX();
      case PLOTY -> state.graphicsCursorY();
      case POINT -> {
        final int x = (int) Math.round(argNum(args, 0));
        final int y = (int) Math.round(argNum(args, 1));
        yield screen.point(x, y);
      }
      case RND -> state.nextRandom();
      case SGN -> Math.signum(argNum(args, 0));
      case SIN -> Math.sin(argNum(args, 0));
      case SQR -> {
        final double arg = argNum(args, 0);
        if (arg < 0.0) {
          throw codedException(ReportCode.INVALID_ARGUMENT, "SQR requires non-negative argument");
        }
        yield Math.sqrt(arg);
      }
      case TAN -> Math.tan(argNum(args, 0));
      case TEXTH -> screen.printHeight();
      case TEXTW -> screen.printWidth();
      case TEXTX -> screen.currentCol();
      case TEXTY -> screen.currentRow();
      case UCNEXT -> {
        final var s = argStr(args, 0);
        final int pos = (int) argNum(args, 1); // 1-based byte position
        if (pos < 1 || pos > s.length() + 1) {
          throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCNEXT position out of range");
        }
        yield s.nextCodepointStart(pos - 1) + 1; // 1-based
      }
      case UCODE -> {
        final var s = argStr(args, 0);
        if (s.isEmpty()) {
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "UCODE of empty string");
        }
        yield s.firstCodepoint();
      }
      case ULEN -> argStr(args, 0).codepointLength();
      case VAL -> evaluateNumericExpression(argStr(args, 0).toJavaString().trim());
      case XATTR -> {
        final int row = (int) Math.round(argNum(args, 0));
        final int col = (int) Math.round(argNum(args, 1));
        final int select = (int) Math.round(argNum(args, 2));
        if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
          throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
        }
        if (select < 0 || select > 8) {
          throw codedException(
              ReportCode.INTEGER_OUT_OF_RANGE, "XATTR selector out of range [0, 8]");
        }
        yield screen.getXAttributes(row, col, select);
      }
    };
  }

  private double evalFnNumCall(NumExpr.FnNumCall call) {
    return (Double) evaluateFnCall(call.name(), call.args());
  }

  private double argNum(List<Expr> args, int i) {
    return evalNum((NumExpr) args.get(i));
  }

  // ===== String expressions =====

  public BStr evalStr(StrExpr expr) {
    return switch (expr) {
      case StrExpr.StrLiteral s -> s.value();
      case StrExpr.StrVarExpr v -> evalStrVar(v);
      case StrExpr.StrSubscriptExpr s -> evalStrSubscriptExpr(s);
      case StrExpr.StrConcat c -> evalStr(c.left()).concat(evalStr(c.right()));
      case StrExpr.StrFuncCall f -> evalStrFuncCall(f);
      case StrExpr.FnStrCall f -> evalFnStrCall(f);
      case StrExpr.StrAnd a -> evalStrAnd(a);
    };
  }

  private BStr evalStrVar(StrExpr.StrVarExpr v) {
    var ref = v.ref;
    if (ref == null) {
      ref = state.getOrAddStrVar(v.name);
      v.ref = ref;
    }
    if (ref.value == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
    }
    final var strVar = ref.value;
    if (strVar
        instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
      if (arrayDimensions.length == 0) {
        return BStr.fromBytes(data, 0, stringLength);
      }
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    if (strVar instanceof EvalState.StrVar.Scalar(BStr value)) {
      return value;
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
  }

  private BStr evalStrSubscriptExpr(StrExpr.StrSubscriptExpr s) {
    var ref = s.ref;
    if (ref == null) {
      ref = state.getOrAddStrVar(s.name);
      s.ref = ref;
    }
    if (ref.value == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string array: " + ref.name);
    }
    return evalStrSubscriptCore(ref.value, s.subscript);
  }

  private BStr evalStrSubscriptCore(EvalState.StrVar strVar, StrSubscript subscript) {
    final int indicesCount = subscript.indices().size();
    final int ptr = this.indexStackPtr;
    if (indexStackPtr + indicesCount > indexStack.length) {
      throw codedException(ReportCode.OUT_OF_MEMORY, "Expression too deeply nested");
    }
    this.indexStackPtr += indicesCount;
    try {
      for (int i = 0; i < indicesCount; i++) {
        indexStack[ptr + i] = (int) evalNum(subscript.indices().get(i));
      }
      int sliceStart = -1;
      int sliceEnd = -1;
      final boolean hasSlice = subscript.slice() != null;
      if (hasSlice) {
        if (subscript.slice().start() != null) {
          sliceStart = (int) evalNum(subscript.slice().start());
        }
        if (subscript.slice().end() != null) {
          sliceEnd = (int) evalNum(subscript.slice().end());
        }
      }

      if (strVar
          instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
        final int n = arrayDimensions.length;
        int byteIndex = -1;
        int count = indicesCount;
        if (count == n + 1) {
          byteIndex = indexStack[ptr + n];
          count--;
        }
        final int arrayIdx = calculateArrayIndex(arrayDimensions, indexStack, ptr, count);

        final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, stringLength);
        if (bounds == null) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
        }
        final int offset = arrayIdx * stringLength + (bounds.start() - 1);
        return BStr.fromBytes(data, offset, bounds.length());
      }
      if (strVar instanceof EvalState.StrVar.Scalar(BStr scalar)) {
        int byteIndex = -1;
        int count = indicesCount;
        if (count == 1 && !hasSlice) {
          byteIndex = indexStack[ptr];
          count--;
        }
        if (count > 0) {
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        final var bounds = SliceBounds.resolve(byteIndex, sliceStart, sliceEnd, scalar.length());
        if (bounds == null) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
        }
        return scalar.slice(bounds.start(), bounds.end());
      }
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string variable");
    } finally {
      this.indexStackPtr = ptr;
    }
  }

  private BStr evalStrAnd(StrExpr.StrAnd a) {
    // str AND n = str if n != 0, "" if n = 0
    final var left = evalStr(a.left());
    final double right = evalNum(a.right());
    return right != 0.0 ? left : BStr.EMPTY;
  }

  private BStr evalStrFuncCall(StrExpr.StrFuncCall call) {
    final var args = call.args();
    return switch (call.kind()) {
      case CHR_STR -> {
        final int code = (int) argNum(args, 0);
        if (code < 0 || code > 255) {
          throw codedException(
              ReportCode.INTEGER_OUT_OF_RANGE, "CHR$ argument out of range (0-255); use UCHR$");
        }
        yield BStr.fromByte(code);
      }
      case INKEY_STR -> input.inkey();
      case SCREEN_STR, USCREEN_STR -> {
        final int row = (int) Math.round(argNum(args, 0));
        final int col = (int) Math.round(argNum(args, 1));
        if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
          throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
        }
        final int cp = screen.getScreenCodepoint(row, col);
        if (call.kind() == StrFuncKind.SCREEN_STR) {
          yield cp >= 0 && cp <= 127 ? BStr.fromByte(cp) : BStr.EMPTY;
        } else {
          yield cp < 0 || !Character.isValidCodePoint(cp)
              ? BStr.EMPTY
              : BStr.fromJavaString(new String(Character.toChars(cp)));
        }
      }
      case STR_STR -> BStr.fromJavaString(formatNum(argNum(args, 0)));
      case TL_STR -> {
        final var s = argStr(args, 0);
        yield s.isEmpty() ? BStr.EMPTY : s.slice(2, s.length());
      }
      case UCHR_STR -> {
        final int code = (int) argNum(args, 0);
        if (code < 0 || !Character.isValidCodePoint(code)) {
          throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCHR$ argument out of range");
        }
        yield BStr.fromJavaString(new String(Character.toChars(code)));
      }
      case UINKEY_STR -> input.uinkey();
      case UTL_STR -> {
        final var s = argStr(args, 0);
        yield s.isEmpty() ? BStr.EMPTY : s.slice(s.nextCodepointStart(0) + 1, s.length());
      }
      case VAL_STR -> evaluateStringExpression(argStr(args, 0).toJavaString().trim());
    };
  }

  private BStr evalFnStrCall(StrExpr.FnStrCall call) {
    return (BStr) evaluateFnCall(call.name(), call.args());
  }

  private BStr argStr(List<Expr> args, int i) {
    return evalStr((StrExpr) args.get(i));
  }

  // ===== Shared helpers =====

  public int calculateArrayIndex(int[] dimensions, int[] indices, int offset, int indicesCount) {
    if (dimensions == null || indices == null) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    final int n = dimensions.length;
    if (indicesCount != n || n < 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    if (offset < 0 || offset + indicesCount > indices.length) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    int idx = 0;
    int m = 1;
    for (int i = n - 1; i >= 0; i--) {
      final int sz = dimensions[i];
      final int v = indices[offset + i];
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  private Object evaluateFnCall(String name, List<Expr> callArgs) {
    if (!state.hasFn(name)) {
      throw codedException(ReportCode.FN_WITHOUT_DEF, "FN without DEF");
    }
    final var def = state.fn(name);
    if (callArgs.size() != def.params().size()) {
      throw codedException(
          ReportCode.PARAMETER_ERROR,
          "Parameter error: expected "
              + def.params().size()
              + " arguments, got "
              + callArgs.size());
    }

    // Evaluate arguments in the current context
    final var argValues = new ArrayList<>();
    for (int i = 0; i < callArgs.size(); i++) {
      final var argExpr = callArgs.get(i);
      final String paramName = def.params().get(i);
      if (paramName.endsWith("$")) {
        if (!(argExpr instanceof StrExpr strArg)) {
          throw codedException(
              ReportCode.PARAMETER_ERROR,
              "Type mismatch: expected string for parameter " + paramName);
        }
        argValues.add(evalStr(strArg));
      } else {
        if (!(argExpr instanceof NumExpr numArg)) {
          throw codedException(
              ReportCode.PARAMETER_ERROR,
              "Type mismatch: expected number for parameter " + paramName);
        }
        argValues.add(evalNum(numArg));
      }
    }

    // Shadow variables
    final var oldNums = new HashMap<String, Double>();
    final var oldStrs = new HashMap<String, EvalState.StrVar>();
    for (int i = 0; i < def.params().size(); i++) {
      final String paramName = def.params().get(i);
      final var val = argValues.get(i);
      if (paramName.endsWith("$")) {
        oldStrs.put(paramName, state.hasStrVar(paramName) ? state.strVar(paramName) : null);
        state.setStrVar(paramName, new EvalState.StrVar.Scalar((BStr) val));
      } else {
        final var ref = state.getNumVarRef(paramName);
        oldNums.put(paramName, (ref != null && ref.initialised) ? ref.value : null);
        state.setNumVar(paramName, (Double) val);
      }
    }

    try {
      if (name.endsWith("$")) {
        return evalStr((StrExpr) def.body());
      } else {
        return evalNum((NumExpr) def.body());
      }
    } catch (StackOverflowError e) {
      // Recursive DEF FN: matches ZX Spectrum report "4 Out of memory".
      throw codedException(ReportCode.OUT_OF_MEMORY, "Out of memory (recursive FN)");
    } finally {
      // Restore variables
      for (var entry : oldNums.entrySet()) {
        final String p = entry.getKey();
        final var oldVal = entry.getValue();
        if (oldVal != null) {
          state.setNumVar(p, oldVal);
        } else {
          state.removeNumVar(p);
        }
      }
      for (var entry : oldStrs.entrySet()) {
        final String p = entry.getKey();
        final var oldVal = entry.getValue();
        if (oldVal != null) {
          state.setStrVar(p, oldVal);
        } else {
          state.removeStrVar(p);
        }
      }
    }
  }

  /**
   * Evaluates a string as a numeric expression. Used by VAL and numeric INPUT. Per Sinclair ZX
   * BASIC, this parses and evaluates the full expression, fresh every call - see the
   * IMPROVEMENTS.md note "VAL / VAL$ / INPUT parse at runtime - intentional" for why this must not
   * be memoized on the AST node the way literals are.
   *
   * @param exprStr the expression string to evaluate
   * @return the numeric result
   * @throws ReportException if the expression is invalid
   */
  public double evaluateNumericExpression(String exprStr) {
    if (exprStr.isEmpty()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Empty expression");
    }
    final var exprCtx = parser.parseNumExpr(exprStr);
    return evalNum(AstLowering.lowerNum(exprCtx, state.currentLineLabel()));
  }

  /**
   * Evaluates a string as a string expression. Used by VAL$.
   *
   * @param exprStr the expression string to evaluate
   * @return the string result
   * @throws ReportException if the expression is invalid
   */
  public BStr evaluateStringExpression(String exprStr) {
    if (exprStr.isEmpty()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Empty expression");
    }
    final var exprCtx = parser.parseStrExpr(exprStr);
    return evalStr(AstLowering.lowerStr(exprCtx, state.currentLineLabel()));
  }

  private static final double ULP0 = 1e-39;

  // DecimalFormat is not thread-safe, so cache per-thread instances for the hot PRINT path.
  private static final ThreadLocal<DecimalFormat> SCI_FORMAT =
      ThreadLocal.withInitial(() -> new DecimalFormat("0.########E0"));
  private static final ThreadLocal<DecimalFormat> DEC_FORMAT =
      ThreadLocal.withInitial(() -> new DecimalFormat("0.########"));

  /** Formats a number with up to 8 decimal digits, scientific notation for extreme values. */
  public static String formatNum(double d) {
    if (Math.abs(d) < ULP0) {
      return "0";
    }
    if (Double.isNaN(d)) {
      return "NaN";
    }
    if (Double.isInfinite(d)) {
      return d > 0.0 ? "Infinity" : "-Infinity";
    }
    final double abs = Math.abs(d);
    if (abs < 1e-5 || abs >= 1e13) {
      // Scientific notation for extreme values
      final var df = SCI_FORMAT.get();
      String result = df.format(d);
      // Add + sign for positive exponents (e.g., 1E15 -> 1E+15)
      final int ePos = result.indexOf('E');
      if (ePos >= 0 && ePos + 1 < result.length() && result.charAt(ePos + 1) != '-') {
        result = result.substring(0, ePos + 1) + "+" + result.substring(ePos + 1);
      }
      return result;
    } else {
      // Normal decimal notation with up to 8 decimal places
      final var df = DEC_FORMAT.get();
      return df.format(d);
    }
  }

  private double requireFinite(double d) {
    if (!Double.isFinite(d)) {
      throw codedException(ReportCode.NUMBER_TOO_BIG, "Arithmetic overflow");
    }
    return d;
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), state.currentStatementIndex(), msg);
  }
}
