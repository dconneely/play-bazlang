# BazLang Interpreter

A Java 25 implementation of a Sinclair ZX81 BASIC-compatible language as a
command-line interpreter. This interpreter accepts UTF-8 encoded source code
files and executes them using standard input/output.

## Documentation

For detailed information on the language and implementation, please refer to
the files in the `docs/` directory:

- [Language features](docs/language_features.md) - Crucial information about
string/array semantics, namespaces, typing, and other dialect peculiarities.
- [Architecture](docs/architecture.md) - System overview and technical design.
- [Implementation](docs/implementation.md) - Specific implementation details.

## Quick Start

### Building

Requires Java 25 or later. Uses Gradle wrapper (included):

```bash
./gradlew clean build
```

### Running

Using shell script:

```bash
./run.sh <optional-source-file.bas>
```

Or using the executable JAR:

```bash
java -jar target/play-bazlang-1.0-SNAPSHOT.jar <optional-source-file.bas>
```

## Example Programs

Several example programs are included in the subdirectories of
`src/test/resources/`:

Run them as follows:

```bash
./run.sh "src/test/resources/games/mastermind.bas"
```

## License

This is a recreational implementation of a simple interpreted language,
developed under the MIT License.
