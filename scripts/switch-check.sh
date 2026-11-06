#!/usr/bin/env bash
# Runs scripts/SwitchCheck.java from the repository root; the exit code is the check's own.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java.exe" ]; then
  java_bin="$JAVA_HOME/bin/java.exe"
elif command -v java >/dev/null 2>&1; then
  java_bin="java"
else
  echo "switch-check: Java not found, set JAVA_HOME or put java on PATH" >&2
  exit 2
fi

exec "$java_bin" scripts/SwitchCheck.java
