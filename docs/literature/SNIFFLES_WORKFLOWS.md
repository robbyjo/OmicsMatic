# Sniffles original-source workflows

**Use the separately validated standalone artifact, not the canonical OmicsMatic
executable. Read the final execution proof before claiming availability.** Earlier
development checkpoints remain historical and grant no calling or catalog credit.
This integration preserves original Sniffles calling; Java captures inputs,
controls execution, retains complete VCF values and checks whole-batch admission.

## Warnings and required actions

| Outcome | What it means | Required action |
| --- | --- | --- |
| Exit 0 and root `PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW`, `analysis_admitted=true` | Every declared case completed and the final checks passed | Read `WARNINGS.md`, original logs, process receipts and complete VCFs before interpreting results. |
| SKIP3 | The exact permitted runtime, source edition, dependency, binding or requested capability is unavailable | Skip dependent calling. Where permitted, install and bind the exact private runtime into NEW paths. Keep the skip if installation/execution is forbidden. |
| FAIL2, cancellation, interruption or incomplete output | No case in the batch is admitted, including earlier complete cases | Preserve every original/provisional output and log. Correct the named data, integrity, capacity, process or filesystem error; retry NEW paths. |

An original process returning zero is insufficient. Original 2.0.2 can print a
fatal annotation error, return zero and leave a zero-byte VCF. That is failed
output. A complete, valid VCF containing zero records can instead be a legitimate
empty callset. `PROVISIONAL.json` is always subordinate to the root receipt.
Create `CANCEL_REQUESTED` inside the owned analysis directory to request cleanup.

Original 1.0.7 omitted an initial deletion in constructed controls and emitted
`INV` for a supplied `DUP`, with zero supporting reads and genotype `0/0`.
**Inspect the original types, support and genotypes; do not relabel or fabricate
calls.** Edition-specific `RE` and `SUPPORT` retain their original meanings; they
do not identify an unspecified paper depth field. Retain `NaN`-like original
strings and all INFO/FORMAT/sample values.

## Install, bind and run a small example

The tested runtime is Linux x86_64 with an exact Python 3.12.3 binary, standard
library and library-provider profile. Windows calling uses Linux Java inside a
permitted WSL environment. Another host needs a separately validated profile;
do not overwrite shared Python/libraries or refresh a scientific binding to
accept a different host. Analysis installs nothing. Cell Ranger is excluded.
The installer uses published repository evidence and four pinned official
wheels. It writes a private runtime, complete corresponding source and notices;
it does not install system packages. Read
[SNIFFLES_RUNTIME_INSTALLATION.md](SNIFFLES_RUNTIME_INSTALLATION.md) for license,
dependency, download and installation details.

Run these commands inside the permitted matching Linux environment, from the
checkout. Every named output and cache path must be NEW. The final standalone
artifact is distributed with its source-bound execution proof; do not use an
unvalidated replacement jar.

```sh
base="$PWD/build/literature-review/sniffles-runtime"
jar="$PWD/docs/literature/sniffles-workflows/sniffles-workflow.jar"
mkdir -p "$base"
/usr/bin/python3.12 -I -B -S -X "pycache_prefix=$base/install-example-cache" \
  tools/literature/install_sniffles_runtime_linux.py \
  --out "$base/install-example" --allow-download --allow-install
/usr/bin/java -cp "$jar" org.jlinalg.cli.SnifflesRuntimeCli \
  --root "$base/install-example/runtime" --out "$base/binding-example"
/usr/bin/python3.12 -I -B -S tools/literature/export_sniffles_public_example.py \
  --binding "$base/binding-example/COMPLETED.json" --artifact "$jar" \
  --out "$base/example-export"
/usr/bin/java -cp "$base/example-export/application/sniffles-workflow.jar" \
  org.jlinalg.cli.SnifflesWorkflowCli --request "$base/example-export/request.json"
/usr/bin/java -cp "$base/example-export/application/sniffles-workflow.jar" \
  org.jlinalg.cli.SnifflesVerifyCli --result "$base/example-export/analysis" \
  --out "$base/example-export/verification"
```

