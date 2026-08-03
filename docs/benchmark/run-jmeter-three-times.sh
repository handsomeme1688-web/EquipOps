#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <result-name> <test-plan.jmx> [jmeter -J options...]" >&2
  exit 2
fi

result_name="$1"
test_plan="$2"
shift 2

if command -v jmeter >/dev/null 2>&1; then
  jmeter_bin="$(command -v jmeter)"
elif [[ -x "${HOME}/.local/bin/jmeter" ]]; then
  jmeter_bin="${HOME}/.local/bin/jmeter"
else
  echo "jmeter not found; add it to PATH or install it at ~/.local/bin/jmeter" >&2
  exit 127
fi

if [[ ! -f "$test_plan" ]]; then
  echo "test plan not found: $test_plan" >&2
  exit 2
fi

if [[ -z "${BENCHMARK_TOKEN:-}" ]]; then
  echo "BENCHMARK_TOKEN is not set" >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" && "${ALLOW_DIRTY_BENCHMARK:-0}" != "1" ]]; then
  echo "working tree is dirty; commit the tested source before collecting reportable numbers" >&2
  echo "set ALLOW_DIRTY_BENCHMARK=1 only for a non-reportable smoke run" >&2
  exit 2
fi

commit="$(git rev-parse --short HEAD)"
source_state="${commit}"
if [[ -n "$(git status --porcelain)" ]]; then
  source_state="${commit}-dirty"
fi
results_root="${BENCHMARK_RESULTS_ROOT:-docs/benchmark/results}"
run_dir="${results_root}/$(date +%Y%m%d)-${source_state}"
mkdir -p "$run_dir"

threads="${JMETER_THREADS:-50}"
ramp_seconds="${JMETER_RAMP_SECONDS:-10}"
warmup_seconds="${JMETER_WARMUP_SECONDS:-15}"
duration_seconds="${JMETER_DURATION_SECONDS:-60}"
pause_seconds="${JMETER_INTER_RUN_PAUSE_SECONDS:-5}"

metadata="${run_dir}/${result_name}-metadata.txt"
{
  echo "source=${source_state}"
  echo "threads=${threads}"
  echo "ramp_seconds=${ramp_seconds}"
  echo "warmup_seconds=${warmup_seconds}"
  echo "duration_seconds=${duration_seconds}"
  echo "started_at=$(date --iso-8601=seconds)"
} > "$metadata"

warmup_output="${run_dir}/${result_name}-warmup.jtl"
if [[ -e "$warmup_output" ]]; then
  echo "result already exists: $warmup_output" >&2
  exit 2
fi
echo "warming up for ${warmup_seconds}s..."
"$jmeter_bin" -n -t "$test_plan" "$@" \
  -JTHREADS="$threads" \
  -JRAMP_SECONDS="$ramp_seconds" \
  -JDURATION_SECONDS="$warmup_seconds" \
  -j "${run_dir}/${result_name}-warmup-jmeter.log" \
  -l "$warmup_output"

for run in 1 2 3; do
  output="${run_dir}/${result_name}-run-${run}.jtl"
  "$jmeter_bin" -n -t "$test_plan" "$@" \
    -JTHREADS="$threads" \
    -JRAMP_SECONDS="$ramp_seconds" \
    -JDURATION_SECONDS="$duration_seconds" \
    -j "${run_dir}/${result_name}-run-${run}-jmeter.log" \
    -l "$output"
  echo "saved $output"
  if [[ "$run" -lt 3 && "$pause_seconds" -gt 0 ]]; then
    sleep "$pause_seconds"
  fi
done

python3 docs/benchmark/summarize-jtl.py \
  "${run_dir}/${result_name}-run-1.jtl" \
  "${run_dir}/${result_name}-run-2.jtl" \
  "${run_dir}/${result_name}-run-3.jtl" \
  --output "${run_dir}/${result_name}-summary.csv"

echo "summary: ${run_dir}/${result_name}-summary.csv"
