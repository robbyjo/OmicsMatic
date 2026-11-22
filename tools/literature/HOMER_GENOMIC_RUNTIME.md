# HOMER genomic runtime development

The development `homer-tools genome` command runs the original genomic known
enrichment, de novo discovery and motif-comparison workflow with a supplied
FASTA and supplied background regions. This guide covers its runtime setup and
primitive verification. See the [workflow guide](../../docs/literature/omics-2023-2026/HOMER_GENOMIC_WORKFLOWS.md)
for the complete intake/statistical scope, runnable recipes and fresh original
workflow verification. The broader HOMER literature entry remains unfinished.

Select `--analysis known` or `--analysis denovo` for an explicit single phase;
default `both` retains the complete workflow. The five public recipes provide
phase-appropriate option files. Unused model/search options fail validation.
Unrequested phases have `not-requested` status, null counts and no result
tables; they are not negative findings. Known-only keeps normalization without
discovery seed requirements. De novo-only keeps matching/SVG and does not apply
the legacy known-enrichment strand restriction.

Run the genomic command after setup, with explicit scientific choices:

```bash
LC_ALL=C.UTF-8 LANG=C.UTF-8 java -Xmx1536m -jar /PATH/TO/DEVELOPMENT.jar \
  homer-tools genome --home /PRIVATE/NEW_HOMER_51 \
  --profile linux-x86_64-genome-5.1 --out /PRIVATE/NEW_ANALYSIS \
  --genome /DATA/GENOME.fa.gz --reference-build DECLARED_BUILD \
  --peaks /DATA/TARGETS.bed --peak-format bed6 \
  --background /DATA/BACKGROUND.bed --background-format bed6 \
  --background-mode supplied --size given \
  --unknown-strand error --duplicate-ids error --alphabet strict-dna \
  --mask-lowercase no --background-overlap remove-original \
  --weighting gc --strands both --statistic binomial \
  --known-motifs profile --comparison-motifs profile
```

Plain or gzip FASTA is streamed to an indexed, case-preserving LF copy. The raw
bytes and full headers remain retained. BED6 uses zero-based half-open intervals;
HOMER6/7 uses one-based inclusive intervals. Exact contigs, region IDs and motif
names map to deterministic ASCII aliases with complete mapping ledgers. Use a
UTF-8 host locale for Unicode paths. Runtime installation homes remain ASCII.
String-valued JSON via `--options-file` supports complex paths; direct flags
override it. Relative paths are relative to the process working directory.

`--size N` follows the original Perl resize and produces **N+1 inclusive bases**.
For `--size START,END`, the width is END-START and the displacement is the floor
of their midpoint, relative to strand. Background removal has a separate source
rule: numeric sizes compare inclusive windows around integer peak midpoints,
with half-width floor(N/2), before the asymmetric displacement. `given` compares
the original inclusive intervals. `population.tsv` retains every exclusion.

The 4.11 and 4.11.1 wrappers do not pass forward-only selection to known
enrichment. With `--strands forward`, default `--known-strand-policy require-match`
therefore skips those legacy configurations. Explicit `original-wrapper` retains
known-BOTH versus normalization/discovery-FORWARD behavior and prominently records
the distinction. Version 5.1 forwards the selection to known enrichment.

Profile motif files retain their original values, including nonunit and count
rows. Custom files default to probability validation; choose
`--matrix-values original-weights` explicitly to retain other original weights.
The native fixed-model audit uses log(max(value,0.001)/0.25), matching original
scoring without pre-score row normalization. Source metadata and thresholds are
retained; raw weights are not presented as calibrated probabilities. The strict
public `HomerMotifs` probability API remains unchanged.

Read **`WARNINGS.md`** before using results. `regions.tsv`, `reference.tsv`,
`population-plan.tsv`, `population.tsv`, model mapping tables and
`discovery-input.tsv` record intake and tested populations. `results.tsv` retains
known enrichment alongside native weighted-presence statistics. Learned models
have separate `discovery-results.tsv` and `discovery-presence.tsv` uncertainty
ledgers. Complete original reports and diagnostics remain in `original-out/` and
`original.stdout/stderr`. Failed/skipped analyses quarantine partial artifacts.

Original weighted totals and motif-presence counts are floored to integer test
inputs. Known-table log P goes through a six-decimal intermediate print and a
three-decimal scientific print; the native full log P is separate. The original
`q-value (Benjamini)` exponentiates the six-decimal intermediate log P and applies
a forward cumulative maximum of sorted m*p/rank, grouping ties at their last
rank and clipping at one. **This is not standard BH's reverse cumulative
minimum.** Original q values and a native reconstruction of the actual source
algorithm are retained with that distinction.

The normalization replay uses the original engine with independently prepared
native initial weights and exact retained sequence order. Learned-model checks
use conservative print-precision presence bounds. These checks do not
reconstruct the optimizer or certify a weighted sampling model. Reruns should
retain exact staging because original iteration order can depend on identities.
Generated backgrounds now have [separate admissions, recipes and native audits](../../examples/homer-generated-workflow/README.md). Named genome packages, repeat/TE, peak/coverage and RNA remain separate work. The command requires the selected background algorithm explicitly.

