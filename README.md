# Play-BazLang repository

This repository contains **BazLang**, a retro-inspired Sinclair ZX BASIC interpreter modernized
for modern terminal emulators, alongside its supporting libraries.

## Repository structure

The project is structured as a multi-module Gradle build:

- **[app-bazlang/](app-bazlang/)** - The main BASIC interpreter application, containing the
  REPL, execution engine, AST visitors, and command-line entrypoint.
- **[lib-cell/](lib-cell/)** - A lightweight character-cell screen buffer library supporting
  24-bit colour, text styles, and sub-pixel graphic rendering modes (e.g. Quadrant, Braille).
- **[lib-repl/](lib-repl/)** - A supporting line reader and console management library that wraps
  JLine to handle command history, ANSI escape sequences, raw keyboard polling, and breaks.

## Getting started

### Prerequisites

- **Java 25** or later.

### Building

Build the entire project (application and libraries) using the Gradle wrapper in the root folder:

```bash
./gradlew clean build
```

For instructions on running the REPL, executing programs, or reading language and architecture
documentation, see the application README in [app-bazlang/README.md](app-bazlang/README.md).

## Licence

This project is licensed under the [MIT License](LICENCE).
