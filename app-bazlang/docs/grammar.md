# ANTLR grammar implementation

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

## Key grammar patterns

### Expression precedence

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

### Case insensitivity

The grammar uses ANTLR's `caseInsensitive` option for keywords and identifiers:

```antlr
options { caseInsensitive=true; }

PRINT : 'PRINT';  // Matches PRINT, print, Print, etc.
```

This allows `PRINT`, `print`, and `Print` to all match the same token. Variable names are
normalised to uppercase when building the AST, so `myVar`, `MYVAR`, and `MyVar` all refer
to the same variable.

String literal _contents_ remain case-sensitive since they're captured as-is between quotes.

### Numeric vs string identifiers

The grammar distinguishes numeric and string variables at the lexer level:

```antlr
STR_IDENTIFIER : [A-Z][A-Z0-9_]*'$' ;
NUM_IDENTIFIER : [A-Z][A-Z0-9_]* ;
```

This ensures `a` is always a numeric variable and `a$` is always a string variable, without
ambiguity. (The pattern uses `[A-Z]` but matches case-insensitively due to the grammar option.)

### Function binding

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

### String subscripts and slicing

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

## Statements vs. REPL commands

BazLang strictly separates program execution logic (statements) from interactive IDE/environment
actions (REPL commands). 

```antlr
replLine
    : NUM_LITERAL statements? EOF                          # NumberedLine
    | replCommand EOF                                      # ReplCommandLine
    | statements EOF                                       # ImmediateLine
    ;
```

As defined in the `replLine` root parsing rule:
- **Statements** (`PRINT`, `LET`, `IF`, etc.) can be placed inside numbered program lines, or
  chained together with colons in immediate execution mode (e.g., `PRINT 1 : PRINT 2`).
- **REPL Commands** (`RENUM`, `REFORMAT`, `EDIT`, `DELETE`) modify the program or interact with the
  editor. They **cannot** be placed inside numbered program lines, they **cannot** be combined with
  other statements using a colon, and they **must** be the only instruction entered on the line.

## Erasing graphics (`UNPLOT` / `UNDRAW`)

Unlike ZX81 BASIC, which had a dedicated `UNPLOT` statement, BazLang adheres to the original ZX
Spectrum philosophy for erasing graphics. There are no explicit `UNPLOT` or `UNDRAW` statements.
Instead, to erase lines or pixels, you should redraw them using style modifiers:

- **`OVER 1`**: Redrawing the same line using `PLOT OVER 1; x, y` or `DRAW OVER 1; dx, dy` will XOR
  the pixels against the screen, perfectly restoring the background state without leaving holes in
  intersecting lines (provided the lines were also drawn using `OVER 1`).
- **`INVERSE 1`**: You can also manually draw over a pixel using the background colour by using
  `PLOT INVERSE 1; x, y`.

When plotting, `INVERSE` means clear pixels rather than set them, and `OVER` means invert the
current pixel state (which is slightly confusing, but consistent with the Sinclair ZX Spectrum).

## Adding new features

To add a new operator (e.g., modulo `%`):

1. Add to grammar: `| numExpr '%' numExpr  # NumModExpr`
2. Add visitor method to `BazLangExecutor`
3. Write tests

The grammar serves as both the implementation and the documentation of the language syntax.
