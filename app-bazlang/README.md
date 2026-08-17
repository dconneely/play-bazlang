# BazLang interpreter (`app-bazlang`)

This module houses the interpreter, execution runtime, and interactive REPL for the **BazLang**
language.

BazLang is a BASIC dialect based on the 1981 / 1982 Sinclair ZX BASIC (ZX80, ZX81, and ZX Spectrum),
modernized with 24-bit colour support, sub-pixel terminal graphics (using Braille and block
characters), and UTF-8 string encoding.

## Documentation

Detailed language and architecture references are available in the `docs/` directory:

- **[Language features](docs/language_features.md)** - Guide to variables, commands, and REPL operations.
- **[Grammar](docs/grammar.md)** - The ANTLR grammar specification for BazLang.
- **[Implementation](docs/implementation.md)** - AST architecture, execution model, state
  tracking, and performance notes (for implementers).
- **[Quirks](docs/quirks.md)** - Deliberately preserved ZX BASIC eccentricities and intentional
  behaviours.
- **[MCP server](docs/mcp_server.md)** - Native MCP debugger protocol and tool reference for
  LLM agents.

## Running the interpreter

To launch the interactive REPL:

```bash
./run.sh
```

To run a specific `.bas` source file:

```bash
./run.sh path/to/program.bas
```

Alternatively, you can run the built JAR directly (Gradle build required first):

```bash
java --enable-native-access=ALL-UNNAMED -jar build/libs/bazlang-1.0.0-SNAPSHOT.jar [program.bas]
```

## Running the MCP server

To let an MCP client (e.g. Claude Code) debug a programme via the `bazlang_*` tools:

```bash
./gradlew :app-bazlang:runMcpServer
```

See [docs/mcp_server.md](docs/mcp_server.md) for the full tool and protocol reference.

## Example programs

A selection of classic game and graphics demo examples can be found under
`src/example/bas/`. You can execute them directly:

```bash
./run.sh src/example/bas/pontoon.bas
```

Available demos include:

- `pong.bas` / `invaders.bas` / `racer.bas` - Interactive arcade games using terminal cells.
- `pontoon.bas` / `hangman.bas` - Card and puzzle games using interactive screen grids.
- `cube.bas` / `torus.bas` - 3D wireframe graphics projections using sub-pixel mode.
- `life.bas` - Conway's Game of Life.
- `lander.bas` - Lunar lander text simulation.
- `hammurabi.bas` / `wumpus.bas` - Classic text-only adventure/simulation games.

## Internals & parsing

The parser is built using ANTLR 4. The grammar is defined in `src/main/antlr/BazLang.g4`.

When running a file or entering commands in the REPL:

- Source statements are parsed by ANTLR, then lowered into a typed Abstract Syntax Tree (AST).
- `StatementExecutor` walks the statement AST nodes via `switch` pattern matching.
- `ExpressionEvaluator` resolves numeric and string expressions the same way.
- `EvalState` maintains variable values, array definitions, loops, and call stacks.
