#!/bin/bash
# BazLang runner script

JAR_FILE="build/libs/play-bazlang-1.0-SNAPSHOT.jar"

# Build if JAR is missing
if [ ! -f "$JAR_FILE" ]; then
    echo "Building project..."
    ./gradlew clean build -x test -q --console=plain
fi

if [ $# -eq 0 ]; then
    java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE"
elif [ $# -eq 1 ]; then
    if [ ! -f "$1" ]; then
        echo "Error: File '$1' not found"
        exit 1
    fi
    java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" "$1"
else
    echo "Usage: $0 [source-file]"
    exit 1
fi
