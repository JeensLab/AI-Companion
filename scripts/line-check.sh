#!/usr/bin/env bash
# Ratchet over the lines a change adds: length, names, TODO. See scripts/LineCheck.java.
# Usage: scripts/line-check.sh [--base <ref>]
cd "$(dirname "$0")/.." || exit 2
JAVA=java
if ! command -v java >/dev/null 2>&1; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
  else
    echo "line-check: java not found (put JDK 21 on PATH or set JAVA_HOME)" >&2
    exit 2
  fi
fi
exec "$JAVA" scripts/LineCheck.java "$@"
