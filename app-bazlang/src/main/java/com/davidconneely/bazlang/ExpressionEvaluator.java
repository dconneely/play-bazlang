package com.davidconneely.bazlang;

import com.davidconneely.bazlang.antlr.AntlrParser;
import com.davidconneely.bazlang.antlr.BazLangBaseVisitor;
import com.davidconneely.bazlang.antlr.BazLangParser.*;
import com.davidconneely.bazlang.io.VirtualInput;
import com.davidconneely.bazlang.io.VirtualScreen;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ExpressionEvaluator extends BazLangBaseVisitor<Void> {
  private final EvalState state;
  private final VirtualScreen screen;
  private final VirtualInput input;
  private final AntlrParser parser;

  private final int[] indexStack = new int[256];
  private int indexStackPtr = 0;

  // Populated by visitor
  private double numResult;
  private BStr strResult;

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
    final String digits = ctx.BIN_LITERAL().getText().substring(3).replaceAll("[ \t]", "");
    if (digits.length() > 64) {
      throw codedException(ReportCode.NUMBER_TOO_BIG, "Binary literal exceeds 64 digits");
    }
    numResult = new BigInteger(digits, 2).doubleValue();
    return null;
  }

  @Override
  public Void visitNumVarExpr(NumVarExprContext ctx) {
    var ref = (EvalState.NumVarRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddNumVar(ctx.NUM_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (!ref.initialised) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable: " + ref.name);
    }
    numResult = ref.value;
    return null;
  }

  @Override
  public Void visitNumArrayExpr(NumArrayExprContext ctx) {
    var ref = (EvalState.NumArrayRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddNumArray(ctx.NUM_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (ref.array == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + ref.name);
    }
    final var na = ref.array;
    final int count = ctx.numExpr().size();
    final int ptr = this.indexStackPtr;
    this.indexStackPtr += count;
    try {
      for (int i = 0; i < count; i++) {
        indexStack[ptr + i] = (int) evalNum(ctx.numExpr(i));
      }
      final int idx = calculateArrayIndex(na.dimensions(), indexStack, ptr, count);
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
    final String name = ctx.NUM_IDENTIFIER().getText().toUpperCase();
    final var args = ctx.args != null ? ctx.args : List.<ExpressionContext>of();
    evaluateFnCall(name, args);
    return null;
  }

  @Override
  public Void visitNumPowerExpr(NumPowerExprContext ctx) {
    final double l = evalNum(ctx.numExpr(0));
    final double r = evalNum(ctx.numExpr(1));
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
    final double l = evalNum(ctx.numExpr(0));
    final double r = evalNum(ctx.numExpr(1));
    final String op = ctx.getChild(1).getText();
    if (op.equals("*")) {
      numResult = requireFinite(l * r);
    } else {
      if (r == 0.0) {
        throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
      }
      numResult = requireFinite(l / r);
    }
    return null;
  }

  @Override
  public Void visitNumAddSubExpr(NumAddSubExprContext ctx) {
    final double l = evalNum(ctx.numExpr(0));
    final double r = evalNum(ctx.numExpr(1));
    final String op = ctx.getChild(1).getText();
    numResult = requireFinite(op.equals("+") ? l + r : l - r);
    return null;
  }

  @Override
  public Void visitNumCompExpr(NumCompExprContext ctx) {
    final double l = evalNum(ctx.numExpr(0));
    final double r = evalNum(ctx.numExpr(1));
    final String op = ctx.getChild(1).getText();
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
    final var l = evalStr(ctx.strExpr(0));
    final var r = evalStr(ctx.strExpr(1));
    final String op = ctx.getChild(1).getText();
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
    final double left = evalNum(ctx.numExpr(0));
    final double right = evalNum(ctx.numExpr(1));
    // A AND B = A if B ≠ 0, 0 if B = 0
    numResult = right != 0.0 ? left : 0.0;
    return null;
  }

  @Override
  public Void visitNumOrExpr(NumOrExprContext ctx) {
    final double left = evalNum(ctx.numExpr(0));
    final double right = evalNum(ctx.numExpr(1));
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
      final double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ACS requires argument in [-1, 1]");
      }
      numResult = Math.acos(arg);
      return null;
    }
    if (ctx.ASN() != null) {
      final double arg = evalNumAtom(ctx.numAtom());
      if (Math.abs(arg) > 1.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "ASN requires argument in [-1, 1]");
      }
      numResult = Math.asin(arg);
      return null;
    }
    if (ctx.ATTR() != null) {
      final int row = (int) Math.round(evalNum(ctx.numExpr(0)));
      final int col = (int) Math.round(evalNum(ctx.numExpr(1)));
      if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
      }
      numResult = screen.getScreenAttributes(row, col);
      return null;
    }
    if (ctx.ATN() != null) {
      numResult = Math.atan(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.CODE() != null) {
      final var s = evalStrAtom(ctx.strAtom());
      // Sinclair ZX BASIC `PRINT CODE ""` shows `0`
      numResult = s.isEmpty() ? 0 : s.byteAt(0);
      return null;
    }
    if (ctx.COLOUR() != null) {
      final double rVal = evalNum(ctx.numExpr(0));
      final double gVal = evalNum(ctx.numExpr(1));
      final double bVal = evalNum(ctx.numExpr(2));
      final int r = (int) Math.round(rVal);
      final int g = (int) Math.round(gVal);
      final int b = (int) Math.round(bVal);
      if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
        throw codedException(
            ReportCode.INVALID_ARGUMENT, "COLOUR components must be between 0 and 255");
      }
      final int y = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
      numResult = 16_777_216.0 + y;
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
      numResult = evalStrAtom(ctx.strAtom()).length();
      return null;
    }
    if (ctx.LN() != null) {
      final double arg = evalNumAtom(ctx.numAtom());
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
      numResult = screen.plotHeight();
      return null;
    }
    if (ctx.PLOTMODE() != null) {
      numResult = screen.plotMode();
      return null;
    }
    if (ctx.PLOTW() != null) {
      numResult = screen.plotWidth();
      return null;
    }
    if (ctx.PLOTX() != null) {
      numResult = state.graphicsCursorX();
      return null;
    }
    if (ctx.PLOTY() != null) {
      numResult = state.graphicsCursorY();
      return null;
    }
    if (ctx.POINT() != null) {
      final int x = (int) Math.round(evalNum(ctx.numExpr(0)));
      final int y = (int) Math.round(evalNum(ctx.numExpr(1)));
      numResult = screen.point(x, y);
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
      final double arg = evalNumAtom(ctx.numAtom());
      if (arg < 0.0) {
        throw codedException(ReportCode.INVALID_ARGUMENT, "SQR requires non-negative argument");
      }
      numResult = Math.sqrt(arg);
      return null;
    }
    if (ctx.TAN() != null) {
      numResult = Math.tan(evalNumAtom(ctx.numAtom()));
      return null;
    }
    if (ctx.TEXTH() != null) {
      numResult = screen.printHeight();
      return null;
    }
    if (ctx.TEXTW() != null) {
      numResult = screen.printWidth();
      return null;
    }
    if (ctx.TEXTX() != null) {
      numResult = screen.currentCol();
      return null;
    }
    if (ctx.TEXTY() != null) {
      numResult = screen.currentRow();
      return null;
    }
    if (ctx.UCNEXT() != null) {
      final var s = evalStr(ctx.strExpr());
      final int pos = (int) evalNum(ctx.numExpr(0)); // 1-based byte position
      if (pos < 1 || pos > s.length() + 1) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCNEXT position out of range");
      }
      numResult = s.nextCodepointStart(pos - 1) + 1;
      return null; // numResult = 1-based
    }
    if (ctx.UCODE() != null) {
      final var s = evalStrAtom(ctx.strAtom());
      if (s.isEmpty()) {
        throw codedException(ReportCode.NONSENSE_IN_BASIC, "UCODE of empty string");
      }
      numResult = s.firstCodepoint();
      return null;
    }
    if (ctx.ULEN() != null) {
      numResult = evalStrAtom(ctx.strAtom()).codepointLength();
      return null;
    }
    if (ctx.VAL() != null) {
      final String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
      numResult = evaluateNumericExpression(exprStr);
      return null;
    }
    if (ctx.XATTR() != null) {
      final int row = (int) Math.round(evalNum(ctx.numExpr(0)));
      final int col = (int) Math.round(evalNum(ctx.numExpr(1)));
      final int select = (int) Math.round(evalNum(ctx.numExpr(2)));
      if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
      }
      if (select < 0 || select > 8) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "XATTR selector out of range [0, 8]");
      }
      numResult = screen.getXAttributes(row, col, select);
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
        var ref = (EvalState.NumArrayRef) ctx.varRef;
        if (ref == null) {
          ref = state.getOrAddNumArray(ctx.NUM_IDENTIFIER().getText().toUpperCase());
          ctx.varRef = ref;
        }
        if (ref.array == null) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined array: " + ref.name);
        }
        final int count = ctx.numExpr().size();
        final int ptr = this.indexStackPtr;
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
        var ref = (EvalState.NumVarRef) ctx.varRef;
        if (ref == null) {
          ref = state.getOrAddNumVar(ctx.NUM_IDENTIFIER().getText().toUpperCase());
          ctx.varRef = ref;
        }
        if (!ref.initialised) {
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
    final String text = ctx.STR_LITERAL().getText();
    final String value = text.substring(1, text.length() - 1).replace("\"\"", "\"");
    strResult = BStr.fromJavaString(value);
    return null;
  }

  @Override
  public Void visitStrVarExpr(StrVarExprContext ctx) {
    var ref = (EvalState.StrVarRef) ctx.varRef;
    if (ref == null) {
      ref = state.getOrAddStrVar(ctx.STR_IDENTIFIER().getText().toUpperCase());
      ctx.varRef = ref;
    }
    if (ref.value == null) {
      throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
    }
    var strVar = ref.value;
    if (strVar
        instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
      if (arrayDimensions.length == 0) {
        strResult = BStr.fromBytes(data, 0, stringLength);
        return null;
      }
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Subscript wrong");
    }
    if (strVar instanceof EvalState.StrVar.Scalar(BStr value)) {
      strResult = value;
      return null;
    }
    throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined string: " + ref.name);
  }

  @Override
  public Void visitStrSubscriptExpr(StrSubscriptExprContext ctx) {
    var ref = (EvalState.StrVarRef) ctx.varRef;
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
    final var strVar = state.strVar(name);
    return evalStrSubscriptCore(strVar, subscript);
  }

  private BStr evalStrSubscriptCore(EvalState.StrVar strVar, StrSubscriptContext subscript) {
    int indicesCount = subscript.indices != null ? subscript.indices.size() : 0;
    final int ptr = this.indexStackPtr;
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

      if (strVar
          instanceof EvalState.StrVar.Array(int[] arrayDimensions, int stringLength, byte[] data)) {
        final int n = arrayDimensions.length;
        int byteIndex = -1;
        if (indicesCount == n + 1) {
          byteIndex = indexStack[ptr + n];
          indicesCount--;
        }
        final int arrayIdx = calculateArrayIndex(arrayDimensions, indexStack, ptr, indicesCount);

        final int st = (byteIndex != -1 ? byteIndex : 1) + (sliceStart != -1 ? sliceStart - 1 : 0);
        final int en =
            (byteIndex != -1 ? byteIndex : 1)
                + (sliceEnd != -1 ? sliceEnd - 1 : (byteIndex != -1 ? 0 : stringLength - 1));
        if (st < 1 || en > stringLength || st > en + 1) {
          throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
        }
        final int offset = arrayIdx * stringLength + (st - 1);
        final int length = en - st + 1;
        return BStr.fromBytes(data, offset, length);
      }
      if (strVar instanceof EvalState.StrVar.Scalar(BStr s)) {
        int byteIndex = -1;
        if (indicesCount == 1 && !hasSlice) {
          byteIndex = indexStack[ptr];
          indicesCount--;
        }
        if (indicesCount > 0) {
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        final int st = (byteIndex != -1 ? byteIndex : 1) + (sliceStart != -1 ? sliceStart - 1 : 0);
        final int en =
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
    final var left = evalStr(ctx.strExpr());
    final double right = evalNum(ctx.numExpr());
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
    final String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
    final var args = ctx.args != null ? ctx.args : List.<ExpressionContext>of();
    evaluateFnCall(name, args);
    return null;
  }

  @Override
  public Void visitStrFunc(StrFuncContext ctx) {
    if (ctx.CHR_STR() != null) {
      final int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || code > 255) {
        throw codedException(
            ReportCode.INTEGER_OUT_OF_RANGE, "CHR$ argument out of range (0-255); use UCHR$");
      }
      strResult = BStr.fromByte(code);
      return null;
    }
    if (ctx.INKEY_STR() != null) {
      strResult = input.inkey();
      return null;
    }
    if (ctx.SCREEN_STR() != null || ctx.USCREEN_STR() != null) {
      final int row = (int) Math.round(evalNum(ctx.numExpr(0)));
      final int col = (int) Math.round(evalNum(ctx.numExpr(1)));
      if (row < 0 || row >= screen.printHeight() || col < 0 || col >= screen.printWidth()) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "Screen coordinates out of bounds");
      }
      final int cp = screen.getScreenCodepoint(row, col);
      if (ctx.SCREEN_STR() != null) {
        if (cp >= 0 && cp <= 127) {
          strResult = BStr.fromByte(cp);
        } else {
          strResult = BStr.EMPTY;
        }
      } else {
        if (cp < 0 || !Character.isValidCodePoint(cp)) {
          strResult = BStr.EMPTY;
        } else {
          strResult = BStr.fromJavaString(new String(Character.toChars(cp)));
        }
      }
      return null;
    }
    if (ctx.STR_STR() != null) {
      strResult = BStr.fromJavaString(formatNum(evalNumAtom(ctx.numAtom())));
      return null;
    }
    if (ctx.UCHR_STR() != null) {
      final int code = (int) evalNumAtom(ctx.numAtom());
      if (code < 0 || !Character.isValidCodePoint(code)) {
        throw codedException(ReportCode.INTEGER_OUT_OF_RANGE, "UCHR$ argument out of range");
      }
      strResult = BStr.fromJavaString(new String(Character.toChars(code)));
      return null;
    }
    if (ctx.UINKEY_STR() != null) {
      strResult = input.uinkey();
      return null;
    }
    if (ctx.VAL_STR() != null) {
      final String exprStr = evalStrAtom(ctx.strAtom()).toJavaString().trim();
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
      final String name = ctx.STR_IDENTIFIER().getText().toUpperCase();
      final var strVar = state.strVar(name);
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

  private void evaluateFnCall(String name, List<ExpressionContext> callArgs) {
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
        evalStr(def.body().strExpr());
      } else {
        evalNum(def.body().numExpr());
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
   * Evaluates a string as a numeric expression. Used by VAL function and INPUT for numeric
   * variables. Per Sinclair ZX BASIC, this parses and evaluates the full expression.
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
    final var exprCtx = parser.parseStrExpr(exprStr);
    new AstAnnotator(state.currentLineLabel()).visit(exprCtx);
    return evalStr(exprCtx);
  }

  private static final double ULP0 = 1e-39;

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
      final var df = new DecimalFormat("0.########E0");
      String result = df.format(d);
      // Add + sign for positive exponents (e.g., 1E15 -> 1E+15)
      final int ePos = result.indexOf('E');
      if (ePos >= 0 && ePos + 1 < result.length() && result.charAt(ePos + 1) != '-') {
        result = result.substring(0, ePos + 1) + "+" + result.substring(ePos + 1);
      }
      return result;
    } else {
      // Normal decimal notation with up to 8 decimal places
      final var df = new DecimalFormat("0.########");
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
