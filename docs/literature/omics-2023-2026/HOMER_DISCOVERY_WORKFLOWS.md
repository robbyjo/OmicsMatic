# Original HOMER de-novo motif discovery

The development `homer-tools denovo` command runs original HOMER 5.1, 4.11 or
4.11.1 on supplied target/background FASTA. Native Java audits check input
identities, eligibility, weighted oligo counts, final motif statistics and the
uncertainty of printed models. `homer-discovery-validation.json` identifies the
historical discovery candidate and verification scope. The current development
candidate also includes supplied-GTF annotation; use `homer-annotation-validation.json`
and [the annotation guide](HOMER_ANNOTATION_WORKFLOWS.md). Genome/Perl annotation, peak, coverage
and RNA workflows remain unfinished; this does not reconstruct whole pipelines.

**Correction to the earlier capacity claim:** HOMER's
`SequenceArray::optimizeArray()` doubles the longest-sequence value before site
arrays are allocated. The earlier dense-scan skips were incorrect and removed.
All 185 scanner protocols now pass against each of three unchanged originals:
555 comparisons and 8,556 hits, with zero exclusions. Historical excluded cases
and incorrect skip controls remain retained as invalidated evidence. No upstream
patch or compiler installation was needed. Use the current candidate; the older
`homer-core-candidate-1` executable still contains the incorrect dense refusal.

## Setup and required recovery

**Missing, forbidden, unsupported or changed dependencies SKIP the affected
analysis, exit 3.** Read the cause and Required action in `WARNINGS.md`. Use Linux
x86_64 Java, inside WSL on Windows, and an explicitly chosen admitted original
profile. See [the core setup guide](HOMER_CORE_WORKFLOWS.md). Installation is
separate through `tools/literature/install_homer.py`; use `--allow-install` and,
only where permitted, `--allow-download`. Verified offline archives also work.
Analysis never installs, switches versions, generates a background or silently
replaces the original engine.

Official HTTP archive hashes identify content and are not independently
authenticated signatures. The original GPL notice and source provenance remain
retained. Do not edit markers to bypass host/library changes; re-admit a new
private profile where permitted. Cell Ranger, ARC and ATAC remain excluded.

**Other failures exit 2 and quarantine provisional files under `partial/`.**
Inspect `WARNINGS.md`, `analysis-status.json`, `session.json` and `original.stderr`.
Correct the named input, population, parameter, resource or audit cause and use
a new output directory. Existing results and source bytes stay intact. Partial
tables are not completed results.

## Choose the actual discovery operation

See [four runnable examples](../../../examples/homer-discovery-workflow/README.md).
Supply target/background contrast, strands, alphabet, duplicate-ID and overlap
policies explicitly. Choose the statistic, motif length, mismatches, number of
motifs, normalization and discovery mode:

| Mode | Original operation |
| --- | --- |
| `full-mask` | Exhaustive sequence masking as motifs are found, with threshold optimization during seed refinement and final fixed-threshold rescoring. |
| `quick-mask-fixed` | Oligo-only masking and fixed-threshold final rescoring (`-quickMask -nozoops`). |
| `quick-mask-optimize` | Oligo-only masking and final threshold optimization on complete sequences (`-quickMask -zoops`). |

`--normalization none` leaves oligo normalization off; `oligo1` through `oligo15`
select original lower-order normalization, subject to motif-length, integer-index
and resource bounds. These are different algorithms. Known-motif enrichment
still accepts only its documented `normalization none` subset; discovery's
normalization is not silently transferred to another operation.

The resolved original argv is recorded. Defaults include ten trials, 160
normalization iterations, original length-dependent expected-site and redundancy
buffer rules, background fraction 0.5, automatic seed log P and an 8 MB discovery
statistic cache. Trials must be divisible by threads so the original cannot
silently change their number. Numeric seed cutoffs in the original automatic
band near -10 fail; choose `auto` or a value outside that band. The source's
final known-motif scorer has its own cache default, which does not inherit the
discovery cache flag.

No supplied motif file is needed or accepted. Fixed-model `--motifs`,
`--offset` and `--maximum-hits` flags fail explicitly in discovery. Unknown,
inapplicable, recursive or non-string options fail. An ASCII-named UTF-8
string-options JSON via `--options-file` supports Unicode paths; direct flags
override it.

