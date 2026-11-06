#!/usr/bin/env bash
# Package-boundary check (client imports, server code importing client/, *Tests placement); rules in scripts/ArchCheck.java.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
java_bin="java"
[ -n "${JAVA_HOME:-}" ] && java_bin="$JAVA_HOME/bin/java"
exec "$java_bin" scripts/ArchCheck.java "$@"
