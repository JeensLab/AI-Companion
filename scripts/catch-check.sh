#!/usr/bin/env bash
# Runs the production catch-block rules (scripts/CatchCheck.java); passes its exit code through, 2 when Java is missing.
# --selftest runs scripts/CatchCheckSelfTest.java instead, which checks the tool itself.
set -u

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="java"
else
  echo "catch-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)" >&2
  exit 2
fi

if [ "${1:-}" = "--selftest" ]; then
  exec "$java_bin" scripts/CatchCheckSelfTest.java
fi
"$java_bin" scripts/CatchCheck.java "$root"
