# Implementation Details

This document explains how the BazLang interpreter is built using Java.

## Code Structure

The code represents the BASIC language using two main categories: `Statement` (commands) and `Expression` (values).

### Main Components

- **`Lexer`**: Reads the source text and turns it into a list of tokens. It handles case-insensitivity for keywords.
- **`Parser`**: Takes tokens and builds the `Statement` and `Expression` objects. It catches syntax errors and ensures the program structure is valid.
- **`EvalState`**: The program's memory. It stores the lines of code, the variables (scalars and arrays), and the state of any active `FOR` loops.
- **`Evaluator`**: Calculates the results of expressions, such as math (`1+2`) or string operations (`A$ + B$`). It also handles complex logic like array indexing and string slicing.
- **`Executor`**: Performs the action of a single `Statement` (like `LET` or `PRINT`). It updates the `EvalState` or sends output to the `Display`.
- **`Interpreter`**: Manages the overall flow. It decides which line to run next, handles jumps, and loops until the program stops.

## I/O System (The `io` Package)

Input and output are handled by a set of classes that share a common `Display` interface, isolating the interpreter from the specific device.

- **`BufferedDisplay`**: An abstract class that maintains a virtual `char[24][32]` screen buffer. It implements the logic for `PLOT` and `UNPLOT` by merging pixels into Unicode 2x2 quadrant characters.
- **`TerminalDisplay`**: The standard version for interactive use. It uses the JLine library to handle raw keyboard input (`INKEY$`) and ANSI escape codes for precise cursor positioning.
- **`StreamDisplay`**: A simpler version used for pipes or non-interactive environments. It uses standard Java `System.in` and `System.out`.

## Specific Logic

- **Graphics**: `PLOT` coordinates (0-63, 0-47) are mapped to the character buffer (0-31, 0-23). The system calculates which bit in a quadrant character needs to change, updates the buffer, and prints the new character.
- **`FOR-NEXT`**: The `FOR` statement saves its state (target variable, limit, step, and return line) in `EvalState`. The `NEXT` statement increments the variable and checks if the loop should continue.
- **`INPUT`**: This uses the `Display` to read a full line of text from the user. It then parses this text to assign it to the target variable, handling type conversion for numbers.

## To Do

- Improve example programs (Lunar Lander, Mastermind).
- Create a 3D maze or adventure game example.
- Add more games like Rubik's Cube, One-Armed Bandit, Backgammon.
- Implement line editing in the REPL (currently just overwrites).
- Implement bulk line deletion and line renumbering.
- Maybe '!' to call out to shell, and '*' to introduce REPL command?
- Consider if `INPUT` should always happen at the bottom of the screen
  (ZX81 style) rather than at the current print position.
- `VAL` (and numeric `INPUT`) can evaluate text as an expression
