@echo off
setlocal

:: Get the absolute path of the directory containing this script, and the repository root (this
:: script's parent directory) - never the caller's cwd, which is preserved for the java invocation
:: below instead.
set SCRIPT_DIR=%~dp0
:: Remove trailing backslash
set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%
set ROOT_DIR=%SCRIPT_DIR%\..

set JAR_FILE=%ROOT_DIR%\app-bazlang\build\libs\bazlang-1.0.0-SNAPSHOT.jar

:: Always (re)build: Gradle's up-to-date checks make this quick when nothing has changed, and it
:: stops an existing jar silently running stale code after a source edit.
call "%ROOT_DIR%\gradlew.bat" -p "%ROOT_DIR%" -q --console=plain :app-bazlang:jar :app-bazlang:copyDependencies
if errorlevel 1 exit /b %errorlevel%

:: Pass all arguments through, preserving your original working directory
java --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%" %*
