# STAR RNA alignment and native annotation checks

`star-tools` supplies typed STAR 2.7.11b bulk RNA indexing and splice alignment,
with native Java FASTA/GTF/FASTQ validation. It exposes raw GeneCounts, automatic
Basic two-pass mapping and a separate supplied-junction second pass. STARsolo,
WASP allele swapping, fusion calling, UMI processing and complete published
pipelines remain separate missing integrations.

## Setup and prominent recovery

Where downloads and source builds are permitted on Windows x86_64:

```powershell
python tools/literature/install_star.py --allow-download --allow-source-build
```

Supply `--star build/omics-star/bin/STAR.exe` to the commands below. The installer
uses a checksum-pinned tagged source archive and the existing pinned private
MSYS2 compiler-package lock. It installs inside the project without changing the
global PATH or running OS package-manager scripts. SSE4.1 is required. Linux and
macOS users should provide their native upstream 2.7.11b executable.

**PRIVATE WINDOWS BUILD:** upstream lists Linux/macOS support. Local fixture
evidence applies to the exposed operations and tested inputs. This is not an
official upstream Windows release. Compiler warnings remain in the dependency
report and `build.log`; inspect them before changing compilers or using other
operations. Successful installation/version loading is distinct from analysis
validation. The source archive SHA is a recorded retrieval fingerprint, not a
separately published binary checksum.

**RECORDED SOURCE FIX:** expanded real tests exposed an uninitialized
`transform.outQuant` flag in upstream `ParametersGenome.cpp`. On this build it
could cause GeneCounts to read `/geneInfo.tab` even though the annotated index
was valid. The installer applies one hash-guarded initialization change,
`transform.outQuant = false`, alongside the other transformation flags. The exact
patch, original/patched hashes and failed attempts are retained. This runtime is
identified as patched source; scoring/alignment algorithms were not rewritten.
The generated parameter-byte header replaces the missing `xxd` utility. C++ flags
supply POSIX declarations and set the optional Linux `SHM_NORESERVE` flag to zero;
the adapter always uses `NoSharedMemory`.

**DEPENDENCY UNAVAILABLE OR INSTALLATION FORBIDDEN:** the affected analysis skips
with exit 3 and `WARNINGS.md` identifies the missing runtime and exact setup
command. If installation/building is forbidden, ask an administrator to supply
the pinned executable and its runtime libraries, then rerun to a new output
directory. Existing unmanaged or integrity-failed environments are preserved.
Do not substitute a different program/version and label the analysis validated.

Analysis/input/output-validation failures return 2, retain native logs and
quarantine partial artifacts under `partial/`, without publishing `results.tsv`.
Invalid options fail before execution with a specific stderr message. Successful
analyses with findings use `completed-with-warnings`; the console points to the
top-level warning file and required action.

## Native annotation inspection and sealed indexing

The executable below is the locally validated source JAR, not a public release:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar star-tools --method inspect-gtf --reference reference.fa --gtf genes.gtf --out annotation-audit
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar star-tools --method index --star build/omics-star/bin/STAR.exe --reference reference.fa --gtf genes.gtf --sjdb-overhang 149 --memory-mb 32768 --threads 4 --out rna-index
```

The example overhang assumes an intended 150-base read protocol. Choose it from
the actual protocol; no assembly, annotation release, contig aliases or species
are inferred. Index RAM is explicit and must fit the host. The small-reference
defaults for suffix-array prefix length and chromosome bins adapt to reference
size; explicit bounded overrides are available in `--help`.

`inspect-gtf` needs no STAR runtime. It checks nine-column GTF rows against the
exact FASTA dictionary, one-based inclusive bounds, valid strands and quoted,
unique `gene_id`/`transcript_id` attributes on lowercase `exon` features. GFF3 is
not accepted as GTF. It reports genes/transcripts/exons and unknown exon strands;
it does not infer transcript biology or validate every optional attribute.
Original FASTA sidecars are preserved.

`star-index.json` seals the reference/GTF identities, dictionary, annotation
overhang, gene-ID list and every generated index component. Alignment verifies
the exact component set and hashes before and after use. References and reads
must remain unchanged during processing. Outputs are published to new folders.

## Explicit alignment, filters and counts

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar star-tools --method align --star build/omics-star/bin/STAR.exe --index rna-index --reads R1.fastq.gz --reads2 R2.fastq.gz --sample SAMPLE1 --gene-counts true --two-pass basic --threads 4 --out rna-alignment
```

