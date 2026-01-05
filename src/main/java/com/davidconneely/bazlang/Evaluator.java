package com.davidconneely.bazlang;

import java.util.List;

public class Evaluator {
  private final MachineState state;
  private final Terminal terminal;

  public Evaluator(MachineState state, Terminal terminal) {
    this.state = state;
    this.terminal = terminal;
  }

  public double evaluateNumericExpression(Expression.Numeric expr) {
    return switch (expr) {
      case Expression.Numeric.Literal nl -> nl.value();
      case Expression.Numeric.ScalarRef sr -> {
        if (!state.numericScalars().containsKey(sr.name())) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
        }
        yield state.numericScalars().get(sr.name());
      }
      case Expression.Numeric.SubscriptRef sr -> {
        if (!state.numericArrays().containsKey(sr.name())) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
        }
        MachineState.NumericArray na = state.numericArrays().get(sr.name());
        int idx = calculateArrayIndex(na.dimensions(), sr.indices());
        yield na.data()[idx];
      }
      case Expression.Numeric.UnaryOp uo ->
          switch (uo.operator()) {
            case TokenType.MINUS -> -evaluateNumericExpression(uo.operand());
            case TokenType.NOT -> evaluateNumericExpression(uo.operand()) == 0.0 ? 1.0 : 0.0;
            default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown unary operator");
          };
      case Expression.Numeric.BinaryOp bo -> evaluateNumericBinaryOp(bo);
      case Expression.Numeric.NumericComparison nc -> evaluateNumericComparison(nc);
      case Expression.Numeric.StringComparison sc -> evaluateStringComparison(sc);
      case Expression.Numeric.FuncCall fc -> evaluateNumericFuncCall(fc);
      case Expression.Numeric.FuncCallStr fc -> evaluateNumericFuncCallStr(fc);
      case Expression.Numeric.NullaryCall nc ->
          switch (nc.func()) {
            case TokenType.PI -> Math.PI;
            case TokenType.RND -> state.random().nextDouble();
            default -> 0.0;
          };
    };
  }

  public String evaluateStringExpression(Expression.String expr) {
    return switch (expr) {
      case Expression.String.Literal sl -> sl.value();
      case Expression.String.ScalarRef sr -> {
        String name = sr.name();
        if (state.characterArrays().containsKey(name)) {
          MachineState.CharacterArray ca = state.characterArrays().get(name);
          if (ca.dimensions().isEmpty()) yield new String(ca.data());
        }
        if (state.variableLengthStrings().containsKey(name)) {
          yield state.variableLengthStrings().get(name);
        }
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      case Expression.String.SubscriptRef sr -> {
        String name = sr.name();
        List<Expression.Numeric> indices = sr.indices();
        Expression.Slice slice = sr.slice();
        if (state.characterArrays().containsKey(name)) {
          MachineState.CharacterArray ca = state.characterArrays().get(name);
          int n = ca.dimensions().size();
          Expression.Numeric ci = null;
          if (indices.size() == n + 1) {
            ci = indices.get(n);
            indices = indices.subList(0, n);
          } else if (indices.size() != n) {
            if (n == 0) {
              if (indices.size() == 1) {
                ci = indices.getFirst();
                indices = List.of();
              } else if (!indices.isEmpty()) {
                throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect number of indices");
              }
            } else {
              throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect number of indices");
            }
          }
          int idx = calculateArrayIndex(ca.dimensions(), indices);
          int base = idx * ca.fixedStringLength();
          yield sliceString(i -> ca.data()[base + i], ca.fixedStringLength(), ci, slice);
        }
        if (state.variableLengthStrings().containsKey(name)) {
          String s = state.variableLengthStrings().get(name);
          if (indices.isEmpty()) {
            yield sliceString(s::charAt, s.length(), null, slice);
          } else if (indices.size() == 1 && slice == null) {
            yield sliceString(s::charAt, s.length(), indices.getFirst(), null);
          }
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      case Expression.String.Concatenation sc ->
          evaluateStringExpression(sc.left()) + evaluateStringExpression(sc.right());
      case Expression.String.FuncCall fc ->
          switch (fc.func()) {
            case TokenType.CHR_STR ->
                new String(Character.toChars((int) evaluateNumericExpression(fc.argument())));
            case TokenType.STR_STR -> formatNumber(evaluateNumericExpression(fc.argument()));
            default ->
                throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string function");
          };
      case Expression.String.NullaryCall nc ->
          switch (nc.func()) {
            case TokenType.INKEY_STR -> terminal.inkey();
            default -> "";
          };
    };
  }

  public String evaluatePrintItemExpr(Expression expr) {
    if (expr instanceof Expression.Numeric n) {
      return formatNumber(evaluateNumericExpression(n));
    } else if (expr instanceof Expression.String s) {
      return evaluateStringExpression(s);
    } else {
      return "";
    }
  }

  private double evaluateNumericBinaryOp(Expression.Numeric.BinaryOp bo) {
    double l = evaluateNumericExpression(bo.left()), r = evaluateNumericExpression(bo.right());
    return switch (bo.operator()) {
      case TokenType.PLUS -> l + r;
      case TokenType.MINUS -> l - r;
      case TokenType.MULTIPLY -> l * r;
      case TokenType.DIVIDE -> {
        if (r == 0.0) {
          throw codedException(ReportCode.NUMBER_TOO_BIG, "Division by zero");
        }
        yield l / r;
      }
      case TokenType.POWER -> Math.pow(l, r);
      case TokenType.AND -> (l != 0.0 && r != 0.0) ? 1.0 : 0.0;
      case TokenType.OR -> (l != 0.0 || r != 0.0) ? 1.0 : 0.0;
      default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown binary operator");
    };
  }

  private double evaluateNumericComparison(Expression.Numeric.NumericComparison co) {
    double l = evaluateNumericExpression(co.left()), r = evaluateNumericExpression(co.right());
    return switch (co.operator()) {
      case TokenType.EQUALS -> l == r ? 1.0 : 0.0;
      case TokenType.NOT_EQUALS -> l != r ? 1.0 : 0.0;
      case TokenType.LESS_THAN -> l < r ? 1.0 : 0.0;
      case TokenType.LESS_EQUAL -> l <= r ? 1.0 : 0.0;
      case TokenType.GREATER_THAN -> l > r ? 1.0 : 0.0;
      case TokenType.GREATER_EQUAL -> l >= r ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  private double evaluateStringComparison(Expression.Numeric.StringComparison co) {
    String l = evaluateStringExpression(co.left()), r = evaluateStringExpression(co.right());
    return switch (co.operator()) {
      case TokenType.EQUALS -> l.equals(r) ? 1.0 : 0.0;
      case TokenType.NOT_EQUALS -> !l.equals(r) ? 1.0 : 0.0;
      case TokenType.LESS_THAN -> l.compareTo(r) < 0 ? 1.0 : 0.0;
      case TokenType.LESS_EQUAL -> l.compareTo(r) <= 0 ? 1.0 : 0.0;
      case TokenType.GREATER_THAN -> l.compareTo(r) > 0 ? 1.0 : 0.0;
      case TokenType.GREATER_EQUAL -> l.compareTo(r) >= 0 ? 1.0 : 0.0;
      default -> 0.0;
    };
  }

  private double evaluateNumericFuncCall(Expression.Numeric.FuncCall fc) {
    return switch (fc.func()) {
      case TokenType.ABS -> Math.abs(evaluateNumericExpression(fc.argument()));
      case TokenType.ACS -> Math.acos(evaluateNumericExpression(fc.argument()));
      case TokenType.ASN -> Math.asin(evaluateNumericExpression(fc.argument()));
      case TokenType.ATN -> Math.atan(evaluateNumericExpression(fc.argument()));
      case TokenType.COS -> Math.cos(evaluateNumericExpression(fc.argument()));
      case TokenType.EXP -> Math.exp(evaluateNumericExpression(fc.argument()));
      case TokenType.INT -> Math.floor(evaluateNumericExpression(fc.argument()));
      case TokenType.LN -> Math.log(evaluateNumericExpression(fc.argument()));
      case TokenType.SGN -> Math.signum(evaluateNumericExpression(fc.argument()));
      case TokenType.SIN -> Math.sin(evaluateNumericExpression(fc.argument()));
      case TokenType.SQR -> Math.sqrt(evaluateNumericExpression(fc.argument()));
      case TokenType.TAN -> Math.tan(evaluateNumericExpression(fc.argument()));
      case TokenType.PEEK, TokenType.USR -> {
        // Not implemented. Consume arg, then return 0.0.
        evaluateNumericExpression(fc.argument());
        yield 0.0;
      }
      default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown math function");
    };
  }

  private double evaluateNumericFuncCallStr(Expression.Numeric.FuncCallStr fc) {
    return switch (fc.func()) {
      case TokenType.CODE -> (double) evaluateStringExpression(fc.argument()).codePointAt(0);
      case TokenType.LEN -> (double) evaluateStringExpression(fc.argument()).length();
      case TokenType.VAL -> {
        try {
          yield Double.parseDouble(evaluateStringExpression(fc.argument()).trim());
        } catch (Exception e) {
          yield 0.0;
        }
      }
      default ->
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string-to-math function");
    };
  }

  public int calculateArrayIndex(List<Integer> dimensions, List<Expression.Numeric> indices) {
    int n = dimensions.size();
    if (indices.size() != n) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    int idx = 0, m = 1;
    for (int i = n - 1; i >= 0; i--) {
      int sz = dimensions.get(i), v = (int) evaluateNumericExpression(indices.get(i));
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  public record Range(int start, int end) {}

  public Range calculateSliceRange(int fullLen, Expression.Numeric ci, Expression.Slice sl) {
    int base = (ci != null) ? (int) evaluateNumericExpression(ci) : 1;
    int st =
        base
            + (sl != null && sl.start() != null
                ? (int) evaluateNumericExpression(sl.start()) - 1
                : 0);
    int en;
    if (sl != null) {
      en =
          base
              + (sl.end() != null ? (int) evaluateNumericExpression(sl.end()) - 1 : fullLen - base);
    } else {
      en = (ci != null) ? base : fullLen;
    }
    if (st < 1 || en > fullLen || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    return new Range(st, en);
  }

  private interface StringSource {
    char get(int index);
  }

  private String sliceString(
      StringSource source, int fullLen, Expression.Numeric ci, Expression.Slice sl) {
    Range r = calculateSliceRange(fullLen, ci, sl);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < (r.end() - r.start() + 1); i++) {
      sb.append(source.get(r.start() + i - 1));
    }
    return sb.toString();
  }

  public String formatNumber(double d) {
    return (d == Math.floor(d)
            && !Double.isInfinite(d)
            && d >= Long.MIN_VALUE
            && d <= Long.MAX_VALUE)
        ? Long.toString((long) d)
        : Double.toString(d);
  }

  private ReportException codedException(ReportCode rc, String msg) {
    return new ReportException(rc, state.currentLineLabel(), msg);
  }
}
