#!/usr/bin/env bash
# Anti-tautology check for @GameTest methods; the work is in TestQuality.java. Usage: test-quality.sh [--selftest]
# Exit code passes through: 1 a test without a reachable assertion, 2 a usage or I/O error.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin="java"; fi
if [ "${1:-}" = "--selftest" ]; then target="--selftest"; else target="$root"; fi
exec "$java_bin" -Dfile.encoding=UTF-8 scripts/TestQuality.java "$target"
