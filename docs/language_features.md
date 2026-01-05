# BazLang Reference Manual

BazLang is a dialect of BASIC loosely modeled on the Sinclair ZX81.
This document serves as a comprehensive reference for the language's syntax,
commands, functions, and behaviors.

## 1. Program Structure

### Line Labels

- All executable lines in a stored program must begin with a numeric line
  label (1 to 999,999,999).
- Line label numbers must be strictly increasing.
- Statements entered without line labels are executed immediately
  (Immediate Mode).

### Comments

- `REM comment`: Standard BASIC comment (must have a line label if stored).
- `# comment`: Line comment (ignored by parser, can be used in source files
  without labels).

### Limitations

- **No Multi-Statement Lines**: The colon (`:`) separator is **not**
  supported. Each statement must be on a new line.
- **Strict Typing**: No implicit conversion between strings and numbers.

## 2. Variables and Types

### Numeric Variables

- **Scalars**: `A`, `B1`, `VarName`. Double-precision floating point.
- **Arrays**: `DIM A(10)`, `DIM B(5, 5)`. Accessed via `A(i)` or `B(x, y)`.
- **Indexing**: 1-based. `A(1)` is the first element.

### String Variables

- **Scalars**: `A$`, `Name$`. Dynamic length (unless dimensioned).
- **Arrays/Fixed Strings**: `DIM A$(10)` creates a fixed-length string of 10
  chars. `DIM A$(5, 10)` creates an array of 5 strings, each 10 chars long.
- **Indexing**: 1-based. `A$(i)` refers to the character at index `i`.

### Namespaces

- Numeric scalars, numeric arrays, and string variables (scalars/arrays
  combined) occupy separate namespaces. `A`, `A()`, and `A$` are distinct.

## 3. Operators

### Arithmetic

| Operator | Description      | Precedence  |
| :------- | :--------------- | :---------- |
| `**`     | Exponentiation   | 1 (Highest) |
| `-`      | Unary Minus      | 2           |
| `*`, `/` | Multiply, Divide | 3           |
| `+`, `-` | Add, Subtract    | 4           |

**Note**: `-2**2` is evaluated as `-(2**2) = -4`.

### Relational

Returns `1.0` for True, `0.0` for False.

- `=`, `<>`, `<`, `<=`, `>`, `>=`
- Precedence: 5 (After arithmetic)

### Logical

- `NOT` (Unary) - Precedence 6
- `AND` - Precedence 7
- `OR` - Precedence 8 (Lowest)

### String

- `+`: Concatenation.

## 4. Statements

### Control Flow

- **`GOTO n`**: Jumps to line `n`. If `n` doesn't exist, jumps to the next
  available line.
- **`GOSUB n` ... `RETURN`**: Calls subroutine at `n`.
- **`IF condition THEN statement`**: Executes statement if condition is
  non-zero. No `ELSE`.
- **`FOR var = start TO end STEP step` ... `NEXT var`**: Loop.
    - If initial condition fails (e.g. `10 TO 1`), the loop body and `NEXT`
      are skipped entirely.
    - Variable is not incremented on skip.
- **`STOP`**: Terminates execution.
- **`CONT`**: Continues execution after `STOP` or `BREAK`.
- **`PAUSE n`**: Pauses for `n` frames (approx `n * 20ms`).
- **`RUN n`**: Clears variables and jumps to line `n` (default first line).

### Input / Output

- **`PRINT item; item, ...`**: Prints to stdout.
    - `;`: Concatenate.
    - `,`: Tab to next zone (16 chars).
    - `AT line, col`: Move cursor.
    - `TAB n`: Move to column `n`.
- **`LPRINT ...`**: Prints to stderr (Printer).
- **`INPUT target`**: Reads a line from stdin into `target`.
    - `target` can be scalar (`A`, `A$`) or array ref (`A(1)`, `A$(1 TO 5)`).
- **`CLS`**: Clears screen.
- **`SCROLL`**: Scrolls the screen up one line.
- **`PLOT x, y`**: Draws a block at `(x, y)`.
- **`UNPLOT x, y`**: Erasers a block at `(x, y)`.
- **`LIST`, `LLIST`**: Lists program source.

### Data Management

- **`LET target = value`**: Assignment.
- **`DIM var(dims)`**: Allocates array.
    - `DIM A(10)`: Numeric array size 10.
    - `DIM A$(10)`: Fixed string length 10.
    - `DIM A$(5, 10)`: 5 strings of length 10.
- **`CLEAR`**: Clears all variables.
- **`NEW`**: Wipes program and variables.
- **`SAVE "file"`, `LOAD "file"`**: Disk operations.

### No-Ops (Compatibility)

- `COPY`, `FAST`, `POKE`, `SLOW`.

## 5. Functions

### Numeric Functions

- **`ABS(x)`**: Absolute value.
- **`ACS(x)`**: Arccosine (radians).
- **`ASN(x)`**: Arcsine (radians).
- **`ATN(x)`**: Arctangent (radians).
- **`COS(x)`**: Cosine (radians).
- **`EXP(x)`**: Exponential ($e^x$).
- **`INT(x)`**: Floor integer.
- **`LEN(s)`**: Length of string `s`.
- **`LN(x)`**: Natural logarithm.
- **`PEEK(addr)`**: Returns 0 (Hardware emulation not implemented).
- **`PI`**: Constant $\pi$.
- **`RND`**: Random number $0 \le n < 1$.
- **`SGN(x)`**: Sign (-1, 0, 1).
- **`SIN(x)`**: Sine (radians).
- **`SQR(x)`**: Square root.
- **`TAN(x)`**: Tangent (radians).
- **`VAL(s)`**: Parses string to number.
- **`CODE(s)`**: Unicode code point of first char in `s`.
- **`USR(x)`**: Machine code call (Returns 0).

### String Functions

- **`CHR$(x)`**: Character from code point `x`.
- **`INKEY$`**: Reads current key press (non-blocking). Returns empty string
  if none.
- **`STR$(x)`**: Formats number `x` as string.

## 6. Subscript & Slicing Syntax

### Numeric Arrays

- `A(x)`: Element at index `x`.
- `A(x, y)`: Element at indices `x, y`.

### String Arrays & Slicing

Unified syntax for character access and slicing.

- **`A$(x)`**: Character at index `x` (Scalar or 1D fixed string).
- **`A$(x TO y)`**: Substring from `x` to `y`.
- **`A$(i, x)`**: Character at index `x` of $i$-th element (2D array).
- **`A$(i, x TO y)`**: Substring of $i$-th element (2D array).

**Slicing Rules:**

- For **Scalar/1D Fixed Strings**: `(start TO end)`.
- For **N-Dimension Arrays**: The slice `TO` must be the **last** dimension.
    - Valid: `A$(1, 2 TO 5)`
    - Invalid: `A$(1 TO 2, 5)`
