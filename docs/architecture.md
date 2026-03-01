# Architecture

BazLang follows a simple design where a program is a list of numbered lines. It processes
and runs code in two main steps.

## The Two Stages

1.  **Lexing & Parsing**: The ANTLR-generated lexer breaks code into tokens (keywords,
    numbers, strings), and the parser organizes them into a parse tree. This is defined
    declaratively in `BazLang.g4`. Parsing is lazy: each line is parsed on first execution
    and cached.
2.  **Execution**: The `BazLangExecutor` visitor walks the parse tree directly, evaluating
    expressions and executing statements. The interpreter goes through the lines one by
    one, keeping track of variables in memory and sending output to the screen or printer.

## Key Design Ideas

### Line-Based Flow
Everything depends on line numbers. The interpreter usually goes from one line to the next
highest number. Commands like `GOTO` or `FOR` change this order by telling the interpreter
to jump to a different line.

### Memory & Persistence
Variables are stored in a central "state" while the program runs. Numbers and strings are
kept separate. This state persists even if the program stops (via `STOP`), allowing you to
check variables and then `CONT`inue.

### Screen Layout
The terminal display is divided into regions:
- **Application display area**: Scrollable output at the top of the screen
- **Input area**: Shows a prompt (`❯` for REPL, bold `#` for numeric input, bold `$` for string input)
- **Status bar**: Shows mode ("READY"), input hints, or report codes after execution

Graphics (`PLOT` and `UNPLOT`) operate on a dynamically sized pixel grid based on the
terminal dimensions. Each character cell represents a 2x2 pixel block using Unicode
quadrant characters (▘▝▀▖▌▞▛▗▚▐▜▄▙▟█). Coordinates (0,0) are at the bottom-left.

### Error Handling
If something goes wrong (like dividing by zero), the interpreter stops and reports a code
in the status bar (e.g., `6/100 Number too big`) indicating the error type and line number.

### Device Independence
The core logic of the interpreter is separate from the terminal. This allows the same code
to run in a real terminal, a simple text stream, or a testing environment.
