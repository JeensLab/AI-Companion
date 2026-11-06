#!/usr/bin/env bash
# Complexity ceilings: runs PMD, then compares its report with tools/pmd-baseline.txt (see scripts/PmdCheck.java).
# Extra arguments go to Gradle. Exit 0 clean, 1 new violation or stale baseline entry, 2 PMD, the report or Java failed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="java"
else
  echo "pmd-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)" >&2
  exit 2
fi

./gradlew pmdMain --offline -q "$@" || { echo "pmd-check: pmdMain failed" >&2; exit 2; }
exec "$java_bin" scripts/PmdCheck.java