Use Linux x86_64 Python and a Java 17+ JDK. On Windows, run them inside WSL.
Setup needs a C++11 compiler, GNU make, headers, pthread, math, and static
C++/GCC libraries. It does not install OS packages. Installations and downloads
require separate explicit flags.

```bash
python3 tools/literature/install_homer_genome.py \
  --profile linux-x86_64-genome-5.1 \
  --home /PRIVATE/NEW_HOMER_51 \
  --cache /PRIVATE/HOMER_ARCHIVES \
  --report /PRIVATE/NEW_SETUP_REPORT \
  --compiler /usr/bin/g++ --make /usr/bin/make \
  --archive-dir /VERIFIED_OFFLINE_ARCHIVES \
  --allow-install
```

Choose `linux-x86_64-genome-4.11` or `linux-x86_64-genome-4.11.1` for those
releases. Use a different private home and report for each. Add `--allow-download`
only where downloading is permitted. Without it, setup needs a complete pinned
archive in the cache or an explicit offline archive directory. Existing homes
are verified before reuse. Existing reports, corrupt archives and failed homes
are preserved; use new paths after correcting a failure.

**Exit 3 means dependency unavailable: skip the dependent analysis.** Read the
named cause and `Required action` in `WARNINGS.md` and `installation.json`.
Provide the missing build/runtime dependency only where installation is
permitted. Otherwise retain the skip. **Exit 2 means setup failed:** inspect the
retained diagnostics and correct the cause before retrying to new paths.

Setup extracts only pinned members from the original release. Perl installation
prefixes and `SeqTag.cpp`'s compiled installation directory are configured for
the new private home. Original scientific source bodies remain unchanged. Only
`homerTools` is compiled; `homer2` and `mergePeaks` retain their pinned original
binary bytes. The runtime marker records original/configured source hashes and
compiler/make executable hashes. Source files, compiler command output, warnings
and link diagnostics remain in the setup report.
This records the build; it does not certify byte reproducibility of a complete
compiler/sysroot environment. Original GPL notices are retained.

The compiled extractor links static C++/GCC runtimes. Setup binds the actual
Perl modules, system utilities and shared libraries used by the installed
workflow. `.jlinalg-homer-genome.json` records that local admission. Changed
members, installation paths, host/kernel or bound dependencies require a new
admission. Core and annotation profiles have their own scopes and homes.

The original installation check runs `findMotifsGenome.pl` on supplied synthetic
FASTA, target regions and background regions. It exercises extraction, overlap
removal, GC weighting, order-two oligonucleotide normalization, known enrichment,
de novo discovery, known-motif matching and SVG/HTML reporting. It requires the
implanted motif to be recovered and the original analytic target population to
be complete. It checks final outputs and diagnostic messages because the
original wrapper can exit zero after an extraction failure. This check uses
the original old-background algorithm, including `-useOldBg` on 5.1. No genome
package is downloaded. It establishes installation health on synthetic data.

`org.jlinalg.sequence.HomerGenomicSequences` supplies native Java operations for
the command's input/population audit:

- One-based inclusive resize using the original midpoint/floor rule. A numeric
  size of 200 produces 201 inclusive bases.
- Bounded indexed extraction with explicit case masking, strand orientation and
  normalization. The API requires intervals wholly within the exact contig.
- Original poor-sequence dispositions, including the strict N-fraction test.
- GC/CpG frequencies and bins using the original denominators and six-decimal
  intermediate values, including the original binary half-even tie rule. Ambiguous bases do not bridge adjacent canonical pairs.
- GC/CpG bin weights with a retained/excluded ledger for every input row. Bins
  need at least unit target and background weight; unsupported bins exclude both
  roles before supported population totals are recomputed.

Verify these operations against all three admitted original releases with a
development JAR containing the native methods and genomic runtime binding:

```bash
python3 tools/literature/verify_homer_genome_primitives.py \
  --jar /PATH/TO/DEVELOPMENT.jar --out /PRIVATE/NEW_VERIFICATION \
  --home-5.1 /PRIVATE/NEW_HOMER_51 \
  --home-4.11 /PRIVATE/NEW_HOMER_411 \
  --home-4.11.1 /PRIVATE/NEW_HOMER_4111 \
  --java /usr/bin/java --javac /usr/bin/javac
```

This compiles the checked-in Java probe and retains native/original outputs,
commands and runtime bindings. Each release has 1,832 row/disposition comparisons
across extraction, masking/strand, resize, frequency, bins, filtering and weights.
Read `verification.json` and `WARNINGS.md`; failed partial comparisons are not a
pass. These primitive checks do not validate discovery optimization, statistical
enrichment, generated backgrounds, biological accuracy or full-paper parity.

Official archives currently use HTTP content pins, without independently
authenticated upstream signatures. The source/member pins are in
[`homer-genome-lock.json`](homer-genome-lock.json). Binaries are installed
privately and are not redistributed by this repository. Cell Ranger remains
excluded by user request.
