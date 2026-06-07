# BazLang Reference

BazLang is a BASIC dialect based on Sinclair ZX BASIC (supporting a superset of both ZX81 and ZX Spectrum). This file lists the available
commands, functions, and syntax rules.

## 1. Structure

### Lines

- Every line in a program needs a number (1 to 999,999,999).
- Line numbers must go up.
- If you type a command without a number, it runs immediately.

### Comments

- `REM comment`: Standard comment.
- `# comment`: Line comment (ignored by the parser, useful for source files).

### Limits

- **Multi-statement lines**: You can use `:` to put multiple commands on one line (e.g., `10 CLS : PRINT "HELLO"`). In an `IF` statement, if the condition is false, the remainder of the line is skipped.
- **Strict Typing**: You cannot mix strings and numbers without converting them.

## 2. Variables

### Numbers

- **Simple Variables**: `A`, `B1`, `Count`. These are double-precision decimals.
- **Arrays**: `DIM A(10)`. Access with `A(1)`. Indices start at 1.

### Strings

- **Simple Variables**: `A$`, `Name$`. These can change length.
- **Fixed Strings**: `DIM A$(10)` is a string of 10 bytes.
- **String Arrays**: `DIM A$(5, 10)` is 5 strings, each 10 bytes long.
- **Indexing**: `A$(1)` is the first byte.
- **Byte Semantics**: Strings are byte arrays internally. `LEN` returns the byte count.
  String literals and input from `INPUT` are stored as UTF-8 bytes. When printed, bytes are
  decoded normally; lone invalid bytes 0xNN are displayed as the utf8-c8 synthetic `?xNN`.

### Namespaces

Variables with different types don't clash. `A`, `A(1)`, and `A$` are all different
variables. However, the names of strings and character arrays would clash. So there
cannot be a string, say `A$`, and a character array, say `A$()` with the same name.

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

- `NOT` - Returns 1 if operand is 0, else 0
- `AND` - `A AND B` returns A if B≠0, else 0 for numeric A;
  `S$ AND B` returns S\$ if B≠0, else "" for string S\$ (Sinclair ZX style)
- `OR` - `A OR B` returns 1 if B≠0, else A (Sinclair ZX style, numeric operands only)

### Strings

- `+` joins two strings together.

## 4. Commands

### Flow Control

- **`GO TO n`** (alias **`GOTO n`**): Jump to line `n`. If missing, jumps to the next one.
- **`GO SUB n`** (alias **`GOSUB n`**) ... `RETURN`: Call a subroutine.
- **`IF condition THEN statement`**: Run statement if true. No `ELSE`.
- **`FOR var = start TO end STEP step` ... `NEXT var`**: Loop.
- **`STOP`**: Stop the program.
- **`CONTINUE`** (alias **`CONT`**): Continue after a `STOP`.
- **`PAUSE n`**: Wait for `n` frames (each frame is 1/50 second = 20ms). Fractional values are accepted, e.g. `PAUSE 0.5` waits 10ms.
- **`RUN n`**: Restart program from line `n`.

### Input / Output

- **`PRINT`**: Print to screen.
    - `;`: Join items.
    - `,`: Tab to next zone.
    - `'`: Advance print position to start of next line.
    - `AT y, x`: Move cursor.
    - `TAB n`: Move to column `n`.
- **`LPRINT`**: Print to "printer" (standard error).
- **`INPUT var`**: Ask user for input. For numeric variables, the input is evaluated as an expression. If the expression is invalid, the user is prompted with "Syntax error? " and can edit their input.
- **`CLS`**: Clear screen.
- **`SCROLL`**: Scroll screen up.
- **`PLOT x, y`**: Draw a block at coordinates `(x, y)`.
    - Coordinates start at `(0,0)` (bottom-left) and extend dynamically based on terminal size.
    - Uses Unicode block characters; the resolution depends on the current pixel mode (see `PLOTMODE`).
- **`UNPLOT x, y`**: Erase a block at coordinates `(x, y)`.
- **`PLOTMODE n`**: Sets the pixel mode for `PLOT` and `UNPLOT`:
    - `1` = full cell (1×1, each cell is blank or `█`)
    - `2` = half cell — upper `▀` / lower `▄` (1×2)
    - `4` = quadrant blocks (2×2, default)
    - `6` = sextant blocks (2×3)
    - `8` = braille patterns (2×4)
    - Does not clear the display. Other values give an error.

### Program Management

- **`LIST [n [TO [m]]]`**: Show program code. `LIST` shows all; `LIST n` shows from line `n` to end;
    `LIST TO m` shows from start to line `m`; `LIST n TO m` shows lines `n` through `m`.
- **`LLIST [n [TO [m]]]`**: Same as `LIST` but outputs to standard error.

### Environment

- **`NEW`**: Clear program and all variables.
- **`CLEAR`**: Clear all variables (keeps program).
- **`SAVE "file"`**: Save program to file.
- **`LOAD "file"`**: Load program from file.
- **`RANDOMIZE n`** (aliases **`RAND n`**, **`RANDOMISE n`**): Seed the random number generator. If `n` is `0` or omitted, it seeds dynamically using system entropy.

