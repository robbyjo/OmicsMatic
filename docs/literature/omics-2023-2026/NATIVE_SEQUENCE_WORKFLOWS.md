# Native sequence processing

`sequence-tools` provides sampling, seqtk-style `trimfq`, indexed interval
extraction and terminal telomere-motif scoring directly in Java. It requires no
seqtk installation or Python environment. Use the validated executable CLI JAR;
this source work is not a release. Algorithms are adapted from
[seqtk](https://github.com/lh3/seqtk), with its MIT notice retained inside the JAR
at `META-INF/LICENSE-seqtk.txt`.

## Read-depth and length simulations

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar sequence-tools `
  --method trimfq --reads R1.fastq.gz --reads2 R2.fastq.gz `
  --length 50 --out build/50bp-reads

java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar sequence-tools `
  --method sample --reads build/50bp-reads/reads_R1.fastq.gz `
  --reads2 build/50bp-reads/reads_R2.fastq.gz `
  --fraction 0.25 --seed 100 --out build/quarter-depth
```

P181 describes truncating paired 75-bp reads to 50 bp and generating 50%/25%
depth datasets with a shared fixed seed. It reports neither a seed value nor a
seqtk release; the example's seed is an explicit choice, not a reconstructed
paper setting. Full STAR/coverage/LaDDR/tensorQTL analysis remains separate.

Fraction sampling independently retains each original record/pair with the
requested probability; it does not guarantee an exact count. Each pair consumes
one draw from seqtk's MT19937-64 generator. Both mates receive the same decision;
every pair, including rejected ones, is checked for matching identity/count/order.
Complete headers, sequences and qualities are preserved. Terminal `/1` and `/2`
are normalized only for identity comparisons. The default seed is 11; explicit
nonnegative signed 64-bit seeds are supported, including values above 32 bits.
Use the same input universe/seed to obtain nested 25%/50% memberships.

For exact-count sampling, use `--count` instead of `--fraction`:

```powershell
java -Xmx1g -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar sequence-tools `
  --method sample --reads spatial-barcodes.fastq.gz `
  --count 25000000 --seed 100 --out build/spatial-downsample
```

P039 reports 25-million-read spatial-barcode downsampling with 1.3-r106 but gives
no seed or one/two-pass mode. Native count sampling uses two passes and matches
the tested `seqtk sample -2` membership/input order. Seqtk's one-pass mode emits
reservoir-slot order instead. Limits are 50 million indices and fewer than 2^53
input records, at eight bytes/index: 25 million requires about 200 MB plus JVM,
buffers and sorting stack. Above-input requests retain all available records
without replacement and warn. Native `--fraction 1` explicitly retains all;
seqtk's positional argument `1` instead invokes count mode. Empty output warns.

## Quality trimming is not read-level filtering

`--length 50` caps the retained sequence after optional `--trim-left` and
`--trim-right` cuts. Short reads are not padded. Any positive fixed cut/length
disables quality trimming. Bases and qualities use the identical interval;
no read or pair is dropped, even when a fixed cut creates an empty mate.

Without fixed cuts, defaults are `--error-rate 0.05` and
`--minimum-retained-length 30`. The method uses seqtk's Mott maximum-score
interval, low-Q scoring clamp and best-Phred-window fallback. Error rate is a
probability threshold, not a Phred cutoff. Reads shorter than the minimum remain
unchanged. **The fallback can retain poor-quality bases**, including an initial
minimum-length segment of an entirely poor-quality read. The CLI counts these
individual reads, prints a notice and writes required quality-review actions in
`WARNINGS.md`. Retained length is not reported as a quality-filter pass.

P032 reports low-quality trimming with seqtk v0.5.0 after Cutadapt but omits the
command/threshold. That reported release and exact behavior still need
reconciliation; do not infer native-default or full RNA/UMI-pipeline equivalence.

## Exact-reference extraction and terminal repeats

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar sequence-tools `
  --method subseq --reference haplotype.fa --regions loci.bed `
  --out build/locus-sequences

java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar sequence-tools `
  --method telo --reference assembly.fa --motif CCCTAA `
  --penalty 1 --maximum-drop 2000 --minimum-score 300 `
  --out build/terminal-repeats
```

`subseq` takes headerless tab-delimited BED3: name, zero-based start, exclusive
end. Blank lines and `#` comments are allowed; limits are 64 MiB/one million rows.
Unknown contigs, empty intervals and out-of-range coordinates fail instead of
being silently clipped/skipped. Overlapping/repeated requests retain request
order. This differs from seqtk's reference-contig grouping for interleaved BED
rows. FASTA output wraps at 60 bases; labels display one-based inclusive positions.
Reference case/IUPAC bases are preserved. No strand inference, BED6, reverse
complement, gap insertion or assembly editing is exposed.

The exact uncompressed FASTA is indexed in Java, including actual LF/CRLF widths.
A new `reference.fai` is written inside the output bundle; source/adjacent indexes
are preserved. Access uses 64-KiB chunks rather than loading chromosomes into
memory. P235 extracted loci after rustybam coordinate identification; coordinates,
orientation and subsequent phylogenetics remain explicit separate work.

`telo` matches rotations of a supplied 1–31-base A/C/G/T motif at both oriented
ends, using seqtk's asymmetric endpoint scoring and maximum-drop stopping.
Ambiguous bases interrupt matches. Default `CCCTAA` covers the forward left end
and reverse-complement right end; no species motif is inferred. Penalty/drop are
nonnegative and minimum score must be positive. `telomeres.bed` is zero-based
half-open; `results.tsv` adds sequence length, terminal end and maximum score.
**No-hit output does not establish biological absence or assembly completeness.**
Inspect motif, orientation, score/drop settings and the actual assembly.

P164 used `telo` in 1.4-r130-dirty and also used seqtk in manually supported
sequence incorporation/100-bp gap edits. Those placement/edit decisions remain
separate. Tagged-reference validation does not establish dirty-build equivalence.

## Failure recovery and independent references

FASTQ must be strict four-line IUPAC/Phred+33, plain/gzip, at most ten million
bases/read. Reference operations require uncompressed consistently wrapped FASTA;
compressed references need a separate `.gzi`-aware workflow. Input hashes must
remain stable. Failure returns **2**, preserves existing destinations, quarantines
partials and retains the cause/required action in `WARNINGS.md`. Warnings return
**0** with `completed-with-warnings` status and a console notice. These native
methods require no external executable and have no dependency-skip case.

`src/test/resources/seqtk/reference.json` freezes 48 sampling cases, 155 trimming
cases, four telomere settings and extraction from **620 actual upstream runs**.
Applicable outputs agree across unmodified 1.3-r106/1.4-r122/1.5-r133 builds;
telomere checks use 1.4/1.5. API and packaged fixtures add paired qualities,
fallback/empty warnings, chunk boundaries, CRLF indexes, strict bounds and
preservation of user-owned adjacent indexes.

To regenerate, obtain the tagged archives from
`https://github.com/lh3/seqtk/archive/refs/tags/v<TAG>.tar.gz` where permitted:

| Tag | Archive SHA-256 |
|---|---|
| 1.3 | `5a1687d65690f2f7fa3f998d47c3c5037e792f17ce119dab52fff3cfdca1e563` |
| 1.4 | `d124604ec24f29ed14ce127426ab90e0f3a2c0280c80d1a3ff8b1c09feede19c` |
| 1.5 | `384aa1e3cecf4f70403839d586cbb29d469b7c6f773a64bc5af48a6e4b8220a6` |

Extract unmodified trees as `build/literature-review/seqtk-reference/seqtk-<TAG>`.
Build each with GCC/zlib and
`make CFLAGS='-g -Wall -Wno-unused-function -O3 -std=gnu11'`. This run used the
private pinned MSYS2 toolchain in [the alignment guide](ALIGNMENT_WORKFLOWS.md).
Compiler warnings remain in build logs. Run
`python tools/literature/generate_seqtk_reference.py`, adjusting
`--reference-root`/`--toolchain` as needed. It verifies archive hashes and records
source/binary identities, invokes only supplied local builds and checks matching
outputs. If reference compilation is forbidden/unavailable, skip regeneration
and report why; native analysis itself still needs no such dependency. Use
`verify_native_sequences.py --jar PATH` for retained packaged checks.
