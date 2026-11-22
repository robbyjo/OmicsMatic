# Original kallisto through Java

`kallisto-tools` supplies typed index and bulk quantification workflows using
the original official Windows x86_64 kallisto **0.48.0** program. Native Java
checks reference and FASTQ identities, index models, output counters, estimated
count totals, effective-length TPM arithmetic and every requested bootstrap.
It does not replace the quantifier with an approximate Java read counter.

**AVAILABLE IN THE VALIDATED LOCAL EXECUTABLE.** The full isolated gate passed
1,461 tests with zero failures/errors and five optional skips; all 55 required
suites ran without skips. Twelve API/CLI tests, 27 actual kallisto workflow
calls/eight checks and all 23 prior executable verifier groups passed.
The unreleased local artifact is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`5680cbc4e457007f8903d1d4db3e74ecc890a150b350870f0b82d5650bdc0fec`.
See [adapter evidence](kallisto-adapter-validation.json) and
[actual executable checks](kallisto-packaged-validation.json).

0.48.0 is deliberate: the reviewed PLATE-seq work uses sequence-specific bias
correction. The upstream [manual](https://pachterlab.github.io/kallisto/manual)
identifies `--bias` as a legacy option and recommends 0.48.0 for those options.
This does not establish numerical parity with papers using 0.43.0, 0.44.0 or
0.46. The separate 0.52.0 Cygwin source build failed and remains retained.

## Runtime setup and failures

Where downloads/installations are permitted:

```shell
python tools/literature/install_kallisto.py --allow-download
```

The installer verifies the official archive and all 12 members, probes the
actual program, and creates `build/omics-kallisto-0.48.0`. No system installation
or global PATH change occurs. Existing unmanaged or changed homes are retained
and rejected; select a new `--environment` after correcting the cause. Analysis
commands never download or install software.

Supply `--home build/omics-kallisto-0.48.0` or set
`JLINALG_KALLISTO_HOME`. The supported profile is Windows x86_64. A different
platform or arbitrary source-built executable is not accepted as this profile.
Official source/release: [kallisto 0.48.0](https://github.com/pachterlab/kallisto/releases/tag/v0.48.0).
Archive SHA-256:
`2590d87e507c9b48a5b76e17c0c788ba9054a2fa3c4c3ed96ce06e8cfc2370b0`.
Program SHA-256:
`5c8bcc2e430a59734f12fe4c153ead32000573dc043db06a3f007e4dc63b6358`.

**Dependency unavailable: skip the analysis.** A missing, changed or unsupported
runtime returns exit code **3**, publishes a skipped status and prominent
`WARNINGS.md`, and produces no successful result. If installation is forbidden,
supply this exact permitted profile or keep the dependent analysis skipped.
A version probe alone is not scientific validation.

**HDF5 IS UNAVAILABLE IN THIS WINDOWS BINARY.** In an actual test the original
program returned zero and reported three bootstraps, but emitted neither HDF5
nor draw files without `--plaintext`. The adapter always requests plaintext,
then requires every draw file and independently audits its values. It does not
silently discard uncertainty. HDF5/Sleuth workflows need a separate permitted,
HDF5-enabled and scientifically validated profile/adapter. Do not pass the
failed 0.52.0 source-build directory as a working runtime.

## Build an exact reference index

```shell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar kallisto-tools \
  --method index --home build/omics-kallisto-0.48.0 \
  --reference transcripts.fa.gz --reference-id GRCh38-GENCODE-release-used \
  --reference-kind transcriptome --reference-processing strict \
  --kmer-size 31 --out transcript-index --no-config
```

On PowerShell, put commands on one line or use PowerShell continuation syntax.
The reference is DNA/RNA IUPAC FASTA, plain or gzip, with optional wrapped
sequences. Target identity is the first header token; descriptions stay in the
unchanged source. Empty/duplicate IDs, empty sequences and invalid bases fail.
Targets shorter than k remain explicitly listed. The k-mer size must be odd
and in 3–31.

Choose `--reference-kind transcriptome`, `contigs` or
`personalized-transcripts` explicitly. This records the intended reference;
it does not construct personalized sequences, infer a genome build, or turn
DNA-contig abundance into RNA expression. Verify species and annotation release.

`--reference-processing strict` rejects transformations. Explicit `upstream`
allows original 0.48.0 processing: U becomes T, ambiguous bases are replaced
with pseudorandom nucleotides using the original seed 42, and terminal A runs
of at least ten bases are clipped for indexing. `targets.tsv` records raw
lengths, U/ambiguity counts and known terminal A runs. Ambiguity replacement
can extend the clipped run. **Abundance output retains raw reference lengths**;
do not confuse them with clipped indexed sequence or effective lengths.

The index bundle contains `index.idx`, `targets.tsv`, `model.json`,
`results.tsv`, original index/inspect logs, status, source hashes and warnings.
The model seals exact index/target bytes, k, engine/index versions and declared
reference identity. Quantification accepts this model, not an unverified
bare index. Changed members, mismatched versions or invalid metadata fail.

## Quantify one biological sample

Paired-end lane manifest, with literal tab separators:

```text
lane	read1	read2
L001	reads/L001_R1.fastq.gz	reads/L001_R2.fastq.gz
L002	reads/L002_R1.fastq.gz	reads/L002_R2.fastq.gz
```

Single-end manifests have only `lane` and `read1`. These are unquoted TSVs;
relative paths resolve beside the manifest. Lane IDs must be unique. Multiple
rows are technical chunks of **one biological sample**, not biological
replicates. Repeated physical FASTQ files are counted repeatedly, with a warning.

```shell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar kallisto-tools \
  --method quant --home build/omics-kallisto-0.48.0 \
  --index-model transcript-index/model.json --lanes paired-lanes.tsv \
  --sample-id sample001 --library paired --strand unstranded \
  --fragment-length-mode estimate --bias false --bootstraps 30 --seed 42 \
  --threads 4 --out sample001-quant --no-config
```

Every FASTQ record must be complete, unwrapped and compatible with the native
Phred+33 reader. Paired names/counts/order must match. No trimming, mate repair,
deduplication, UMI removal or sample pooling is inferred. Sources and the entire
runtime profile are hashed before/after use. Repeated input snapshots retain
their first digest and fail if bytes change between uses.

The required scientific choices are:

| Choice | Meaning |
| --- | --- |
| `--library single\|paired` | Single reads or paired fragments; paired processed counters count pairs |
| `--strand unstranded\|fr\|rf` | Original unstranded, first read forward or first read reverse behavior |
| `--fragment-length-mode estimate\|supplied` | Estimate from paired data, or supply positive `--fragment-mean` and `--fragment-sd` |
| `--bias true\|false` | Explicit legacy sequence-specific bias correction |
| `--bootstraps N` | Explicit 0–1,000 draws; positive N requires `--seed` |
| `--single-overhang true\|false` | Single-end only; original behavior for fragments extending beyond a target |

Single-end data require supplied mean and SD. Paired supplied values are
accepted, but original kallisto warns that empirical estimation is preferred;
the warning is promoted. Wrong strand/reference or zero pseudoalignment fails
with original logs and partial outputs. No zero library is fabricated or
silently dropped.

## Counts, uncertainty and metadata

**ESTIMATED COUNTS ARE FRACTIONAL.** The actual executable fixture allocates
41 fragments shared by indistinguishable isoforms as **20.5 + 20.5**. This is
an EM allocation, not unique read evidence for either isoform. The known
fixture has 161 pseudoaligned fragments, of which 120 are uniquely
pseudoaligned. TPM uses estimated counts divided by effective length, scaled
to a million. Java audits this arithmetic with the original text output's
six-significant-digit rounding budget. It does not fit another abundance model.

| Output | Meaning |
| --- | --- |
| `results.tsv` | Exact IDs plus parsed length, effective length, fractional estimated counts, TPM and sample ID |
| `upstream/abundance.tsv` | Original five-column point estimates, unchanged |
| `upstream/bs_abundance_N.tsv` | Every requested original plaintext draw, unchanged |
| `bootstrap-inventory.tsv` | Draw IDs, paths, count/TPM totals and hashes |
| `lanes.tsv` | Exact lane/file associations and read/pair counts |
| `run-info-audited.json` | Strict metadata copy with documented call-field compatibility handling |
| `model.json`, `session.yaml`, `manifest.yaml` | Exact input/index/reference/runtime choices, member hashes and status |
| `WARNINGS.md`, original logs | Required scientific actions and retained upstream warnings |

**THE ORIGINAL WINDOWS METADATA MAY BE INVALID JSON.** Original 0.48.0 does
not escape Windows backslashes in its `call` field, and Java normalizes the
executable path to that form. `upstream/run_info.json` is retained byte for
byte. The adapter verifies that the literal call exactly matches launched
arguments, re-escapes only that field in a derived copy, then strictly parses
all remaining JSON. Unknown corruption or a different call fails. Use
`run-info-audited.json` for JSON consumers. Numeric data are never repaired.

Bootstraps are quantification uncertainty, not biological replicates. Every
draw must match the exact target order/lengths, finite domains, mapped-count
total and TPM arithmetic. No unrequested uncertainty is inferred. `--threads`
can change upstream execution order; bitwise cross-host/thread reproducibility
is not claimed.

For explicit gene import, put `upstream/abundance.tsv` paths into a sample/path
manifest and use native `transcript-import --format kallisto` with a matching
transcript-to-gene map. Read [the import guide](TXIMPORT_WORKFLOWS.md) before
choosing count scaling and length correction. Its required
`--inferential-replicates drop` **discards** uncertainty for that import; it
does not transfer the bootstrap draws to gene-level uncertainty-aware analysis.
Do not double-correct reconstructed counts with a length offset.

## Failures, limits and remaining work

Exit code **2** means analysis/input failure. No successful `results.tsv` is
published; `partial/` retains diagnostic artifacts. Read `WARNINGS.md` and
the named original log, correct the reference/library/strand/fragment choice,
mate mismatch, changed model or resource bound, and rerun into a new directory.
An existing destination is not overwritten.

Defaults: stage timeout 3,600 seconds; one million targets; index input one
billion raw bases; 100 million reads/pairs; ten million target-by-(point plus
bootstrap) cells; 8 GiB per input/output file. Explicit hard maxima are 86,400
seconds, one million targets, 100 billion reference bases, two billion
reads/pairs, 100 million estimate cells and 1 TiB per file. These are work/file
bounds, not guarantees of sufficient RAM or upstream scalability. A target
length must fit the original signed 32-bit representation. Choose suitable
memory, cohort and bootstrap count.

The eight source-backed executions include contig/MAG DNA and transcript RNA
quantification (P037), SMART-seq isoforms (P061), kallisto-bustools count (P085),
cross-species estimates (P110), personalized transcript abundance (P132/P149),
bias-corrected single-end PLATE-seq (P134) and count/TPM downstream RNA analysis
(P215). **P085 BUS/UMI counting is outside this bulk adapter.** Personalized
reference construction, RNA aggregation/PSI, annotation retrieval, DE,
association, source-specific filters and historical versions remain separate.
P108's compatible-algorithm example was removed as kallisto execution evidence;
its separate actual Salmon evidence remains.

Standalone BUS/UMI/kb-count, HDF5/Sleuth, fusion/pseudoBAM/genomeBAM, custom
quant-TCC, personalized-reference construction and full-paper/historical parity
remain open. The failed 0.52.0 source build and original metadata/HDF5 failures
are retained, not counted as successful integrations.

Original method: [Bray et al., 2016](https://doi.org/10.1038/nbt.3519).
Original software is BSD 2-Clause licensed. The JAR includes its notice/license,
not the upstream executable. Regenerate references with
`python tools/literature/generate_kallisto_references.py --home build/omics-kallisto-0.48.0`
and run the actual-JAR verifier with
`python tools/literature/verify_kallisto_workflows.py --jar PATH_TO_CLI_JAR --report NEW_REPORT.json`.
