# fastp native methods and private runtime setup

**Development status: the native per-read methods and four original-runtime setup
profiles are tested. A typed `fastp-tools` analysis command and catalog integration
are still pending. fastp remains one of the 260 missing catalog integrations. The
project total remains 60 new scoped integrations.** This document describes
current source and an isolated development JAR, not an updated public release.

The seven catalog papers use fastp for short-read preprocessing before separate
alignment, expression, chromatin or variant analyses. They report versions
0.22.0, 0.23.2, 0.25.0 and 1.0.1; one reports only v0.23 and two omit a version.
The pinned draft role review is `tools/literature/fastp-use-reviews.json`. It has
not replaced the aggregate catalog yet. In particular, P054 specifies adapter
detection, overlap correction, right-side cutting, quality/length filters and
deduplication. P046 says reads were trimmed to 100 bp without giving the complete
command. These statements do not supply all effective author settings.

## Native Java methods

`org.jlinalg.sequence.FastpReadFilter` implements the simpler source-stage methods:

- Global front/tail removal and ordered front/right/tail quality cutting.
- A separate final maximum-length stage.
- Quality, uppercase-N, length and adjacent-base-complexity predicates, including
  their first-failure precedence and complete character counts.
- Original zero-based retained intervals, qualities and null-versus-empty state.

For example, this processes an already supplied Phred+33 read at the indicated
stages. `sequence` and `quality` must have equal lengths.

```java
import org.jlinalg.sequence.FastpReadFilter;

var cutting = new FastpReadFilter.Trimming(
    0, 0,
    FastpReadFilter.Cut.disabled(),
    new FastpReadFilter.Cut(true, 4, 20),
    FastpReadFilter.Cut.disabled());
var trimmed = FastpReadFilter.trimAndCut(sequence, quality, cutting);
// In a full pipeline, adapter/correction/poly-tail processing belongs before
// the final max_len stage. This example provides no such stages.
var capped = FastpReadFilter.maximumLength(trimmed, 100);
var assessment = FastpReadFilter.assess(capped, FastpReadFilter.Filters.defaults());
boolean passes = assessment.reason() == FastpReadFilter.Reason.PASS;
```

This is a caller-selected example, not a recovered command from P046 or P054.
The API accepts literal printable ASCII and at most ten million characters per
read; it requires Phred+33 qualities. Invalid inputs or settings throw
`IllegalArgumentException`. It does not read FASTQ files or assign command exit
codes. File parsing, pairing, compressed input, runtime execution and complete
workflow ledgers require the pending typed workflow.

**Source boundaries matter.** Cutting loops use strict endpoints and skip the
final complete window. A read no longer than an enabled cutting window becomes
source-null. Right cutting suppresses tail cutting. The source's average-quality
comparison uses an integer mean; poor-quality fractions use a strict greater-than
comparison. Only uppercase `N` counts toward the N filter. A source-null or empty
read fails length even if length filtering is disabled. These behaviors are
retained rather than replaced with generic window or interval conventions.

The methods do not implement adapter detection/removal, overlap correction,
poly-G/X trimming, UMI processing, merging, index filtering or Bloom-filter
deduplication. The original runtime is needed for those stages. The original
MIT notice is in `src/main/resources/org/jlinalg/omics/fastp-MIT-NOTICE.txt`.

## Private original-runtime setup

