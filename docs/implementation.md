# Implementation Details

This document explains how the BazLang interpreter is built using Java.

## Code Structure

The interpreter executes directly from ANTLR's parse tree using a visitor pattern.

### Main Components

- **ANTLR Grammar (`BazLang.g4`)**: Defines the lexer and parser rules declaratively. ANTLR
  generates `BazLangLexer` and `BazLangParser` from this grammar.
- **`AntlrParser`**: A facade that wraps the ANTLR parser, providing simple `parseProgramLines()`
  and `parseReplLine()` methods.
- **`ExpressionEvaluator`**: A visitor that evaluates numeric and string expressions from the parse
  tree.
- **`StatementExecutor`**: A visitor that executes statements. It handles variable assignment, I/O
  operations, and state mutation.
- **`Interpreter`**: Manages the overall flow. It coordinates the executor and evaluator, decides
  which line to run next, handles jumps, and loops until the program stops.
- **`EvalState`**: The program's memory. It stores variables (scalars and arrays), custom functions,
  and the state of any active `FOR` loops.
- **`Program`**: Encapsulates the AST line storage, ensuring the underlying map is protected.
- **`ProgramLine`**: Stores the source text of each line and lazily parses to a parse tree on first
  execution.

## I/O System (The `io` Package)

Input and output are handled by a set of classes that share a common `BazLangDisplay` interface
(which extends the base `Display` and `Shell` interfaces), isolating the interpreter from the
specific device.

- **`TerminalDisplay`**: The standard version for interactive use. It provides a TUI (Text User
  Interface) with distinct screen regions: an application display area at the top, an input area
  with prompt, and a status bar. Uses the `TerminalEngine` class (which wraps JLine) for terminal
  control, escape sequences, and raw input. Supports command history, cursor movement, and handles
  terminal window resizes gracefully.
- **`StreamDisplay`**: A simpler version used for pipes or non-interactive environments. It uses
  standard Java `System.in` and `System.out`. Graphics (`PLOT`/`UNPLOT`) are no-ops.

The `BazLangDisplay` interface defines methods for screen output (`print`, `println`, `cls`),
graphics (`plot`, `unplot`, `setPlotMode`), input (`readln` with different modes for REPL vs INPUT),
and status updates (`setStatus` for showing report codes).

## Specific Logic

- **Graphics & Rendering**: The display uses a `CellBuffer` designed with a Structure-of-Arrays
  (SoA) layout for high performance, supporting 24-bit RGB colours and styles. `PLOT` and `UNPLOT`
  operate on this buffer with dynamic sizing. Coordinates (0,0) start at the bottom-left corner.
  Rendering resolution is pluggable via `PixelMode` (e.g., `QuadrantMode` for 2x2 blocks,
  `SextantMode` for 2x3 blocks, or `BrailleMode` for 2x4 patterns). Text output (`PRINT`) and
  graphics share the same buffer seamlessly.
- **Line-Based Flow**: Everything depends on line numbers. The interpreter usually proceeds
  sequentially. Commands like `GO TO` or `FOR` change this order by modifying the `EvalState`
  program counter.
- **Error Handling**: If something goes wrong (e.g. dividing by zero), the interpreter stops
  execution, throws a `ReportException`, and reports a Sinclair ZX Spectrum-style code in the status
  bar. The format is `<Code> <Message>, <Line>:<Statement> (Optional details)`, for example:
  `6 Number too big, 100:1 (Arithmetic overflow)`.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step, and return
  line) in `EvalState`. The `NEXT` statement increments the variable and checks if the loop should
  continue.
- **`INPUT`**: This uses the `Display` to read a full line of text from the user. It then parses
  this text to assign it to the target variable, handling type conversion for numbers.
