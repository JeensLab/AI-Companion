#!/usr/bin/env bash
# Complexity ceilings: runs PMD, then compares its report with tools/pmd-baseline.txt (see scripts/PmdCheck.java).
# Extra arguments go to Gradle. Exit 0 clean, 1 new violation or stale baseline entry, 2 PMD or the report failed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
./gradlew pmdMain --offline -q "$@" || { echo "pmd-check: pmdMain failed" >&2; exit 2; }
java scripts/PmdCheck.java
