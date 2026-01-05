# Implementation Details

This document provides a low-level overview of the Bazlang interpreter's Java
implementation.

## Abstract Syntax Tree (AST)

The core logic is represented by the `Statement` and `Expression` sealed
interfaces (using Java records).

### Statements

Statements represent the executable instructions. Key changes from standard
ASTs include:

- **Unified LET**: `Let(Expression target, Expression value)` handles both
  numeric and string assignments, as well as scalar and array targets.
- **Unified INPUT**: `Input(Expression target)` allows inputting directly
  into scalars or array elements.
- **Next-Line Logic**: Control flow statements like `GOTO` and `FOR` logic
  rely on `MachineState`'s `NavigableMap` to find the "next" line if a
  target doesn't exist.

### Expressions

Expressions are split into `Expression.Numeric` and `Expression.String` to
enforce parse-time type safety.

- **Precedence**: Implemented via recursive descent in `Parser.java`. The
hierarchy is strictly defined to match the language specification.

## Core Components

### `Lexer`

- **Regex-free**: Uses a simple character-by-character scanning loop for
  performance and simplicity.
- **Keywords**: Case-insensitive, stored in a static map.
- **Output**: A list of `Token` records.

### `Parser`

- **Recursive Descent**: Directly maps grammar rules to methods
  (`parseStatement`, `parseExpression`).
- **L-Value Parsing**: `parseLValue` handles valid assignment targets
  (vars, array refs), used by both `LET` and `INPUT`.
- **Error Handling**: Throws `CodedException` with line numbers attached.

### `MachineState`

- **Program Storage**: `NavigableMap<Integer, Statement>` allows efficient
  lookups for `GOTO` (using `ceilingKey`).
- **Variable Storage**: Separate Maps for numeric/string scalars and arrays.
- **Loop State**: `ForLoopData` stores the limit, step, and the line number
  of the loop body start.

### `Executor` / `Interpreter`

- **Executor**: Stateless visitor that executes a single `Statement` against
  the `MachineState`.
- **Interpreter**: Manages the program counter (`currentLineLabel`) and the
  run loop.
- **Loop Skipping**: When a `FOR` loop condition is initially false, the
  interpreter scans forward for the matching `NEXT` and sets the program
  counter to that line, effectively skipping the loop body *and* the `NEXT`
  statement itself.

### `Evaluator`

- Pure functional component that reduces `Expression` objects to values
  (`double` or `String`).
- Handles array index calculation and bounds checking.
- Manages string slicing logic.

## Key Differences from original ZX81

- **Case-insensitive keywords**: Keywords can be written in any case.
- **Full keyword spelling**: Keywords are spelled out in full, not tokenized.
- **UTF-8 support**: Source files use UTF-8 encoding.
- **Unicode graphics**: PLOT uses Unicode block character (█) instead of
  quadrant graphics.
- **ANSI terminal support**: Cursor positioning and screen clearing use ANSI
  escape sequences.

## Specific Implementations

### `INPUT`

The `INPUT` statement uses `Executor.assignNumeric` or `Executor.assignString`
(shared with `LET`) to allow robust input handling:

1. Reads a line from the `Terminal`.
2. If the target is numeric, attempts to parse as `Double` (defaults to `0.0`
   on failure).
3. If the target is string/array, assigns strictly.

### `FOR-NEXT`

- **Initialization**: `FOR` calculates start, end, step. If the loop should
  not run, it scans for `NEXT`.
- **Iteration**: `NEXT` increments the variable. If the condition holds, it
  sets the PC back to the saved loop start label.
- **Nesting**: Handled via simple variable name lookup. Shadowing a loop
  variable overwrites the outer loop's control data.

## Console I/O

- `Terminal` class abstracts `System.in` / `System.out`.
- Uses ANSI codes for `CLS` and `AT`.
- Buffers input to support `readln`.
