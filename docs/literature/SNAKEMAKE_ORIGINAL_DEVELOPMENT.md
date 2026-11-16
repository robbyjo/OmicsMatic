# Original Snakemake adapter development checkpoint

**WARNING: unfinished development code. There is no supported original-engine
analysis command or accepted analysis bundle in this checkpoint.** The public
`snakemake-tools benchmark` command remains the separate native Java component
documented in [SNAKEMAKE_BENCHMARKS.md](SNAKEMAKE_BENCHMARKS.md). Use that guide for
benchmark aggregation. This checkpoint adds no catalog integration credit and
does not establish reproduction of a paper or its software environment.

The package-private `SnakemakeOriginal*` classes now connect lossless requests,
complete captured workflow/data roots, original execution, controller events,
declared outputs and before/after working-state records. They remain absent
from the public CLI dispatcher. The Python driver calls the selected upstream
Snakemake CLI API and a project logger records original events. The qualification
probe under `tools/literature/snakemake_original/` is a developer entrypoint;
a successful probe is not analysis admission. Successful development execution
writes `EXECUTED.json`, explicitly declaring `user_analysis_admitted=false`;
there is no final `COMPLETED.json` analysis bundle.

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

## Private runtime setup

**Use a persistent, case-sensitive native Linux filesystem for the installation
prefix.** It must preserve POSIX permission modes and symlinks. The Windows-mounted
development folder failed these checks and was skipped before installation. The
installer checks these capabilities before downloading software. Temporary Linux
directories were cleared between WSL sessions during development; keep the
runtime outside temporary directories and export its records to persistent
project storage.

From a Linux checkout of the current source, with installation permitted:

```bash
mkdir -p "$HOME/.local/share/omicsmatic" build/snakemake-original
python3 -I -B tools/literature/snakemake_original/setup_runtime.py \
  --out "$HOME/.local/share/omicsmatic/snakemake-9271-NEW" \
  --records-out build/snakemake-original/NEW-setup-records \
  --allow-install
```

Both output paths must be new and their parents must exist. Omit `--allow-install`
when installation is forbidden: the command writes **SKIP3**, gives the required
action and launches no download, installation or control workflow. Unsupported
hosts/filesystems, unavailable permitted downloads and failed installation also
skip dependent analysis. Integrity or source-control failures produce **FAIL2**.
Never reuse a partial installation directory to conceal an earlier failure.

Setup uses the byte-pinned Pixi 0.83.0 binary, bundled package manifest/lock and
official Snakemake 9.27.1 wheel. It creates its own environment, home and cache;
runs three successful synthetic source DAGs and one expected failed-rule control;
then seals the complete installed environment and resolved host helpers. The
result names the `adoption/` directory consumed by the development runtime check.
It installs no user workflow dependencies and runs no user analysis. An optional
`--pixi` path can supply the exact existing pinned bootstrap binary instead of
downloading it.

`--records-out` retains source snapshots, setup/control logs, lock, wheel,
adoption metadata and complete file hashes. Case-distinct filesystem probe names
and symlink spelling are retained inside `FILESYSTEM-PROBE.zip`, so exporting to
a case-insensitive record filesystem cannot collapse that evidence. Installation
and package-cache binaries remain in the private prefix. Each new installation
has its own complete manifest: the same resolved lock does not establish whole
environment byte identity, identical filesystem modes or an author environment.

## Qualified development checks

The original installed runtime and a fresh native Linux installation both passed
complete Java runtime verification and deliberate corruption controls. The
checks retain all file bytes, modes, link targets, resolved paths and membership;
eight bounded workers reduce repeated filesystem overhead, and full before/after
inventories reject concurrent changes. Original selected source/package pins and
old adoption records were not refreshed.

Owned-process controls passed ordinary success, an orphan that changed its
working directory to `/`, a wall-time failure and JVM shutdown. An unrelated
process sharing the working directory survived. These controls establish the
tested local process behavior, not a malicious-code sandbox, remote-job cleanup
or survival after forcibly killing the JVM without shutdown hooks.

Ten captured-workflow controls passed on each of the two tested installations:
standard/repeated/extended benchmarks; `run:`; `script:`; a dynamic checkpoint;
a recovered retry; a pipe group; the expected failed rule; and merged
configuration with external data and output bindings. The last control preserves
`9007199254740993` through the original Python configuration parser. Caller inputs
remain unchanged. Retry diagnostics are retained and prominently warned about;
the expected failed-rule run retains partial outputs and admits no result.
Recorded benchmark values remain timing measurements, not deterministic rerun
references. The checkpoint control retains actual source ordering and checks
only its declared tag multiset.

The current Java regression suite passed 50 tests with no failures/errors/skips.
Five Linux setup-policy/export tests passed, including disabled installation,
existing-path preservation and case-distinct evidence retention. These checks
do not add catalog credit or complete the 250-paper objective.

Before offering original-engine analysis, finish and qualify final bundle sealing,
portable verification, genuine rerun comparison, managed workflow software
deployment, the public CLI and catalog admission for the six reviewed source roles.
The development execution path currently accepts an explicitly selected
preinstalled deployment. It warns that upstream Conda/Apptainer rule directives
are inactive in that mode and that arbitrary dependencies loaded by trusted
workflow code are not sealed by the executable list. Managed-deployment requests
currently produce SKIP3; they never silently fall back to preinstalled software.
No successful setup, runtime, process or execution probe satisfies the remaining
analysis-admission gates.

**Required action when execution prerequisites are unavailable or installation
is forbidden:** skip the dependent original-engine analysis and retain a
prominent warning naming the missing prerequisite and the permitted setup
needed to retry. Do not silently switch to another engine, drop rules or replace
a FIFO with a regular file. Retain original inputs, source diagnostics and every
failed attempt, and use new output/setup directories for retries. Native
benchmark aggregation remains independently usable with complete valid inputs.

Source-control qualification scripts still use the recorded development control
paths. The separate setup utility carries its own pinned synthetic fixtures.
Do not edit an old receipt or refresh its saved hashes to make changed code pass.
Changes to a driver, logger or selected software require new controls and a new
adoption record.
