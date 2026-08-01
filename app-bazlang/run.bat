@echo off
setlocal

:: Get the absolute path of the directory containing this script
set SCRIPT_DIR=%~dp0
:: Remove trailing backslash
set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

set JAR_FILE=%SCRIPT_DIR%\build\libs\bazlang-1.0.0-SNAPSHOT.jar

if not exist "%JAR_FILE%" (
    echo Building project...
    :: Run Gradle from the root project directory via -p
    call "%SCRIPT_DIR%\..\gradlew.bat" -p "%SCRIPT_DIR%\.." -q --console=plain :app-bazlang:jar :app-bazlang:copyDependencies
)

:: Pass all arguments through, preserving your original working directory
java --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%" %*
