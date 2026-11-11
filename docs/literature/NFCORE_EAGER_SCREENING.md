# Eager screening consistency and private GATK 3.8 control

**The full eager adapter, its three paper roles and the broader implementation
goal remain open.** This checkpoint adds native Java screening consistency checks
and a private original-source capture/genotyping control. It adds **zero** catalog
tools. The catalog remains at 99 new scoped integrations (19 native and 80
adapters), plus three existing adapters. The released executable is unchanged.

**Missing or forbidden dependencies: skip the dependent analysis.** Preserve the
complete inputs and diagnostics. Supply the exact prerequisite through permitted
setup, or use an allowed environment, before retrying in a new directory. A failed
run, absent statistic or missing genotype must never become a negative finding.

The [paper/source audit](NFCORE_EAGER.md) and
[earlier execution checkpoint](NFCORE_EAGER_EXECUTION.md) explain the distinct
screening, capture and ancestry roles and unresolved provider differences.

## Native screening check

`org.jlinalg.workflow.NfcoreEagerScreeningReads` checks the complete supplied
host BAM, filtered BAM, unmapped FASTQ and MALT SAM populations. It uses the
existing HTSJDK dependency, requires Java 17 or newer, and needs no installed
samtools, MALT, Python, Conda or container runtime.

```java
var refs = NfcoreEagerScreeningReads.references(
    Path.of("ref.db"), Path.of("ref.idx"), Path.of("ref.inf"),
    1_000_000, 10_000_000_000L);
var audit = NfcoreEagerScreeningReads.inspect(
    Path.of("mapped.bam"), Path.of("filtered.bam"),
    Path.of("unmapped.fastq.gz"), Path.of("malt.blastn.sam.gz"),
    refs, 37, 0,
    new NfcoreEagerScreeningReads.Limits(
        10_000_000, 1_000_000, 50_000_000, 10_000_000));
```

The first two capacities bound complete reference sequences and bases. The four
`Limits` values bound BAM records, eligible unmapped queries, stored query bases
and MALT alignments. Capacity exhaustion refuses the complete assessment; it
never substitutes a subset. Eligible query identities must be unique. Duplicate
identities are unassessable, without renaming or choosing a representative.

The check compares every ordered filtered BAM record with the original
unmapped-flag, MAPQ and query-length selection. It compares all eligible unmapped
FASTQ identities, copied RG/BC/QT tags, read suffixes, strand orientation, bases
and qualities against the original samtools 1.12 `fastq -tN` behavior. Secondary
and supplementary records are excluded from that FASTQ population.

Every byte of the original MALT 0.4.1 reference database is consumed; index
offsets, literal headers, lengths, complete sequence/base counts and trailing
bytes are checked. Every SAM alignment must refer to an input query and a valid
reference interval. Strand and terminal hard-clip projection must reproduce its
sequence and qualities. CIGAR, MD, NM, ZL and integer ZI counts must agree.
MALT's adjacent MD mismatch letters are parsed directly without rewriting SAM.
This source profile accepts M/I/D/H/S CIGAR operations; other operations refuse
assessment. MAPQ 255 is retained without treating it as mapping confidence.

These are consistency contracts. They do not independently rescore alignments,
recompute MALT's LCA assignments, interpret HOPS authenticity classes or validate
the paper's historical database. Empty alignment counts are valid counts, while
minimum/maximum identity remain `null` with a prominent warning.

## Build and use the development CLI

The [development builder](../../tools/literature/build_nfcore_eager_source.py)
uses the configured Windows JDK 21/25 paths and the pinned public ChIP-seq
development dependency JAR. It creates a separate Java 17-compatible JAR and
preserves its predecessor members. Configure those permitted build prerequisites
first if they are unavailable. Choose a new build directory:

```powershell
python tools/literature/build_nfcore_eager_source.py --compiler 21 --out build/literature-review/nfcore-runtime/my-screening-build-1
java -cp build/literature-review/nfcore-runtime/my-screening-build-1/nfcore-eager244-source-control.jar org.jlinalg.cli.NfcoreEagerScreeningCli --mapped-bam mapped.bam --filtered-bam filtered.bam --unmapped-fastq unmapped.fastq.gz --malt-sam-gzip malt.blastn.sam.gz --malt-reference-db ref.db --malt-reference-index ref.idx --malt-reference-info ref.inf --minimum-mapq 37 --minimum-length 0 --out new-screening-audit
```

The minimum query length is the length threshold actually used to produce the
supplied filtered BAM; use zero when that optional length filter was absent.
The reference files must belong to the complete original MALT database used for
the alignments. Run the CLI's `--help` for capacities and defaults.

Success writes `audit.json`, `WARNINGS.md` and `COMPONENT_COMPLETED.json`.
Exit 2 means **no result admitted**. For an owned new output directory it writes
`FAILURE.json` and recovery actions in `WARNINGS.md`. Existing output directories
are preserved. Resolve the named error and retry a new directory. Raising a
complete-population capacity may also require more permitted Java heap.

## Complete screening validation

All records from both upstream screening libraries were checked, without further
sampling of the upstream files whose names already contain `tengrand`:

