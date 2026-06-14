# ANTLR Grammar Implementation

BazLang uses ANTLR 4 to generate its lexer and parser from a declarative grammar file
(`BazLang.g4`).

## Why ANTLR?

| Aspect              | Hand-Written              | ANTLR                        |
|---------------------|---------------------------|------------------------------|
| **Style**           | Imperative (how to parse) | Declarative (what to parse)  |
| **Precedence**      | Manual climbing           | Automatic from structure     |
| **Maintainability** | Tedious changes           | Update grammar rules         |
| **Error Recovery**  | Basic                     | Sophisticated built-in       |
| **Documentation**   | Comments + separate docs  | Grammar IS the documentation |

## Key Grammar Patterns

### Expression Precedence

ANTLR handles operator precedence by ordering - earlier alternatives bind tighter:

```antlr
numExpr
    : NUM_LITERAL                                           # NumLiteralExpr
    | NUM_IDENTIFIER                                        # NumVarExpr
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'         # NumArrayExpr
    | '(' numExpr ')'                                       # NumParenExpr
    | numFunc                                               # NumFuncCallExpr
    | <assoc=right> numExpr ('**' | '^') numExpr            # NumPowerExpr
    | '-' numExpr                                           # NumUnaryMinusExpr
    | numExpr ('*' | '/') numExpr                           # NumMulDivExpr
    | numExpr ('+' | '-') numExpr                           # NumAddSubExpr
    | numExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') numExpr # NumCompExpr
    | strExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') strExpr # StrCompExpr
    | NOT numExpr                                           # NumNotExpr
    | numExpr AND numExpr                                   # NumAndExpr
    | numExpr OR numExpr                                    # NumOrExpr
    ;
```

Note: `<assoc=right>` makes `**` and `^` right-associative, so `2^3^4` = `2^(3^4)`.

### Case Insensitivity

The grammar uses ANTLR's `caseInsensitive` option for keywords and identifiers:

```antlr
options { caseInsensitive=true; }

PRINT : 'PRINT';  // Matches PRINT, print, Print, etc.
```

This allows `PRINT`, `print`, and `Print` to all match the same token. Variable names are
normalised to uppercase when building the AST, so `myVar`, `MYVAR`, and `MyVar` all refer
to the same variable.

String literal _contents_ remain case-sensitive since they're captured as-is between quotes.

### Numeric vs String Identifiers

The grammar distinguishes numeric and string variables at the lexer level:

```antlr
STR_IDENTIFIER : [A-Z][A-Z0-9_]*'$' ;
NUM_IDENTIFIER : [A-Z][A-Z0-9_]* ;
```

This ensures `a` is always a numeric variable and `a$` is always a string variable, without
ambiguity. (The pattern uses `[A-Z]` but matches case-insensitively due to the grammar option.)

### Function Binding

Functions bind tightly to their arguments (atoms), not full expressions:

```antlr
numFunc
    : SIN numAtom
    | COS numAtom
    // ...
    ;

numAtom
    : NUM_LITERAL
    | NUM_IDENTIFIER
    | '(' numExpr ')'
    | numFunc
    ;
```

This means `SIN PI/2` parses as `SIN(PI)/2`, not `SIN(PI/2)`.

Multi-argument functions require explicit parentheses and comma-separated full expressions
(not just atoms), consistent with ZX Spectrum BASIC functions like `ATTR` and `SCREEN$`:

```antlr
numFunc
    : UCNEXT '(' strExpr ',' numExpr ')'
    // ...
    ;
```

This means `UCNEXT(a$, i+1)` works as expected.

### String Subscripts and Slicing

String subscripts use a unified rule that allows indices and optional slicing:

```antlr
strSubscript
    : numExpr (',' numExpr)*                        // indices only
    | numExpr (',' numExpr)* ',' numExpr? TO numExpr?  // indices + slice
    | numExpr? TO numExpr?                          // slice only
    ;
```

This supports: `a$(1)`, `a$(1,2)`, `a$(1 TO 5)`, `a$(TO 5)`, `a$(1 TO)`, `a$(TO)`,
`a$(1, 2 TO 5)`, etc.

## Adding New Features

To add a new operator (e.g., modulo `%`):

1. Add to grammar: `| numExpr '%' numExpr  # NumModExpr`
2. Add visitor method to `BazLangExecutor`
3. Write tests

The grammar serves as both the implementation and the documentation of the language syntax.
