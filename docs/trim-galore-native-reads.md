# Native Trim Galore read operations

`TrimGaloreReads` supplies native Java fixed-end clipping, ordinary final read
filtering and first/last-base hard trimming. The `trim-galore-reads` command
exposes these operations without Perl, Python or a downloaded analysis runtime.
The implementation has separate original 0.6.7 and 0.6.10 conventions.

**Adapter and quality trimming must already have been performed before
`--mode finalize`.** Supply the output of the intended Cutadapt phase. Running
this operation on raw reads does not remove adapters or low-quality bases.
Running it again on already-finalized Trim Galore output can apply fixed cuts
twice. RRBS and explicit poly-A processing are outside this native subset.
The distinct [original workflow adapter](literature/omics-2023-2026/TRIM_GALORE_WORKFLOWS.md)
now performs ordinary adapter/quality processing with unmodified original
Perl 0.6.7/0.6.10 and Cutadapt 5.2. Its bounded scope and dependencies are
separate from this native helper.

The canonical source-build executable is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`2bfb8abfea10e53ce7b0e1035b0a7954feb9bed990d63578e817882a6d0541c0`. It passed 1,504 tests with zero failures/errors;
all 61 required suites ran without skips and five optional tests skipped.
The native helper's 13 API/CLI tests, 1,692 original read observations across
38 cases and 40 packaged calls passed on that same JAR. The complete extraction,
dependency and policy suite passed 176 tests without skips. See the
[native validation record](trim-galore-native-validation.json).

The earlier native-only candidate remains retained at
`build/literature-review/trim-galore-native-candidate/jlinalg-0.3.7.jar`, SHA-256
`d01947c2b4d2b12ffeecdffe41581dd0eedfffad0fc44cf73c148389c6398841`,
with its original 1,474-test evidence in validation history. Build the current
source with `executableJar` for a new source build. This work is unreleased.

## Finalize ordinary reads

```powershell
java -jar CURRENT_SOURCE_BUILD.jar trim-galore-reads `
  --mode finalize --release 0.6.10 --input-stage post-cutadapt `
  --reads after-cutadapt-R1.fastq.gz --reads2 after-cutadapt-R2.fastq.gz `
  --minimum-length 20 --clip-r2 15 --three-prime-clip-r1 15 `
  --out results/native-finalization
```

This example applies the stated fixed-end cuts after the upstream phase; it
does not infer that a particular paper used these options. Choose the exact
parameters for the declared analysis. `--minimum-length` defaults to 20;
`--maximum-length 0` disables the upper bound. `--maximum-n` accepts an integer
0–100, or a fraction strictly between zero and one only for release 0.6.10.
Only uppercase `N` is counted by the original programs. Base case and original
quality characters are preserved.

For each read, fixed clipping runs from the 5′ end and then from the 3′ end.
Each requested cut is skipped if the current length is no greater than the
cut. This can leave a short read unchanged. Warnings count these guarded cuts.
Single reads then undergo N, minimum-length and maximum-length filtering.
Paired files require synchronized identifiers and order; either mate can
reject the pair.

**Original 0.6.10 ordinary single-end fractional-N behavior:** after comparing
the N fraction, the original branch also compares the N count to that fraction.
It therefore rejects every read with any uppercase `N`. The native method
preserves this behavior and warns. Use an explicitly chosen integer cutoff
when that convention is unsuitable. The paired branch uses the fraction
without that extra count comparison. This statement does not cover RRBS.

## Orphan reads

Paired input can enable `--retain-unpaired true`. A pair rejected because a mate
is too short can emit either or both mates to separate orphan streams.
The original default orphan threshold is 35 for each mate, even when the pair
minimum exceeds 35. Explicit `--orphan-min1`/`--orphan-min2` values must be 15–100
and greater than the paired minimum.

