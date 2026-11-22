# Native HOMER fixed-motif scanning

`homer-scan` reads supplied HOMER DNA probability matrices and FASTA, then
calculates fixed-threshold motif sites entirely in Java. The public Java API is
`org.jlinalg.sequence.HomerMotifs`. Read `homer-discovery-validation.json` for the current executable and
`homer-native-validation.json` for the retained historical native checkpoint. This method does not
discover motifs, test enrichment, annotate genomic regions, call peaks,
generate coverage or quantify RNA. Broader HOMER integration remains missing.

## Run and review

Use a development executable containing `homer-scan`. From the repository root:

```sh
java -Xmx1g -jar "$JAR" homer-scan \
  --motifs examples/native-homer-scan/motifs.motif \
  --fasta examples/native-homer-scan/sequences.fa \
  --strands both --offset midpoint --alphabet mask-n \
  --duplicate-ids error --out NEW_RESULT_DIRECTORY
```

**Always read `WARNINGS.md` and its Required action.** Exit 0 means the complete
supplied model/sequence scan passed with review warnings. Exit 2 means input,
identity, resource, callback or output failure: provisional files move under
`partial/` and are not completed results. Correct the cause and choose a new
output directory. Existing outputs and source inputs are preserved. This
native command requires only Java and never downloads or installs software.

The original enrichment/discovery workflows remain separate. If a required
upstream dependency is unavailable, unsupported or forbidden, skip that
dependent analysis with its cause and recovery; the native scanner must not be
silently substituted for discovery, enrichment, peak/RNA analysis or annotation.
Cell Ranger remains excluded.

## Matrix, alphabet and coordinate rules

The tab-separated motif header is `>consensus`, `name`, `threshold`, optionally
source log P and further metadata. A/C/G/T rows must contain finite probabilities
in [0,1], with row sums within the possible 0.002 rounding error of four
three-decimal fields. Values stay unchanged; counts/weights are not converted
to probabilities, and rows are not renormalized. Duplicate names retain distinct
motif ordinals. The consensus is a source label and does not replace the matrix.

Each base contributes `ln(max(p,0.001)/0.25)`. Scores use natural logarithms and
the header threshold is inclusive. The probability floor is part of the
inspected original scoring rule; `models.tsv` counts affected cells. Header
log-P metadata remains source metadata and is not a computed site P value.
No P, q, E, footprinting, motif enrichment or binding probability is produced.
Floating arithmetic is checked against original printed-score precision, not
claimed bitwise equivalent for every possible threshold boundary.

Choose forward, reverse or both strands explicitly. `strict-dna` accepts A/C/G/T
and N, with canonical lowercase promoted. `mask-n` explicitly maps other
printable ASCII residues to N, including U, IUPAC ambiguity and punctuation.
HOMER's U-to-N masking differs from the FIMO U-to-T convention. Any window
containing N is excluded on both orientations. Whitespace within sequence lines,
controls and non-ASCII sequence residues fail; no gap removal is inferred.
Plain/gzip magic and LF/CRLF input are supported. UTF-8 header identities are
preserved. Duplicate IDs require explicit `retain-ordinal`; equal names are
never merged. Use sequence ordinals in downstream joins.

`results.tsv` always includes zero-based half-open `left0/end_exclusive` sequence
intervals. `homer_position` uses the plus leftmost or minus rightmost base, plus
the selected integer offset. `midpoint` subtracts truncation(sequence_length/2),
including odd-length sequences. Minus `forward_word` remains forward-oriented,
matching the original output convention. No chromosome, genome build or genomic
coordinate is inferred from a header. The original reserved -12345678 midpoint
sentinel is rejected as a literal offset; choose `midpoint` explicitly.

## Outputs and resource limits

- `input-motifs`, `input-fasta` and optional `input-options-file` preserve source
  bytes, with pre/post hashes checked against originals and snapshots.
- `results.tsv` retains every threshold-passing site with motif/sequence ordinals,
  identity, interval, oriented anchor, strand, forward word and full native score.
- `models.tsv` retains labels, widths, fixed cutoffs, floor counts and source
  header metadata. `sequences.tsv` retains all input IDs, headers and lengths,
  including short, empty and zero-hit records.
- `scan-counts.tsv` records every motif/sequence pair, masked windows, scored
  sites, hits and worst-case operation counts. `session.json`,
  `analysis-status.json`, `manifest.yaml` and `WARNINGS.md` retain status and
  required actions. A zero-hit analysis receives an explicit warning.

Input bytes, decoded text, line length, sequence count/length, motifs/width/rows,
global score work and global hits have explicit positive flags; see `--help`.
The model reader bounds cumulative metadata/matrix allocation before splitting
fields. Immutable cached score models avoid repeated logarithm construction.
The command checks conservative sequence/model/identity estimates against both
the workspace allowance and half the declared Java maximum heap. Raise the heap
or change explicit capacities only after review; no automatic retuning occurs.
`workspace-bytes` also bounds copied input and audit streams. Final status and
manifest diagnostics add metadata outside that byte check. These estimates do
not guarantee total JVM resident memory. No successful truncated table is
published after a limit failure.

An ASCII-named UTF-8 string-options JSON file supports Unicode paths through
`--options-file`; direct flags override JSON. Recursive and unknown options fail.
No subprocess, external resource lookup or network request runs during scanning.

## Original-reference correction and remaining work

The earlier capacity finding was incorrect: `SequenceArray::optimizeArray()`
doubles the longest-sequence value before the original C++ site arrays are
allocated. At most two strand sites occur per candidate window, so nonempty
width>=1 models fit that doubled capacity. Native streaming does not require a
separate site array, but that does not imply an original capacity defect.

All 185 scanner protocols, including the 11 formerly mislabeled unsafe, now
actually pass each original 5.1,4.11 and4.11.1:555 comparisons and8,556 hits,
with zero exclusions. Read `homer-capacity-correction.json`. Historical exclusion
files and incorrect skip controls remain retained as invalidated evidence;
they are not permission to drop records. No original patch or compiler
installation was required.

Current `homer-tools` supports original known enrichment, find and de-novo
discovery. Read [the core guide](HOMER_CORE_WORKFLOWS.md) and
[the discovery guide](HOMER_DISCOVERY_WORKFLOWS.md). Older core-candidate-1
incorrectly refuses dense inputs; use the current candidate.
Original HTTP archives have local content pins, not independently authenticated
signatures. Private profiles are admitted explicitly; analysis never installs.

Read [the paper-role review](HOMER_SOURCE_REVIEW.md) before mapping a paper's
operation to this native scanner. Motif-model reuse through MotifDB does not
establish program execution; P215's E-value wording is not converted into a P/q
cutoff. Genome/Perl annotation, peak/coverage/tag processing, RNA counting and
full pipelines remain separate unfinished work.
