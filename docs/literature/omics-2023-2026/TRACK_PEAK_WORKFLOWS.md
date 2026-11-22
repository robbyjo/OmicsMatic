# Native threshold peaks from supplied genomic scores

`track-peaks` implements the threshold/gap/span and plateau-summit step in Java.
It supports the already normalized CUT&Tag bedGraph workflow in
[P163](https://doi.org/10.1038/s41588-024-01911-7). That paper reports MACS2 2.1.0
`bdgpeakcall`, minimum length 100 and raw score cutoff 2. It does not state the
gap parameter in that evidence; choose and record it explicitly rather than
claiming a full reproduction of the paper.

The native positive-cutoff float32 geometry and summit conventions have 129
actual MACS3 3.1.0 `bdgpeakcall` reference runs. This does not validate the older
paper version or alignment-based MACS `callpeak`. The latter still fails its
private Windows scientific fixture; see [MACS recovery](MACS_RUNTIME_STATUS.md).
No external executable or installation is needed to run the Java method.

## Explicit command and outputs

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar track-peaks --input normalized.bedGraph --sizes reference.chrom.sizes --genome-build GRCh38 --missing zero --precision float32 --cutoff 2 --min-length 100 --max-gap 30 --out threshold-peaks
```

The example's gap of 30 is an explicit chosen setting, not a reported P163
parameter. The source executable at that path passed the recorded isolated and
packaged checks; it remains an unreleased source artifact.

Supply four tab-delimited bedGraph fields: chromosome, zero-based start,
exclusive end, finite score. Unordered rows are accepted. Overlaps, unknown
contigs and out-of-reference intervals fail. Supply exact two-column reference
names/lengths, or `--sizes-format fai` for declared FAI names/lengths. FAI byte
geometry and the declared assembly label are not independently verified here.

`results.tsv` reports peak ID, chromosome, start/end, full span, absolute summit,
summit offset, maximum signal and qualifying base count. `peaks.bed` is
headerless BED4 with matching IDs. Both are ordered by chromosome label and start.
No P-values, q-values or fabricated narrowPeak significance fields are written.

## Threshold, gap and summit rules

- Values **equal to the cutoff qualify**. Qualified runs separated by a gap at
  most `--max-gap` join one peak. The full span, including subthreshold gaps,
  must be at least `--min-length`. `qualifying_bases` excludes those gaps.
- Adjacent identical effective scores merge into a single plateau before
  summit selection. For all maximum-height plateaus in a peak, compute each
  center rounded down to a base; choose the lower median of those centers.
  Plateaus have equal weight in that choice, regardless of their length.
- `float32` rounds both scores and cutoff before comparisons and plateau
  merging, as MACS track code does. A rounding warning reports the effective
  cutoff and number of changed segment values. Values outside finite float32
  range fail; no infinities or saturating substitutions are allowed.
- `float64` retains supplied doubles. Its near-cutoff decisions can differ
  from float32. Native coordinates use 64-bit arithmetic without a per-base
  genome array; the upstream reference fixtures use 32-bit bounds.

For example, signal 3 on `[10,30)` and `[40,70)` with cutoff 3, gap 10 and
minimum span 60 produces `[10,70)`, 50 qualifying bases and summit 20 (offset
10). Increasing minimum span to 61 removes that peak. Decreasing gap to 9
separates the two qualifying runs.

## Warnings and decisions users must make

**MISSING SIGNAL:** `--missing error` requires every declared reference base
to be represented. `--missing zero` explicitly fills internal gaps, trailing
sequence and absent contigs with zero and warns. The complete reference is the
analysis universe. Zero filling is a decision about track generation, not
evidence of biological absence. It avoids silently inheriting sparse-parser
assumptions about omitted positions.

Upstream `bdgpeakcall` expects continuous tracks within each chromosome. Its
raw sparse-file parser can interpret omitted positions differently. The 129
upstream references use complete continuous tracks; explicitly zero-fill or
otherwise reconcile gaps before comparing results. Native zero filling is not
a claim of identical behavior on an unmodified sparse upstream input file.

**ZERO OR NEGATIVE CUTOFF:** zero-filled background can itself qualify. This
always warns. Native summits use the actual maximum even after a zero plateau;
the upstream zero-as-uninitialized summit quirk is not reproduced. The positive
cutoff reference results do not claim zero/signed-cutoff summit parity.

**SCORE UNITS:** a raw cutoff of 2 is not automatically a p-value or q-value of
0.01. Determine how the supplied track was generated. This command performs no
library normalization, treatment/control background estimation, P/Q scoring,
MACS alignment-based calling, HMMRATAC, IDR, multiple-summit splitting or automatic
cutoff sweep. Those remain separate required stages where the paper uses them.

**NO PEAKS:** an empty successful BED is retained with a prominent warning.
Inspect the score units and explicit settings before interpretation; the empty
result does not establish biological absence.

Inputs are hashed before/after use. Results publish to a new output directory.
Failure returns 2 and retains the cause and required correction in `WARNINGS.md`,
with no successful `results.tsv` or `peaks.bed`. Successful sparse, rounding,
nonpositive-cutoff and no-hit warnings remain visible in console, status,
manifest and session files. The BSD MACS3 notice is retained in the source and
executable resources.