**The original short-pair rescue branch runs before maximum-length filtering.**
A rescued mate can exceed the declared maximum; the command reports this
prominently. N rejection prevents rescue. A pair rejected only by the maximum
also receives no rescue. Original 0.6.7 rejects paired maximum-length filtering
entirely; choose its declared valid options rather than substituting 0.6.10.

Required action: review `WARNINGS.md`, `read-status.tsv` and each orphan stream
before downstream analysis. Apply a separate, explicitly recorded filter if
the original rescue behavior does not meet the downstream input contract.

## Hard trimming is a separate operation

```powershell
java -jar CURRENT_SOURCE_BUILD.jar trim-galore-reads `
  --mode hardtrim5 --bases 50 --reads input.fastq.gz `
  --out results/first-50-bases
```

`hardtrim5` keeps the **first** N bases; `hardtrim3` keeps the **last** N bases.
These modes accept N from 1 to 999 and preserve shorter or empty reads. They
apply no length, N, adapter or quality filtering. Finalization/filter options
are rejected in these modes. `--reads2` crops synchronized mates independently.

Required action: use a separate minimum-length stage if required downstream.
Keeping the first 50 bases and dropping reads shorter than 50 are different
operations; the command never substitutes one for the other.

## Java API

```java
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.TrimGaloreReads;

var input = new FastqIO.Read("@read1 original comment", "ACGTA", "!#$%&");
var filters = new TrimGaloreReads.Filters(
    TrimGaloreReads.Release.V0_6_10, 1, 0, null);
var result = TrimGaloreReads.single(input,
    new TrimGaloreReads.Clips(3, 3), filters);
// The first cut leaves TA/%&; the second cut is guarded because length <= 3.
if (result.retained()) {
    FastqIO.Read output = result.clipped();
}
```

`paired` returns clipped mates, the pair decision and separate rescue booleans.
Do not treat a clipped read as retained without inspecting those decisions.
The pure API retains rejected reads in its return objects to support auditing.
`hardtrim` selects `End.FIVE_PRIME` or `End.THREE_PRIME` explicitly.

## Results, failures and evidence

Every command writes source SHA-256 hashes, `session.yaml`, per-record decisions,
count summaries and plain or gzip FASTQ (`--gzip true`, the default). Plus lines
are canonicalized to `+`; headers, retained bases and quality characters are
preserved. Gzip compression bytes need not match the original program.

**Exit 2 means processing failed.** Read the console message and the output
`WARNINGS.md`; correct the named input, release or option error, then rerun to
a new output directory. Partial streams move to `partial/`, and no successful
`results.tsv` is published. Existing output directories are preserved. Empty
retained streams and the original edge cases produce prominent QC warnings;
no downstream analysis is started. Native operations require no external tool
installation.

The native reference fixtures contain **1,692 read observations from 38 cases**
run through the unmodified original Perl releases and actual Cutadapt 5.2.
The neutral upstream phase was independently checked record-for-record before
observing final filtering and hardtrim output. These cases validate the native
read operations, not adapter alignment, quality trimming, RRBS, poly-A or whole
paper reproduction. See `src/test/resources/trim-galore/` and
`tools/literature/reproduce_trim_galore_references.py` for hashes and reproduction.

The reference harness uses a small transport bridge for native Windows Cutadapt
and POSIX Perl. It changes stdout CRLF pairs to LF and retains both byte streams,
stderr, arguments, hashes and the child exit code. The original Trim Galore
source is unchanged. This bridge alone is not an installed or validated full
Trim Galore analysis adapter. Missing or changed transport dependencies return
exit 3 with an analysis-skipped recovery message.

Original sources: [Trim Galore 0.6.7](https://github.com/FelixKrueger/TrimGalore/tree/0.6.7)
and [Trim Galore 0.6.10](https://github.com/FelixKrueger/TrimGalore/tree/0.6.10).
The current upstream Rust rewrite is a separate implementation and has not been
silently substituted for these declared original profiles.
