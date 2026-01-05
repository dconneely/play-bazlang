package com.davidconneely.bazlang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

public class Parser {
  private static final Map<TokenType, Function<Parser, Statement>> STATEMENT_PARSERS;

  static {
    STATEMENT_PARSERS =
        Map.ofEntries(
            Map.entry(TokenType.CLEAR, _ -> new Statement.Clear()),
            Map.entry(TokenType.CLS, _ -> new Statement.Cls()),
            Map.entry(TokenType.CONT, _ -> new Statement.Cont()),
            Map.entry(TokenType.COPY, _ -> new Statement.Copy()),
            Map.entry(TokenType.DIM, Parser::parseDim),
            Map.entry(TokenType.FAST, _ -> new Statement.Fast()),
            Map.entry(TokenType.FOR, Parser::parseFor),
            Map.entry(TokenType.GOSUB, Parser::parseGosub),
            Map.entry(TokenType.GOTO, Parser::parseGoto),
            Map.entry(TokenType.IF, Parser::parseIf),
            Map.entry(TokenType.INPUT, Parser::parseInput),
            Map.entry(TokenType.LET, Parser::parseLet),
            Map.entry(TokenType.LIST, Parser::parseList),
            Map.entry(TokenType.LLIST, Parser::parseLList),
            Map.entry(TokenType.LOAD, Parser::parseLoad),
            Map.entry(TokenType.LPRINT, Parser::parseLPrint),
            Map.entry(TokenType.NEW, _ -> new Statement.New()),
            Map.entry(TokenType.NEXT, Parser::parseNext),
            Map.entry(TokenType.PAUSE, Parser::parsePause),
            Map.entry(TokenType.PLOT, Parser::parsePlot),
            Map.entry(TokenType.POKE, Parser::parsePoke),
            Map.entry(TokenType.PRINT, Parser::parsePrint),
            Map.entry(TokenType.RAND, Parser::parseRand),
            Map.entry(TokenType.REM, Parser::parseRem),
            Map.entry(TokenType.RETURN, _ -> new Statement.Return()),
            Map.entry(TokenType.RUN, Parser::parseRun),
            Map.entry(TokenType.SAVE, Parser::parseSave),
            Map.entry(TokenType.SCROLL, _ -> new Statement.Scroll()),
            Map.entry(TokenType.SLOW, _ -> new Statement.Slow()),
            Map.entry(TokenType.STOP, _ -> new Statement.Stop()),
            Map.entry(TokenType.UNPLOT, Parser::parseUnplot));
  }

  private final List<Token> tokens;
  private int pos = 0;
  private int currentLineLabel = 0;
  private int prevLineLabel = 0;

  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  public NavigableMap<Integer, Statement> parseProgram() {
    var program = new TreeMap<Integer, Statement>();
    prevLineLabel = 0;
    while (!isAtEnd()) {
      skipNewlines();
      if (!isAtEnd()) {
        putParsedLine(program);
      }
    }
    return program;
  }

  public Statement parseReplStatement() {
    return parseStatement();
  }

  private void putParsedLine(Map<Integer, Statement> program) {
    Token labelToken = consume(TokenType.NUMERIC_LITERAL, "Line must begin with a label");
    try {
      currentLineLabel = Integer.parseInt(labelToken.rep());
      if (currentLineLabel < Limits.MIN_LINE_LABEL || currentLineLabel > Limits.MAX_LINE_LABEL) {
        throw codedException("Invalid line label range: " + labelToken.rep());
      }
    } catch (NumberFormatException e) {
      throw codedException("Invalid line label format: " + labelToken.rep());
    }
    if (currentLineLabel <= prevLineLabel) {
      throw codedException(
          "Line labels must be monotonically increasing (current: "
              + currentLineLabel
              + ", previous: "
              + prevLineLabel
              + ")");
    }
    prevLineLabel = currentLineLabel;
    Statement statement = parseStatement();
    program.put(currentLineLabel, statement);
    // Every statement must end with a newline or EOF.
    if (!isAtEnd()) {
      consume(TokenType.NEWLINE, "Expected newline after statement");
    }
  }

