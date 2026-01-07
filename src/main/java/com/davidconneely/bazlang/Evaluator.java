package com.davidconneely.bazlang;

import java.util.List;

public class Evaluator {
  private final EvalState state;
  private final Display display;

  public Evaluator(EvalState state, Display display) {
    this.state = state;
    this.display = display;
  }

  public double evaluateNumExpr(Expression.NumExpr expr) {
    return switch (expr) {
      case Expression.NumExpr.Literal nl -> nl.value();
      case Expression.NumExpr.ScalarRef sr -> {
        if (!state.numScalars().containsKey(sr.name())) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
        }
        yield state.numScalars().get(sr.name());
      }
      case Expression.NumExpr.SubscriptRef sr -> {
        if (!state.numArrays().containsKey(sr.name())) {
          throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
        }
        EvalState.NumArray na = state.numArrays().get(sr.name());
        int idx = calculateArrayIndex(na.dimensions(), sr.indices());
        yield na.data()[idx];
      }
      case Expression.NumExpr.UnaryOp uo ->
          switch (uo.operator()) {
            case TokenType.MINUS -> -evaluateNumExpr(uo.operand());
            case TokenType.NOT -> evaluateNumExpr(uo.operand()) == 0.0 ? 1.0 : 0.0;
            default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown unary operator");
          };
      case Expression.NumExpr.BinaryOp bo -> evaluateNumBinaryOp(bo);
      case Expression.NumExpr.NumComp nc -> evaluateNumComp(nc);
      case Expression.NumExpr.StrComp sc -> evaluateStrComp(sc);
      case Expression.NumExpr.NumFunc nf -> evaluateNumNumFunc(nf);
      case Expression.NumExpr.StrFunc sf -> evaluateNumStrFunc(sf);
      case Expression.NumExpr.NullFunc nc ->
          switch (nc.func()) {
            case TokenType.PI -> Math.PI;
            case TokenType.RND -> state.random().nextDouble();
            default -> 0.0;
          };
    };
  }

  public String evaluateStrExpr(Expression.StrExpr expr) {
    return switch (expr) {
      case Expression.StrExpr.Literal sl -> sl.value();
      case Expression.StrExpr.ScalarRef sr -> {
        String name = sr.name();
        if (state.charArrays().containsKey(name)) {
          EvalState.CharArray ca = state.charArrays().get(name);
          if (ca.dimensions().isEmpty()) yield new String(ca.data());
        }
        if (state.strVars().containsKey(name)) {
          yield state.strVars().get(name);
        }
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      case Expression.StrExpr.SubscriptRef sr -> {
        String name = sr.name();
        List<Expression.NumExpr> indices = sr.indices();
        Expression.Slice slice = sr.slice();
        if (state.charArrays().containsKey(name)) {
          EvalState.CharArray ca = state.charArrays().get(name);
          int n = ca.dimensions().size();
          Expression.NumExpr ci = null;
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
          int base = idx * ca.fixedStrLen();
          yield sliceStr(i -> ca.data()[base + i], ca.fixedStrLen(), ci, slice);
        }
        if (state.strVars().containsKey(name)) {
          String s = state.strVars().get(name);
          if (indices.isEmpty()) {
            yield sliceStr(s::charAt, s.length(), null, slice);
          } else if (indices.size() == 1 && slice == null) {
            yield sliceStr(s::charAt, s.length(), indices.getFirst(), null);
          }
          throw codedException(
              ReportCode.SUBSCRIPT_WRONG, "Scalar string only takes one index or slice");
        }
        throw codedException(ReportCode.VARIABLE_NOT_FOUND, "Undefined variable");
      }
      case Expression.StrExpr.StrConcat sc ->
          evaluateStrExpr(sc.left()) + evaluateStrExpr(sc.right());
      case Expression.StrExpr.NumFunc nf ->
          switch (nf.func()) {
            case TokenType.CHR_STR ->
                new String(Character.toChars((int) evaluateNumExpr(nf.argument())));
            case TokenType.STR_STR -> formatNum(evaluateNumExpr(nf.argument()));
            default ->
                throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string function");
          };
      case Expression.StrExpr.NullFunc nf ->
          switch (nf.func()) {
            case TokenType.INKEY_STR -> display.inkey();
            default -> "";
          };
    };
  }

  public String evaluatePrintItemExpr(Expression expr) {
    if (expr instanceof Expression.NumExpr n) {
      return formatNum(evaluateNumExpr(n));
    } else if (expr instanceof Expression.StrExpr s) {
      return evaluateStrExpr(s);
    } else {
      return "";
    }
  }

  private double evaluateNumBinaryOp(Expression.NumExpr.BinaryOp bo) {
    double l = evaluateNumExpr(bo.left()), r = evaluateNumExpr(bo.right());
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

  private double evaluateNumComp(Expression.NumExpr.NumComp co) {
    double l = evaluateNumExpr(co.left()), r = evaluateNumExpr(co.right());
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

  private double evaluateStrComp(Expression.NumExpr.StrComp co) {
    String l = evaluateStrExpr(co.left()), r = evaluateStrExpr(co.right());
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

  private double evaluateNumNumFunc(Expression.NumExpr.NumFunc fc) {
    return switch (fc.func()) {
      case TokenType.ABS -> Math.abs(evaluateNumExpr(fc.argument()));
      case TokenType.ACS -> Math.acos(evaluateNumExpr(fc.argument()));
      case TokenType.ASN -> Math.asin(evaluateNumExpr(fc.argument()));
      case TokenType.ATN -> Math.atan(evaluateNumExpr(fc.argument()));
      case TokenType.COS -> Math.cos(evaluateNumExpr(fc.argument()));
      case TokenType.EXP -> Math.exp(evaluateNumExpr(fc.argument()));
      case TokenType.INT -> Math.floor(evaluateNumExpr(fc.argument()));
      case TokenType.LN -> Math.log(evaluateNumExpr(fc.argument()));
      case TokenType.SGN -> Math.signum(evaluateNumExpr(fc.argument()));
      case TokenType.SIN -> Math.sin(evaluateNumExpr(fc.argument()));
      case TokenType.SQR -> Math.sqrt(evaluateNumExpr(fc.argument()));
      case TokenType.TAN -> Math.tan(evaluateNumExpr(fc.argument()));
      case TokenType.PEEK, TokenType.USR -> {
        // Not implemented. Consume arg, then return 0.0.
        evaluateNumExpr(fc.argument());
        yield 0.0;
      }
      default -> throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown math function");
    };
  }

  private double evaluateNumStrFunc(Expression.NumExpr.StrFunc fc) {
    return switch (fc.func()) {
      case TokenType.CODE -> (double) evaluateStrExpr(fc.argument()).codePointAt(0);
      case TokenType.LEN -> (double) evaluateStrExpr(fc.argument()).length();
      case TokenType.VAL -> {
        try {
          yield Double.parseDouble(evaluateStrExpr(fc.argument()).trim());
        } catch (Exception e) {
          yield 0.0;
        }
      }
      default ->
          throw codedException(ReportCode.NONSENSE_IN_BASIC, "Unknown string-to-math function");
    };
  }

  public int calculateArrayIndex(List<Integer> dimensions, List<Expression.NumExpr> indices) {
    int n = dimensions.size();
    if (indices.size() != n) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Incorrect dimensions");
    }
    int idx = 0, m = 1;
    for (int i = n - 1; i >= 0; i--) {
      int sz = dimensions.get(i), v = (int) evaluateNumExpr(indices.get(i));
      if (v < 1 || v > sz) {
        throw codedException(ReportCode.SUBSCRIPT_WRONG, "Index out of bounds");
      }
      idx += (v - 1) * m;
      m *= sz;
    }
    return idx;
  }

  public record Range(int start, int end) {}

  public Range calculateSliceRange(int fullLen, Expression.NumExpr ci, Expression.Slice sl) {
    int base = (ci != null) ? (int) evaluateNumExpr(ci) : 1;
    int st = base + (sl != null && sl.start() != null ? (int) evaluateNumExpr(sl.start()) - 1 : 0);
    int en;
    if (sl != null) {
      en = base + (sl.end() != null ? (int) evaluateNumExpr(sl.end()) - 1 : fullLen - base);
    } else {
      en = (ci != null) ? base : fullLen;
    }
    if (st < 1 || en > fullLen || st > en + 1) {
      throw codedException(ReportCode.SUBSCRIPT_WRONG, "Slice out of bounds");
    }
    return new Range(st, en);
  }

  private interface StrSource {
    char get(int index);
  }

  private String sliceStr(
      StrSource source, int fullLen, Expression.NumExpr ci, Expression.Slice sl) {
    Range r = calculateSliceRange(fullLen, ci, sl);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < (r.end() - r.start() + 1); i++) {
      sb.append(source.get(r.start() + i - 1));
    }
    return sb.toString();
  }

  public String formatNum(double d) {
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
