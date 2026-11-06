#!/usr/bin/env bash
# Spot mutation testing; the work is in MutationSpot.java. Usage: mutation-spot.sh (--list | [--dry-run] --all | [--dry-run] --one <id>)
# Exit code passes through: 1 when a mutant survived, 2 on a usage error or a dirty tree.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin="java"; fi
exec "$java_bin" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 scripts/MutationSpot.java --root "$root" "$@"
