# Synthetic original-Squidpy workflows

**Scoped original-runtime workflows; current source build required.**
Build the current source; existing published release binaries may predate this
command. Read the [workflow guide](../../docs/literature/SQUIDPY_WORKFLOWS.md),
including its prominent SKIP3/FAIL2 and historical malformed-export notices.

These small artificial expression, graph, interaction and image files contain no
cohort data. The expression object contains one synthetic sample. Coordinates
use arbitrary units. Ligand/receptor labels and annotations are invented examples,
not a supplied CellPhoneDB/OmniPath database or biological result. `DATA-PROVENANCE.json`
records the input derivation and hashes. Existing source/reference inputs are
retained unchanged.

Run inside the same Linux/WSL host as the chosen private runtime and JAR:

```sh
python3 examples/squidpy-original/prepare_requests.py --python /PATH/TO/PRIVATE/bin/python --edition 1.8.4 --out /WORK/NEW-REQUESTS
java -jar /PATH/TO/CURRENT.jar squidpy-tools --request /WORK/NEW-REQUESTS/neighbors.json
java -jar /PATH/TO/CURRENT.jar squidpy-tools --verify /WORK/NEW-REQUESTS/results/neighbors
java -jar /PATH/TO/CURRENT.jar squidpy-tools --request /WORK/NEW-REQUESTS/neighbors-replay.json
```

Substitute actual absolute paths. The helper queries exact installed Python/core
dependency editions without importing or downloading Squidpy, preserves the
virtual-environment executable path, and creates NEW requests/results parents.
It also generates `moran.json`, `ligrec.json` and their replay requests, plus
`nanostring.json`/replay where that original edition provides the reader. Run a
parent request before its replay. Original1.1.2 has no Nanostring reader; the
helper records that explicit SKIP3 and creates the three other operations.

**SKIP3:** install/provide the named permitted matching runtime/dependency, or
retain the skip. **FAIL2:** retain raw/partial/failed files, correct the stated
cause and choose NEW output. Do not change an existing completed request's
policies, versions or evidence. Missing original runtimes never trigger a silent
native fallback. Final admission is in `analysis-status.json`/`COMPLETED.json`;
read all warnings and per-row probability statuses before downstream use.