Four-line canonical A/C/G/T/N FASTQ uses Phred+33 qualities and synchronized mate
identities/order after terminal `/1` or `/2` removal. Native Java decompresses
gzip and stages uppercase bases while preserving quality strings. It performs
no trimming and runs no `readFilesCommand`, shell pipeline or FIFO. RNA U and
ambiguous IUPAC bases require explicit preprocessing. Staged FASTQs are removed
after successful content checks; failures retain them as partial artifacts.

`rna.Aligned.out.sam` retains unmapped observations, one best primary and accepted
secondary alignments. Reference dictionary, sample/read group and every primary
mate's original bases/qualities are checked. Normal filtering preserves input
order. **BySJout cannot use STAR's input-order mode:** the adapter uses native
output order and a bounded disk-backed SHA-256 multiset audit of length-prefixed
read name, mate, original-orientation bases and qualities. Duplicate multiplicities
remain part of the check. Its native spill/content-change tests are recorded separately.
STAR MAPQ 255 for unique alignments is not a calibrated posterior confidence.

**FILTERS CHANGE WHAT COUNTS AS MAPPED:** absolute `--match-min`/`--score-min` and
fractional `--match-fraction`/`--score-fraction` thresholds both apply. Defaults
retain the upstream 0.66 fractional thresholds. For paired reads these thresholds
use the combined mate length, so an otherwise matching orphan mate can fail the
default pair threshold. Lowering it is an explicit analysis decision. Local versus
end-to-end, canonical-junction selection, BySJout, multimapping, mismatch and
intron bounds are independent typed settings; inspect `session.yaml` and the
native final mapping log. No automatic unique-only or duplicate removal occurs.

**GENE COUNTS REQUIRE LIBRARY-STRAND CHOICE:** `rna.ReadsPerGene.out.tab` contains
unstranded, read-1-strand and read-2-strand columns. Choose the column matching the
actual library protocol. These are raw fragment counts, without normalization,
UMI deduplication or featureCounts equivalence. The adapter checks exact gene IDs,
nonnegative integer counts and every column's total across genes plus unmapped,
multimapping, no-feature and ambiguous categories against input fragments.
An unannotated index cannot supply GeneCounts. Zero mapping and omitted count
requests produce prominent warnings and specific recovery steps.

## Basic and supplied-junction two-pass workflows

`--two-pass basic` runs STAR's automatic first/second-pass workflow. With an
unannotated index, junction insertion additionally requires explicit
`--sjdb-overhang`, normally intended read length minus one. Annotated indices
retain their sealed overhang and reject conflicting overrides.

`--sj-junctions junctions.tab --two-pass none` inserts an explicitly supplied
junction list into a separate alignment. Input may be STAR's four-column intron
format or nine-column `SJ.out.tab`; the first four fields are used. Coordinates
are one-based inclusive intron boundaries, with exact reference names and strand
0/1/2. Empty, duplicate, out-of-bounds and non-STAR files fail. Generating and
filtering the first-pass list remains a separate responsibility; Basic and a
manual first/second-pass protocol are not automatically interchangeable.

See [adapter evidence](star-adapter-validation.json),
[packaged known-truth checks](star-packaged-validation.json),
[dependency/source-build details](star-dependency-status.json), and
[warnings and required actions](WARNINGS.md). Primary references are the
[pinned release](https://github.com/alexdobin/STAR/releases/tag/2.7.11b),
[upstream requirements](https://github.com/alexdobin/STAR/blob/2.7.11b/README.md),
[parameter definitions](https://github.com/alexdobin/STAR/blob/2.7.11b/source/parametersDefault)
and [initialization source](https://github.com/alexdobin/STAR/blob/2.7.11b/source/ParametersGenome.cpp).