### Data

- **`LET var = value`**: Set a variable.
- **`DIM var(size)`**: Create an array.

## 5. Functions

### Math Functions

- **`ABS x`**: Absolute value.
- **`INT x`**: Round down to integer.
- **`RND`**: Random number between 0 and 1.
- **`SGN x`**: Sign (-1, 0, 1).
- **`SQR x`**: Square root.
- **`PI`**: 3.14159...
- **`LEN s`**: Byte length of string (not character count for multi-byte characters).
- **`VAL s`**: Evaluate string as numeric expression (not just parse a literal).
- **`CODE s`**: Raw byte value (0-255) of first byte in string.
- **`CODEPOINT s`**: Unicode codepoint value of first character (UTF-8 decoded).
- **`NEXTCP(s, i)`**: Returns the 1-based byte position of the codepoint that starts immediately
  after position `i`. Consistent with utf8-c8: each invalid byte counts as one codepoint of
  width 1. Use for codepoint-by-codepoint iteration:
  ```
  LET I = 1
  WHILE I <= LEN(S$)
    LET CP = CODEPOINT(S$(I TO LEN(S$)))
    LET I = NEXTCP(S$, I)
  WEND
  ```
- **Trig**: `SIN`, `COS`, `TAN`, `ASN`, `ACS`, `ATN`.
- **Logs**: `EXP`, `LN`.

### String Functions

- **`CHR$ x`**: Single-byte string with raw byte value `x` (0-255). Error for x > 255.
- **`CODEPOINT$ x`**: String containing the UTF-8 encoding of Unicode codepoint `x`.
  Use for codepoints above U+007F, e.g. `CODEPOINT$(9608)` for the full-block character █.
- **`STR$ x`**: Convert number to string.
- **`VAL$ s$`**: Evaluate a string as a string expression.
- **`INKEY$`**: Check key press.

## 6. Slicing

You can slice strings and arrays. String indices are **byte offsets** (1-based).

- **`A$(x)`**: Byte at position `x`.
- **`A$(x TO y)`**: Bytes from `x` to `y`.
- **`A$(i, x TO y)`**: Slice of `i`-th string in an array.

**Rule**: The `TO` slice must always be the last part of the index.

## 7. Number Formatting

Numbers are displayed in Sinclair ZX style:
- Up to 8 significant digits
- Scientific notation (E notation) for very small or very large values
- No trailing zeros after decimal point
- Integers display without a decimal point

Examples: `42`, `3.14159`, `1.23E+15`, `-5E-8`

## 8. Divergences from Sinclair ZX BASIC

BazLang follows Sinclair ZX BASIC semantics where practical, with these intentional differences:

| Feature        | BazLang                 | Sinclair ZX BASIC              |
|:---------------|:------------------------|:-------------------------------|
| Character set  | UTF-8                   | Proprietary ZX charset         |
| Variable names | Multi-character allowed | Single letters for arrays/FOR  |
| PAUSE >= 32767 | Waits that many frames  | Waits forever until keypress   |
| File I/O       | File system             | Tape                           |
| RND algorithm  | Java Random             | Linear feedback shift register |
| Report codes   | Similar convention      | Same codes, different messages |

## 9. REPL-Only Commands

The following commands are only available in the REPL (interactive mode) and cannot be stored as part of a program.

### DELETE

Delete program lines:

```
DELETE 100
DELETE 10 TO 50
DELETE TO 100
DELETE 100 TO
DELETE TO
```

`DELETE n` deletes only line `n`; `DELETE n TO m` deletes lines `n` through `m`; `DELETE TO m`
deletes from start to `m`; `DELETE n TO` deletes from `n` to end; `DELETE TO` deletes all lines.
Requires at least one line number or the `TO` keyword. Typing just a line number (e.g., `100`)
at the REPL also deletes that line.

### EDIT

Edit an existing line:

```
EDIT 100
```

Pre-fills the input with the contents of line 100 for editing. If the line doesn't exist, pre-fills with just the line number followed by a space.

### REFORMAT

Normalize program formatting:

```
REFORMAT
REFORMAT 100
REFORMAT 10 TO 50
REFORMAT TO 100
REFORMAT 100 TO
REFORMAT TO
```

Reformats the specified range of lines (or all lines if no range is given). It converts keywords
and function names to uppercase and normalizes whitespace around operators and separators.

### RENUM

Renumber program lines:

```
RENUM
RENUM 100
RENUM 100 STEP 5
RENUM 100, 50 TO 80
```

`RENUM` renumbers all lines starting at 10 with step 10. `RENUM n` starts at `n`. `RENUM n STEP s`
uses step `s`. A comma introduces a range: `RENUM n, from TO to` renumbers lines `from` through
`to` starting at `n`. Updates `GO TO`/`GO SUB` literal targets automatically.
