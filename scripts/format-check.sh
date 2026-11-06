#!/usr/bin/env bash
# Whole-tree formatting rules: trailing whitespace, final newline, unused imports. See scripts/FormatCheck.java.
# Usage: scripts/format-check.sh [--fix | --selftest]; exit 0 clean, 1 findings, 2 tool error.
set -u

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root" || exit 2

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="java"
else
  echo "format-check: java was not found (set JAVA_HOME or put a JDK 21 on PATH)" >&2
  exit 2
fi

exec "$java_bin" scripts/FormatCheck.java "$@"