| Library | BAM records | Unmapped FASTQ queries | MALT alignments | Aligned queries |
| --- | ---: | ---: | ---: | ---: |
| JK2782 | 9,892 | 8,642 | 96 | 93 |
| JK2802 | 9,828 | 8,208 | 79 | 79 |

The complete reference database has three sequences and 52,244 bases. Independent
reference metadata came from the original MALT Java API. Original samtools 1.12
and pysam measured the complete record populations. Both compiler builds passed
on Windows Java 21/25 and Linux Java 21: 12 complete library replays, 144
deliberate refusals and six empty-statistics controls. Separate packaged CLI
checks passed 12 complete libraries, 24 refusal checks and six help checks.

**Retained provider failure:** original pysam 0.16 exited by segmentation fault
when opening the gzip MALT SAM directly. The failed process receipt and inputs
remain retained. A separately declared original samtools 1.12 SAM-to-BAM format
view of every alignment was then read by original pysam. The raw SAM, alignments
and query population were unchanged. Sequential missing-index diagnostics are
retained; no sort order or index was fabricated.

The byte-pinned regression fixture contains both complete libraries, independent
expectations, late-record corruptions, duplicate-identity controls, malformed
reference files and capacity controls. It contains no upstream program binaries.
Run the focused offline regression tests with the normal project dependencies:

```powershell
./gradlew.bat :test --tests org.jlinalg.cli.NfcoreEagerScreeningReadsTest
```

## Private GATK 3.8 capture control

The user accepted the
[official GATK 3.8 academic noncommercial terms](https://github.com/broadgsa/gatk-protected/blob/3.8-1/licensing/protected_license.txt),
including the disclosed default usage reporting and redistribution restriction.
The exact official `broadinstitute/gatk3:3.8-1` image was obtained privately and
its program loaded with the original eager image's Java 8. The literal version
was `3.8-1-0-gf15c1c3ef`. Its private JAR is 15,827,714 bytes, SHA-256
`4c7c52b0344acbe87ee05d0ae2639d2d905e2f5b5414dc8ad2b97891b79d0bb1`.

**Keep the GATK program, its images and complete engine object trees private.**
The captured engine includes that licensed program. Public evidence therefore
contains only a named data/receipt selection and is explicitly **not** a complete
standalone engine bundle. The Java screening component needs none of these
licensed runtime files.

The original eager 2.4.4 source, Nextflow 21.04.1 and original source image ran
the reported capture branch with this explicitly selected private provider.
All three upstream FASTQs and both original sample rows were retained. A separate
synthetic control covered every possible 75-base window of the 16,770-base
Mammoth test reference, with a declared T-to-A SNP at position 1,001. The complete
workflow finished **53 tasks**, including realignment, UnifiedGenotyper, bcftools
statistics and MultiQC for all three samples.

All **50,310 VCF rows** matched reference alleles and ordered positions, sample
and genotype fields, original bcftools summary counts and the selected runtime
header. The synthetic SNP was `1/1`, AD `0,73`, DP `73`; all 16,696 synthetic
input reads were also checked against their generation rule. No-call genotypes
remain missing: 874 rows for JK2782, 1,022 for JK2802 and 34 for the synthetic
sample. Source nonvariant AD `0` with nonzero DP is retained literally.

**Limits and required actions:** the authors' exact 3.8 patch, Conda environment
and adapter-list contents are unreported. This control explicitly uses patch
3.8-1 and the original default adapter pair. Upstream defaults retain diploid
SNP genotyping, downsampling depth 250 and zero BAM end-clipping lengths; merely
enabling BAM trimming does not supply nonzero clipping lengths. This Mammoth
software control does not reproduce the authors' Treponema data. Genotyping
likelihood equivalence, remaining eager scientific contracts, capture/ancestry
paper roles and whole-family admission remain outstanding. Graphviz is absent,
so the optional rendered execution DAG is missing; install it only through
permitted setup if that diagram is required.

The controller's earlier copied ChIP-seq receipt labels were corrected to eager
labels. Historical receipts and the prior source are preserved without rewriting
them. The older execution checkpoint remains evidence for its original commit;
this checkpoint records the changed controller and builder source separately.

The [validation record](nfcore-eager-screening-development/VALIDATION.json)
and its named evidence archive record the exact sources, fixtures, runtime
receipts, retained failures and scope. Neither hashes nor successful software
controls establish historical author environment identity or biological validity.

To verify the saved archive and replay both native builds from the public
fixture, use the exact two development artifacts recorded by `VALIDATION.json`
and a new verification directory. They can be rebuilt with the recorded compiler
environment using the builder above. This command needs Windows Java 21 and the
normal development dependency JAR, without GATK or any original container:

```powershell
python tools/literature/verify_nfcore_eager_screening_checkpoint.py --jar21 PATH_TO_RECORDED_JAVA21_BUILD.jar --jar25 PATH_TO_RECORDED_JAVA25_BUILD.jar --out build/literature-review/nfcore-runtime/my-saved-screening-check-1
```

That verification checks evidence/source bytes and reruns both native fixtures.
It does not replay the private genotyping engine or convert the named public
capture-data selection into a complete engine bundle.
