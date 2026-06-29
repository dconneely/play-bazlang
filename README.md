# BazLang Interpreter

BazLang is a simple BASIC interpreter written in Java 25. It is loosely based on Sinclair ZX BASIC
(supporting a superset of both ZX81 and ZX Spectrum BASIC) but designed for the modern command line.
It includes a REPL and works with standard UTF-8 source files.

## Documentation

For more details, check the `app-bazlang/docs/` folder:

- [Language Features](app-bazlang/docs/language_features.md) - Details on variables, types,
  commands, and REPL editor commands.
- [Grammar](app-bazlang/docs/grammar.md) - The ANTLR grammar that defines the language syntax.
- [Implementation](app-bazlang/docs/implementation.md) - How the code is structured and architected.

## Quick Start

### Building

You need Java 25 or later. Build it with Gradle:

```bash
./gradlew clean build
```

### Running

Use the shell script:

```bash
app-bazlang/run.sh <optional-source-file.bas>
```

Or run the JAR directly:

```bash
java --enable-native-access=ALL-UNNAMED -jar app-bazlang/build/libs/bazlang-1.0.0-SNAPSHOT.jar <optional-source-file.bas>
```

## Examples

You can find example programs in `app-bazlang/src/example/bas/`. Run one like this:

```bash
app-bazlang/run.sh app-bazlang/src/example/bas/pontoon.bas
```

## License

This project is open source under the MIT License.
