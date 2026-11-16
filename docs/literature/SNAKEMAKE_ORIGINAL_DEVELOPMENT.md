# Original Snakemake adapter development checkpoint

**WARNING: unfinished development code. There is no supported original-engine
analysis command or accepted analysis bundle in this checkpoint.** The public
`snakemake-tools benchmark` command remains the separate native Java component
documented in [SNAKEMAKE_BENCHMARKS.md](SNAKEMAKE_BENCHMARKS.md). Use that guide for
benchmark aggregation. This checkpoint adds no catalog integration credit and
does not establish reproduction of a paper or its software environment.

The new package-private `SnakemakeOriginal*` classes contain draft primitives for
lossless requests, captured inputs, outputs, owned Linux processes, selected
runtime identity and original controller events. They are deliberately absent
from the public CLI dispatcher. The Python driver calls the selected upstream
Snakemake CLI API and a project logger records original events. The qualification
probe under `tools/literature/snakemake_original/` is a developer entrypoint;
a successful probe is not analysis admission.

The selected original edition is Snakemake 9.27.1, official tag commit
`f79d9eb5e2b679e1191818f24335907b041578be`, with its MIT license retained in
`LICENSES/Snakemake-MIT.txt`. The bundled `official-package.json` records the
complete selected wheel package. Selection of this edition does not establish
which version any cataloged paper used. `GENERIC-HELPER-PROVENANCE.json` records
the initial primitive copies; later adapter edits are visible in Git and are
not represented as unchanged copies of those initial sources.

Original-engine development controls cover standard, repeated and extended
benchmarks, an expected failed rule, Python `run:`, `script:`, a dynamic
checkpoint, a recovered retry and a pipe group. Their raw logs and failed
attempts remain in the development workspace. The checkpoint control preserves
the source's actual file order and checks the declared tag multiset; it does
not impose an order the source never promised. Pipe controls require a Linux
filesystem supporting FIFOs. A successful source control does not qualify the
unfinished Java workflow adapter.

Before offering original-engine execution, complete and qualify the workflow,
bundle and CLI layers; software installation policy; ownership and cleanup
controls; source completion and output admission; runtime checks; and portable
verification/replay. The runtime checker currently performs full environment
membership and byte checks and may be slow on Windows-mounted WSL storage.
No successful runtime or process probe alone satisfies these remaining gates.

**Required action when execution prerequisites are unavailable or installation
is forbidden:** skip the dependent original-engine analysis and retain a
prominent warning naming the missing prerequisite and the permitted setup
needed to retry. Do not silently switch to another engine, drop rules or replace
a FIFO with a regular file. Retain original inputs, source diagnostics and every
failed attempt, and use new output/setup directories for retries. Native
benchmark aggregation remains independently usable with complete valid inputs.

The development scripts bind to the recorded local runtime and control paths;
they are not a portable end-user installer. Do not edit an old receipt or refresh
its saved hashes to make changed code pass. Changes to a driver, logger or
selected software require new controls and a new adoption record.