  private Statement parseStatement() {
    if (isAtEnd() || check(TokenType.NEWLINE)) {
      throw codedException("Statement expected");
    }
    Token typeToken = advance();
    Function<Parser, Statement> parser = STATEMENT_PARSERS.get(typeToken.type());
    if (parser != null) {
      return parser.apply(this);
    }
    throw codedException("Unexpected token: " + typeToken);
  }

  private Statement parseDim() {
    Token varToken = consume(TokenType.IDENTIFIER, "Expected variable name");
    consume(TokenType.LEFT_PAREN, "Expected '('");
    List<Expression.Numeric> dimensions = new ArrayList<>();
    dimensions.add(parseNumericExpression());
    while (match(TokenType.COMMA)) {
      dimensions.add(parseNumericExpression());
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')'");
    return new Statement.Dim(varToken.rep(), dimensions);
  }

  private Statement parseFor() {
    Token varToken = consume(TokenType.IDENTIFIER, "Expected variable name");
    consume(TokenType.EQUALS, "Expected '='");
    Expression.Numeric start = parseNumericExpression();
    consume(TokenType.TO, "Expected TO");
    Expression.Numeric end = parseNumericExpression();
    Expression.Numeric step = new Expression.Numeric.Literal(1.0);
    if (match(TokenType.STEP)) {
      step = parseNumericExpression();
    }
    return new Statement.For(varToken.rep(), start, end, step);
  }

  private Statement parseGosub() {
    return new Statement.Gosub(parseNumericExpression());
  }

  private Statement parseGoto() {
    return new Statement.Goto(parseNumericExpression());
  }

  private Statement parseIf() {
    Expression.Numeric condition = parseNumericExpression();
    consume(TokenType.THEN, "Expected THEN");
    return new Statement.If(condition, parseStatement());
  }

  private Statement parseInput() {
    return new Statement.Input(parseLValue());
  }

  private Statement parseLet() {
    Expression target = parseLValue();
    consume(TokenType.EQUALS, "Expected '='");
    Expression value;
    if (target instanceof Expression.Numeric) {
      value = parseNumericExpression();
    } else {
      value = parseStringExpression();
    }
    return new Statement.Let(target, value);
  }

  private Expression parseLValue() {
    Token varToken = consume(TokenType.IDENTIFIER, "Expected variable name");
    String name = varToken.rep();
    if (!check(TokenType.LEFT_PAREN)) {
      if (!name.endsWith("$")) {
        return new Expression.Numeric.ScalarRef(name);
      } else {
        return new Expression.String.ScalarRef(name);
      }
    } else if (name.endsWith("$")) {
      return parseStringSubscript(name);
    } else {
      return parseNumericSubscript(name);
    }
  }

  private Expression.Numeric.SubscriptRef parseNumericSubscript(String name) {
    consume(TokenType.LEFT_PAREN, "Expected '(' after numeric variable name");
    List<Expression.Numeric> indices = new ArrayList<>();
    while (true) {
      indices.add(parseNumericExpression());
      if (check(TokenType.TO)) {
        throw codedException("Cannot slice numeric array");
      }
      if (!match(TokenType.COMMA)) break;
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' after numeric subscript");
    return new Expression.Numeric.SubscriptRef(name, indices);
  }

  private Expression.String parseStringSubscript(String name) {
    consume(TokenType.LEFT_PAREN, "Expected '(' after string variable name");
    List<Expression.Numeric> indices = new ArrayList<>();
    Expression.Slice slice = null;
    if (match(TokenType.TO)) {
      Expression.Numeric end = !check(TokenType.RIGHT_PAREN) ? parseNumericExpression() : null;
      slice = new Expression.Slice(null, end);
    } else {
      while (true) {
        indices.add(parseNumericExpression());
        if (match(TokenType.TO)) {
          Expression.Numeric start = indices.removeLast();
          Expression.Numeric end = !check(TokenType.RIGHT_PAREN) ? parseNumericExpression() : null;
          slice = new Expression.Slice(start, end);
          break;
        }
        if (!match(TokenType.COMMA)) {
          break;
        }
      }
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' after string subscript");
    return new Expression.String.SubscriptRef(name, indices, slice);
  }

  private Statement parseLoad() {
    return new Statement.Load(parseStringExpression());
  }

  private Statement parseSave() {
    return new Statement.Save(parseStringExpression());
  }

  private Statement parseList() {
    Expression.Numeric start = new Expression.Numeric.Literal(Limits.MIN_TARGET_LABEL);
    Expression.Numeric end = new Expression.Numeric.Literal(Limits.MAX_TARGET_LABEL);
    if (match(TokenType.COMMA)) {
      if (!check(TokenType.NEWLINE) && !isAtEnd()) {
        end = parseNumericExpression();
      }
    } else if (!check(TokenType.NEWLINE) && !isAtEnd()) {
      start = parseNumericExpression();
      if (match(TokenType.COMMA)) {
        if (!check(TokenType.NEWLINE) && !isAtEnd()) {
          end = parseNumericExpression();
        }
      }
    }
    return new Statement.ListStmt(start, end);
  }

  private Statement parseLList() {
    Expression.Numeric start = new Expression.Numeric.Literal(Limits.MIN_TARGET_LABEL);
    Expression.Numeric end = new Expression.Numeric.Literal(Limits.MAX_TARGET_LABEL);
    if (match(TokenType.COMMA)) {
      if (!check(TokenType.NEWLINE) && !isAtEnd()) {
        end = parseNumericExpression();
      }
    } else if (!check(TokenType.NEWLINE) && !isAtEnd()) {
      start = parseNumericExpression();
      if (match(TokenType.COMMA)) {
        if (!check(TokenType.NEWLINE) && !isAtEnd()) {
          end = parseNumericExpression();
        }
      }
    }
    return new Statement.LList(start, end);
  }

  private Statement parseRand() {
    return new Statement.Rand(parseOptionalNumeric(0.0));
  }

  private Statement parseRun() {
    return new Statement.Run(parseOptionalNumeric(0.0));
  }

  private Statement parseLPrint() {
    return parsePrintItems(Statement.LPrint::new);
  }

  private Statement parseNext() {
    return new Statement.Next(consume(TokenType.IDENTIFIER, "Expected variable name").rep());
  }

  private Statement parsePause() {
    return new Statement.Pause(parseNumericExpression());
  }

  private Statement parsePlot() {
    return parseXy(Statement.Plot::new);
  }

  private Statement parsePoke() {
    Expression.Numeric addr = parseNumericExpression();
    consume(TokenType.COMMA, "Expected ','");
    return new Statement.Poke(addr, parseNumericExpression());
  }

  private Statement parsePrint() {
    return parsePrintItems(Statement.Print::new);
  }

  private Statement parseRem() {
    return new Statement.Rem(previous().rep());
  }

  private Statement parseUnplot() {
    return parseXy(Statement.Unplot::new);
  }

  @FunctionalInterface
  private interface PrintFactory {
    Statement create(List<PrintItem> items, boolean newline);
  }

  private Statement parsePrintItems(PrintFactory factory) {
    List<PrintItem> items = new ArrayList<>();
    boolean newline = true;
    while (!isAtEnd() && !check(TokenType.NEWLINE)) {
      if (match(TokenType.AT)) {
        Expression.Numeric row = parseNumericExpression();
        consume(TokenType.COMMA, "Expected ',' after AT row");
        items.add(new PrintItem.At(row, parseNumericExpression()));
      } else if (match(TokenType.TAB)) {
        items.add(new PrintItem.Tab(parseNumericExpression()));
      } else {
        items.add(new PrintItem.Expr(parseExpression()));
      }
      if (match(TokenType.SEMICOLON)) {
        newline = !isAtEnd() && !check(TokenType.NEWLINE);
      } else if (match(TokenType.COMMA)) {
        items.add(new PrintItem.Tab(new Expression.Numeric.Literal(-1)));
        newline = !isAtEnd() && !check(TokenType.NEWLINE);
      } else {
        break;
      }
    }
    return factory.create(items, newline);
  }

  @FunctionalInterface
  private interface XyFactory {
    Statement create(Expression.Numeric x, Expression.Numeric y);
  }

  private Statement parseXy(XyFactory f) {
    Expression.Numeric x = parseNumericExpression();
    consume(TokenType.COMMA, "Expected ','");
    return f.create(x, parseNumericExpression());
  }

  private Expression.Numeric parseOptionalNumeric(double defaultValue) {
    if (!isAtEnd() && !check(TokenType.NEWLINE)) {
      return parseNumericExpression();
    }
    return new Expression.Numeric.Literal(defaultValue);
  }

  private Expression.Numeric parseNumericExpression() {
    return parseOr();
  }

  private Expression.String parseStringExpression() {
    return parseStringConcatenation();
  }

  private Expression parseExpression() {
    if (isStringNext()) return parseStringExpression();
    return parseNumericExpression();
  }

  private Expression.Numeric parseOr() {
    Expression.Numeric left = parseAnd();
    while (match(TokenType.OR)) {
      left = new Expression.Numeric.BinaryOp(left, TokenType.OR, parseAnd());
    }
    return left;
  }

  private Expression.Numeric parseAnd() {
    Expression.Numeric left = parseNot();
    while (match(TokenType.AND)) {
      left = new Expression.Numeric.BinaryOp(left, TokenType.AND, parseNot());
    }
    return left;
  }

  private Expression.Numeric parseNot() {
    if (match(TokenType.NOT)) {
      return new Expression.Numeric.UnaryOp(TokenType.NOT, parseNot());
    }
    return parseComparison();
  }

  private Expression.Numeric parseComparison() {
    if (isStringNext()) {
      return parseStringComparison();
    } else {
      return parseNumericComparison();
    }
  }

  private Expression.Numeric parseStringComparison() {
    Expression.String left = parseStringConcatenation();
    if (match(
        TokenType.EQUALS,
        TokenType.NOT_EQUALS,
        TokenType.LESS_THAN,
        TokenType.LESS_EQUAL,
        TokenType.GREATER_THAN,
        TokenType.GREATER_EQUAL)) {
      TokenType op = previous().type();
      return new Expression.Numeric.StringComparison(left, op, parseStringConcatenation());
    }
    throw codedException("Expected comparison operator after string expression");
  }

  private Expression.Numeric parseNumericComparison() {
    Expression.Numeric left = parseAddSub();
    if (match(
        TokenType.EQUALS,
        TokenType.NOT_EQUALS,
        TokenType.LESS_THAN,
        TokenType.LESS_EQUAL,
        TokenType.GREATER_THAN,
        TokenType.GREATER_EQUAL)) {
      TokenType op = previous().type();
      return new Expression.Numeric.NumericComparison(left, op, parseAddSub());
    }
    return left;
  }

  private Expression.Numeric parseAddSub() {
    Expression.Numeric left = parseMulDiv();
    while (match(TokenType.PLUS, TokenType.MINUS)) {
      TokenType op = previous().type();
      left = new Expression.Numeric.BinaryOp(left, op, parseMulDiv());
    }
    return left;
  }

  private Expression.Numeric parseMulDiv() {
    Expression.Numeric left = parseNumericUnary();
    while (match(TokenType.MULTIPLY, TokenType.DIVIDE)) {
      TokenType op = previous().type();
      left = new Expression.Numeric.BinaryOp(left, op, parseNumericUnary());
    }
    return left;
  }

  private Expression.Numeric parsePower() {
    Expression.Numeric left = parseNumericPrimary();
    if (match(TokenType.POWER)) {
      left = new Expression.Numeric.BinaryOp(left, TokenType.POWER, parsePower());
    }
    return left;
  }

  private Expression.Numeric parseNumericUnary() {
    if (match(TokenType.MINUS)) {
      return new Expression.Numeric.UnaryOp(TokenType.MINUS, parseNumericUnary());
    }
    if (match(TokenType.PLUS)) {
      return parseNumericUnary();
    }
    return parsePower();
  }

  private Expression.Numeric parseNumericPrimary() {
    if (match(TokenType.NUMERIC_LITERAL)) {
      return new Expression.Numeric.Literal(Double.parseDouble(previous().rep()));
    }
    if (match(TokenType.LEFT_PAREN)) {
      Expression.Numeric expr = parseNumericExpression();
      consume(TokenType.RIGHT_PAREN, "Expected ')' after numeric expression");
      return expr;
    }
    if (checkFunction()) {
      TokenType type = peek().type();
      if (type == TokenType.PI || type == TokenType.RND) {
        advance();
        return new Expression.Numeric.NullaryCall(type);
      } else if (type == TokenType.VAL || type == TokenType.CODE || type == TokenType.LEN) {
        advance();
        return new Expression.Numeric.FuncCallStr(type, parseStringPrimary());
      } else {
        advance();
        return new Expression.Numeric.FuncCall(type, parseNumericUnary());
      }
    }
    if (match(TokenType.IDENTIFIER)) {
      String name = previous().rep();
      if (check(TokenType.LEFT_PAREN)) {
        return parseNumericSubscript(name);
      }
      if (name.endsWith("$")) {
        throw codedException("Expected numeric variable");
      }
      return new Expression.Numeric.ScalarRef(name);
    }
    throw codedException("Unexpected token in numeric expression: " + peek());
  }

  private Expression.String parseStringConcatenation() {
    Expression.String left = parseStringPrimary();
    while (match(TokenType.PLUS)) {
      left = new Expression.String.Concatenation(left, parseStringPrimary());
    }
    return left;
  }

  private Expression.String parseStringPrimary() {
    if (match(TokenType.STRING_LITERAL)) {
      return new Expression.String.Literal(previous().rep());
    }
    if (match(TokenType.LEFT_PAREN)) {
      Expression.String expr = parseStringExpression();
      consume(TokenType.RIGHT_PAREN, "Expected ')' after string expression");
      return expr;
    }
    if (checkFunction()) {
      TokenType type = peek().type();
      if (type == TokenType.INKEY_STR) {
        advance();
        return new Expression.String.NullaryCall(type);
      }
      if (type == TokenType.CHR_STR || type == TokenType.STR_STR) {
        advance();
        return new Expression.String.FuncCall(type, parseNumericExpression());
      }
    }
    if (match(TokenType.IDENTIFIER)) {
      String name = previous().rep();
      if (check(TokenType.LEFT_PAREN)) {
        return parseStringSubscript(name);
      }
      if (!name.endsWith("$")) {
        throw codedException("Expected string variable");
      }
      return new Expression.String.ScalarRef(name);
    }
    throw codedException("Unexpected token in string expression: " + peek());
  }

  private boolean isStringNext() {
    int lookahead = 0;
    while (pos + lookahead < tokens.size()
        && tokens.get(pos + lookahead).type() == TokenType.LEFT_PAREN) {
      lookahead++;
    }
    if (pos + lookahead >= tokens.size()) {
      return false;
    }
    Token t = tokens.get(pos + lookahead);
    if (t.type() == TokenType.STRING_LITERAL) {
      return true;
    }
    if (t.type() == TokenType.IDENTIFIER && t.rep().endsWith("$")) {
      return true;
    }
    return t.type() == TokenType.CHR_STR
        || t.type() == TokenType.STR_STR
        || t.type() == TokenType.INKEY_STR;
  }

  private boolean checkFunction() {
    return pos < tokens.size() && tokens.get(pos).type().tokenClass() == TokenClass.FUNCTION;
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType type) {
    return !isAtEnd() && peek().type() == type;
  }

  private Token advance() {
    if (!isAtEnd()) {
      ++pos;
    }
    return previous();
  }

  private Token peek() {
    return tokens.get(pos);
  }

  private Token previous() {
    return tokens.get(pos - 1);
  }

  private boolean isAtEnd() {
    return pos >= tokens.size() || peek().type() == TokenType.EOF;
  }

  private Token consume(TokenType type, String msg) {
    if (check(type)) {
      return advance();
    }
    throw codedException(msg + " at " + peek());
  }

  private void skipNewlines() {
    while (match(TokenType.NEWLINE)) {}
  }

  private ReportException codedException(String msg) {
    return new ReportException(ReportCode.NONSENSE_IN_BASIC, currentLineLabel, msg);
  }
}
