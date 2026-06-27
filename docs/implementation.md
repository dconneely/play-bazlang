# Implementation Details

This document explains how the BazLang interpreter is built using Java.

## Code Structure

The interpreter executes directly from ANTLR's parse tree using a visitor pattern.

### Main Components

- **ANTLR Grammar (`BazLang.g4`)**: Defines the lexer and parser rules declaratively. ANTLR
  generates `BazLangLexer` and `BazLangParser` from this grammar.
- **`AntlrParser`**: A facade that wraps the ANTLR parser, providing simple `parseProgramLines()`
  and `parseReplLine()` methods.
- **`ExpressionEvaluator`**: A visitor that evaluates numeric and string expressions from the
  parse tree.
- **`StatementExecutor`**: A visitor that executes statements. It handles variable assignment,
  I/O operations, and state mutation.
- **`Interpreter`**: Manages the overall flow. It coordinates the executor and evaluator,
  decides which line to run next, handles jumps, and loops until the program stops.
- **`EvalState`**: The program's memory. It stores variables (scalars and arrays), custom
  functions, and the state of any active `FOR` loops.
- **`Program`**: Encapsulates the AST line storage, ensuring the underlying map is protected.
- **`ProgramLine`**: Stores the source text of each line, lazily parses to a parse tree on first
  execution, and caches a flattened statement list to avoid rebuilding it on subsequent calls.

## I/O System (The `io` Package)

Input and output are handled by a set of classes that share a common `BazLangDisplay` interface
(which extends the base `Display` and `Shell` interfaces), isolating the interpreter from the
specific device.

- **`TerminalDisplay`**: The standard version for interactive use. It provides a TUI
  (Text User Interface) with distinct screen regions: an application display area at the top,
  an input area with prompt, and a status bar. Uses the `TerminalEngine` class (which wraps
  JLine) for terminal control, escape sequences, and raw input. Supports command history,
  cursor movement, and handles terminal window resizes gracefully.
- **`StreamDisplay`**: A simpler version used for pipes or non-interactive environments.
  It uses standard Java `System.in` and `System.out`. Graphics (`PLOT`/`UNPLOT`) are no-ops.

The `BazLangDisplay` interface defines methods for screen output (`print`, `println`, `cls`),
graphics (`plot`, `unplot`, `setPlotMode`), input (`readln` with different modes for REPL vs
INPUT), and status updates (`setStatus` for showing report codes).

## Specific Logic

- **Graphics & Rendering**: The display uses a `CellBuffer` designed with a Structure-of-Arrays
  (SoA) layout for high performance, supporting 24-bit RGB colours and styles. `PLOT` and
  `UNPLOT` operate on this buffer with dynamic sizing. Coordinates (0,0) start in the bottom-left
  corner. Rendering resolution is pluggable via `CellMode` (e.g., `QuadrantMode` for 2x2 blocks,
  `SextantMode` for 2x3 blocks, or `BrailleMode` for 2x4 patterns). Text output (`PRINT`) and
  graphics share the same buffer seamlessly.
- **Line-Based Flow**: Everything depends on line numbers. The interpreter usually proceeds
  sequentially. Commands like `GO TO` or `FOR` change this order by modifying the `EvalState`
  program counter.
- **Error Handling**: If something goes wrong (e.g. dividing by zero), the interpreter stops
  execution, throws a `ReportException`, and reports a Sinclair ZX Spectrum-style code in the
  status bar. The format is `<Code> <Message>, <Line>:<Statement> (Optional details)`,
  for example: `6 Number too big, 100:1 (Arithmetic overflow)`.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step, and return
  line) in `EvalState`. The `NEXT` statement increments the variable and checks if the loop
  should continue.
- **`INPUT`**: This uses the `Display` to read a full line of text from the user. It then parses
  this text to assign it to the target variable, handling type conversion for numbers.

## Performance & Memory Optimizations

To ensure high execution performance and minimize garbage collector pressure on the JVM, the
interpreter implements two key patterns:

- **Visitor as Calculator**: Rather than returning boxed `Double` wrapper objects from parsing
  visitor methods (which would cause massive boxing and unboxing overhead during arithmetic
  evaluation), the evaluation visitor
  [ExpressionEvaluator](../app-bazlang/src/main/java/com/davidconneely/bazlang/ExpressionEvaluator.java)
  returns `Void` and stores primitive double results directly in a class field.
