# Original ATAC region and track workflows

`homer-tools atac` is a development operation for original HOMER5.1 SAM tag
directories, explicit replicate merging, region-mode peak calling and one-base
full-fragment coverage. A pinned original UCSC `bedGraphToBigWig`2.10 converts
the printed coverage. Setup and analysis have separate permissions and reports.

[The validation record](homer-atac-validation.json) identifies candidate
`afe3e7b2`: 964 focused tests in 75 suites and 964 exact-JAR tests on each Java
21/25 pass without skips, with strict Java 17, Javadoc and packaging. Three public
synthetic workflows match three fresh complete original references, including
all tag/QC/metadata families, regions, coverage and BigWig bytes apart from
command-path fields. They cover 10,620 input alignments, 5,300 weighted tags,
nine regions and 10,500 coverage rows. Seventeen prelaunch controls, three
postlaunch failures, two positive boundaries, two Windows skips and seven setup
capability refusals pass. The failed first verifier is retained and its temporary-
directory comparison bug is fixed with four regressions. Broader HOMER and the
250-paper implementation goal remain active.

## Paper role and upstream boundary

P016 ([Nature,10.1038/s41586-023-06845-4](https://doi.org/10.1038/s41586-023-06845-4),
[primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10765522/)) reports
Cutadapt adapter removal, Bowtie2 alignment to mm10 with maximum insert1000,
Picard duplicate identification and SAMtools duplicate/MAPQ30 processing.
HOMER called open-chromatin regions with150bp fragments and300bp initial peaks.
Replicates were merged for1bp tracks normalized to10million reads, with a
reported4bp upstream shift and full paired-fragment extension. The viewer was
Integrated Genome Browser9.0.0.

The paper does not identify HOMER/converter editions or complete HOMER commands.
This explicit5.1/2.10 development profile is a selected implementation. It does
not prove those editions were used in the paper. Initial peak styles, filters,
effective genome size, length scaling and endpoint choices require review.
No aligner, duplicate remover, viewer or named genome is substituted by this entry.

See [the executable examples](../../../examples/homer-atac-workflow/README.md).
The exact lock is `tools/literature/homer-atac-lock.json`, with identical packaged
resource bytes. Original scientific source is retained; only the C++ installation
prefix changes when building the tag helper. Other admitted HOMER homes do not
admit this operation. No binary or genome is redistributed with the project.

## Inputs and identities

Supply plain UTF8 SAM and an exact reference-length dictionary. A manifest lists
one to32 uniquely identified replicates; relative paths resolve against the
manifest. SAM `@SQ` lengths must agree with the dictionary and cover every
eligible contig. Build/provenance labels are explicit declarations rather than
proof of assembly identity. No contig alias is inferred.

Every input byte is copied and hashed, and every alignment receives a retained
disposition. Unicode input paths, read names and replicate IDs stay in the input
and identity ledgers; execution uses safe aliases. Original C++ shell utilities
and comma-sensitive filename parsing require a real ASCII Linux output parent
containing only letters,digits,underscore,dot,slash or hyphen. Use Linux Java
inside WSL on Windows. Native Windows reports SKIP3 before any original program.

Original unique/MAPQ30 mode removes secondary and low-quality rows. Duplicate
and supplementary flags remain unless removed upstream. Paired-flag alignments
carry weight0.5 each, including an orphan or a one-mate selection. Accepted
duplicate,supplementary,unpaired and improper states require explicit error or
retention policies. Nonzero absolute TLEN supplies the stored paired length;
zero TLEN requires explicit sequence-length fallback or rejection.

The original tag builder can exit0 without `tagInfo.txt` when eligible weighted
mass is below1.0. The adapter detects that population before launch and reports
the cause. It never rescales a half-weight mate to make a dataset pass.

## Native algorithms and complete population checks

`HomerSamTags` streams the original matching-block projection, all filtering
dispositions and half weights. Its CIGAR rules are explicit source compatibility:
S is ignored, I subtracts coordinate length, and D/N/X/H/P advance and break
blocks. Plus/minus anchors use the first/last matching block. Invalid,unsupported
or reserved-error-code projections fail; a standard-SAM repair is not inferred.

The disk-backed native tag ledger sorts full keys with exact long half units.
Each replicate and the merge must match every source tag record after complete
full-key aggregation. The original comparator truncates length to a signed
byte, but the stored length remains an integer. Length100 and356 stay distinct;
repeated full keys need not be adjacent or coalesced. `Unique Positions` is the
serialized source row count. Complete masses, chromosome families, metadata and
QC files are retained and checked without claiming the QC statistics are native
reconstructions. The source genome-size estimate sums maximum tag anchors and
is not a whole-assembly length.

`HomerRegionCounts` independently checks final disjoint,both-strand region
counts using inclusive tag-anchor containment after the150bp fragment-center
shift. Source adjustment clamps anchors to the original input range. Exact raw
half-unit counts feed the original float32 normalization and printed precision.
Discovery scores,FDR/Poisson thresholds and local/clonal diagnostics remain
upstream results. Their calibration is not established by count agreement.
Original region-size metadata precedes optional boundary clamping and can
differ from the final inclusive span. No BED conversion is inferred.

HOMER may replace an effective genome size equal to its default2billion with
its smaller tag-anchor estimate. This requires explicit retention or the
operation stops before peak calling. Requested and used denominators remain
separate in the session and warnings.

`HomerCoverage` replays every source event with float32 arithmetic, stored paired
lengths, one-base resolution, the reported4bp upstream adjustment and10million
weighted-tag normalization. Length normalization is separately chosen as0 or100;
the source minimum denominator is25bp. Even upstream raw mode would still scale
by length unless disabled. Source anchor adjustment always clamps to the input
minimum/maximum range. Choosing extended last-tag events does not remove that
clamp. Equal source comparator keys use explicit stable order in Java; every
printed source bin must match exactly on the pinned runtime. No tolerance or
cross-libc ordering guarantee is invented.

The source suppresses abs(value)<=0.05 and prints two decimals. Terminal float32
residues and comparator ties remain in the session. Native `BigWigSignal` reads
all raw intervals and the on-disk dictionary, checking exact float32 conversion
of the **printed** source text. Zoom summaries and downstream rendering are
separate. Overruns,unsupported shapes or partial converter output fail and stay
retained; no clipping,zero filling or silent output replacement is inferred.

## Installation, failure states and capacity

Where permitted, explicit setup verifies the original archive/converter, builds
the installation-specific tag helper and runs actual tag,merge,region,coverage
and raw-BigWig health checks. The C++11 toolchain needs headers,pthread,math and
static C++/GCC libraries. Runtime members, host kernel and library closure are
checked before/after analysis. Compiler executable identities and original and
configured sources are recorded; this is not a toolchain/sysroot byte-rebuild
certificate. Official HTTP content pins are not authenticated signatures.

HOMER is GPL software; original notices are retained. The selected UCSC format
converter is covered by the upstream MIT licensing for format tools. See the
[official UCSC license](https://genome.ucsc.edu/license/), the retained
`tools/literature/ucsc-kent-LICENSE.txt` and the source/archive lock.

**SKIPPED,exit3** means the required dependency or capability is unavailable,
changed,unsupported or forbidden. **Required action:** read `WARNINGS.md` and
the session cause, use Linux/WSL and the correct private profile, or prepare the
required plain SAM/software where permitted. Analysis never installs or registers.

**FAILED,exit2** means an input,population,process,numerical or capacity check
failed. **Required action:** inspect retained `partial/` and original stderr,
correct the stated cause and rerun to a new directory. Even a nonempty converter
file remains provisional if a later native audit fails. Existing user output is
preserved. Empty partial directories exist consistently for early refusals.

Defaults allow64GiB source bytes,100billion decoded characters,128GiB workspace,
8GiB owned upstream RSS,10million alignment rows,2million serialized tags per
chromosome,1million regions,10million raw coverage rows and100,000 in-memory
ledger keys. At most256 disk chunks/readers are allowed. Choose declared limits
and Java heap for the full population. Native replay and BigWig decoding have
separate memory costs; upstream RSS excludes Java heap. Time/RSS/workspace
checks are periodic monitors rather than OS quotas. All original child processes
are joined before final publication.

Broader HOMER integration,including the reviewed P040 RNA role, remains active.
Cell Ranger remains excluded. These technical synthetic checks do not certify
biological findings,whole-paper reproduction or production-data accuracy.
