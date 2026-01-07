# Architecture

BazLang works by transforming source code into executable actions through three main stages. This document describes how the interpreter processes code.

## How It Works

1.  **Scanning and Parsing**: The interpreter reads the source code character by character. It ignores comments and blank lines, then groups the remaining characters into tokens (like keywords, numbers, and strings). It checks that the code follows basic rules, like having correct line numbers.
2.  **Type Checking**: The tokens are turned into a structured representation of the code. During this step, the interpreter checks for type errors—like trying to add a number to a string—before running the code.
3.  **Execution**: The structured code is loaded into the engine. The engine runs the program line by line, managing variables, loops, and screen output.

## Key Components

### Normalization

The first step is to clean up the source code. The interpreter ensures that every line has a valid, unique, and increasing line number. This makes sure the program runs in the correct order.

### Logic Analysis

The interpreter maps out the program's logic. It handles mathematical operations (like strict order of operations) and ensures that numeric and string operations are kept separate.

### Execution Engine

This is the core of the interpreter. It:
*   Allocates memory for variables and arrays.
*   Handles jumps (`GOTO`, `GOSUB`) and loops (`FOR`/`NEXT`).
*   Maintains the state of the program as it runs.

### Interface

The interface manages interaction with the user. It tracks where the cursor is on the screen, clears the screen when needed, and separates normal output from "printer" output.

## How it Runs

### Line Numbers

Everything in BazLang is driven by line numbers. When the code says `GOTO 100`, the interpreter jumps directly to line 100. If line 100 doesn't exist, it jumps to the next available line number.

### State

Variables (like `A` or `A$`) stay in memory as long as the program runs. This means you can stop a program, check a variable, and continue (if supported by the implementation).

### Errors

If something goes wrong, the interpreter reports an error code and the line number where it happened (e.g., `2/100` means error type 2 at line 100). This helps in finding bugs quickly.