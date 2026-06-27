#!/bin/sh
# Get the absolute path of the directory containing this script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="${SCRIPT_DIR}/build/libs/bazlang-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Building project..."
    # Run Gradle from the root project directory via -p
    "${SCRIPT_DIR}/../gradlew" -p "${SCRIPT_DIR}/.." -q --console=plain :app-bazlang:jar :app-bazlang:copyDependencies
fi

# Pass all arguments through, preserving your original working directory
java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" "$@"
