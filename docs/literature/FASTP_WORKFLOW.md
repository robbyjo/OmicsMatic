# fastp: complete read processing with retained evidence

The current source provides `fastp-tools` for original fastp 0.22.0, 0.23.2,
0.25.0 and 1.0.1. It retains complete inputs and original outputs, checks the full
FASTQ populations and source report statistics, and writes a portable result
bundle. Older released 0.3.7 executables lack this command; build current source.
The [workflow validation record](FASTP_WORKFLOW_DEVELOPMENT_VALIDATION.json)
identifies the exact tested development JAR and evidence archives.

**Require exit 0, a valid top-level `COMPLETED.json`, and successful
`fastp-tools --verify DIRECTORY` before downstream use.** Read `WARNINGS.md`.
Runtime setup completion, original process exit zero and nested provisional
stage records do not admit a user result.

## Quick start on Linux or inside WSL

Use Linux x86_64 Java and Python 3.11+ with permitted `setsid` and `prlimit`
execution. The pinned original binaries are static; this workflow does not
install a compiler, package manager or global runtime. On Windows, run setup and
analysis in a Linux WSL shell with Linux Java. Native Windows Java can verify
copied result bundles. Other unavailable hosts must skip original execution.

From the current repository, build a separate JAR into a **new** directory:

```bash
./gradlew -I tools/literature/fastp-development.init.gradle \
  -PfastpDevelopmentOut=build/fastp-local-build-1 executableJar
omics_jar="$(pwd)/build/fastp-local-build-1/omicsmatic-0.3.7-fastp-development.jar"
java -jar "$omics_jar" fastp-tools --help
java -jar "$omics_jar" fastp-tools --runtime-profiles

mkdir fastp-example
cd fastp-example
java -jar "$omics_jar" fastp-tools --export-runtime-setup setup-files
python3 setup-files/tools/literature/install_fastp.py \
  --profile linux-x86_64-fastp-0.23.2 \
  --home "$PWD/runtime" --report "$PWD/setup-report" --cache "$PWD/cache" \
  --allow-install --allow-download
```

The install/download flags are separate permissions. Omit `--allow-download`
when the exact pinned original binary is already in `CACHE/fastp.VERSION`.
Existing changed homes, corrupt caches and failed outputs are preserved. Require
a successful setup and its `COMPLETED.json`; that marker means **setup only**.

Generate an explicitly synthetic paired example, then the complete typed recipe:

```bash
python3 - <<'PY'
from pathlib import Path
seq = "ACGTTGCAAGCTTAGCCGAT" * 4
mate = seq.translate(str.maketrans("ACGT", "TGCA"))[::-1]
for number, bases in [(1, seq), (2, mate)]:
    Path(f"read{number}.fastq").write_text(
        f"@example:1/{number}\n{bases}\n+\n{'I' * len(bases)}\n")
PY
java -jar "$omics_jar" fastp-tools --example > recipe.json
java -Xmx512m -jar "$omics_jar" fastp-tools \
  --recipe recipe.json --home "$PWD/runtime" --setup-report "$PWD/setup-report" \
  --out result-1
java -Xmx512m -jar "$omics_jar" fastp-tools --verify result-1
```

`result-1` must not exist. Input paths are relative to the recipe file. Runtime,
setup and result trees must be separate. For a single-end analysis, remove
`inputs.read2` and leave paired-only choices disabled. Replace the example inputs
and explicitly review every policy, option and limit before using study data.
The example is not a paper reconstruction or an analysis recommendation.

## Recipe choices

`--example` emits schema version 1, an exact supported `profile`, `inputs`,
`policies`, all typed `options` and all `limits`. Unknown fields are rejected.
An unsupported edition/capability is skipped rather than silently substituted.

