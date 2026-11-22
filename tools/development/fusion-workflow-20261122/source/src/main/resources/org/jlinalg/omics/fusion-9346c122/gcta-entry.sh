#!/bin/sh
# Opt-in invocation compatibility only. Real analysis argv/status are unchanged.
set -u
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IFS= read -r binary < "$base/gcta-program.txt"
if [ "$#" -eq 0 ]; then
  "$binary" > "$base/gcta-probe.stdout" 2> "$base/gcta-probe.stderr"
  status=$?
  cat "$base/gcta-probe.stdout"
  cat "$base/gcta-probe.stderr" >&2
  printf '%s\n' "$status" > "$base/gcta-probe.original-exit"
  if [ "$status" -eq 1 ] && grep -F 'Genome-wide Complex Trait Analysis (GCTA)' "$base/gcta-probe.stdout" >/dev/null && grep -F 'Error: no analysis has been launched by the option(s)' "$base/gcta-probe.stdout" >/dev/null; then
    printf '%s\n' 'Declared GCTA probe compatibility: recognized no-analysis exit 1 accepted for the startup probe only; original output/status retained.' >&2
    exit 0
  fi
  exit "$status"
fi
exec "$binary" "$@"
