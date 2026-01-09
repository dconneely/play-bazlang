# Architecture

BazLang follows a simple design where a program is a list of numbered lines. It processes and runs code in three main steps.

## The Three Stages

1.  **Reading (Lexing)**: The code is read and broken into small pieces like keywords, numbers, and strings.
2.  **Structuring (Parsing)**: These pieces are organized into commands (statements) and values (expressions). This is where the interpreter checks if the code makes sense (for example, making sure an `IF` has a condition).
3.  **Running (Execution)**: The interpreter goes through the lines one by one. It keeps track of variables in memory and sends output to the screen or printer.

## Key Design Ideas

### Line-Based Flow
Everything depends on line numbers. The interpreter usually goes from one line to the next highest number. Commands like `GOTO` or `FOR` change this order by telling the interpreter to jump to a different line.

### Memory & Persistence
Variables are stored in a central "state" while the program runs. Numbers and strings are kept separate. This state persists even if the program stops (via `STOP`), allowing you to check variables and then `CONT`inue.

### Screen and Graphics
The display is handled as a 32x24 grid for graphics (`PLOT` and `UNPLOT`), mapped to the terminal window. Text can be printed anywhere on the terminal (even outside this grid), but graphics are constrained to the buffer to allow for pixel-level manipulation.

### Error Handling
If something goes wrong (like dividing by zero), the interpreter stops and reports a code (e.g., `6/100`) indicating the error type and the line number, helping you find bugs quickly.

### Device Independence
The core logic of the interpreter is separate from the terminal. This allows the same code to run in a real terminal, a simple text stream, or a testing environment.
