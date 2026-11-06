#!/usr/bin/env bash
# The single pre-PR gate for Linux, macOS and Git Bash: one command, one summary line, nonzero exit on any failure.
# Steps, in order: source-tree guard, git diff --check, optional shape checks, Gradle build, GameTests.
# Usage: scripts/gate.sh [--fast]
#   --fast  skips the GameTests. For local iteration only: CI and the final pre-PR run must never use it.
# Env GATE_GRADLE_ARGS passes extra arguments to the Gradle build (for example --offline).
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root" || exit 2

fast=0
while [ $# -gt 0 ]; do
  case "$1" in
    --fast) fast=1; shift ;;
    *) echo "unknown argument: $1" >&2; echo "GATE FAIL: usage"; exit 2 ;;
  esac
done

steps=""
fail() {
  echo "GATE FAIL: $1"
  exit 1
}

# 1. Guard: a build over an empty source tree is green and proves nothing.
if [ -z "$(find src/main/java -name '*.java' -print -quit 2>/dev/null)" ]; then
  echo "src/main/java has no .java file: the build must not pass on an empty tree" >&2
  fail "source-guard"
fi
steps="source-guard"

# 2. Whitespace errors, in the working tree and in the committed range.
echo "== diff-check"
git diff --check HEAD || fail "diff-check (working tree)"
if git rev-parse --verify -q origin/main >/dev/null; then
  base="$(git merge-base origin/main HEAD)" || fail "diff-check (merge base)"
  echo "range: ${base:0:10}..HEAD (merge base with origin/main)"
  git diff --check "$base" HEAD || fail "diff-check (range)"
elif [ -n "${CI:-}" ]; then
  echo "origin/main is missing in CI: the committed range cannot be checked" >&2
  fail "diff-check (origin/main missing)"
elif git rev-parse --verify -q HEAD~1 >/dev/null; then
  echo "WARNING: origin/main is not available: checking only HEAD~1..HEAD"
  git diff --check HEAD~1 HEAD || fail "diff-check (range)"
else
  echo "WARNING: origin/main and HEAD~1 are not available: the committed range is not checked"
fi
steps="$steps, diff-check"

# 3. Shape checks run only when their script exists, so later stages add them without editing the gate.
shape_run=0
for name in code-shape switch-check arch-check catch-check magic-numbers line-check test-quality; do
  script="scripts/$name.sh"
  if [ -f "$script" ]; then
    echo "== $name"
    bash "$script" || fail "$name"
    shape_run=$(( shape_run + 1 ))
  else
    echo "skip: $script (not present)"
  fi
done
steps="$steps, $shape_run shape checks"

# 4. Build. It compiles and packs the JAR but runs no tests.
echo "== build"
# shellcheck disable=SC2086
./gradlew build ${GATE_GRADLE_ARGS:-} || fail "build"
steps="$steps, build"

# 5. GameTests through the sharded runner, which counts shards honestly and prints failed test names.
if [ "$fast" -eq 1 ]; then
  echo "GameTests skipped (--fast)"
  steps="$steps, gametest SKIPPED"
else
  echo "== gametest"
  mkdir -p build/diagnostics
  rm -f build/diagnostics/fast-summary.json
  bash scripts/verify-fast.sh || fail "gametest"
  passed="$(sed -n 's/.*"passed": *\([0-9]*\).*/\1/p' build/diagnostics/fast-summary.json 2>/dev/null | tail -1)"
  selected="$(sed -n 's/.*"selected": *\([0-9]*\).*/\1/p' build/diagnostics/fast-summary.json 2>/dev/null | tail -1)"
  [ -n "$passed" ] && [ "$passed" = "$selected" ] || fail "gametest (no passed count in fast-summary.json)"
  steps="$steps, gametest $passed/$selected"
fi

echo "GATE PASS ($steps)"