Stop dependent steps when installation or binding skips/fails. The exported
request uses a published constructed 40-read CRAM and matching FASTA; it contains
no supplied index. The workflow creates NEW owned indexes and a declared-reference
MD5 cache, avoiding the missing original encoding path. It retains a resolved
optional-index lookup diagnostic as a warning. This numeric, phased example
returns two original records with phase set `1001` and literal sample label
`--literal sample24`. It is not a biological benchmark or paper reproduction.

Java 17 is the compiled target; actual checks use Windows JVM21/JVM25 and Linux
JVM21. An actual JVM17 run is not claimed. Frozen VCF verification can run without
the original caller/runtime and checks stored integrity and structural
consistency. It does not rerun callers, check today's runtime, authenticate past
execution, independently refit SVs or certify biological accuracy.

## Declare user data and source settings

Use the exported JSON as a complete example of the strict schema. Each input
declares `id`, `kind`, absolute canonical `path` and `pin` containing the exact
byte count and SHA-256. A CRAM input also names its FASTA input in `reference`.
Do not change an old request/binding hash to accept different data; create a new
request and output. Inputs are copied unchanged, fully scanned through original
pysam and indexed in the owned directory. Original inputs remain intact.

| Per-analysis field | Exact source behavior |
| --- | --- |
| `edition`, `input`, `id` | Required exact edition, named BAM/CRAM and unique case label. Supported editions: `1.0.7`, `2.0.2`, `2.0.7`, `2.3.2`, `2.4`. `2.4` is not silently replaced by `2.4.0` or latest. |
| `threads`, `minsvlen`, `mapq` | Explicit bounded source scalars. Omission/null leaves the original defaults. |
| `minsupport` | Positive integer, or modern `"auto"`. Legacy automatic support is unavailable. |
| `legacy_minimum_segment_size` | Only legacy `--min_seq_size`: event-segment handling. It is not a read-length filter. |
| `minimum_alignment_length` | Only modern `--min-alignment-length`: alignment-length filtering. |
| `sample_id` | Literal modern sample label, including leading dashes/spaces. Legacy retains its captured BAM-path label with explicit input mapping. |
| `tandem_repeats` | Named BED input passed to the original modern caller. |
| `genotype_vcf` | Named VCF for original supplied-site genotyping. It does not imply a corrected or complete input-site result. |
| `phase` | Modern phasing from existing read HP/PS assignments. It does not perform read haplotagging. |
| `legacy_genotype` | Legacy original genotype flag. |

Wrong-edition settings return SKIP3 before upstream execution; they are never
translated to a supposedly equivalent option. A high legacy segment value and a
high modern alignment value produce different original outputs. Arbitrary command
arguments and unknown JSON keys are rejected. Legacy CRAM, phasing, modern-only
sample labels and other unsupported combinations skip explicitly.

Declare adequate input, alignment-record/read-length/reference, VCF and process
capacities in `limits`. The example deliberately uses small fixture limits;
increase them explicitly for real data. Hard per-process address-space, CPU and
file limits coexist with sampled workspace/file/process/thread/RSS accounting.
This is not an OS security sandbox or a hard aggregate memory reservation.
Complete source/runtime/data checks surround every upstream process and final
admission. Pure Java bookkeeping checks data, populations and capacities. Final
library census checks cancellation and native helpers for each probe, with full
integrity checks before and after the census. No digest cache crosses an original
process. Causal errors and suppressed cleanup errors remain visible in warnings.

SNF/pickle combining, caller consensus/merging, Iris polishing, read haplotagging,
bcftools consensus and paper-specific downstream filtering remain separate.
Whole-package and whole-paper reproduction are not claimed. The canonical
executable is unchanged and the 250-paper implementation goal remains active.
