#!/usr/bin/env bash
# Spot mutation testing; the work is in MutationSpot.java.
# Usage: mutation-spot.sh (--list | [--dry-run] --all | [--dry-run] --one <id>) [--timeout-min 20]
# Exit code passes through: 1 survived, broken, timed out or unexpected failure; 2 usage error, dirty tree (untracked files
# count) or red baseline; 3 the tree was dirty after a restore.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin="java"; fi
exec "$java_bin" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 scripts/MutationSpot.java --root "$root" "$@"
