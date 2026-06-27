# BazLang Interpreter

BazLang is a simple BASIC interpreter written in Java 25. It is loosely based on Sinclair ZX BASIC
(supporting a superset of both ZX81 and ZX Spectrum) but designed for the modern command line. It
includes a REPL and runs standard UTF-8 source files.

## Documentation

For more details, check the `docs/` folder:

- [Language Features](docs/language_features.md) - Details on variables, types, commands, and REPL
  editor commands.
- [Grammar](docs/grammar.md) - The ANTLR grammar that defines the language syntax.
- [Implementation](docs/implementation.md) - How the code is structured and architected.

## Quick Start

### Building

You need Java 25 or later. Build it with Gradle:

```bash
./gradlew clean build
```

### Running

Use the shell script:

```bash
./run.sh <optional-source-file.bas>
```

Or run the JAR directly:

```bash
java --enable-native-access=ALL-UNNAMED -jar bazlang/build/libs/bazlang-1.0.0-SNAPSHOT.jar <optional-source-file.bas>
```

## Examples

You can find example programs in `examples/`. Run one like this:

```bash
./run.sh "examples/games/mastermind.bas"
```

## License

This project is open source under the MIT License.