| Recipe field | Meaning and required choice |
| --- | --- |
| `inputs.read1`, optional `inputs.read2` | Complete plain or gzip FASTQ, detected by magic bytes; unwrapped four-line records. |
| `policies.encoding` | `PHRED33` or `PHRED64`; encoding is never guessed. |
| `policies.alphabet` | `UPPER_ACGTN` or deliberately literal printable ASCII. Literal symbols/case remain visible. |
| `policies.plus` | `EMPTY_ONLY`, `MATCH_FIRST_TOKEN_OR_EMPTY`, or deliberately `SOURCE_IGNORED`. Ignored mismatches are counted and warned about. |
| `policies.pairing` | `EXACT_FIRST_TOKEN`, `TERMINAL_MATE_SUFFIX`, `CASAVA_FIRST_TOKEN`, or `ORDINAL_ONLY_UNVERIFIED`. Complete output conservation still requires a shared pair identity; unequal ordinal-only names are skipped, not renamed. |
| `options.adapters` | Enable/disable trimming, explicit read1/read2 adapter sequences and paired auto-detection. No adapter FASTA-list contract. |
| `options.global` | Explicit per-mate front/tail trims and final maximum lengths. Explicit mate2 zero overrides source inheritance. |
| `options.cutting` | Separate front/right/tail quality windows and thresholds. Source right-cutting suppresses tail-cutting. Sliding-window cutting is disabled by the emitted source-default example. |
| `options.filters` | Quality, average-quality, N, length and complexity choices; source predicate order and integer means are retained. |
| `options.polyG`, `polyX` and minimum lengths | Header-dependent source auto, explicit enabled/disabled poly-G, and explicit poly-X processing. |
| `options.correction`, `overlap` | Paired overlap correction and thresholds; disabled for single-end input. |
| `options.duplication` | Explicit evaluation, deduplication and accuracy. Accuracy 0 selects the source default; 0.22.0 has no configurable accuracy flag. |
| `options.storeUnpaired` | Separate passing orphans when their mates fail; paired input only. |
| `limits.stage` | Complete input, original process and retained bundle capacities. |
| `limits.audit` | Complete statistics, external-sort, duplicate-header matching and metadata capacities. |

Merging, UMI processing, read splitting, stdin streaming, adapter FASTA lists and
index filtering are outside this typed workflow. Do not translate an unsupported
request into a different analysis. The wrapper has no free-form shell or flag
passthrough.

## What a completed result proves

The wrapper runs the exact managed original on complete captured inputs. Java
independently recomputes all input/main passing totals, source GC counts,
Q20/Q30, integer mean length, every quality/content cycle and all 1,024 five-mers.
Edition 1.0.1's detailed Q40 field is checked separately. Source ASCII base
buckets, uppercase five-mer conventions and six-significant-digit output
precision are retained. Orphan files are excluded from the original main
passing statistics and reconciled separately.

Every retained output occurrence must fit an original header/plus/quality and
sequence transformation envelope. Native source cutting/filtering, bounded
prefix trimming and high-quality mate-complement correction constraints are
checked. Complete disk sorting and bounded assignment preserve occurrences.
Ambiguous duplicate-header assignments are marked explicitly; a feasible witness
does not establish a unique biological occurrence identity.

**This scope does not independently reproduce the upstream adapter, overlap,
poly-tail or Bloom-filter choices.** Auxiliary counters receive separately
reported consistency bounds. Visible corrections are lower bounds because
corrected bases can later be trimmed, filtered or suppressed. Original
HTML/stdout/stderr remain byte-pinned raw supplements. No source-to-binary,
host-security, execution-authenticity, biological-accuracy or whole-paper
certificate is claimed.

Portable verification checks the exact recipe replay, complete file/directory
census, captured setup/edition evidence, original process declarations and
warnings. It recomputes every retained audit file in separate bounded temporary
scratch. It does not access original study inputs or installed runtimes and does
not rerun fastp. A failed replay preserves scratch and reports its path; the
source bundle is unchanged. Preserve the complete directory, including hidden
runtime-evidence files, when moving it to another machine.

