#!/usr/bin/env bash
# Size ceilings (file, method, parameters) against tools/code-shape-baseline.txt; see scripts/CodeShape.java.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="$(command -v java)"
else
  echo "java not found: set JAVA_HOME to a JDK 21 or put java on PATH" >&2
  exit 2
fi

exec "$java_bin" scripts/CodeShape.java "$@"
