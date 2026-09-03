# Language quirks

BazLang deliberately replicates a number of eccentric Sinclair ZX BASIC behaviours, and makes a few
deliberate design decisions of its own that can look like bugs. This document is the register of
those behaviours. It serves two audiences:

- **BazLang programmers**: these are observable language behaviours you can rely on.
- **Interpreter implementers**: these are **intentional**. Do not "fix" anything listed here; any
  refactoring must preserve every behaviour on this page. See
  [architecture.md](spec/architecture.md) for how the interpreter is built.

## Flow control quirks

- **FOR loop stale loop variable value**: The loop variable retains its last value after loop
  completion. This value is equal to `limit + step` (e.g., if running `FOR i=1 TO 5`, `i` will be
  `6` after the loop terminates).
- **FOR loop stale loops and stray `NEXT`**: A `FOR` loop is not deactivated when it terminates
  naturally. Executing a stray `NEXT var` statement _after_ the loop has finished will continue to
  increment `var` and resume execution from the statement following `NEXT` without raising an error.
- **FOR loop flat skip scan**: When a loop's initial value falls outside its range (e.g.,
  `FOR i=1 TO 0`), the loop body is skipped. The interpreter performs a flat, linear scan through
  all statements in source code order to find the first `NEXT i`. This scan is unconditional: it
  includes statements nested inside `IF ... THEN` bodies, even if the condition is false. For
  example:

  ```bas
  10 FOR i=1 TO 0
  20 IF 0 THEN NEXT i
  30 PRINT "A"
  40 NEXT i
  50 PRINT "B"
  ```

  This prints `A` then `B`. The skip scan on line 10 finds the `NEXT i` on line 20 (inside the
  always-false `IF`), causing execution to resume at line 30. (Covered by `ForNextProgramTest`.)

## Variables & memory quirks

- **Editing lines preserves runtime state**: Adding, replacing, or deleting numbered program lines
  in interactive (REPL) mode does _not_ clear runtime variables, the `DATA` pointer, the `GOSUB`
  return stack, or active `FOR` loop states. Only `NEW` and `CLEAR` reset this state. This allows
  debugging and hot-patching program code mid-run.
- **Recursive user functions (`DEF FN`)**: Because user-defined functions (`DEF FN`) are evaluated
  on the host system stack, deep recursion in custom functions will exceed stack depth limits.
  Rather than crashing the JVM, this is caught and surfaced as report code
  `4 Out of memory, <line>:<statement>`, matching real ZX Spectrum behaviour.

## Data quirks

- **DATA statements in IF bodies**: `DATA` statements are indexed globally at parse time, not at
  execution time. A `DATA` statement inside an `IF` block is always visible to `READ` and `RESTORE`
  operations, regardless of whether the enclosing `IF` condition evaluates to true or is ever
  executed. (Covered by `DataReadProgramTest`.)

## Input & output quirks

- **Negative graphics coordinates**: For graphics commands (`PLOT`, `DRAW`), negative coordinates
  are accepted and mirrored onto the positive grid using their absolute values (matching original
  Sinclair ZX BASIC behaviour). For example, `PLOT -10, -10` draws at coordinate `(10, 10)`.
- **Byte-oriented fixed-length string arrays**: Fixed-length string arrays (declared via
  `DIM a$(rows, cols)`) are byte-oriented. The column size `cols` specifies the maximum width in
  **bytes**, not character count. When assigning multibyte UTF-8 characters, ensure `cols` is sized
  large enough to hold the character's full byte sequence. If the assigned string exceeds `cols`
  bytes, it is truncated at the byte boundary, which can result in partial, invalid UTF-8 sequences.
- **`TL$` truncates at the byte boundary, not the character boundary**: `TL$ s$` always removes
  exactly one byte, so on a multibyte UTF-8 character it leaves a partial, invalid sequence behind
  rather than dropping the whole character. This is deliberate - `TL$` is the byte-oriented sibling
  of Unicode-aware `UTL$`, matching the project's other byte/Unicode function pairs (`CODE`/`UCODE`,
  `CHR$`/`UCHR$`); it is not a bug to make `TL$` itself Unicode-aware.

## Interpreter & application behaviours

These are deliberate decisions in the surrounding application rather than language semantics, but
they equally look like bugs at first sight:

- **Immediate-mode errors report as line 0**: Statements typed without a line number execute as a
  synthetic "line 0" (mirroring the Spectrum's edit line), and any error they raise reports with
  line 0. In particular, a false `IF` condition in an immediate statement reports
  `N Statement lost, 0:1`:

  ```text
  ❯ PRINT "a": IF 0 THEN PRINT "b"
  a
  N Statement lost, 0:1
  ```

  This is authentic ZX BASIC behaviour: when a false `IF` skips "to the next line" there is no next
  line for the edit line to continue on.

- **Silent terminal fallback**: If the interactive `TerminalScreen` cannot be initialised (its
  construction throws `IOException`), `MainClass` silently falls back to the plain stdin/stdout
  `StreamScreen`. This is intentional graceful degradation for environments without a usable
  terminal, not an ignored error.

## Accepted-wrong behaviour

Known defects, knowingly left unfixed for now. Unlike the sections above, these are **not**
deliberate - do not point to this section to justify keeping the behaviour; each entry names what
would make it go away. No entries currently.