## Warnings that change interpretation

**Absent paired reads are not necessarily duplicates.** The audited original
editions emit neither mate to any FASTQ when both fail. Deduplication also
suppresses whole occurrences. The ledger therefore does not assign an absent
pair's individual cause merely from absence; source counters/rate and possible
failure envelopes constrain the totals. Single-end missing occurrences require
enabled deduplication. Review `audit/read-dispositions.tsv` and the original
filtering report.

**Insert-size histograms sample eligible reads from thread zero.** They are not
the complete all-pair distribution. **0.22.0 duplicate buffers/counters are
non-atomic.** Multithreaded duplicate-rate reproducibility is not certified; this
is a source-code inference, not an observed race in these controls. Select one
thread explicitly when that limitation matters. The Bloom estimate is not proof
of exact identical-sequence duplication.

**Accuracy 6 requests a 24 GiB Bloom buffer plus overhead.** The synthetic P054
reported-option control completed using explicit 28 GiB address-space and
27 GiB sampled-RSS capacities. An insufficient reservation returns SKIP3.
Use adequate permitted resources or skip that requested analysis; never silently
lower accuracy. Sampled aggregate RSS and process limits are not a hard
whole-host-memory guarantee or an OS sandbox.

If the main passing population is empty, a valid QC bundle can still complete.
Its warnings contain `NO_MAIN_PASSING_READS` and `session.json` sets
`downstream_usable` to false. **Skip dependent main-read analyses.** Passing
orphans and failed reads remain visible for review.

## Errors and recovery

**SKIP3: analysis skipped.** The exact runtime, permitted execution/helper,
requested capability or duplicate-memory reservation is unavailable. Keep
dependent analysis skipped. Install the exact permitted dependency, choose an
adequate supported host or resolve the named contract, then retry to new paths.
If installation is forbidden, do not continue with that analysis.

**FAIL2: no result admitted.** Input, integrity, process, capacity, cancellation
or finalization failed. Preserve raw/partial files, `FAILURE.md`, `FAILURE.json`
and logs. Correct the named cause; increase heap and explicit capacities only
where resources permit; retry with a new output directory. A prefix is never
promoted to a complete population. The 84,300,000-byte/300,000-read regression
completed and verified on Windows with a 128 MiB Java heap; large FASTQ pins use
their declared input capacity rather than the executable/setup-size ceiling.

## Paper roles and limits

| Paper | Reported use | What remains unresolved |
| --- | --- | --- |
| P007 | 1.0.1 default processing of paired ATAC-seq/CUT&Tag reads before Bowtie2. | The phrase quality trimming does not establish enabled sliding-window cuts; source default cutting is off. |
| P046 | 0.22.0 processing to 100-bp RNA reads before STAR/RSEM. | Exact trimmed end and full command are absent; do not invent a final-length flag. |
| P054 | 0.23.2 with paired adapter detection, correction, right cutting, average Q30, minimum length100, deduplication and accuracy6. | Synthetic option configuration is tested; barcode positions and full biological workflow are not reproduced. |
| P089 | 0.23, quality filtering, minimum45bp and adapter removal. | Exact patch is unreported; no automatic 0.23.2 substitution. |
| P110 | ChIP read trimming before separate alignment, duplicate and peak tools. | Version and complete options are unreported. |
| P223 | Standard ATAC adapter/quality processing. | Version and full command are unreported; downstream peak/annotation work is separate. |
| P248 | 0.25.0 default quality assessment before BWA/Picard/GATK. | Do not infer extra trimming flags from quality assessment. |

The primary DOI/PMCID, paragraph hashes and full usage contexts remain in the
250-paper catalog. Earlier `FASTP_*DEVELOPMENT*` documents are historical
milestones, including the former no-public-CLI state. Their original archives
and failed attempts remain retained. Cell Ranger remains excluded by the user.
