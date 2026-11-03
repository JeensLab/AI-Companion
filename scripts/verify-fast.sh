#!/usr/bin/env bash
# Fast GameTest gate for Linux and macOS: the sharded run the Windows script does, with the same checks.
# A shard that dies early, reports nothing or passes fewer tests than it selected must not look green, and the
# shard set together must cover the whole required pool.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
reports="$root/build/diagnostics"
mkdir -p "$reports"

shards="auto"
repetitions=1
while [ $# -gt 0 ]; do
  case "$1" in
    --shards|--repetitions)
      [ $# -ge 2 ] || { echo "$1 needs a value" >&2; exit 2; }
      case "$1" in
        --shards) shards="$2" ;;
        --repetitions) repetitions="$2" ;;
      esac
      shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [ "$shards" != "auto" ]; then
  case "$shards" in
    ''|*[!0-9]*) echo "--shards must be 'auto' or 1..8" >&2; exit 2 ;;
  esac
  if [ "$shards" -lt 1 ] || [ "$shards" -gt 8 ]; then echo "--shards must be 'auto' or 1..8" >&2; exit 2; fi
fi
case "$repetitions" in
  ''|*[!0-9]*) echo "--repetitions must be 1..20" >&2; exit 2 ;;
esac
if [ "$repetitions" -lt 1 ] || [ "$repetitions" -gt 20 ]; then echo "--repetitions must be 1..20" >&2; exit 2; fi

# One server holds about 1.5 GB and needs its own world; 2 GB each keeps the machine out of swap.
# Six shards were the fastest here (952 tests in about 130 s); eight and ten only added contention and flake.
if [ -r /proc/meminfo ]; then
  free_gb=$(( $(awk '/MemAvailable/ {print $2}' /proc/meminfo) / 1024 / 1024 ))
elif command -v sysctl >/dev/null 2>&1; then
  # macOS: total memory, and half of it assumed free for the shards.
  free_gb=$(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1024 / 1024 / 1024 / 2 ))
else
  free_gb=0
fi
cores=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)
if [ "$shards" = "auto" ]; then
  by_memory=$(( free_gb / 2 ))
  wanted=$(( cores - 1 ))
  [ "$by_memory" -lt "$wanted" ] && wanted="$by_memory"
  [ "$wanted" -gt 6 ] && wanted=6
  [ "$wanted" -lt 1 ] && wanted=1
  shards="$wanted"
  echo "shards: $shards (cores $cores, free ${free_gb} GB, limit 6)"
fi

# Reads one shard's own log: the required pool it saw, how many required tests it selected, and how many it passed.
read_shard() {
  local shard_log="$1"
  shard_pool=0
  shard_selected=0
  shard_passed=0
  [ -f "$shard_log" ] || return 0
  local selection summary
  selection=$(grep -ao "Game test selection: pool=[0-9]* selected=[0-9]* pool-required=[0-9]* selected-required=[0-9]*" "$shard_log" | tail -1 || true)
  summary=$(grep -ao "All [0-9]* required tests passed" "$shard_log" | tail -1 || true)
  shard_pool=$(printf '%s\n' "$selection" | sed -n 's/.*pool-required=\([0-9]*\).*/\1/p' || true)
  shard_selected=$(printf '%s\n' "$selection" | sed -n 's/.*selected-required=\([0-9]*\).*/\1/p' || true)
  shard_passed=$(printf '%s\n' "$summary" | sed -n 's/All \([0-9]*\) required tests passed/\1/p' || true)
  shard_pool=${shard_pool:-0}
  shard_selected=${shard_selected:-0}
  shard_passed=${shard_passed:-0}
}

failed_runs=0
for iteration in $(seq 1 "$repetitions"); do
  rm -rf run/shard-*
  log="$reports/fast-$iteration.txt"
  started=$(date +%s)
  set +e
  ./gradlew --offline --continue -PgameTestShards="$shards" runFastGameTests > "$log" 2>&1
  batch_rc=$?
  set -e
  seconds=$(( $(date +%s) - started ))

  passed=0
  selected=0
  retried=0
  retried_list=""
  pools=""
  broken=""
  for shard in $(seq 0 $(( shards - 1 ))); do
    shard_log="run/shard-$shard/logs/latest.log"
    read_shard "$shard_log"
    if [ "$shard_selected" -le 0 ] || [ "$shard_passed" -ne "$shard_selected" ]; then
      # A flaked shard is retried alone, exactly like the CI job; its world and log start clean so a stale lock
      # cannot fail the retry as well.
      echo "::warning::shard $shard failed, retrying it alone"
      rm -rf "run/shard-$shard/world" "run/shard-$shard/logs/latest.log"
      set +e
      ./gradlew --offline "runGameTestShard$shard" -PgameTestShards="$shards" >> "$log" 2>&1
      retry_rc=$?
      set -e
      retried=$(( retried + 1 ))
      retried_list="$retried_list $shard"
      if [ "$retry_rc" -ne 0 ]; then broken="$broken shard-$shard:exit-$retry_rc"; fi
      read_shard "$shard_log"
    fi
    passed=$(( passed + shard_passed ))
    selected=$(( selected + shard_selected ))
    pools="$pools $shard_pool"
    if [ ! -f "$shard_log" ]; then
      broken="$broken shard-$shard:no-log"
    elif [ "$shard_selected" -gt 0 ] && [ "$shard_passed" -ne "$shard_selected" ]; then
      broken="$broken shard-$shard:${shard_passed}/${shard_selected}"
      grep -ao "\]: [^ ]* failed at" "$shard_log" 2>/dev/null | sed 's/]: //' | sed 's/^/  FAILED /' || true
    fi
  done

  unique_pools=$(printf '%s\n' "$pools" | tr ' ' '\n' | grep -v '^$' | sort -u | wc -l || true)
  pool_required=$(printf '%s\n' "$pools" | tr ' ' '\n' | grep -v '^$' | head -1 || true)
  pool_required=${pool_required:-0}
  unique_pools=${unique_pools:-0}

  cat > "$reports/fast-summary.json" <<JSON
{"iteration": $iteration, "shards": $shards, "seconds": $seconds, "passed": $passed, "selected": $selected, "poolRequired": $pool_required, "broken": "$broken", "retried": "$(echo $retried_list)"}
JSON

  echo "run $iteration: $passed of $selected passed, pool $pool_required, in ${seconds}s (${shards} shards, batch exit $batch_rc, retries $retried)"
  # Nothing looked broken, yet Gradle failed: a shard that printed its success line and then died on shutdown.
  if [ -z "$broken" ] && [ "$retried" -eq 0 ] && [ "$batch_rc" -ne 0 ]; then
    broken="batch-exit-$batch_rc"
  fi
  # A shard set that selects less than the whole required pool is a smaller suite passing as a green one.
  if [ -n "$broken" ] || [ "$unique_pools" -ne 1 ] || [ "$selected" -ne "$passed" ] || [ "$selected" -ne "$pool_required" ]; then
    echo "fast gate failed:$broken (passed $passed of $selected, pool $pool_required, pools:${pools})" >&2
    failed_runs=$(( failed_runs + 1 ))
  fi
done

if [ "$failed_runs" -ne 0 ]; then
  echo "$failed_runs of $repetitions runs failed" >&2
  exit 1
fi
echo "fast gate passed"
