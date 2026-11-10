#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
mkdir -p build/diagnostics
reports=$(mktemp -d "$PWD/build/diagnostics/jokes-full.XXXXXX")
world="$reports/server"
failed=0
run_step() {
    local name=$1 code
    shift
    if "$@" > "$reports/$name.log" 2>&1; then code=0; else code=$?; fi
    printf '%s\t%s\n' "$name" "$code" >> "$reports/results.tsv"
    echo "$name: exit $code; log: $reports/$name.log"
    if [[ $code -ne 0 ]]; then failed=1; fi
}
run_step build ./gradlew --offline build
run_step switches bash scripts/switch-check.sh
run_step whitespace git -c core.whitespace=cr-at-eol diff --check
# The full registry includes the existing regression tests and CompanionJokeTests, without exclusions.
run_step full-gametest ./gradlew --offline runGameTestServer "-PgameTestDirectory=$world"
run_step client bash scripts/verify-jokes-client.sh
echo "Combined results: $reports/results.tsv"
exit "$failed"