## Seed populations differ from final motif populations

The seed phase requires canonical motif-length windows. Short or N-only records
can supply no seed windows while remaining in the complete final population.
Every record stays in `sequences.tsv` and `discovery-input-eligibility.tsv`;
none is silently filtered from the final contrast. Empty FASTA records fail
before execution because the original reader omits them.

The seed phase uses the integer-floor mean input sequence length. Records with
more canonical windows than that mean receive reduced per-window weights.
Background oligo coverage is adjusted before approximate zero-or-one-occurrence
scoring. Native Java independently counts canonical words, reverse-complement
grouping, role weights and eligibility; inspect `native-oligo-counts.tsv` and
`original-oligo-enrichment.tsv`.

**A positive `1.000` in the seed log-P column is an unscored sentinel,** usually
for a background-only or sufficiently low-target-count word. Do not exponentiate
it. Mismatch seed scores and weighted occurrences differ from final statistics.

Final motif counts and log P use fresh complete, unweighted populations. Native
audits recompute the selected one-sided statistic from reported integer presence
counts and complete populations. Original coarse `1e...` strings truncate log P
divided by 2.3026; they are not exact P values. No multiple-testing family, q
value, E value or biological binding probability is inferred.

With oligo normalization, native target counts stay checked; normalized
background weights remain original-engine results. The normalization file has
leading empty fields and percentage values despite its `Counts` labels. It is
an emitted iteration snapshot; the final normalization print is not written to
that file. Do not treat it as final per-oligo weights.

## Printed models do not reconstruct internal scoring exactly

**Retain original counts and statistics beside native checks.** After final
scoring, printing invokes `getConsensus`/`expxform`/`justNormalize` twice. Matrix
rows are renormalized and rounded again. Three-decimal matrices and six-decimal
thresholds cannot always recover the model used for the reported internal score.

For the inspected versions, native audits use conservative absolute cell
envelopes of ±0.0081 and threshold envelopes of ±0.000000501. Previously rounded
rows sum within 0.002 of one; scoring floors small cells to 0.001. Bounding the
two normalization/floor/rounding passes gives cell drift below
0.005525 + 0.002511, enclosed by 0.0081. Floating arithmetic is also allowed.
These are conservative enclosures, not reconstruction of a feasible joint
internal matrix or the optimizer.

`results.tsv` retains original counts, percentages, log P and coarse P; separate
columns hold native log P from source counts, definite/possible presence and the
printed-model point rescan. Every model/sequence pair appears in
`discovery-sequence-counts.tsv`.

`exact-presence-determined` means lower and upper presence bounds agree.
`compatible-precision-uncertainty` means original counts fall inside unresolved
bounds. Successful uncertain audits do not establish exact native reconstruction.
Never overwrite original statistics with a point rescan. Source position,
strand-bias and multiplicity fields remain original internal diagnostics, not
independently reconstructed genomic results.

`learned-motifs.motif` is byte-identical to original stdout. Reloading that
printed file through `find`, `known` or native `homer-scan` is a new fixed-model
analysis; it must not inherit discovery's source counts or log P.

## Resource and output review

Unique-word counts, mismatch neighborhoods, motif rows, score work and memory
estimates are checked before original execution. Original workspace, resident
memory and time are monitored, and owned processes joined after failure.
`/proc` access is required for resident-memory monitoring. Periodic monitoring
is not an OS-enforced disk or RSS quota.

See `--help` for positive capacity flags, including
`maximum-upstream-memory-bytes`, `maximum-neighborhoods`,
`maximum-unique-oligos`, workspace, input/decoded sizes and native score work.
Review `-Xmx` and capacities before increasing them. Final status/manifest
diagnostics add metadata outside the input/audit byte budget. No automatic
retuning or successful truncated tables are published.

Review source snapshots, aliases, complete eligibility/oligo/count ledgers, raw
original models/normalization outputs, warnings and runtime hashes before using
results. Synthetic validation does not establish empirical calibration or
whole-paper, historical-environment or genomic-pipeline parity.
