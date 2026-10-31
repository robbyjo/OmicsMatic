# Native Java FLASH read merging

`flash-reads` reconstructs paired reads in Java using the FLASH 1.2.11 ungapped
overlap and quality-consensus rules. It needs no FLASH executable, Python, R,
download or installation. The original FLASH program is used separately for
validation. Its [official source](https://ccb.jhu.edu/software/FLASH/index.shtml)
and [method paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC3198573/) are supporting
method references, outside the frozen 250-paper cohort.

The validated canonical source-build executable is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`66be181d854806d9ae64d6f060e8defba8af5bdf594d3d352c9c76097a2a8d2e`.
The configured isolated gate passed 1,593 tests with zero failures/errors, all
72 required suites without skips and five optional skips. Eighteen tests cover
this new native API/CLI. Forty-five original FLASH workflows matched 5,185 read
pairs; six packaged invalid-input controls and two additional Unicode recipe
checks passed, for 53 packaged calls. All 26 prior packaged verifier groups,
25 original Trim Galore calls/22 comparisons, its Unicode and fresh-Python/QC
checks, and the complete 215-test policy gate passed on this executable.
The earlier canonical JAR and failed/unconfigured attempts remain retained.
See [native validation](flash-native-validation.json) and
[packaged comparisons](flash-native-packaged-validation.json). This is an
unreleased source build; the wider implementation goal remains active.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar flash-reads `
  --reads R1.fastq.gz --reads2 R2.fastq.gz --phred-offset 33 `
  --min-overlap 10 --max-overlap 200 --max-mismatch-density 0.25 `
  --out output/merged-reads
```

Use a new output directory. Declare Phred+33 or Phred+64; encoding is never
guessed. For interleaved pairs, replace `--reads2` with `--interleaved true`.
Plain and magic-detected gzip input are accepted; `--gzip false` selects plain
output. Records must be unwrapped four-line FASTQ. Headers use valid UTF-8;
sequence and quality fields use ASCII. Each mate is limited to ten million
bases. Search is quadratic in the worst case, with exact mismatch pruning and
first-perfect termination; this is intended for read merging, not general
long-sequence alignment. No external tool options are silently forwarded.

## Scientific choices that affect results

- Minimum overlap counts positions where both bases are non-N. Positions
  containing N do not contribute to the mismatch count or callable overlap.
- **Maximum overlap caps the scoring denominator. It is not a hard exclusion
  of physically longer overlaps.** Density is mismatches divided by the smaller
  of callable overlap and this cap. Binary32 arithmetic, mismatch-quality tie
  breaking, scan order and first-candidate ties follow the original program.
- The second mate is reverse-complemented, including reversed qualities.
  `--allow-outies true` also searches the opposite ordering; innies precede
  outies in original scan order.
- Matching bases retain the greater quality. Mismatches select the base with
  greater quality. Original equal-quality base ties select the second oriented
  base unless it is N. Default mismatch quality is at least 2 and otherwise the
  absolute quality difference. `--cap-mismatch-quals true` instead selects the
  original older minimum-quality cap, which can produce quality 0 or 1.
- Default settings are minimum 10, cap 65, mismatch density 0.25, innies only,
  and uncapped mismatch qualities. Explicit read/fragment settings can derive
  the cap by truncating `2*read-length-fragment-length+2.5*fragment-length-sd`;
  original defaults are 100/180/18. A supplied cap overrides these settings.
- Original cleanup uppercases lowercase A/C/G/T and changes other ASCII base
  characters to N. It strips trailing ASCII whitespace separately from headers,
  sequences and qualities. Internal sequence whitespace is an error. Repeated
  plus identifiers are ignored as in original FLASH; differing ones are counted.

## Prominent warnings and required actions

**CHECK HEADER MAPPINGS BEFORE JOINING RESULTS.** Original FLASH derives a
merged header from the entire first header, removing its last slash and suffix.
This can remove a comment containing a slash. A barcode immediately after
`/X#` is retained: `@id/1#BC comment` becomes `@id#BC comment`. No slash leaves
the complete first header unchanged. `read-status.tsv` retains JSON-escaped
original headers and the merged header for every pair, so tabs and Unicode
cannot corrupt the table. Use that mapping for downstream joins. Unmerged
headers retain their cleaned original text.

**CHECK NORMALIZATION AND PAIR ORDER.** `WARNINGS.md`, console diagnostics and
`completed-with-qc-warnings` status identify normalization, trailing whitespace,
header changes and ignored repeated identifiers. Review the per-pair counts and
original input before downstream analysis. Strict pairing is the default: the
first header token must match after removing `/1` or `/2`, including before a
`#` barcode. Original FLASH pairs solely by position. Select
`--pair-policy positional` only after independently checking the order; it
always produces an actionable warning and counts differing identifiers.

**ZERO MERGES REQUIRE REVIEW.** A valid run with no merges returns 0 and a
prominent warning. Inspect both unmerged streams, orientation, callable-overlap
threshold, scoring cap, mismatch density and declared encoding. No downstream
analysis runs automatically. An entirely empty input fails with exit 2.

**FAILURE IS NOT A RESULT.** Malformed records, missing/extra mates, identity
mismatches, invalid parameters/encoding, source changes or output errors return
2. The run retains `analysis-status.json`, `WARNINGS.md`, `session.yaml` and the
manifest. Partial FASTQ, ledgers, histograms and metrics are quarantined under
`partial/`; no successful `results.tsv` is published. Correct the named cause
and rerun to a new directory. An existing destination is never overwritten.

**WINDOWS UNICODE PATHS:** some tested JVM launchers replace non-ANSI command
arguments. Put Unicode input/output paths in a UTF-8 JSON recipe whose own path
is ASCII, then use `--options-file recipe.json`. The recipe must contain known
option names with string values, for example:

```json
{"reads":"D:/reads/样本_R1.fastq.gz","reads2":"D:/reads/样本_R2.fastq.gz","phred-offset":"33","max-overlap":"200","out":"D:/results/合并"}
```

Command-line values override recipe values. Unknown, duplicate, recursive or
non-string entries fail. The recipe is snapshotted and hashed with all input
files; source hashes are checked again after processing.

**INSTALLATION:** native merging has no external dependency. The separate
original-program verification script requires an explicitly supplied original
FLASH binary; absence of that validator does not prevent native merging. Other
toolbox adapters retain their own installation rules: if a required runtime
cannot be installed, skip its affected analysis and follow its named recovery
instructions. Never substitute FLASH for an unavailable aligner or trimmer.

## Output and evidence

`merged.fastq.gz`, `unmerged_R1.fastq.gz` and `unmerged_R2.fastq.gz` partition the
input pairs. Unmerged mates stay in original orientation. `results.tsv` provides
merge/orientation, base, cleanup, header and identity counts.
`length-histogram.tsv` separates innies and outies at each merged length.
`read-status.tsv` gives per-pair decisions, overlap start, physical/callable
overlap, mismatch density/quality score and cleanup counts. The manifest hashes
input and output files; `session.yaml` records all resolved choices.

The formal 5,171-case fixture freezes actual original output from 41 separately
launched reference workflows, not Java-generated expectations. It spans both
quality encodings, four overlap parameter sets, both orientations, both
quality modes, unknown bases, unequal lengths, quality ties, homopolymers and
printable-quality boundaries. The packaged comparison additionally checks
complete output headers, both unmerged streams, counters and histogram against
those captured originals. Four fresh original workflows cover sequence cleanup,
barcode/comment/Unicode headers, 1,501–4,096-base mates, interleaved gzip,
Phred+64, outies and explicit positional pairing. Six invalid-input controls
verify persistent errors and absence of successful results. These checks do not
establish arbitrary historical-version or complete-paper pipeline parity.

Source code is in `org.jlinalg.sequence.FlashReads`, `FlashFastqIO` and
`org.jlinalg.cli.FlashReadsCli`. Original algorithm attribution and GPLv3+ license
text are included under `META-INF/NOTICE-FLASH.txt` and `LICENSE-FLASH.txt`.

## Actual uses in the 250-paper cohort

| Paper | Reported use | Preserved boundary |
| --- | --- | --- |
| P075, [primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649568/) | FLASH 1.2.11 reconstructed reads after Trim Galore 0.3.1 in allele-specific MCC. | Overlap, encoding and orientation options are unstated. BLAT mini-chromosome mapping, alternate-genome alignment, custom splitting, duplicate/ligation processing and normalization are separate. |
| P091, [primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807871/) | Forward-only Trimmomatic HEADCROP 10, followed by FLASH minimum 10, cap 200 and density 0.25. | Version and encoding are unstated. Do not crop both mates. Galaxy, demultiplexing, BWA-MEM, Samtools, custom mutation selection and R/Excel normalization remain separate. |

The extractor requires a read-merging/reconstruction role in the same sentence
as FLASH. Forty-four other contexts remain retained for review, including
flash-frozen samples, instruments and P244's unrelated flash matrix-factorization
method. The latter is not implemented by this read merger. The catalog's
semantic completeness review and the wider implementation goal remain active.
