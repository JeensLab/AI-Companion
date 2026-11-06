#!/usr/bin/env bash
# Anti-tautology check for @GameTest methods; the work is in TestQuality.java. Exit code passes through.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin="java"; fi
exec "$java_bin" scripts/TestQuality.java "$root" "$@"
