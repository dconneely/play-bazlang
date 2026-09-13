#!/bin/sh
set -eu

# Prints a markdown table of Jacoco test coverage (instructions/branches) per module, the same
# table .github/workflows/_build.yml posts as a job summary - run this locally after `./gradlew
# build` or `./gradlew test` for a quick coverage check without opening the full HTML reports:
#   sh scripts/coverage-summary.sh
#   MODULES='app-bazlang lib-cell' sh scripts/coverage-summary.sh   # a subset only
#
# Requires GNU grep with PCRE support (-P) - already the case wherever this repo's tests run
# (Linux, Git Bash on Windows); macOS's default grep lacks it, so install GNU grep there
# (`brew install grep`) and invoke as `ggrep`, or run this under Linux/WSL instead.
# A module with no report yet (tests not run, or a module name typo) shows as "n/a", not an error.

cd "$(dirname "$0")/.."

: "${MODULES:=app-bazlang lib-cell lib-repl}"

echo "## Test coverage"
echo
echo "| Module | Instructions | Branches |"
echo "| --- | --- | --- |"
for module in $MODULES; do
  report="$module/build/reports/jacoco/test/html/index.html"
  row=$(grep -oP '(?<=<tfoot><tr>).*?(?=</tr></tfoot>)' "$report" 2>/dev/null || true)
  pcts=$(printf '%s' "$row" | grep -oP '(?<=class="ctr2">)\d+%' || true)
  instructions=$(printf '%s' "$pcts" | sed -n 1p)
  branches=$(printf '%s' "$pcts" | sed -n 2p)
  echo "| $module | ${instructions:-n/a} | ${branches:-n/a} |"
done
