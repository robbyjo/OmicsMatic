# Salmon setup and complete-output development audits

**Salmon remains missing in the catalog. Skip dependent project analysis until
the typed public quantification workflow and native current-host admission are
complete.** The current work adds complete-table arithmetic checks, an explicit
runtime setup prototype and portable native verification of setup receipts.
It does not provide an admitted user quantification command.

The earlier decoder checkpoint remains intact at
`salmon-native-development/VALIDATION.json`. The additional checkpoint is
`salmon-setup-development/VALIDATION.json`. Neither is a release, deployment,
whole-paper reproduction or biological-accuracy certificate. The existing
canonical executable is unchanged.

## Native complete-table checks

`org.jlinalg.sequence.SalmonQuantification.read` requires a separately protected,
complete ordered transcript population, original lengths, explicit count
precision, complete character/line capacities and cancellation checks. It reads
every original field through exact end of file and preserves the printed decimal
tokens, including signed zero. Do not infer the expected population or precision
from a successful output prefix.

`SalmonQuantification.audit` checks the complete estimated-count sum and every
row's TPM compatibility using fitted effective lengths, printed rounding bins
and an explicit conservative binary64 allowance. Supply the independently
audited assigned-fragment normalization mass. A count sum derived from the same
table is not independent evidence. The synthetic setup's mass of 8,520 is not a
general default for user data; mapped and assigned populations need their own
audit in a public workflow.

These are necessary arithmetic checks. Passing does not establish a unique
joint latent vector, correct effective-length fitting, optimizer convergence,
complete indexing, upstream process success or biological accuracy. A retained
original crashed-process table passes the necessary arithmetic checks and remains
explicitly unadmitted.

## Explicit runtime setup prototype

The source-workspace installer supports five exact original Linux x86_64
editions: **1.0.0, 1.1.0, 1.5.2, 1.8.0 and 1.9.0**. It checks complete original
archive and payload hashes, names, node types and declared modes. The lock records
six safe archive-to-materialized permission normalizations in the two oldest
editions; program and library bytes/modes are unchanged. Original GPL license
text accompanies the setup resources. Runtime binaries are acquired separately.

Setup requires permitted Linux x86_64 Python **3.12+**, `-I -B`, existing
`prlimit`, `ldd`, `bash` and `setsid`, a mode-preserving filesystem and new
canonical ASCII home/report paths without whitespace. On Windows, use Python
inside WSL. Installation and downloading each require their own explicit flag.
No analysis-time installation is implemented.

For example, from the repository in WSL, use a new private parent and complete
offline release archive directory:

```sh
cd /mnt/d/projects/JLinAlg
mkdir -m 700 /var/tmp/my-salmon-setup
python3 -I -B tools/literature/install_salmon.py \
  --kit src/main/resources/org/jlinalg/omics/salmon-runtime-setup \
  --profile linux-x86_64-salmon-1.8.0 \
  --home /var/tmp/my-salmon-setup/home-1.8.0 \
  --report /var/tmp/my-salmon-setup/report-1 \
  --archive-dir /path/to/complete-official-archives \
  --allow-install
```

The archive directory must contain the exact
`salmon-1.8.0_linux_x86_64.tar.gz` locked by
`tools/literature/salmon-runtime-lock.json`. To permit an official download when
no archive is available, use `--allow-download` in place of `--archive-dir`.
Use permitted locations and network access. To recheck an existing registered
home, omit installation/download flags and provide a **new** report path.
Existing homes, reports and partial downloads are preserved on failure.

Each setup retains version/library receipts, all supervised commands and clean
environment values, process-group receipts, an original index build, two original
40-target fits (EM/50 bootstraps and VB/100 bootstraps), complete output files,
warnings and a file census. Limits are 8 GiB address space, 180 CPU seconds,
300 seconds per process, 512 MiB per file and 4,096 file descriptors. A process
that exits zero with unfinished owned workers fails and is cleaned up.

**`COMPLETE_SETUP_ONLY` admits no user analysis.** The Python prototype is not
the missing native current-host gate. Internal Java `SalmonSetupEvidence` checks
copied reports on Windows or Linux without opening producer runtime/helper/
library paths. It verifies complete namespace/bytes, declared metadata, exact
commands/options, clean process receipts, protected health inputs, both original
tables and both complete bootstrap streams. Recorded Linux modes remain recorded
modes on a copied Windows report. Hashes prove integrity, not authenticity.

## Source differences and retained warnings

**Salmon 1.0.0 leaves two quantification SHA-512 fields empty.** Its original
`SalmonIndex.hpp` initializes those members empty and omits them from the
mapping-index JSON load; `ReadExperiment.hpp` returns them and `GZipWriter.cpp`
emits them. The 1.1.0 loader includes both fields. The five-edition original
outputs corroborate this difference. Native receipts list
`index_seq_hash512` and `index_name_hash512` as unavailable for 1.0.0; they never
fill them from `index/info.json` or claim those missing links match.

The automatic-library health fit reports **IU** even though its observed ISR
counter is 8,520. Preserve the requested `A`, reported IU classification and
observed ISR count as separate facts. Do not relabel the original classification.

All setup fits reuse one small synthetic population below the original
five-million-fragment burn-in threshold. Retain the observed-mapping effective-
length fallback and all original warnings. The separate four-target positional-
bias crashes remain unresolved and unadmitted. Do not silently remove requested
position bias to make a failed analysis pass.

The new 52-case setup suite plus the existing 60 table and 27 bootstrap cases
pass against the actual development JAR on Windows Java 21/25 and Linux Java 21
without skips. Six complete historical setup reports cover five editions,
480 health-table rows and 36,000 bootstrap values; three JVM replays do not make
them independent biological datasets. Installer policy controls have 16 Linux
passes and 14 Windows passes plus two explicit Linux-only skips. Mock policy
cases do not establish scientific runtime admission.

## Failures and required actions

**SKIP3 — dependent analysis skipped:** a required permitted runtime, helper,
resource, installation/download permission or capability is unavailable. Read
the named cause, complete only the permitted setup action and retry into new
paths. If the environment forbids the installation, skip that analysis.

**FAIL2 — no result admitted:** invalid input, changed evidence, upstream process
failure, incomplete output, numerical/capacity/cancellation or finalization
failure. Inspect `WARNINGS.md`, `FAILURE.json` when present, all launch/process
receipts, original stdout/stderr, `health.json`, `manifest.json` and the complete
index/result families. Preserve partials, correct the cause and use new paths.
Never treat `quant.sf`, a valid bootstrap prefix or a setup marker alone as a
successful user analysis.

Native current-host checks, typed index/reference/read admission, complete public
quantification, CLI/recipe/example and catalog closure remain unfinished.
Unknown paper settings and downstream aggregation/differential analyses remain
separate. **Cell Ranger remains excluded at the user's request.**
