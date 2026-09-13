#!/bin/sh
set -eu

# Runs the same lychee link check as .github/workflows/link-check.yml, so a broken or flaky link
# can be reproduced and investigated locally rather than only found the next time the scheduled
# workflow runs. Invoke with sh/bash explicitly (works via Git Bash on Windows):
#   sh scripts/check-links.sh
#
# Requires `lychee` on PATH - CI installs its own pinned copy (see the workflow), but this script
# doesn't install one itself, since it also needs to run on whatever platform you develop on:
#   Windows: scoop install lychee
#   macOS:   brew install lychee
#   other:   cargo install lychee
#   (or download a prebuilt binary: https://github.com/lycheeverse/lychee/releases)
#
# EXCLUDE and ACCEPT hold the same defaults CI uses - override either via the environment to
# investigate a link without permanently changing them, e.g.:
#   EXCLUDE='' sh scripts/check-links.sh              # check every link, including known-flaky ones
#   ACCEPT='200..=299' sh scripts/check-links.sh       # don't silently accept a 429 this time
# Any arguments given to this script are passed straight through to lychee.

if ! command -v lychee >/dev/null 2>&1; then
  echo "error: lychee not found on PATH - install it: https://github.com/lycheeverse/lychee#installation" >&2
  exit 1
fi

# theqlforum.com (incl. its qlwiki. subdomain) 403s every automated checker, UA or no UA - it's a
# real, human-reachable site, just not link-checker-reachable.
# element.zxfiles.net (NextBASIC manual) is a live, working URL that's just consistently
# slow/flaky from GitHub's runner network - excluded rather than accepted as a timeout, since
# lychee's in-run cache re-reports a cached timeout as a generic (non-timeout) error for every doc
# after the first that cites the same URL, defeating --accept-timeouts.
# fruitcake.plus.com (3D Monster Maze disassembly) refuses connections from GitHub's runner
# network specifically - confirmed 2026-09-13 still live and returning 200 over plain HTTP from
# elsewhere, so this is the same "reachable but not from here" class as the two above, not a dead
# link.
: "${EXCLUDE:=theqlforum\.com|element\.zxfiles\.net|fruitcake\.plus\.com}"

# Transient rate-limiting from whichever host is having a bad day (seen on
# blog.tynemouthsoftware.co.uk and news.ycombinator.com on different runs, different URLs each
# time) - report it, don't fail the job over it, the same reasoning as --accept-timeouts previously.
# --accept REPLACES lychee's own default accepted-status set rather than adding to it, so 429 has
# to be listed alongside the normal 2xx range here, not on its own.
: "${ACCEPT:=200..=299,429}"

cd "$(dirname "$0")/.."

# No arrays in POSIX sh - append our defaults onto "$@" (the caller's own passthrough args) instead
# of building a separate list, so nothing is lost or needs re-quoting.
set -- "$@" --no-progress --method get --max-concurrency 4
if [ -n "$ACCEPT" ]; then
  set -- "$@" --accept "$ACCEPT"
fi
if [ -n "$EXCLUDE" ]; then
  set -- "$@" --exclude "$EXCLUDE"
fi

exec lychee "$@" "docs/research/**/*.md" "docs/adr/**/*.md"