- **Variable Reference Caching (`ctx.varRef`)**: Variables are normally looked up in the
  [EvalState](../app-bazlang/src/main/java/com/davidconneely/bazlang/EvalState.java)
  maps by their name strings. To avoid continuous hash map lookups during execution (especially
  in tight loops), the parser context objects (`ctx`) cache their resolved reference objects
  (such as `NumVarRef`) after the first lookup. Subsequent evaluations retrieve the cached
  reference directly.

## Language Quirks & Sinclair Eccentricities

To faithfully replicate Sinclair ZX81 and ZX Spectrum behaviour, the interpreter implements several
unusual behaviours:

### Flow Control Quirks

- **FOR Loop Stale Loop Variable Value**: The loop variable retains its last value after loop
  completion. This value is equal to `limit + step` (e.g., if running `FOR i=1 TO 5`, `i` will be
  `6` after the loop terminates).
- **FOR Loop Stale Loops and Stray `NEXT`**: A `FOR` loop is not deactivated when it terminates
  naturally. Executing a stray `NEXT var` statement *after* the loop has finished will continue to
  increment `var` and resume execution from the statement following `NEXT` without raising
  an error.
- **FOR Loop Flat Skip Scan**: When a loop's initial value falls outside its range (e.g.,
  `FOR i=1 TO 0`), the loop body is skipped. The interpreter performs a flat, linear scan through
  all statements in source code order to find the first `NEXT i`. This scan is unconditional: it
  includes statements nested inside `IF ... THEN` bodies, even if the condition is false.
  For example:
  ```bas
  10 FOR i=1 TO 0
  20 IF 0 THEN NEXT i
  30 PRINT "A"
  40 NEXT i
  50 PRINT "B"
  ```
  This prints `A` then `B`. The skip scan on line 10 finds the `NEXT i` on line 20 (inside the
  always-false `IF`), causing execution to resume at line 30.
- **PAUSE Break and CONTINUE**: On real ZX Spectrum hardware, pressing the BREAK key during a
  `PAUSE` statement generates report code `L` (BREAK into program). When `CONT` (CONTINUE) is
  subsequently issued, execution resumes at the statement *after* the `PAUSE`, rather than
  repeating it.

### Variables & Memory Quirks

- **Editing Lines Preserves Runtime State**: Adding, replacing, or deleting numbered program lines
  in interactive (REPL) mode does *not* clear runtime variables, the `DATA` pointer, the `GOSUB`
  return stack, or active `FOR` loop states. Only `NEW` and `CLEAR` reset this state. This allows
  debugging and hot-patching program code mid-run.
- **Recursive User Functions (`DEF FN`)**: Because user-defined functions (`DEF FN`) are evaluated
  on the host system stack, deep recursion in custom functions will exceed stack depth limits.
  Rather than crashing the JVM, this is caught and surfaced as report code `4 Out of memory,
  <line>:<statement>`, matching real ZX Spectrum behaviour.

### Data Quirks

- **DATA Statements in IF Bodies**: `DATA` statements are indexed globally at parse time, not at
  execution time. A `DATA` statement inside an `IF` block is always visible to `READ` and `RESTORE`
  operations, regardless of whether the enclosing `IF` condition evaluates to true or is ever
  executed.

### Input & Output Quirks

- **Negative Graphics Coordinates**: For graphics commands (`PLOT`, `DRAW`), negative coordinates
  are accepted and mirrored onto the positive grid using their absolute values (matching original
  Sinclair BASIC behaviour). For example, `PLOT -10, -10` draws at coordinate `(10, 10)`.
- **Byte-Oriented Fixed-Length String Arrays**: Fixed-length string arrays (declared via
  `DIM a$(rows, cols)`) are byte-oriented. The column size `cols` specifies the maximum width in
  **bytes**, not character count. When assigning multi-byte UTF-8 characters, ensure `cols` is
  sized large enough to hold the character's full byte sequence. If the assigned string exceeds
  `cols` bytes, it is truncated at the byte boundary, which can result in partial, invalid UTF-8
  sequences.


