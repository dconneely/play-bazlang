package com.davidconneely.bazlang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
  private static final Map<String, TokenType> KEYWORDS =
      Map.ofEntries(
          Map.entry("ABS", TokenType.ABS),
          Map.entry("ACS", TokenType.ACS),
          Map.entry("AND", TokenType.AND),
          Map.entry("ASN", TokenType.ASN),
          Map.entry("AT", TokenType.AT),
          Map.entry("ATN", TokenType.ATN),
          Map.entry("CHR$", TokenType.CHR_STR),
          Map.entry("CLEAR", TokenType.CLEAR),
          Map.entry("CLS", TokenType.CLS),
          Map.entry("CODE", TokenType.CODE),
          Map.entry("CONT", TokenType.CONT),
          Map.entry("COPY", TokenType.COPY),
          Map.entry("COS", TokenType.COS),
          Map.entry("DIM", TokenType.DIM),
          Map.entry("EXP", TokenType.EXP),
          Map.entry("FAST", TokenType.FAST),
          Map.entry("FOR", TokenType.FOR),
          Map.entry("GOTO", TokenType.GOTO),
          Map.entry("GOSUB", TokenType.GOSUB),
          Map.entry("IF", TokenType.IF),
          Map.entry("INKEY$", TokenType.INKEY_STR),
          Map.entry("INPUT", TokenType.INPUT),
          Map.entry("INT", TokenType.INT),
          Map.entry("LEN", TokenType.LEN),
          Map.entry("LET", TokenType.LET),
          Map.entry("LIST", TokenType.LIST),
          Map.entry("LLIST", TokenType.LLIST),
          Map.entry("LN", TokenType.LN),
          Map.entry("LOAD", TokenType.LOAD),
          Map.entry("LPRINT", TokenType.LPRINT),
          Map.entry("NEXT", TokenType.NEXT),
          Map.entry("NEW", TokenType.NEW),
          Map.entry("NOT", TokenType.NOT),
          Map.entry("OR", TokenType.OR),
          Map.entry("PAUSE", TokenType.PAUSE),
          Map.entry("PEEK", TokenType.PEEK),
          Map.entry("PI", TokenType.PI),
          Map.entry("PLOT", TokenType.PLOT),
          Map.entry("POKE", TokenType.POKE),
          Map.entry("PRINT", TokenType.PRINT),
          Map.entry("RAND", TokenType.RAND),
          Map.entry("REM", TokenType.REM),
          Map.entry("RETURN", TokenType.RETURN),
          Map.entry("RND", TokenType.RND),
          Map.entry("RUN", TokenType.RUN),
          Map.entry("SAVE", TokenType.SAVE),
          Map.entry("SCROLL", TokenType.SCROLL),
          Map.entry("SGN", TokenType.SGN),
          Map.entry("SIN", TokenType.SIN),
          Map.entry("SLOW", TokenType.SLOW),
          Map.entry("SQR", TokenType.SQR),
          Map.entry("STEP", TokenType.STEP),
          Map.entry("STOP", TokenType.STOP),
          Map.entry("STR$", TokenType.STR_STR),
          Map.entry("TAB", TokenType.TAB),
          Map.entry("TAN", TokenType.TAN),
          Map.entry("THEN", TokenType.THEN),
          Map.entry("TO", TokenType.TO),
          Map.entry("UNPLOT", TokenType.UNPLOT),
          Map.entry("USR", TokenType.USR),
          Map.entry("VAL", TokenType.VAL));

  private final String source;
  private int pos = 0;

  public Lexer(String source) {
    this.source = source;
  }

  public List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    while (pos < source.length()) {
      skipHorizontalWhitespace();
      if (pos >= source.length()) break;
      char ch = source.charAt(pos);
      if (ch == '\n') {
        tokens.add(new Token(TokenType.NEWLINE, "\n"));
        ++pos;
        continue;
      }
      if (ch == '#') {
        skipToNextLine();
        continue;
      }
      if (Character.isDigit(ch)
          || (ch == '.'
              && pos + 1 < source.length()
              && Character.isDigit(source.charAt(pos + 1)))) {
        tokens.add(readNumericLiteral());
      } else if (ch == '"') {
        tokens.add(readStringLiteral());
      } else if (Character.isLetter(ch)) {
        Token t = readIdentifierOrKeyword();
        tokens.add(t);
        if (t.type() == TokenType.REM) {
          skipToNextLine();
          tokens.add(new Token(TokenType.NEWLINE, "\n"));
        }
      } else {
        tokens.add(readOperatorOrDelimiter());
      }
    }
    tokens.add(new Token(TokenType.EOF, null));
    return tokens;
  }

  private void skipHorizontalWhitespace() {
    while (pos < source.length()
        && (source.charAt(pos) == ' '
            || source.charAt(pos) == '\t'
            || source.charAt(pos) == '\r')) {
      ++pos;
    }
  }

  private void skipToNextLine() {
    while (pos < source.length() && source.charAt(pos) != '\n') {
      ++pos;
    }
    if (pos < source.length()) {
      ++pos;
    }
  }

  private Token readNumericLiteral() {
    StringBuilder sb = new StringBuilder();
    while (pos < source.length()
        && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
      sb.append(source.charAt(pos));
      ++pos;
    }
    if (pos < source.length() && (source.charAt(pos) == 'E' || source.charAt(pos) == 'e')) {
      sb.append(source.charAt(pos));
      ++pos;
      if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
        sb.append(source.charAt(pos));
        ++pos;
      }
      while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
        sb.append(source.charAt(pos));
        ++pos;
      }
    }
    return new Token(TokenType.NUMERIC_LITERAL, sb.toString());
  }

  private Token readStringLiteral() {
    StringBuilder sb = new StringBuilder();
    ++pos;
    while (pos < source.length()) {
      char ch = source.charAt(pos);
      if (ch == '"') {
        if (pos + 1 < source.length() && source.charAt(pos + 1) == '"') {
          sb.append('"');
          pos += 2;
        } else {
          break;
        }
      } else {
        sb.append(ch);
        ++pos;
      }
    }
    if (pos < source.length()) {
      ++pos;
    }
    return new Token(TokenType.STRING_LITERAL, sb.toString());
  }

  private Token readIdentifierOrKeyword() {
    StringBuilder sb = new StringBuilder();
    while (pos < source.length()
        && (Character.isLetterOrDigit(source.charAt(pos))
            || source.charAt(pos) == '$'
            || source.charAt(pos) == '_')) {
      sb.append(source.charAt(pos));
      ++pos;
    }
    String text = sb.toString();
    String upper = text.toUpperCase();
    TokenType type = KEYWORDS.getOrDefault(upper, TokenType.IDENTIFIER);
    if (type == TokenType.REM) {
      StringBuilder comment = new StringBuilder();
      while (pos < source.length() && source.charAt(pos) != '\n') {
        comment.append(source.charAt(pos));
        ++pos;
      }
      return new Token(TokenType.REM, comment.toString().trim());
    }
    return new Token(type, type == TokenType.IDENTIFIER ? upper : text);
  }

  private Token readOperatorOrDelimiter() {
    char ch = source.charAt(pos);
    ++pos;
    return switch (ch) {
      case '(' -> new Token(TokenType.LEFT_PAREN, "(");
      case ')' -> new Token(TokenType.RIGHT_PAREN, ")");
      case '*' -> {
        if (pos < source.length() && source.charAt(pos) == '*') {
          ++pos;
          yield new Token(TokenType.POWER, "**");
        }
        yield new Token(TokenType.MULTIPLY, "*");
      }
      case '+' -> new Token(TokenType.PLUS, "+");
      case ',' -> new Token(TokenType.COMMA, ",");
      case '-' -> new Token(TokenType.MINUS, "-");
      case '/' -> new Token(TokenType.DIVIDE, "/");
      case ';' -> new Token(TokenType.SEMICOLON, ";");
      case ':' ->
          throw new ReportException(
              ReportCode.NONSENSE_IN_BASIC, 0, "Multi-statement lines (':') are not supported");
      case '<' -> {
        if (pos < source.length() && source.charAt(pos) == '=') {
          ++pos;
          yield new Token(TokenType.LESS_EQUAL, "<=");
        } else if (pos < source.length() && source.charAt(pos) == '>') {
          ++pos;
          yield new Token(TokenType.NOT_EQUALS, "<>");
        }
        yield new Token(TokenType.LESS_THAN, "<");
      }
      case '=' -> new Token(TokenType.EQUALS, "=");
      case '>' -> {
        if (pos < source.length() && source.charAt(pos) == '=') {
          ++pos;
          yield new Token(TokenType.GREATER_EQUAL, ">=");
        }
        yield new Token(TokenType.GREATER_THAN, ">");
      }
      default ->
          throw new ReportException(ReportCode.NONSENSE_IN_BASIC, 0, "Unexpected character: " + ch);
    };
  }
}