The official Linux x86_64 binaries are pinned by bytes and SHA-256 in
`tools/literature/fastp-runtime-lock.json`. The supported profiles are
`linux-x86_64-fastp-0.22.0`, `linux-x86_64-fastp-0.23.2`,
`linux-x86_64-fastp-0.25.0` and `linux-x86_64-fastp-1.0.1`. Version-specific
downloads are described in the [original fastp documentation](https://github.com/OpenGene/fastp/blob/v1.0.1/README.md#get-fastp).
These are the reported editions under development, not a claim about the latest
upstream release or the unknown patch in P089.

On a permitted Linux x86_64 host with Python 3.11 or newer, from the project root:

```bash
python3 tools/literature/install_fastp.py \
  --profile linux-x86_64-fastp-0.23.2 \
  --home /YOUR/PRIVATE/NEW_FASTP_HOME \
  --report /YOUR/EVIDENCE/NEW_FASTP_SETUP \
  --cache /YOUR/PRIVATE/FASTP_CACHE \
  --allow-install --allow-download
```

Replace every `/YOUR/...` path with a permitted location. Home, report and cache
must be disjoint. On Windows, run **Linux Python inside WSL**, with Linux paths;
Windows Python returns an explicit unsupported-host skip. Nothing installs into
a global software directory or changes the system PATH. The binaries remain
private and are not bundled in the project JAR or evidence archive.

For offline setup, omit `--allow-download` and place the exact pinned binary in
`FASTP_CACHE/fastp.VERSION`, for example `FASTP_CACHE/fastp.0.23.2`. The cache is
verified before execution. A new home requires `--allow-install`. A completed,
unchanged managed home may be reused with a **new** setup report. Changed homes,
extra home files, hardlinked files and corrupt caches are refused and preserved.

**Require exit 0 and `COMPLETED.json`, then inspect `setup.json`, `manifest.json`,
the version/health command receipts and raw outputs.** Setup checks every byte of
a tiny 32-read synthetic output and the before/after quality counts. It proves
that the pinned binary ran that fixture on the current host; it does not admit
a user analysis, establish a complete host/ELF binding or prove source-to-binary
compilation. Hashes establish content integrity, not authenticity certification.

## Warnings and required recovery

**SKIP3 means the required profile, host or install/download permission is
unavailable. Skip the dependent analysis.** Supply the exact supported edition
and permitted Linux/Python environment, or use the exact offline cache. If the
environment forbids installation or execution, keep that analysis skipped.

**FAIL2 means setup integrity or execution failed.** Read `FAILURE.json` and the
retained logs. Preserve the changed cache/home, partial download and failed
outputs; correct the named cause and retry with new home/report paths. A failed
setup is not usable analysis evidence. Existing reports are never overwritten.

**Deduplication accuracy level 6 needs 24 GiB for the source Bloom buffer plus
other memory.** P054 requests this setting. Do not silently reduce it, substitute
exact set deduplication or treat a lower-memory run as the paper's configuration.
The exact high-memory P054 workflow has not been run in this development stage.
The source uses a probabilistic Bloom filter, and cutting can affect duplicate
identification. A future analysis must account for the full requested memory and
skip or fail with an explicit cause when it cannot run.

**Defaults depend on edition and input headers.** Poly-G behavior can depend on
sequencing-machine identifiers. Default quality filtering does not imply an
enabled sliding-window quality-cut operation. Barcode positions, unreported
versions and omitted author settings must not be inferred. Cell Ranger remains
excluded at the user's request. Whole-paper reproduction and biological accuracy
are not certified by these small synthetic controls.

## Validation evidence

`FASTP_NATIVE_DEVELOPMENT_VALIDATION.json` and its source/control archive retain
124 fresh original protocols: 31 explicit configurations on each of four
editions, with 256 reads per protocol. Native comparisons inspect every passed
sequence and quality, complete output identity/order and every failed-read
disposition across 31,744 evaluations. Adapter/poly-G/duplication processing is
explicitly disabled in this isolated filter/cutting matrix. Separate original
default-processing setup checks use 32 synthetic reads per edition.

The native suite has 134 tests, including capacity/invalid-input guards. The
same tests run against the actual isolated development JAR on Windows Java 21,
Windows Java 25 and Linux Java 21 with 128 MiB heaps. Strict Java 17 compilation,
Javadoc and source-site checks are separate gates. An initial test-reader error
used an uppercase N in the source failure tag; its failed XML, log and earlier
test source are retained. The correction changed the tag comparison only.
The native scientific methods did not need a corresponding change.

The pending work is the typed original-runtime analysis workflow, complete
FASTQ/input/output admission, full processing controls including paired-end
adapter/correction/deduplication stages, and catalog/source-role integration.
