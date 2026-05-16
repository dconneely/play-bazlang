# BazLang Reference

BazLang is a BASIC dialect based on the Sinclair ZX81. This file lists the available
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
  `S$ AND B` returns S\$ if B≠0, else "" for string S\$ (ZX81 style)
- `OR` - `A OR B` returns 1 if B≠0, else A (ZX81 style, numeric operands only)

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
- **`PAUSE n`**: Wait for `n` frames (each frame is 1/50 second = 20ms). Fractional values are accepted, e.g. `PAUSE 0.5` waits 10ms.
- **`RUN n`**: Restart program from line `n`.

### Input / Output

- **`PRINT`**: Print to screen.
    - `;`: Join items.
    - `,`: Tab to next zone.
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

### Data

- **`LET var = value`**: Set a variable.
- **`DIM var(size)`**: Create an array.

### ZX81 Compatibility Stubs

These commands are recognized but have no effect (for source compatibility):

- **`COPY`**: Would copy screen to printer on ZX81.
- **`FAST`** / **`SLOW`**: Display modes on ZX81.
- **`POKE addr, val`**: Memory access on ZX81.
- **`RAND n`**: Seed random number generator (ignored; use for source compatibility).

## 5. Functions

### Math Functions

- **`ABS x`**: Absolute value.
- **`INT x`**: Round down to integer.
- **`RND`**: Random number between 0 and 1.
- **`SGN x`**: Sign (-1, 0, 1).
- **`SQR x`**: Square root.
- **`PI`**: 3.14159...
- **`LEN s`**: String length.
- **`VAL s`**: Evaluate string as numeric expression (not just parse a literal).
- **`CODE s`**: Unicode value of first char.
- **Trig**: `SIN`, `COS`, `TAN`, `ASN`, `ACS`, `ATN`.
- **Logs**: `EXP`, `LN`.
- **`PEEK addr`**: Memory read (always returns 0, for compatibility).
- **`USR addr`**: Machine code call (always returns 0, for compatibility).

### String Functions

- **`CHR$ x`**: Character from code `x`.
- **`STR$ x`**: Convert number to string.
- **`INKEY$`**: Check key press.

## 6. Slicing

You can slice strings and arrays.

- **`A$(x)`**: Character at `x`.
- **`A$(x TO y)`**: String from `x` to `y`.
- **`A$(i, x TO y)`**: Slice of `i`-th string in an array.

**Rule**: The `TO` slice must always be the last part of the index.

## 7. Number Formatting

Numbers are displayed in ZX81 style:
- Up to 8 significant digits
- Scientific notation (E notation) for very small or very large values
- No trailing zeros after decimal point
- Integers display without a decimal point

Examples: `42`, `3.14159`, `1.23E+15`, `-5E-8`

## 8. Divergences from ZX81 BASIC

BazLang follows ZX81 BASIC semantics where practical, with these intentional differences:

| Feature        | BazLang                 | ZX81 BASIC                     |
|:---------------|:------------------------|:-------------------------------|
| Character set  | UTF-8                   | Proprietary ZX81 charset       |
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
`to` starting at `n`. Updates `GOTO`/`GOSUB` literal targets automatically.
