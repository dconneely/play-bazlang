# BazLang Reference

BazLang is a BASIC dialect based on Sinclair ZX BASIC (supporting a superset of both ZX81 and ZX
Spectrum BASIC). This file lists the available commands, functions, and syntax rules.

## Structure

### Lines

- Every line in a program needs a number (1 to 999,999,999).
- Line numbers must go up.
- If you type a command without a number, it runs immediately.

### Comments

- `REM comment`: Standard comment.
- `# comment`: Line comment (ignored by the parser, useful for source files).

### Limits

- **Multi-statement lines**: You can use `:` to put multiple commands on one line (e.g.,
  `10 CLS : PRINT "HELLO"`). In an `IF` statement, if the condition is false, the remainder of the
  line is skipped.
- **Strict Typing**: You cannot mix strings and numbers without converting them.

## Variables

### Numbers

- **Simple Variables**: `a`, `b1`, `count`. These are double-precision decimals.
- **Arrays**: `DIM a(10)`. Access with `a(1)`. Indices start at 1.

### Strings

- **Simple Variables**: `a$`, `name$`. These can change length.
- **Fixed Strings**: `DIM a$(10)` is a string of 10 bytes.
- **String Arrays**: `DIM a$(5, 10)` is 5 strings, each 10 bytes long.
- **Indexing**: `a$(1)` is the first byte.
- **Byte Semantics**: Strings are byte arrays internally. `LEN` returns the byte count. String
  literals and input from `INPUT` are stored as UTF-8 bytes. When printed, bytes are decoded
  normally; lone invalid bytes 0xNN are displayed as the utf8-c8 synthetic `?xNN`. For behaviour of
  fixed-length string arrays with UTF-8 characters, see
  [implementation.md](implementation.md#language-quirks--sinclair-zx-basic-eccentricities).

### Namespaces

Variables with different types don't clash. `a`, `a(1)`, and `a$` are all different variables.
However, the names of strings and character arrays would clash. So there cannot be a string, say
`a$`, and a character array, say `a$()` with the same name.

## Operators

### Math

| Operator    | Action           | Priority    |
|:------------|:-----------------|:------------|
| `^` or `**` | Power            | 1 (Highest) |
| `-`         | Negative (Unary) | 2           |
| `*`, `/`    | Multiply, Divide | 3           |
| `+`, `-`    | Add, Subtract    | 4           |

**Note**: `-2^2` means `-(2^2)`, which is `-4`.

### Comparisons

Returns `1` for True, `0` for False.

- `=`, `<>`, `<`, `<=`, `>`, `>=`

### Logic

- `NOT` - Returns `1` if operand is `0`, else `0`
- `AND` - `a AND b` returns `a` if `b≠0`, else `0` for numeric `a`; `a$ AND b` returns `a$` if
  `b≠0`, else `""` for string `a$` (Sinclair ZX BASIC style)
- `OR` - `a OR b` returns `1` if `b≠0`, else `a` (Sinclair ZX BASIC style, numeric operands only)

### Strings

- `+` joins two strings together.

## Commands

### Flow Control

- **`GO TO n`** (alias **`GOTO n`**): Jump to line `n`.
  If line `n` doesn't exist, jumps to the next numerically higher line.
- **`GO SUB n`** (alias **`GOSUB n`**) ... `RETURN`: Call a subroutine.
- **`IF condition THEN statement`**: Run statement if true. No `ELSE`.
- **`FOR varname = start TO end STEP step` ... `NEXT varname`**: Loop.
  - Note: While the Spectrum only allows single-character loop variables (`A` to `Z`), BazLang
    supports multi-character loop variables. See
    [implementation.md](implementation.md#language-quirks--sinclair-zx-basic-eccentricities) for loop
    execution quirks.
- **`STOP`**: Stop the program.
- **`CONTINUE`** (alias **`CONT`**): Continue after a `STOP`.
- **`PAUSE n`**: Wait for `n` frames (each frame is 1/50 second = 20ms).
  Fractional values are accepted, e.g. `PAUSE 0.5` waits 10ms.
- **`RUN n`**: Restart program from line `n`.

### Input / Output

- **`PRINT`**: Print to screen.
  - `;`: Join items.
  - `,`: Tab to next zone.
  - `'`: Advance print position to start of next line.
  - `AT y, x`: Move cursor.
  - `TAB n`: Move to column `n`.
  - `INK n` / `PAPER n` / `FLASH n` / `BRIGHT n` / `INVERSE n` / `OVER n`: Temporary colour/style
    modifiers for the print statement.
- **`LPRINT`**: Print to "printer" (standard error).
- **`INPUT varname`**: Ask user for input.
  For numeric variables, the input is evaluated as an expression.
  If the expression is invalid, the user is prompted with "Syntax error? " and can edit their input.
- **`CLS`**: Clear screen.
- **`SCROLL`**: Scroll screen up.
- **`PLOT [modifiers;] x, y`**: Draw a block at coordinates `(x, y)`. Updates the current plot
  position. Accepts colour/style modifiers before coordinates (e.g. `PLOT INK 2; x, y`).
- **`DRAW [modifiers;] x, y`**: Draw a line from the current plot position to relative offset
  `(x, y)`. Updates the current plot position. Accepts colour/style modifiers before coordinates
  (e.g. `DRAW INK 2; x, y`).
  - Coordinates start at `(0,0)` (bottom-left) and extend dynamically based on terminal size.
    For negative coordinate behaviours, see
    [implementation.md](implementation.md#language-quirks--sinclair-zx-basic-eccentricities).
  - Uses Unicode block characters; the resolution depends on the current pixel mode (see
    `PLOTMODE`).
- **`PLOTMODE n`**: Sets the pixel mode for graphics (`PLOT` and `DRAW`):
  - `1` = full cell (1×1, each cell is blank or `█`)
  - `2` = half cell — upper `▀` / lower `▄` (1×2)
  - `4` = quadrant blocks (2×2, default)
  - `6` = sextant blocks (2×3)
  - `8` = braille patterns (2×4)
  - Does not clear the display. Other values give an error.

### Colour / Style Attributes

- **`BRIGHT n`**: Set active brightness style (`1` = bright, `0` = normal, `8` = inherit/default).
- **`FLASH n`**: Set active flashing style (`1` = flashing, `0` = normal, `8` = inherit/default).
- **`INK n`**: Set active foreground text/pixel ink colour (0-7, or `-1` = default terminal
  foreground, `8` = inherit/default).
- **`INVERSE n`**: Set active inverse style (`1` = inverse colour, `0` = normal, `8` = inherit/default).
- **`OVER n`**: Set active overlay style for graphics (`1` = XOR/overlay, `0` = overwrite,
  `8` = inherit/default).
- **`PAPER n`**: Set active background paper colour (0-7, or `-1` = default terminal background,
  `8` = inherit/default).

### Program Management

- **`LIST [n [TO [m]]]`**: Show program code. `LIST` shows all; `LIST n` shows from line `n` to
  end; `LIST TO m` shows from start to line `m`; `LIST n TO m` shows lines `n` through `m`.
- **`LLIST [n [TO [m]]]`**: Same as `LIST` but outputs to standard error.

### Environment

- **`NEW`**: Clear program and all variables.
- **`CLEAR`**: Clear all variables (keeps program).
- **`SAVE "file"`**: Save program to file.
- **`LOAD "file"`**: Load program from file.
- **`RANDOMIZE n`** (aliases **`RAND n`**, **`RANDOMISE n`**): Seed the random number generator.
  If `n` is `0` or omitted, it seeds dynamically using system entropy.

### Data

- **`LET varname = value`**: Set a variable.
- **`DIM varname(size)`**: Create an array.
- **`DATA value1, value2, ...`**: Define a list of constant values to be read by `READ`.
- **`READ varname1, varname2, ...`**: Read values from `DATA` statements into variables.
- **`RESTORE [n]`**: Reset the data pointer to the first `DATA` statement,
  or optionally to line `n`.

## Functions

### User Functions

- **`DEF FN name(param1, ...) = expr`**: Define a single-line custom function.
- **`FN name(arg1, ...)`**: Call a custom function.

### Math Functions

- **`ABS x`**: Absolute value.
- **`ATTR(row, col)`**: Sinclair Spectrum attribute byte at `(row, col)` computed as
  `(flash * 128) + (bright * 64) + (paper * 8) + ink`. Reports 'Integer out of range' if
  coordinates are out of bounds.
- **`CODE s$`**: Raw byte value (0-255) of first byte in string.
- **`COLOUR(r, g, b)`**: Packs 24-bit RGB values to a BazLang colour number (`16777216 + RGB`).
- **`FRAMES`**: Number of ticks (1 tick = 20 milliseconds) since epoch.
  It increases by `50.0` every second. Fractional ticks are allowed.
- **`INT x`**: Round down to integer.
- **`LEN s$`**: Byte length of string (not character count for multibyte characters).
- **`PI`**: 3.14159...
- **`PLOTH`**: Logical plot height for the current pixel mode (in pixels).
- **`PLOTMODE`**: The current pixel mode id (e.g. 1, 2, 4, 6, 8).
- **`PLOTW`**: Logical plot width for the current pixel mode (in pixels).
- **`PLOTX`**: Current X coordinate of the plot cursor.
- **`PLOTY`**: Current Y coordinate of the plot cursor.
- **`POINT(x, y)`**: Returns `1` if the pixel at `(x, y)` is set, or `0` if it is erased. Only inspects the pixel bitmask; returns `0` if the cell contains non-graphic characters.
- **`RND`**: Random number between 0 and 1.
- **`SGN x`**: Signum (-1, 0, 1).
- **`SQR x`**: Square root.
- **`TEXTH`**: Screen height (in character cells).
- **`TEXTW`**: Screen width (in character cells).
- **`TEXTX`**: Current text cursor column (X coordinate).
- **`TEXTY`**: Current text cursor row (Y coordinate).
- **`UCNEXT(s$, i)`**: Returns the 1-based byte position of the codepoint that starts immediately
  after position `i`. Consistent with utf8-c8: each invalid byte counts as one codepoint of width
  1. Use for codepoint-by-codepoint iteration:
  ```
  10 LET i = 1
  20 IF i > LEN(s$) THEN GOTO 60
  30 LET cp = UCODE(s$(i TO LEN(s$)))
  40 LET i = UCNEXT(s$, i)
  50 GOTO 20
  60 REM ...
  ```
- **`UCODE s$`**: Unicode codepoint value of first character (UTF-8 decoded).
- **`VAL s$`**: Evaluate string as numeric expression (not just parse a literal).
- **`XATTR(row, col, select)`**: Extended attribute cell value at `(row, col)`. The `select` code
  determines the return value: `0`=ink color, `1`=paper color, `2`=flash, `3`=bright, `4`=inverse,
  `5`=italic, `6`=underline, `7`=strikethrough, `8`=faint. Reports 'Integer out of range' if
  parameters are out of range.
- **Logs**: `EXP`, `LN`.
- **Trig**: `SIN`, `COS`, `TAN`, `ASN`, `ACS`, `ATN`.

### String Functions

- **`CHR$ x`**: Single-byte string with raw byte value `x` (0-255). Error for x > 255.
- **`INKEY$`**: Check key press.
- **`SCREEN$(row, col)`**: Character at screen coordinate `(row, col)` as a single-byte string.
  Returns `""` if the character codepoint is outside `0..127`. Reports 'Integer out of range' if
  coordinates are out of bounds.
- **`STR$ x`**: Convert number to string.
- **`UCHR$ x`**: String containing the UTF-8 encoding of Unicode codepoint `x`.
  Use for codepoints above U+007F, e.g. `UCHR$(9608)` for the full-block character █.
- **`UINKEY$`**: Check key press, interpreting multibyte UTF-8 sequences and ANSI escape sequences.
- **`USCREEN$(row, col)`**: Character at screen coordinate `(row, col)` as a UTF-8 string.
  Returns Unicode Braille/quadrant characters if the location has been plotted to. Reports
  'Integer out of range' if coordinates are out of bounds.
- **`VAL$ s$`**: Evaluate a string as a string expression.

## Slicing

You can slice strings and arrays. String indices are **byte offsets** (1-based).

- **`a$(x)`**: Byte at position `x`.
- **`a$(x TO y)`**: Bytes from `x` to `y`.
- **`a$(i, x TO y)`**: Slice of `i`-th string in an array.

**Rule**: The `TO` slice must always be the last part of the index.

### 2D string array row access

When `a$` is declared as a 2D string array (e.g. `DIM a$(rows, cols)`),
a single index `a$(i)` refers to the entire `i`-th row as a string of length `cols`.
This can be used for reading, comparison, and assignment:

```
DIM board$(8, 8)
LET board$(1) = board$(8)   : REM copy row 8 to row 1
IF board$(3) = board$(4) THEN ...  : REM compare two rows
```

### String array initialisation

String arrays (both fixed-length scalars and 2D arrays) are initialised to all spaces (`CHR$ 32`).
This is consistent with ZX Spectrum BASIC. Simple (variable-length) string variables are
initialised to the empty string `""`.

```
DIM grid$(24, 80)   : REM all 1920 bytes are spaces
PRINT grid$(1, 1)   : REM prints " "
PRINT CODE grid$(1, 1)  : REM prints 32
```


## Number Formatting

Numbers are displayed in Sinclair ZX BASIC style:

- Up to 8 significant digits
- Scientific notation (E notation) for very small or very large values
- No trailing zeros after decimal point
- Integers display without a decimal point

Examples: `42`, `3.14159`, `1.23E+15`, `-5E-8`

## Divergences from Sinclair ZX BASIC

BazLang follows Sinclair ZX BASIC semantics where practical, with these intentional differences:

| Feature        | BazLang                              | Sinclair ZX BASIC              |
|:---------------|:-------------------------------------|:-------------------------------|
| Character set  | UTF-8                                | Proprietary ZX charset         |
| Variable names | Multi-character allowed              | Single letters for arrays/FOR  |
| PAUSE >= 32767 | Waits that many frames               | Waits forever until keypress   |
| File I/O       | File system                          | Tape                           |
| RND algorithm  | Java Random                          | Linear feedback shift register |
| Report codes   | Same codes & messages, extra context | Same codes & messages          |
| PRINT AT bounds| Clamps to terminal bounds            | Throws "5 Out of screen"       |
| POINT bounds   | Returns 0                            | Throws "B Integer out of range"|

## REPL-Only Commands

The following commands are only available in the REPL (interactive mode) and cannot be stored as
part of a program.

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

Pre-fills the input with the contents of line 100 for editing. If the line doesn't exist,
pre-fills with just the line number followed by a space.

### REFORMAT

Normalise program formatting:

```
REFORMAT
REFORMAT 100
REFORMAT 10 TO 50
REFORMAT TO 100
REFORMAT 100 TO
REFORMAT TO
```

Reformats the specified range of lines (or all lines if no range is given). It converts keywords
and function names to uppercase and normalises whitespace around operators and separators.

### RENUM

Renumber program lines:

```
RENUM
RENUM 100
RENUM 100 STEP 5
RENUM 100, 50 TO 80
```

`RENUM` renumbers all lines starting at 10 with step 10. `RENUM n` starts at `n`.
`RENUM n STEP s` uses step `s`. A comma introduces a range: `RENUM n, from TO to` renumbers lines
`from` through `to` starting at `n`. Updates `GO TO`/`GO SUB` literal targets automatically.
