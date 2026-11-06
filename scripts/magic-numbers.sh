#!/usr/bin/env bash
# Magic-number ratchet on changed production lines. Usage: scripts/magic-numbers.sh [--base <ref>]
# Compares the working tree with the merge base of origin/main and HEAD (uncommitted and untracked files included).
set -euo pipefail

cd "$(dirname "$0")/.."

base=""
while [ $# -gt 0 ]; do
    case "$1" in
        --base)
            if [ $# -lt 2 ]; then echo "magic-numbers: --base needs a ref" >&2; exit 2; fi
            base="$2"; shift 2 ;;
        *) echo "magic-numbers: unknown argument '$1' (usage: --base <ref>)" >&2; exit 2 ;;
    esac
done

if [ -n "${JAVA_HOME:-}" ] && { [ -x "$JAVA_HOME/bin/java" ] || [ -x "$JAVA_HOME/bin/java.exe" ]; }; then
    java_cmd="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    java_cmd="java"
else
    echo "magic-numbers: java not found; set JAVA_HOME to a JDK 21 or put java on PATH" >&2
    exit 2
fi

if [ -z "$base" ]; then
    if git rev-parse --verify -q origin/main >/dev/null; then
        base="$(git merge-base origin/main HEAD)"
    elif [ -n "${CI:-}" ]; then
        echo "magic-numbers: origin/main is missing in CI; fetch it (e.g. fetch-depth: 0) or pass --base <ref>" >&2
        exit 2
    else
        echo "magic-numbers: warning: origin/main is missing, comparing with HEAD~1" >&2
        base="HEAD~1"
    fi
fi

{
    git diff --no-color --no-ext-diff -U0 "$base" -- src/main/java
    git ls-files --others --exclude-standard -z -- src/main/java | while IFS= read -r -d '' file; do
        git diff --no-color --no-ext-diff -U0 --no-index -- /dev/null "$file" || true
    done
} | "$java_cmd" scripts/MagicNumbers.java
