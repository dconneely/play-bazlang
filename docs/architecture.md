# Architecture: Principles and Pipeline

The Bazlang interpreter is designed to transform Sinclair BASIC source code
into observable output through a deterministic, multi-stage pipeline. The
system prioritizes dialect fidelity and immediate feedback over internal
complexity.

## The Transformation Pipeline

1.  **Normalization and Tokenization**: The raw character stream is filtered
    to remove non-executable content (comments and blank lines). Valid source
    is then broken down into its fundamental linguistic units (keywords,
    literals, and operators) while enforcing mandatory monotonic line labels.
2.  **Strictly-Typed Translation**: The sequence of units is translated into a
    structured, type-aware representation. By separating numeric and string
    logic during this translation, the system identifies logic errors (such as
    adding a number to a string) before any code is executed.
3.  **State-Driven Execution**: The structured program is loaded into an
    execution engine that manages variables, control flow (loops and jumps),
    and the simulated screen state.

## Core Behavioral Components

### Linguistic Normalization

Enforces the structural rules of the language, ensuring every statement is
associated with a unique, increasing address (line label). It acts as the
first gate for source validity.

### Structural Logic Analysis

Builds a complete map of the program's logic. It resolves operator precedence
and enforces the strict typing rules that define the language's mathematical
and string-handling behavior.

### Execution Engine

The heart of the interpreter. It manages the lifecycle of the program,
handling the dynamic allocation of arrays, the navigation between line labels,
and the persistent state of the user's environment.

### Simulated Interface

A centralized component that manages all interaction between the program and
the user. It tracks cursor positions, handles screen clearing, and manages the
distinction between standard output and printer output.

## Operational Principles

### Address-Based Navigation

Control flow is exclusively driven by line labels. Jumps (`GOTO`, `GOSUB`)
target these addresses directly, with automatic resolution to the next
available address if an exact match is not found.

### Persistent Environment

Variables and program structure are maintained in a cohesive state that
survives between execution cycles, mimicking the behavior of a physical
microcomputer.

### Diagnostic Reliability

Errors are reported using a consistent, address-aware format
(`ReportCode/Address`), ensuring that every fault can be traced back to a
specific location in the source code.
