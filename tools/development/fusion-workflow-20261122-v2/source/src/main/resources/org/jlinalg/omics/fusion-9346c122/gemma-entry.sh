#!/bin/sh
# Explicit output routing only: no scientific option, seed or exit-status repair.
set -u
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
IFS= read -r binary < "$base/gemma-program.txt"
for argument do
  if [ "$argument" = '-outdir' ]; then
    printf '%s\n' 'FAIL2: original invocation already declares -outdir; refusing conflicting routing.' >&2
    exit 2
  fi
done
if [ "$#" -eq 1 ] && [ "$1" = '-h' ]; then
  printf '%s\000' 'ORIGINAL-INVOCATION' "$@" 'UNMODIFIED-HELP-PROBE' >> "$base/gemma-invocations.nul"
  exec "$binary" "$@"
fi
printf '%s\000' 'ORIGINAL-INVOCATION' "$@" 'EXPLICIT-ADDED-OUTDIR' '.' >> "$base/gemma-invocations.nul"
printf '%s\n' 'Declared GEMMA output routing: adding -outdir . so unchanged FUSION reads its original output prefix.' >&2
exec "$binary" "$@" -outdir .
