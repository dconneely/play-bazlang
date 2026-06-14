package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.BazLangDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressionEvaluator extends BazLangBaseVisitor<Void> {
  private final EvalState state;
  private final BazLangDisplay display;
  private final AntlrParser parser;

  final int[] indexStack = new int[256];
  int indexStackPtr = 0;

  public ExpressionEvaluator(EvalState state, BazLangDisplay display, AntlrParser parser) {
    this.state = state;
    this.display = display;
    this.parser = parser;
  }

  public BazLangDisplay display() {
    return display;
  }

  private double numResult;
  private BStr strResult;

  public double evalNum(NumExprContext ctx) {
    visit(ctx);
    return numResult;
  }

  public BStr evalStr(StrExprContext ctx) {
    visit(ctx);
    return strResult;
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
  public Void visitNumLiteralExpr(NumLiteralExprContext ctx) {
    numResult = ctx.cachedNum;
    return null;
  }

  @Override
  public Void visitBinLiteralExpr(BinLiteralExprContext ctx) {
    // Strip the BIN prefix and any spaces, then parse as binary integer.
    String digits = ctx.BIN_LITERAL().getText().substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw codedException(ReportCode.NUMBER_TOO_BIG, "Binary literal exceeds 64 digits");
    }
    numResult = new java.math.BigInteger(digits, 2).doubleValue();
    return null;
  }

  @Override
  public Void visitNumVarExpr(NumVarExprContext ctx) {
    EvalState.NumVarRef ref = (EvalState.NumVarRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddNumVar(ctx.NUM_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (!ref.initialized) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + ref.name);
    }
    numResult = ref.value;
    return null;
  }

  @Override
  public Void visitNumArrayExpr(NumArrayExprContext ctx) {
    EvalState.NumArrayRef ref = (EvalState.NumArrayRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddNumArray(ctx.NUM_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (ref.array == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + ref.name);
    }
    EvalState.NumArray na = ref.array;
    int count = ctx.numExpr().size();
    int ptr = this.indexStackPtr;
    this.indexStackPtr += count;
    try {
      for (int i = 0; i < count; i++) {
        indexStack[ptr + i] = (int) evalNum(ctx.numExpr(i));
      }
      int idx = calculateArrayIndex(na.dimensions(), indexStack, ptr, count);
      numResult = na.data()[idx];
    } finally {
      this.indexStackPtr = ptr;
    }
    return null;
  }

  @Override
  public Void visitNumParenExpr(NumParenExprContext ctx) {
    numResult = evalNum(ctx.numExpr());
    return null;
  }

  @Override
  public Void visitNumFuncCallExpr(NumFuncCallExprContext ctx) {
    visit(ctx.numFunc());
    return null;
  }

  @Override
  public Void visitFnNumCallExpr(FnNumCallExprContext ctx) {
    String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    List<ExpressionContext> args = ctx.args != null ? ctx.args : List.of();
    evaluateFnCall(name, args);
    return null;
  }

  @Override
  public Void visitNumPowerExpr(NumPowerExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    if (l < 0.0 && r != Math.floor(r)) {
      throw codedException(ReportCode.INVALID_ARGUMENT, "Negative base with non-integer exponent");
    }
    numResult = requireFinite(Math.pow(l, r));
    return null;
  }

  @Override
  public Void visitNumUnaryMinusExpr(NumUnaryMinusExprContext ctx) {
    numResult = -evalNum(ctx.numExpr());
    return null;
  }

  @Override
  public Void visitNumMulDivExpr(NumMulDivExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    if (op.equals("*")) {
      numResult = requireFinite(l * r);
      return null;
    } else {
      if (r == 0.0) {
        throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
      }
      numResult = l / r;
      return null;
    }
  }

  @Override
  public Void visitNumAddSubExpr(NumAddSubExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    numResult = requireFinite(op.equals("+") ? l + r : l - r);
    return null;
  }

  @Override
  public Void visitNumCompExpr(NumCompExprContext ctx) {
    double l = evalNum(ctx.numExpr(0));
    double r = evalNum(ctx.numExpr(1));
    String op = ctx.getChild(1).getText();
    numResult =
        switch (op) {
          case "=" -> l == r ? 1.0 : 0.0;
          case "<>" -> l != r ? 1.0 : 0.0;
          case "<" -> l < r ? 1.0 : 0.0;
          case "<=" -> l <= r ? 1.0 : 0.0;
          case ">" -> l > r ? 1.0 : 0.0;
          case ">=" -> l >= r ? 1.0 : 0.0;
          default -> 0.0;
        };
    return null;
  }

  @Override
  public Void visitStrCompExpr(StrCompExprContext ctx) {
    BStr l = evalStr(ctx.strExpr(0));
    BStr r = evalStr(ctx.strExpr(1));
    String op = ctx.getChild(1).getText();
    numResult =
        switch (op) {
          case "=" -> l.equals(r) ? 1.0 : 0.0;
          case "<>" -> !l.equals(r) ? 1.0 : 0.0;
          case "<" -> l.compareTo(r) < 0 ? 1.0 : 0.0;
          case "<=" -> l.compareTo(r) <= 0 ? 1.0 : 0.0;
          case ">" -> l.compareTo(r) > 0 ? 1.0 : 0.0;
          case ">=" -> l.compareTo(r) >= 0 ? 1.0 : 0.0;
          default -> 0.0;
        };
    return null;
  }

  @Override
  public Void visitNumNotExpr(NumNotExprContext ctx) {
    numResult = evalNum(ctx.numExpr()) == 0.0 ? 1.0 : 0.0;
    return null;
  }

  @Override
  public Void visitNumAndExpr(NumAndExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A AND B = A if B ≠ 0, 0 if B = 0
    numResult = right != 0.0 ? left : 0.0;
    return null;
  }

  @Override
  public Void visitNumOrExpr(NumOrExprContext ctx) {
    double left = evalNum(ctx.numExpr(0));
    double right = evalNum(ctx.numExpr(1));
    // A OR B = 1 if B ≠ 0, A if B = 0
    numResult = right != 0.0 ? 1.0 : left;
    return null;
  }

  // Numeric function visitors

  @Override
  @SuppressWarnings(
      "PMD.NcssCount") // Visitor method: one branch per grammar production is expected
  public Void visitNumFunc(NumFuncContext ctx) {
    if (ctx.ABS() != null) {
      numResult = Math.abs(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.ACS() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ACS requires argument in [-1, 1]");
      }
      numResult = Math.acos(arg);
      return null;
    }
    if (ctx.ASN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ASN requires argument in [-1, 1]");
      }
      numResult = Math.asin(arg);
      return null;
    }
    if (ctx.ATN() != null) {
      numResult = Math.atan(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.CODE() != null) {
      BStr s = evalStrAtom(ctx.strAtom());
      if (s.isEmpty()) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "CODE of empty string");
      }
      numResult = (double) s.byteAt(0);
      return null;
    }
    if (ctx.COS() != null) {
      numResult = Math.cos(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.EXP() != null) {
      numResult = requireFinite(Math.exp(evalNumAtom(ctx.numAtom())));
      return null;
    }
    if (ctx.FRAMES() != null) {
      numResult = System.currentTimeMillis() / 20.0;
      return null;
    }
    if (ctx.INT() != null) {
      numResult = Math.floor(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.LEN() != null) {
      numResult = (double) evalStrAtom(ctx.strAtom()).length();
      return null;
    }
    if (ctx.LN() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg <= 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "LN requires a positive argument");
      }
      numResult = Math.log(arg);
      return null;
    }
    if (ctx.PI() != null) {
      numResult = Math.PI;
      return null;
    }
    if (ctx.PLOTH() != null) {
      numResult = display.plotHeight();
      return null;
    }
    if (ctx.PLOTMODE() != null) {
      numResult = display.plotMode();
      return null;
    }
    if (ctx.PLOTW() != null) {
      numResult = display.plotWidth();
      return null;
    }
    if (ctx.PRINTH() != null) {
      numResult = display.printHeight();
      return null;
    }
    if (ctx.PRINTW() != null) {
      numResult = display.printWidth();
      return null;
    }
    if (ctx.RND() != null) {
      numResult = state.nextRandom();
      return null;
    }
    if (ctx.SGN() != null) {
      numResult = Math.signum(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.SIN() != null) {
      numResult = Math.sin(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.SQR() != null) {
      double arg = evalNumAtom(ctx.numAtom());
      if (arg < 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "SQR requires a non-negative argument");
      }
      numResult = Math.sqrt(arg);
      return null;
    }
    if (ctx.TAN() != null) {
      numResult = Math.tan(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.UCNEXT() != null) {
      BStr s = evalStr(ctx.strExpr());
      int pos = (int) evalNum(ctx.numExpr()); // 1-based byte position
      if (pos < 1 || pos > s.length() + 1) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCNEXT position out of range");
      }
      numResult = (double) (s.nextCodepointStart(pos - 1) + 1);
      return null; // numResult = 1-based
    }
    if (ctx.UCODE() != null) {
      BStr s = evalStrAtom(ctx.strAtom());
      if (s.isEmpty()) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "UCODE of empty string");
      }
      numResult = (double) s.firstCodepoint();
      return null;
    }
    if (ctx.VAL() != null) {
      String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
      numResult = evaluateNumericExpression(exprStr);
      return null;
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown function");
  }

  private double evalNumAtom(NumAtomContext ctx) {
    if (ctx.NUM_LITERAL() != null) {
      return ctx.cachedNum;
    }
    if (ctx.NUM_IDENTIFIER() != null) {
      if (!ctx.numExpr().isEmpty()) {
        EvalState.NumArrayRef ref = (EvalState.NumArrayRef) ctx.varRef;
        if (ref == null) {
          ref = state.getOrAddNumArray(ctx.NUM_IDENTIFIER().getText().toUpperCase());
          ctx.varRef = ref;
        }
        if (ref.array == null) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + ref.name);
        }
        int count = ctx.numExpr().size();
        int ptr = this.indexStackPtr;
        this.indexStackPtr += count;
        try {
          for (int i = 0; i < count; i++) {
            indexStack[ptr + i] = (int) evalNum(ctx.numExpr(i));
          }
          int arrayIdx = calculateArrayIndex(ref.array.dimensions(), indexStack, ptr, count);
          return ref.array.data()[arrayIdx];
        } finally {
          this.indexStackPtr = ptr;
        }
      } else {
        EvalState.NumVarRef ref = (EvalState.NumVarRef) ctx.varRef;
        if (ref == null) {
          ref = state.getOrAddNumVar(ctx.NUM_IDENTIFIER().getText().toUpperCase());
          ctx.varRef = ref;
        }
        if (!ref.initialized) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + ref.name);
        }
        return ref.value;
      }
    }
    if (!ctx.numExpr().isEmpty()) {
      // Parenthesized: ( numExpr )
      return evalNum(ctx.numExpr(0));
    }
    if (ctx.numFunc() != null) {
      visit(ctx.numFunc());
      return numResult;
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid numeric atom");
  }

  // String expression visitors

  @Override
  public Void visitStrLiteralExpr(StrLiteralExprContext ctx) {
    String text = ctx.STR_LITERAL().getText();
    String value = text.substring(1, text.length() - 1).replace("\"\"", "\"");
    strResult = BStr.fromJavaString(value);
    return null;
  }

  @Override
  public Void visitStrVarExpr(StrVarExprContext ctx) {
    EvalState.StrVarRef ref = (EvalState.StrVarRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddStrVar(ctx.STR_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (ref.value == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
    }
    EvalState.StrVar var = ref.value;
    if (var instanceof EvalState.StrVar.Array ca) {
      if (ca.arrayDimensions().length == 0) {
        strResult = BStr.fromBytes(ca.data(), 0, ca.stringLength());
        return null;
      }
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    if (var instanceof EvalState.StrVar.Scalar s) {
      strResult = s.value();
      return null;
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
  }

  @Override
  public Void visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    EvalState.StrVarRef ref = (EvalState.StrVarRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddStrVar(ctx.STR_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (ref.value == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string array: " + ref.name);
    }
    strResult = evalStrSubscriptCore(ref.value, ctx.strSubscript());
    return null;
  }

  private BStr evalStrSubscript(String name, StrSubscriptContext subscript) {
    EvalState.StrVar var = state.strVar(name);
    return evalStrSubscriptCore(var, subscript);
  }

  private BStr evalStrSubscriptCore(EvalState.StrVar var, StrSubscriptContext subscript) {
    int indicesCount = subscript.indices != null ? subscript.indices.size() : 0;
    int ptr = this.indexStackPtr;
    this.indexStackPtr += indicesCount;
    try {
      if (indicesCount > 0) {
        for (int i = 0; i < indicesCount; i++) {
          indexStack[ptr + i] = (int) evalNum(subscript.indices.get(i));
        }
      }
      int sliceStart = -1;
      int sliceEnd = -1;
      boolean hasSlice = subscript.slice != null;
      if (hasSlice) {
        if (subscript.slice.start != null) {
          sliceStart = (int) evalNum(subscript.slice.start);
        }
        if (subscript.slice.end != null) {
          sliceEnd = (int) evalNum(subscript.slice.end);
        }
      }

      if (var instanceof EvalState.StrVar.Array ca) {
        int n = ca.arrayDimensions().length;
        int byteIndex = -1;
        if (indicesCount == n + 1) {
          byteIndex = indexStack[ptr + n];
          indicesCount--;
        } else if (indicesCount != n && n == 0 && indicesCount == 1) {
          byteIndex = indexStack[ptr + 0];
          indicesCount--;
        }
        int arrayIdx = calculateArrayIndex(ca.arrayDimensions(), indexStack, ptr, indicesCount);

        int st = (byteIndex != -1 ? byteIndex : 1) + (sliceStart != -1 ? sliceStart - 1 : 0);
        int en =
            (byteIndex != -1 ? byteIndex : 1)
                + (sliceEnd != -1 ? sliceEnd - 1 : (byteIndex != -1 ? 0 : ca.stringLength() - 1));
        if (st < 1 || en > ca.stringLength() || st > en + 1) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
        }
        int offset = arrayIdx * ca.stringLength() + (st - 1);
        int length = en - st + 1;
        return BStr.fromBytes(ca.data(), offset, length);
      }
      if (var instanceof EvalState.StrVar.Scalar scalar) {
        BStr s = scalar.value();
        int byteIndex = -1;
        if (indicesCount == 1 && !hasSlice) {
          byteIndex = indexStack[ptr + 0];
          indicesCount--;
        }
        if (indicesCount > 0) {
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        int st = (byteIndex != -1 ? byteIndex : 1) + (sliceStart != -1 ? sliceStart - 1 : 0);
        int en =
            (byteIndex != -1 ? byteIndex : 1)
                + (sliceEnd != -1 ? sliceEnd - 1 : (byteIndex != -1 ? 0 : s.length() - 1));
        if (st < 1 || en > s.length() || st > en + 1) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
        }
        return s.slice(st, en);
      }
      throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string variable");
    } finally {
      this.indexStackPtr = ptr;
    }
  }

  @Override
  public Void visitStrParenExpr(StrParenExprContext ctx) {
    strResult = evalStr(ctx.strExpr());
    return null;
  }

  @Override
  public Void visitStrConcatExpr(StrConcatExprContext ctx) {
    strResult = evalStr(ctx.strExpr(0)).concat(evalStr(ctx.strExpr(1)));
    return null;
  }

  @Override
  public Void visitStrAndExpr(StrAndExprContext ctx) {
    // str AND n = str if n ≠ 0, "" if n = 0
    BStr left = evalStr(ctx.strExpr());
    double right = evalNum(ctx.numExpr());
    strResult = right != 0.0 ? left : BStr.EMPTY;
    return null;
  }

  @Override
  public Void visitStrFuncCallExpr(StrFuncCallExprContext ctx) {
    visit(ctx.strFunc());
    return null;
  }

  @Override
  public Void visitFnStrCallExpr(FnStrCallExprContext ctx) {
    String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    List<ExpressionContext> args = ctx.args != null ? ctx.args : List.of();
    evaluateFnCall(name, args);
    return null;
  }

  @Override
  public Void visitStrFunc(StrFuncContext ctx) {
    if (ctx.CHR_STR() != null) {
      int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || code > 255) {
        throw codedException(
            ReportCode.INTEGER_OUT_OF_RANGE, "CHR$ argument out of range (0-255); use UCHR$");
      }
      strResult = BStr.fromByte(code);
      return null;
    }
    if (ctx.INKEY_STR() != null) {
      strResult = BStr.fromJavaString(display.inkey());
      return null;
    }
    if (ctx.STR_STR() != null) {
      strResult = BStr.fromJavaString(formatNum(evalNumAtom(ctx.numAtom())));
      return null;
    }
    if (ctx.UCHR_STR() != null) {
      int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || !Character.isValidCodePoint(code)) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCHR$ argument out of range");
      }
      strResult = BStr.fromJavaString(new String(Character.toChars(code)));
      return null;
    }
    if (ctx.UINKEY_STR() != null) {
      strResult = BStr.fromJavaString(display.uinkey());
      return null;
    }
    if (ctx.VAL_STR() != null) {
      String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
      strResult = evaluateStringExpression(exprStr);
      return null;
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string function");
  }

  private BStr evalStrAtom(StrAtomContext ctx) {
    if (ctx.STR_LITERAL() != null) {
      return (BStr) ctx.cachedStr;
    }
    if (ctx.strSubscript() != null) {
      return evalStrSubscript(ctx.STR_IDENTIFIER().getText().toUpperCase(), ctx.strSubscript());
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
      visit(ctx.strFunc());
      return strResult;
    }
    throw codedException(ReportCode.NONSENSE_IN_BASIC, "Invalid string atom");
  }

  // ===== Assignment Helpers =====

  public int calculateArrayIndex(int[] dimensions, int[] indices, int offset, int indicesCount) {
    int n = dimensions.length;
    if (indicesCount != n) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    int idx = 0;
    int m = 1;
    for (int i = n - 1; i >= 0; i--) {
      int sz = dimensions[i];
      int v = indices[offset + i];
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  private void evaluateFnCall(String name, List<ExpressionContext> callArgs) {
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
        EvalState.NumVarRef ref = state.getNumVarRef(paramName);
        oldNums.put(paramName, (ref != null && ref.initialized) ? ref.value : null);
        state.setNumVar(paramName, (Double) val);
      }
    }

    try {
      if (name.endsWith("$")) {
        evalStr(def.body().strExpr());
      } else {
        evalNum(def.body().numExpr());
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
    new AstAnnotator(state.currentLineLabel()).visit(exprCtx);
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
    new AstAnnotator(state.currentLineLabel()).visit(exprCtx);
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
