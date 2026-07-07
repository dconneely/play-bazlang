# REPL shell library (`lib-repl`)

`lib-repl` is a utility library wrapping **JLine** to provide a robust, interactive Read-Eval-Print
Loop (REPL) environment in system terminals.

It isolates JLine terminal control, keyboard polling, command history, and native process callouts
from the application logic.

## Core features

- **Keyboard polling:** Provides synchronous, non-blocking polling for raw keyboard sequences via
  `ReplReader`, supporting multibyte UTF-8 input sequences and ANSI escape codes (arrows, home,
  end, function keys).
- **Command line engine:** Abstract wrapper (`TerminalEngine`) managing raw-mode terminal
  configurations, output streaming, status lines, window resizing handlers, and prompt rendering.
- **Robust signal handling:** Intercepts system interrupts (like Ctrl+C / `SIGINT`) and translates
  them into a clean runtime `BreakException` so that user-driven breaks can be caught and handled
  gracefully (e.g., stopping execution but keeping the program state intact).
- **System clipboard access:** Standard platform interfaces (`Clipboard`, `NativeProcessClipboard`)
  to read and write to the operating system's clipboard using system commands.
