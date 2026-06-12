package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressionEvaluator extends BazLangBaseVisitor<Object> {
  private final EvalState state;
  private final BazLangDisplay display;
  private final AntlrParser parser;

  public ExpressionEvaluator(EvalState state, BazLangDisplay display, AntlrParser parser) {
    this.state = state;
    this.display = display;
    this.parser = parser;
  }

  public BazLangDisplay display() {
    return display;
  }

  public double evalNum(NumExprContext ctx) {
    return ((Number) visit(ctx)).doubleValue();
  }

  public BStr evalStr(StrExprContext ctx) {
    return (BStr) visit(ctx);
  }

  public String evalPrintExpr(ExpressionContext ctx) {
    if (ctx.numExpr() != null) {
      return formatNum(evalNum(ctx.numExpr()));
    } else {
      return evalStr(ctx.strExpr()).toJavaString();
    }
  }

  // Numeric expression visitors

  @Override
  public Double visitNumLiteralExpr(NumLiteralExprContext ctx) {
    return Double.parseDouble(ctx.NUM_LITERAL().getText());
  }

  @Override
  public Double visitBinLiteralExpr(BinLiteralExprContext ctx) {
    // Strip the BIN prefix and any spaces, then parse as binary integer.
    String digits = ctx.BIN_LITERAL().getText().substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw codedException(ReportCode.NUMBER_TOO_BIG, "Binary literal exceeds 64 digits");
    }
    return new java.math.BigInteger(digits, 2).doubleValue();
  }

  @Override
  public Double visitNumVarExpr(NumVarExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.hasNumVar(name)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
    return state.numVar(name);
  }

  @Override
  public Double visitNumArrayExpr(NumArrayExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    if (!state.hasNumArray(name)) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
    }
    EvalState.NumArray na = state.numArray(name);
    int count = ctx.numExpr().size();
    int[] indices = new int[count];
    for (int i = 0; i < count; i++) {
      indices[i] = (int) evalNum(ctx.numExpr(i));
    }
    int idx = calculateArrayIndex(na.dimensions(), indices, count);
    return na.data()[idx];
  }

  @Override
  public Double visitNumParenExpr(NumParenExprContext ctx) {
    return evalNum(ctx.numExpr());
  }

  @Override
  public Double visitNumFuncCallExpr(NumFuncCallExprContext ctx) {
    return (Double) visit(ctx.numFunc());
  }

  @Override
  public Double visitFnNumCallExpr(FnNumCallExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    List<ExpressionContext> args = ctx.args != null ? ctx.args : List.of();
    return (Double) evaluateFnCall(name, args);
  }

  @Override
  public Double visitNumPowerExpr(NumPowerExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    if (l < 0.0 && r != Math.floor(r)) {
      throw codedException(ReportCode.INVALID_ARGUMENT, "Negative base with non-integer exponent");
    }
    return requireFinite(Math.pow(l, r));
  }

  @Override
  public Double visitNumUnaryMinusExpr(NumUnaryMinusExprContext ctx) {
    return -evalNum(ctx.numExpr());
  }

  @Override
  public Double visitNumMulDivExpr(NumMulDivExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    if (op.equals("*")) {
      return requireFinite(l * r);
    } else {
      if (r == 0.0) {
        throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
      }
      return l / r;
    }
  }

  @Override
  public Double visitNumAddSubExpr(NumAddSubExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    return requireFinite(op.equals("+") ? l + r : l - r);
  }

  @Override
  public Double visitNumCompExpr(NumCompExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    return switch (op) {
      case "=" -> l == r ? 1.0 : 0.0;
      case "<>" -> l != r ? 1.0 : 0.0;
      case "<" -> l < r ? 1.0 : 0.0;
      case "<=" -> l <= r ? 1.0 : 0.0;
      case ">" -> l > r ? 1.0 : 0.0;
      case ">=" -> l >= r ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  @Override
  public Double visitStrCompExpr(StrCompExprContext ctx) {
    BStr l = evalStr(ctx.strExpr(0));
    BStr r = evalStr(ctx.strExpr(1));
    String op = ctx.getChild(1).getText();
    return switch (op) {
      case "=" -> l.equals(r) ? 1.0 : 0.0;
      case "<>" -> !l.equals(r) ? 1.0 : 0.0;
      case "<" -> l.compareTo(r) < 0 ? 1.0 : 0.0;
      case "<=" -> l.compareTo(r) <= 0 ? 1.0 : 0.0;
      case ">" -> l.compareTo(r) > 0 ? 1.0 : 0.0;
      case ">=" -> l.compareTo(r) >= 0 ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  @Override
  public Double visitNumNotExpr(NumNotExprContext ctx) {
    return evalNum(ctx.numExpr()) == 0.0 ? 1.0 : 0.0;
  }

  @Override
  public Double visitNumAndExpr(NumAndExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A AND B = A if B ≠ 0, 0 if B = 0
    return right != 0.0 ? left : 0.0;
  }

  @Override
  public Double visitNumOrExpr(NumOrExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A OR B = 1 if B ≠ 0, A if B = 0
    return right != 0.0 ? 1.0 : left;
  }

  // Numeric function visitors

  @Override
  @SuppressWarnings(
      "PMD.NcssCount") // Visitor method: one branch per grammar production is expected
  public Double visitNumFunc(NumFuncContext ctx) {
    if (ctx.ABS() != null) {
      return Math.abs(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.ACS() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ACS requires argument in [-1, 1]");
      }
      return Math.acos(arg);
    }
    if (ctx.ASN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ASN requires argument in [-1, 1]");
      }
      return Math.asin(arg);
    }
    if (ctx.ATN() != null) {
      return Math.atan(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.CODE() != null) {
      BStr s = evalStrAtom(ctx.strAtom());
      if (s.isEmpty()) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "CODE of empty string");
      }
      return (double) s.byteAt(0);
    }
    if (ctx.CODEPOINT() != null) {
      BStr s = evalStrAtom(ctx.strAtom());
      if (s.isEmpty()) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "CODEPOINT of empty string");
      }
      return (double) s.firstCodepoint();
    }
    if (ctx.COS() != null) {
      return Math.cos(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.EXP() != null) {
      return requireFinite(Math.exp(evalNumAtom(ctx.numAtom())));
    }
    if (ctx.INT() != null) {
      return Math.floor(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.LEN() != null) {
      return (double) evalStrAtom(ctx.strAtom()).length();
    }
    if (ctx.LN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg <= 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "LN requires a positive argument");
      }
      return Math.log(arg);
    }
    if (ctx.NEXTCP() != null) {
      BStr s = evalStr(ctx.strExpr());
      int pos = (int) evalNum(ctx.numExpr()); // 1-based byte position
      if (pos < 1 || pos > s.length() + 1) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "NEXTCP position out of range");
      }
      return (double) (s.nextCodepointStart(pos - 1) + 1); // return 1-based
    }

    if (ctx.PI() != null) {
      return Math.PI;
    }
    if (ctx.RND() != null) {
      return state.nextRandom();
    }
    if (ctx.SGN() != null) {
      return Math.signum(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.SIN() != null) {
      return Math.sin(evalNumAtom(ctx.numAtom()));
    }
    if (ctx.SQR() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg < 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "SQR requires a non-negative argument");
      }
      return Math.sqrt(arg);
    }
    if (ctx.TAN() != null) {
      return Math.tan(evalNumAtom(ctx.numAtom()));
    }

    if (ctx.VAL() != null) {
      String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
      return evaluateNumericExpression(exprStr);
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown function");
  }

  private double evalNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      return Double.parseDouble(ctx.NUM_LITERAL().getText());
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      // Either simple variable or array subscript
      String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
      if (!ctx.numExpr().isEmpty()) {
        // Array subscript: NUM_IDENTIFIER ( numExpr, ... )
        if (!state.hasNumArray(name)) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + name);
        }
        EvalState.NumArray na = state.numArray(name);
        int count = ctx.numExpr().size();
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
          indices[i] = (int) evalNum(ctx.numExpr(i));
        }
        return na.data()[calculateArrayIndex(na.dimensions(), indices, count)];
      } else {
        // Simple variable
        if (!state.hasNumVar(name)) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
        }
        return state.numVar(name);
      }
    }
    if (!ctx.numExpr().isEmpty()) {
      // Parenthesized: ( numExpr )
      return evalNum(ctx.numExpr(0));
    }
    if (ctx.numFunc() != null) {
      return (Double) visit(ctx.numFunc());
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid numeric atom");
  }

  // String expression visitors

  @Override
  public BStr visitStrLiteralExpr(StrLiteralExprContext ctx) {
    String text = ctx.STR_LITERAL().getText();
    String value = text.substring(1, text.length() - 1).replace("\"\"", "\"");
    return BStr.fromJavaString(value);
  }

  @Override
  public BStr visitStrVarExpr(StrVarExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    EvalState.StrVar var = state.strVar(name);
    if (var instanceof EvalState.StrVar.Array ca) {
      if (ca.arrayDimensions().length == 0) {
        return BStr.fromBytes(ca.data(), 0, ca.stringLength());
      }
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    if (var instanceof EvalState.StrVar.Scalar s) {
      return s.value();
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
  }

  @Override
  public BStr visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    var subscript = ctx.strSubscript();
    return evalStrSubscript(name, subscript);
  }

  private BStr evalStrSubscript(String name, StrSubscriptContext subscript) {
    ParsedSubscript parsed = parseStrSubscript(subscript);
    int[] indices = parsed.indices();
    int sliceStart = parsed.sliceStart();
    int sliceEnd = parsed.sliceEnd();
    boolean hasSlice = parsed.hasSlice();
    // Now evaluate the subscript based on variable type
    EvalState.StrVar var = state.strVar(name);
    if (var instanceof EvalState.StrVar.Array ca) {
      int n = ca.arrayDimensions().length;
      int byteIndex = -1;
      int indicesCount = indices.length;
      // Handle char index (extra index beyond array dimensions)
      if (indicesCount == n + 1) {
        byteIndex = indices[n];
        indicesCount--;
      } else if (indicesCount != n && n == 0 && indicesCount == 1) {
        byteIndex = indices[0];
        indicesCount--;
      }
      int arrayIdx = calculateArrayIndex(ca.arrayDimensions(), indices, indicesCount);
      int[] bounds = calculateSliceBounds(ca.stringLength(), byteIndex, sliceStart, sliceEnd);
      int offset = arrayIdx * ca.stringLength() + (bounds[0] - 1);
      int length = bounds[1] - bounds[0] + 1;
      return BStr.fromBytes(ca.data(), offset, length);
    }
    if (var instanceof EvalState.StrVar.Scalar scalar) {
      BStr s = scalar.value();
      int byteIndex = -1;
      int indicesCount = indices.length;
      if (indicesCount == 1 && !hasSlice) {
        byteIndex = indices[0];
        indicesCount--;
      }
      if (indicesCount > 0) {
        throw codedException(
            ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
      }
      int[] bounds = calculateSliceBounds(s.length(), byteIndex, sliceStart, sliceEnd);
      return s.slice(bounds[0], bounds[1]);
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
  }

  @Override
  public BStr visitStrParenExpr(StrParenExprContext ctx) {
    return evalStr(ctx.strExpr());
  }

  @Override
  public BStr visitStrConcatExpr(StrConcatExprContext ctx) {
    return evalStr(ctx.strExpr(0)).concat(evalStr(ctx.strExpr(1)));
  }

  @Override
  public BStr visitStrAndExpr(StrAndExprContext ctx) {
    // str AND n = str if n ≠ 0, "" if n = 0
    BStr left = evalStr(ctx.strExpr());
    double right = evalNum(ctx.numExpr());
    return right != 0.0 ? left : BStr.EMPTY;
  }

  @Override
  public BStr visitStrFuncCallExpr(StrFuncCallExprContext ctx) {
    return (BStr) visit(ctx.strFunc());
  }

  @Override
  public BStr visitFnStrCallExpr(FnStrCallExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    List<ExpressionContext> args = ctx.args != null ? ctx.args : List.of();
    return (BStr) evaluateFnCall(name, args);
  }

  @Override
  public BStr visitStrFunc(StrFuncContext ctx) {
    if (ctx.CHR_STR() != null) {
      int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || code > 255) {
        throw codedException(
            ReportCode.INTEGER_OUT_OF_RANGE, "CHR$ argument out of range (0-255); use CODEPOINT$");
      }
      return BStr.fromByte(code);
    }
    if (ctx.CODEPOINT_STR() != null) {
      int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || !Character.isValidCodePoint(code)) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "CODEPOINT$ argument out of range");
      }
      return BStr.fromJavaString(new String(Character.toChars(code)));
    }
    if (ctx.INKEY_STR() != null) {
      return BStr.fromJavaString(display.inkey());
    }
    if (ctx.STR_STR() != null) {
      return BStr.fromJavaString(formatNum(evalNumAtom(ctx.numAtom())));
    }
    if (ctx.VAL_STR() != null) {
      String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
      return evaluateStringExpression(exprStr);
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string function");
  }

  private BStr evalStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      String text = ctx.STR_LITERAL().getText();
      return BStr.fromJavaString(text.substring(1, text.length() - 1));
    }
    if (ctx.strSubscript() != null) {
      String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
      return evalStrSubscript(name, ctx.strSubscript());
    }
    if (ctx.STR_IDENTIFIER() != null) {
      String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
      EvalState.StrVar var = state.strVar(name);
      if (var instanceof EvalState.StrVar.Array ca) {
        if (ca.arrayDimensions().length == 0) {
          return BStr.fromBytes(ca.data(), 0, ca.stringLength());
        }
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
      }
      if (var instanceof EvalState.StrVar.Scalar s) {
        return s.value();
      }
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + name);
    }
    if (ctx.strExpr() != null) {
      return evalStr(ctx.strExpr());
    }
    if (ctx.strFunc() != null) {
      return (BStr) visit(ctx.strFunc());
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string atom");
  }

  // ===== Assignment Helpers =====

  public record ParsedSubscript(int[] indices, int sliceStart, int sliceEnd, boolean hasSlice) {}

  public ParsedSubscript parseStrSubscript(StrSubscriptContext subscript) {
    int indicesCount = subscript.indices != null ? subscript.indices.size() : 0;
    int[] indices = new int[indicesCount];
    int sliceStart = -1;
    int sliceEnd = -1;
    boolean hasSlice = subscript.slice != null;
    if (indicesCount > 0) {
      for (int i = 0; i < indicesCount; i++) {
        indices[i] = (int) evalNum(subscript.indices.get(i));
      }
    }
    if (hasSlice) {
      if (subscript.slice.start != null) {
        sliceStart = (int) evalNum(subscript.slice.start);
      }
      if (subscript.slice.end != null) {
        sliceEnd = (int) evalNum(subscript.slice.end);
      }
    }
    return new ParsedSubscript(indices, sliceStart, sliceEnd, hasSlice);
  }

  public int calculateArrayIndex(int[] dimensions, int[] indices, int indicesCount) {
    int n = dimensions.length;
    if (indicesCount != n) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    int idx = 0;
    int m = 1;
    for (int i = n - 1; i >= 0; i--) {
      int sz = dimensions[i];
      int v = indices[i];
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  public int[] calculateSliceBounds(int len, int byteIdx, int sliceStart, int sliceEnd) {
    int st = (byteIdx != -1 ? byteIdx : 1) + (sliceStart != -1 ? sliceStart - 1 : 0);
    int en =
        (byteIdx != -1 ? byteIdx : 1)
            + (sliceEnd != -1 ? sliceEnd - 1 : (byteIdx != -1 ? 0 : len - 1));
    if (st < 1 || en > len || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    return new int[] {st, en};
  }

  private Object evaluateFnCall(String name, List<ExpressionContext> callArgs) {
    if (!state.hasFn(name)) {
      throw codedException(ReportCode.FN_WITHOUT_DEF, "FN without DEF");
    }
    EvalState.FnDefinition def = state.fn(name);
    if (callArgs.size() != def.params().size()) {
      throw codedException(
          ReportCode.PARAMETER_ERROR,
          "Parameter error: expected "
              + def.params().size()
              + " arguments, got "
              + callArgs.size());
    }

    // Evaluate arguments in the current context
    List<Object> argValues = new ArrayList<>();
    for (int i = 0; i < callArgs.size(); i++) {
      ExpressionContext argExpr = callArgs.get(i);
      String paramName = def.params().get(i);
      if (paramName.endsWith("$")) {
        if (argExpr.strExpr() == null) {
          throw codedException(
              ReportCode.PARAMETER_ERROR,
              "Type mismatch: expected string for parameter " + paramName);
        }
        argValues.add(evalStr(argExpr.strExpr()));
      } else {
        if (argExpr.numExpr() == null) {
          throw codedException(
              ReportCode.PARAMETER_ERROR,
              "Type mismatch: expected number for parameter " + paramName);
        }
        argValues.add(evalNum(argExpr.numExpr()));
      }
    }

    // Shadow variables
    Map<String, Double> oldNums = new HashMap<>();
    Map<String, EvalState.StrVar> oldStrs = new HashMap<>();
    for (int i = 0; i < def.params().size(); i++) {
      String paramName = def.params().get(i);
      Object val = argValues.get(i);
      if (paramName.endsWith("$")) {
        oldStrs.put(paramName, state.hasStrVar(paramName) ? state.strVar(paramName) : null);
        state.setStrVar(paramName, new EvalState.StrVar.Scalar((BStr) val));
      } else {
        oldNums.put(paramName, state.hasNumVar(paramName) ? state.numVar(paramName) : null);
        state.setNumVar(paramName, (Double) val);
      }
    }

    try {
      if (name.endsWith("$")) {
        return evalStr(def.body().strExpr());
      } else {
        return evalNum(def.body().numExpr());
      }
    } finally {
      // Restore variables
      for (var entry : oldNums.entrySet()) {
        String p = entry.getKey();
        Double oldVal = entry.getValue();
        if (oldVal != null) {
          state.setNumVar(p, oldVal);
        } else {
          state.removeNumVar(p);
        }
      }
      for (var entry : oldStrs.entrySet()) {
        String p = entry.getKey();
        EvalState.StrVar oldVal = entry.getValue();
        if (oldVal != null) {
          state.setStrVar(p, oldVal);
        } else {
          state.removeStrVar(p);
        }
      }
    }
  }

  /**
   * Evaluates a string as a numeric expression. Used by VAL function and INPUT for numeric
   * variables. Per ZX81 BASIC, this parses and evaluates the full expression.
   *
   * @param exprStr the expression string to evaluate
   * @return the numeric result
   * @throws ReportException if the expression is invalid
   */
  public double evaluateNumericExpression(String exprStr) {
    if (exprStr.isEmpty()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Empty expression");
    }
    NumExprContext exprCtx = parser.parseNumExpr(exprStr);
    return evalNum(exprCtx);
  }

  /**
   * Evaluates a string as a string expression. Used by VAL$ function.
   *
   * @param exprStr the expression string to evaluate
   * @return the string result
   * @throws ReportException if the expression is invalid
   */
  public BStr evaluateStringExpression(String exprStr) {
    if (exprStr.isEmpty()) {
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Empty expression");
    }
    StrExprContext exprCtx = parser.parseStrExpr(exprStr);
    return evalStr(exprCtx);
  }

  private static final double ULP0 = 1e-39;

  /** Formats a number with up to 8 decimal digits, scientific notation for extreme values. */
  private String formatNum(double d) {
    if (Math.abs(d) < ULP0) {
      return "0";
    }
    if (Double.isNaN(d)) {
      return "NaN";
    }
    if (Double.isInfinite(d)) {
      return d > 0.0 ? "Infinity" : "-Infinity";
    }
    double abs = Math.abs(d);
    if (abs < 1e-5 || abs >= 1e13) {
      // Scientific notation for extreme values
      java.text.DecimalFormat df = new java.text.DecimalFormat("0.########E0");
      String result = df.format(d);
      // Add + sign for positive exponents (e.g., 1E15 -> 1E+15)
      int ePos = result.indexOf('E');
      if (ePos >= 0 && ePos + 1 < result.length() && result.charAt(ePos + 1) != '-') {
        result = result.substring(0, ePos + 1) + "+" + result.substring(ePos + 1);
      }
      return result;
    } else {
      // Normal decimal notation with up to 8 decimal places
      java.text.DecimalFormat df = new java.text.DecimalFormat("0.########");
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
    return new ReportException(rc, state.currentLineLabel(), msg);
  }
}
