#!/bin/sh
cd "$(dirname "$0")"

JAR_FILE="build/libs/bazlang-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Building project..."
    ./gradlew -q --console=plain jar copyDependencies
fi

# Pass all arguments through to the Java application
java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" "$@"
