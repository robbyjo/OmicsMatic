# Native genomic-signal summaries and state enrichment

`genomic-signal` implements length-weighted bedGraph summaries directly in Java.
It also computes state-level log2 enrichment relative to the mean over an
explicit genome universe. This supports the signal-summary step described in
[P003](https://doi.org/10.1038/s41586-025-09890-3); it does not reproduce the
paper's entire SPIN, peak-calling or imputation pipeline.

## Explicit inputs and coordinate universe

Supply uncompressed, tab-delimited files:

- Signal: bedGraph chromosome, start, end, finite value. Segments are zero-based,
  half-open; touching endpoints do not overlap. Segments may be unordered, but
  overlapping segments fail instead of being silently summed or averaged.
- Regions: BED4 chromosome, start, end, region/state ID. Summary IDs are unique;
  state enrichment aggregates repeated IDs. State assignments must not overlap,
  including intervals assigned to the same state.
- Reference: headerless two-column chromosome/length `chrom.sizes`. With
  `--sizes-format fai`, a five-column FAI supplies the declared names/lengths;
  this does not verify the original FASTA byte geometry.

Exact contig names and bounds are required. `--genome-build` is the user's
declaration; it neither verifies the assembly nor performs liftover. Metadata
lines starting with `#`, `track ` or `browser ` are skipped. There is no external
executable, download or installation requirement.

## Per-region summaries

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar genomic-signal --method summarize --input signal.bedGraph --regions regions.bed --sizes reference.chrom.sizes --genome-build GRCh38 --missing zero --out region-signal
```

The result preserves input region order and reports the interval length,
represented bases, coverage fraction, integral, mean including explicit
zero-filled gaps and mean over represented bases. The latter is `NA` when no
bases are represented. These two means answer different questions.

For example, five bases at signal 2, ten unrepresented bases and five bases at
signal 4 have integral 30. With `--missing zero`, the mean over all 20 bases is
1.5; the mean over the ten represented bases is 3. Unrepresented positions remain
distinguishable from bedGraph segments whose measured value is explicitly zero.

**MISSING SIGNAL REQUIRES A CHOICE:** `--missing zero` treats absent positions as
zeros and emits a prominent warning when queries include them. `--missing error`
requires every queried base to be represented. Choose according to the source
track's generation and assay; absence from a sparse file does not automatically
establish a biological zero. An empty track can produce explicitly zero-filled
summaries with a warning, but cannot establish biological absence.

## Length-weighted state enrichment

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar genomic-signal --method state-enrichment --input signal.bedGraph --regions states.bed --sizes reference.chrom.sizes --genome-build GRCh38 --missing zero --pseudocount 0 --out state-enrichment
```

Regions with the same state ID are aggregated by base length. The baseline uses
**every base in the reference dictionary**, including absent contigs. With
`--missing error`, all declared genome bases must therefore have signal. With
`--missing zero`, a sparse genome background is explicitly zero-filled and
warned. This prevents a sparse track from silently changing the denominator.

The unadjusted score is `log2(mean_state / mean_genome)`. The implementation uses
a difference of logs to avoid overflowing a ratio. A required explicit
`--pseudocount 0` preserves that formula. A positive pseudocount, in the supplied
signal's units, is added to both means and produces a prominent warning because
it changes the formula. Nonnegative signal and a positive genome mean are
required. A zero-mean state with pseudocount zero produces `NA` with a warning;
an all-zero genome background fails even when a pseudocount was supplied.

**NORMALIZATION IS SEPARATE:** this command does not generate P-value tracks,
call peaks, convert bigWig, perform RPM/RPGC normalization, compute score per
million, infer library sizes or test differential accessibility. Supply the
appropriate signal and declared genome universe. A weighted signal ratio is
not a p-value or FDR estimate.

## Verification and diagnostics

The native range structure queries disjoint interval sums without subtracting
large genome-wide prefixes. Compensated sums also retain small contributions
during signed cancellation. Native tests compare 1,000 random windows against
independent per-base integration and check gaps, boundaries, exact reference
coverage, large-value precision, invalid overlaps and nonfinite inputs. CLI tests
check explicit missing/pseudocount choices, state weighting, zero/signed signals
and actionable failure recovery.

Inputs are hashed before and after use. Results publish to a new directory.
Failures return 2, retain `WARNINGS.md` with the cause and required correction,
and publish no successful `results.tsv`. Successful sparse/zero/pseudocount
findings remain visible in the console, status, manifest and warning file.
Review coverage, signal units, the declared assembly/reference and missing-data
choices before interpreting the result.
