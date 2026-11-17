# Original Snakemake adapter development checkpoint

**WARNING: unfinished development code. There is no supported original-engine
analysis command. Completed developer bundles remain experimental and are not
accepted user analyses or catalog integration credit.** The public
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
a successful probe is not analysis admission. Development execution writes
`EXECUTED.json`, explicitly declaring `user_analysis_admitted=false`. The new
developer `bundle` probe additionally seals `bundle/COMPLETED.json` only after
its input, process, event, software-record and output checks pass. That marker
records completion of the experimental evidence format, not public analysis
admission. The developer `verify` probe reads captured records without invoking
the producer engine; `replay` performs fresh original-engine execution from
captured inputs and retains both result families and their comparison.

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

The current Snakemake Java regression suite passed 34 tests with no failures,
errors or skips, followed by complete source compilation and private executable-JAR
assembly. The earlier main-branch publication passed 303 targeted Java regression
tests and the website check; 33 of those tests were in the Snakemake test classes.
The previous checkpoint's Snakemake-only count of 52 was incorrect. Raw JUnit XML
supplies these counts; the current build wrapper's first incorrect expectation
and its independent assessment remain retained.
Five Linux setup-policy/export tests passed, including disabled installation,
existing-path preservation and case-distinct evidence retention. These checks
do not add catalog credit or complete the 250-paper objective.

The current bundle prototype passed twelve captured-source controls: ten
successful executions, the expected failed rule and a profile-policy skip. Two
fresh rerun generations each passed for the resource-profile and benchmark
fixtures, including rerun-of-rerun comparisons. During these controls the original
caller paths were absent or temporarily relocated; relocated controlled inputs
were then restored byte-for-byte. Exact class/source pins intentionally require
the frozen matching reader; old evidence is never rehashed to make a newer reader
accept it. These checks cover the named synthetic controls and do not qualify
every workflow for rerun or prove biological accuracy.

The independent corruption qualifier reconstructs the complete owned metadata,
blob closure, object-file references, physical manifest and completion hash of
new damaged copies. A valid independently resealed copy passes; fourteen
contradictory/corrupt cases are rejected, including changed parameters, effective
deployment or DAG policy, incomplete job events, live-process claims, changed
outputs, removed warnings, changed/extra helpers and invalid file/directory
censuses or completion claims. This checks consistency, not authorship or a
cryptographic signature.

**WARNING: the earlier development reader accepted an internally resealed helper
edit.** The failed qualification and its frozen reader remain retained. The
current reader explicitly compares the captured helper census and bytes with
its exact bundled driver/logger edition. Use newly qualified code and new
bundles; do not refresh an old bundle's code hashes to make it pass a new reader.

The process controls also exposed a procfs exit race: an already opened process
record can fail while the process terminates. The supervisor now ignores the
failed sample only after confirming disappearance or a terminal state. Success,
orphan, timeout and JVM-shutdown controls passed after this change; the original
failed control remains retained. Explicit allowed locale and timezone values now
override defaults and are recorded in the launch environment.

The driver now checks effective settings after the unchanged original parser
applies its captured workflow profile and before calling the original API. The
version-2 command and driver records bind the explicit recipe policy, effective
controlled settings and the parser's option provenance. Java independently
checks the same contract when reading complete bundles. Nineteen real parser
controls passed: ordinary execution, resource overrides and explicit CLI
precedence, plus sixteen rejected deployment, report, destructive, internal-mode
or partial-DAG requests. Rejected controls launched no original API, loaded no
Snakefile and produced no analysis output. The Java workflow retains a prominent
SKIP3 with the conflicting setting and recovery steps; cleanup or integrity
failures take precedence over a policy skip.

Changing the driver required new source controls and adoption records. The fresh
adoption used the existing installed engine without installing software. Its
complete installed-software manifest is byte-identical to the prior manifest;
the previous helper receipts were preserved. The private packaged JAR separately
passed a resource-profile execution and policy-skip control using adapter classes
loaded directly from that JAR.

Before offering original-engine analysis, finish managed workflow software
deployment, broader workflow qualification, the public CLI and catalog admission
for the six reviewed source roles. **Do not request managed installation through
a captured profile while the recipe selects preinstalled software.** Such a
conflict is skipped before the original API is called. Remove the conflicting
profile setting or select a separately qualified deployment, then retry NEW paths.
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

## Private deployment-provider checkpoint

**WARNING: installing or adopting these providers does not enable managed
workflow execution in the Java adapter. Managed Java requests still produce
SKIP3. The public original-engine command and catalog admission remain unfinished.**

The developer-only `setup_deployment.py` selects Miniforge 26.9.4-2 (Conda
26.9.4) and Apptainer 1.5.5, verifies pinned download bytes and installs into new
private prefixes. It requires Linux x86-64, a native case-sensitive filesystem
preserving POSIX modes and symlinks, and at least 3 GiB of free space. The selected
Apptainer dependency profile requires glibc 2.38 or later and `dpkg-deb`.
It extracts private libraries and the exact upstream helper wrapper without
installing system packages. Run from a checkout with Python 3:

```bash
mkdir -p "$HOME/.local/share/omicsmatic" build/snakemake-original
python3 -I -B tools/literature/snakemake_original/setup_deployment.py \
  --out "$HOME/.local/share/omicsmatic/snakemake-managed-NEW" \
  --records-out build/snakemake-original/NEW-manager-records \
  --allow-install
```

Omit `--allow-install` when installation is forbidden. Setup launches no download
or installation and records **SKIP3** with recovery instructions. For missing
host, filesystem, library or execution prerequisites, **skip the dependent
analysis**, retain the warning and logs, restore the named permitted prerequisite
and retry new paths. Preserve integrity failures as **FAIL2**; never overwrite a
failed prefix or refresh historical hashes to admit changed software.

`qualify_deployment.py --help` describes separate synthetic original-source
controls; `adopt_deployment.py --help` describes provider inventory adoption.
Original Snakemake 9.27.1 passed Conda, Apptainer and combined deployment controls,
including installation from its genuine Conda archive. Container bytes and the
complete provider trees remained unchanged across the controls. The recorded
host was Ubuntu 24.04 under WSL, glibc 2.39, effective UID 0; these checks do not
qualify nonroot hosts, other operating systems or a paper's environment.
The provider audit inspected 282 active ELF objects and recorded 58 external
library paths. Package-cache payloads and the retained Debian source tree remain
sealed as data. Original channel-priority diagnostics, earlier failed controls
and partial installations remain retained. Different Conda query scopes are
recorded separately; they do not justify removing the upstream warning.

Java provider verification and scoped installation-permission controls are
described in [SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md](SNAKEMAKE_DEPLOYMENT_DEVELOPMENT.md).
They do not yet enable managed Java workflow analysis. Spawned-process permission
propagation and its portable Java reader now also have twelve original-engine
controls bound to the actual Java process supervisor. Explicit software staging
data has eight separate link/scope/capacity controls; the failed first unpack is
retained. See the deployment development guide for its warning and recovery steps.
Complete managed-software capture/replay and the
remaining admission gates are unfinished.

Source-control qualification scripts still use the recorded development control
paths. The separate setup utility carries its own pinned synthetic fixtures.
Do not edit an old receipt or refresh its saved hashes to make changed code pass.
Changes to a driver, logger or selected software require new controls and a new
adoption record.
