# Implementation Details

This document explains how the BazLang interpreter is built using Java.

## Code Structure

The interpreter executes directly from ANTLR's parse tree using a visitor pattern.

### Main Components

- **ANTLR Grammar (`BazLang.g4`)**: Defines the lexer and parser rules declaratively.
  ANTLR generates `BazLangLexer` and `BazLangParser` from this grammar.
- **`AntlrParser`**: A facade that wraps the ANTLR parser, providing simple
  `parseProgramLines()` and `parseReplLine()` methods.
- **`BazLangExecutor`**: A visitor that executes statements directly from the parse tree.
  It handles expression evaluation, variable assignment, and all statement execution.
- **`EvalState`**: The program's memory. It stores the lines of code, the variables
  (scalars and arrays), and the state of any active `FOR` loops.
- **`Interpreter`**: Manages the overall flow. It decides which line to run next,
  handles jumps, and loops until the program stops.
- **`ProgramLine`**: Stores the source text of each line and lazily parses to a
  parse tree on first execution.

## I/O System (The `io` Package)

Input and output are handled by a set of classes that share a common `Display` interface,
isolating the interpreter from the specific device.

- **`TerminalDisplay`**: The standard version for interactive use. It provides a TUI
  (Text User Interface) with distinct screen regions: an application display area at the
  top, an input area with prompt, and a status bar. Uses the TamboUI library (which builds
  on JLine) for terminal control, escape sequences, and raw input. Supports command history,
  cursor movement, and handles terminal window resizes gracefully.
- **`StreamDisplay`**: A simpler version used for pipes or non-interactive environments.
  It uses standard Java `System.in` and `System.out`. Graphics (`PLOT`/`UNPLOT`) are no-ops.

The Display interface defines methods for screen output (`print`, `println`, `cls`),
graphics (`plot`, `unplot`), input (`readln` with different modes for REPL vs INPUT),
and status updates (`setStatus` for showing report codes).

## Specific Logic

- **Graphics**: `PLOT` and `UNPLOT` operate on the full display area with dynamic sizing
  based on terminal dimensions. Coordinates (0,0) start at the bottom-left corner. Each
  character cell represents a 2x2 pixel block using Unicode quadrant characters (▘▝▀▖▌▞▛▗▚▐▜▄▙▟█).
  Text output (`PRINT`) and graphics share the same display buffer and can coexist.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step,
  and return line) in `EvalState`. The `NEXT` statement increments the variable and
  checks if the loop should continue.
- **`INPUT`**: This uses the `Display` to read a full line of text from the user.
  It then parses this text to assign it to the target variable, handling type conversion
  for numbers.

## To Do

- Improve example programs (Lunar Lander, Mastermind).
- Create a 3D maze or adventure game example.
- Add more games like Rubik's Cube, One-Armed Bandit, Backgammon.
- Maybe '!' to call out to shell or change directory.
