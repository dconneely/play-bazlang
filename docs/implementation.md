# Implementation Details

This document explains how the BazLang interpreter is built in Java.

## Code Structure

The code is built around two main types: `Statement` and `Expression`.

### Statements

Statements are the commands the program executes. Some key details:

- **Assignments (`LET`)**: Handles both numbers and strings, and works for single variables or array elements.
- **Input (`INPUT`)**: Reads user input into variables or array elements.
- **Flow Control**: Commands like `GOTO` and `FOR` use the `EvalState` to find which line to run next.

### Expressions

Expressions represent values or calculations. They are strictly divided into Numeric and String types to prevent mixing them up improperly.

## Main Components

### `Lexer`

This reads the source code string.
- It scans through characters one by one.
- It looks up keywords (which are case-insensitive).
- It produces a list of tokens.

### `Parser`

This turns the list of tokens into a structured program.
- It matches the tokens against the rules of the language.
- It handles complex things like assignment targets (variables vs arrays).
- It catches syntax errors and reports the line number.

### `EvalState`

This holds the memory of the running program.
- **Program**: Stores the lines of code so they can be looked up by line number.
- **Variables**: Stores numbers, strings, and arrays in separate maps.
- **Loops**: Keeps track of active `FOR` loops so `NEXT` knows where to go back to.

### `Executor` / `Interpreter`

- **Executor**: Runs a single statement. It looks at what the statement is and updates the `EvalState` or the screen.
- **Interpreter**: Controls the flow. It keeps track of the current line number and moves to the next one. It also handles logic for skipping loops if they shouldn't run.

### `Evaluator`

This calculates values.
- It takes an expression (like `1 + 2` or `LEN("A")`) and returns the result.
- It handles array indexing and string slicing logic.

## ZX81 Differences

BazLang is similar to ZX81 BASIC but has some modern changes:

- **Keywords**: You can type them in any case (e.g., `print`, `PRINT`).
- **Typing**: You type the full word, not a special token.
- **Files**: Source code is just standard UTF-8 text.
- **Graphics**: Uses the Unicode block character (█) for `PLOT`.
- **Screen**: Uses standard terminal codes (ANSI) to move the cursor and clear the screen.

## Specific Logic

### `INPUT`

The `INPUT` command shares logic with `LET`.
1. It reads a line of text from the user.
2. If the variable is a number, it tries to convert the text to a number (defaulting to 0 if it fails).
3. If the variable is a string, it takes the text as-is.

### `FOR-NEXT` Loop

- **Start**: `FOR` sets up the loop. If the start is past the end (and step is positive), it searches ahead for `NEXT` and skips the loop entirely.
- **End**: `NEXT` adds the step to the variable. If the loop isn't finished, it jumps back to the line after `FOR`.
- **Nesting**: Nested loops work naturally because they use the variable name to store state.

## I/O

- The `Display` class handles printing and reading text.
- It abstracts away the details of `System.in` and `System.out`.
- It buffers input to allow for line editing if needed.