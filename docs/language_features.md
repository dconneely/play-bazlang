# BazLang Reference

BazLang is a BASIC dialect based on the Sinclair ZX81. This file lists the available commands, functions, and syntax rules.

## 1. Structure

### Lines

- Every line in a program needs a number (1 to 999,999,999).
- Line numbers must go up.
- If you type a command without a number, it runs immediately.

### Comments

- `REM comment`: Standard comment.
- `# comment`: Line comment (ignored by the parser, useful for source files).

### Limits

- **One statement per line**: You cannot use `:` to put multiple commands on one line.
- **Strict Typing**: You cannot mix strings and numbers without converting them.

## 2. Variables

### Numbers

- **Simple Variables**: `A`, `B1`, `Count`. These are double-precision decimals.
- **Arrays**: `DIM A(10)`. Access with `A(1)`. Indices start at 1.

### Strings

- **Simple Variables**: `A$`, `Name$`. These can change length.
- **Fixed Strings**: `DIM A$(10)` is a string of 10 characters.
- **String Arrays**: `DIM A$(5, 10)` is 5 strings, each 10 characters long.
- **Indexing**: `A$(1)` is the first character.

### Namespaces

Variables with different types don't clash. `A`, `A(1)`, and `A$` are all different variables.

## 3. Operators

### Math

| Operator | Action           | Priority    |
| :------- | :--------------- | :---------- |
| `**`     | Power            | 1 (Highest) |
| `-`      | Negative (Unary) | 2           |
| `*`, `/` | Multiply, Divide | 3           |
| `+`, `-` | Add, Subtract    | 4           |

**Note**: `-2**2` means `-(2**2)`, which is `-4`.

### Comparisons

Returns `1` for True, `0` for False.

- `=`, `<>`, `<`, `<=`, `>`, `>=`

### Logic

- `NOT`
- `AND`
- `OR`

### Strings

- `+` joins two strings together.

## 4. Commands

### Flow Control

- **`GOTO n`**: Jump to line `n`. If missing, jumps to the next one.
- **`GOSUB n` ... `RETURN`**: Call a subroutine.
- **`IF condition THEN statement`**: Run statement if true. No `ELSE`.
- **`FOR var = start TO end STEP step` ... `NEXT var`**: Loop.
- **`STOP`**: Stop the program.
- **`CONT`**: Continue after a `STOP`.
- **`PAUSE n`**: Wait for `n` frames.
- **`RUN n`**: Restart program from line `n`.

### Input / Output

- **`PRINT`**: Print to screen.
    - `;`: Join items.
    - `,`: Tab to next zone.
    - `AT y, x`: Move cursor.
    - `TAB n`: Move to column `n`.
- **`LPRINT`**: Print to "printer" (standard error).
- **`INPUT var`**: Ask user for input.
- **`CLS`**: Clear screen.
- **`SCROLL`**: Scroll screen up.
- **`PLOT x, y`**: Draw a block at coordinates `(x, y)`.
    - Coordinates range from `0,0` (bottom-left) to `63,47` (top-right).
    - Uses Unicode 2x2 block characters (quadrants) to simulate higher resolution.
    - Modifies the underlying character cell without overwriting the entire character if possible.
- **`UNPLOT x, y`**: Erase a block at coordinates `(x, y)`.
    - Same coordinate system as `PLOT`.
- **`LIST`**: Show program code.

### Data

- **`LET var = value`**: Set a variable.
- **`DIM var(size)`**: Create an array.
- **`CLEAR`**: Delete all variables.
- **`NEW`**: Delete program and variables.
- **`SAVE "file"`, `LOAD "file"`**: Save or load a script.

## 5. Functions

### Math Functions

- **`ABS(x)`**: Absolute value.
- **`INT(x)`**: Round down to integer.
- **`RND`**: Random number between 0 and 1.
- **`SGN(x)`**: Sign (-1, 0, 1).
- **`SQR(x)`**: Square root.
- **`PI`**: 3.14159...
- **`LEN(s)`**: String length.
- **`VAL(s)`**: Convert string to number.
- **`CODE(s)`**: Unicode value of first char.
- **Trig**: `SIN`, `COS`, `TAN`, `ASN`, `ACS`, `ATN`.
- **Logs**: `EXP`, `LN`.

### String Functions

- **`CHR$(x)`**: Character from code `x`.
- **`STR$(x)`**: Convert number to string.
- **`INKEY$`**: Check key press.

## 6. Slicing

You can slice strings and arrays.

- **`A$(x)`**: Character at `x`.
- **`A$(x TO y)`**: String from `x` to `y`.
- **`A$(i, x TO y)`**: Slice of `i`-th string in an array.

**Rule**: The `TO` slice must always be the last part of the index.