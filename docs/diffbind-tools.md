# DiffBind workflows

`diffbind-tools` runs the original DiffBind 3.24.0 implementation with R 4.6.2,
DESeq2 1.54.0 and edgeR 5.0.0. It accepts complete BAM libraries, their indexes,
BED peak sets and explicit sample metadata. Native Java validates captured BAMs
and BED intervals and projects the original fitted DESeq2 results. Upstream
DiffBind performs consensus formation, counting, normalization, modeling,
shrinkage, reports, PCA and heatmaps.

**Validated scope:** 21 project tests passed on both Windows and Linux with no
skips. Four complete command captures and their fresh saved replays passed,
including both engines, controls, RLE/TMM and a valid empty selected report.
Seven controlled failure/skip/cancellation cases admitted no complete result.
The [qualification](literature/diffbind-validation/QUALIFICATION.json) records
the exact source and retained evidence. This is one scoped upstream family;
exact author editions/recipes and whole-package equivalence remain unqualified.
Existing 0.4.1 release downloads predate this source-built command.

## Warnings, errors and required action

**Exit 3 / SKIP3 means the dependent analysis was skipped.** Missing or forbidden
software, missing complete input/index files, and unsupported execution platforms
need a permitted installed runtime or the named missing input. On Windows,
execute Linux Java inside WSL. This command installs nothing. Download or install
prerequisites only where permitted, then create a new runtime binding.

**Exit 2 / FAIL2 means no complete result is admitted.** Inspect the error and all
retained logs. Changed runtime/input bytes, invalid metadata or models,
nonconvergence, failed upstream processes, exhausted capacities and cancellation
require correcting the cause and rerunning into new paths. Preserve failed
attempts. Never refresh an old manifest or alter its saved evidence to make it
pass.
An externally terminated JVM can return its operating-system signal exit code.
Its shutdown ledger and absence of `COMPLETED.json` still identify an incomplete
attempt; never admit it as a completed scientific result.

**A process exit of zero alone is insufficient.** Keep the producing JAR,
unchanged runtime binding and complete capture. Require `COMPLETED.json`, inspect
`WARNINGS.md`, `OUTPUT-AUDIT.json`, `engine/SOURCE-WARNINGS.json` and
`engine/process/process.stdout` and `.stderr`, then run saved verification.
Warnings remain visible in original process logs and the structured warning
record. The workflow retains all source result rows and original report columns.
When the original selected report is `NULL` because no sites pass the requested
threshold, its TSV is deliberately zero bytes and `engine/RESULTS.json` records
`source_null=true`, zero rows and an empty column schema. The source warning is
printed prominently. This is a valid empty selected result; the complete
all-results report and fitted model remain available. It is distinct from an
empty data frame or a failed fit, and saved verification preserves that distinction.

## Start with explicit requests

```sh
java -jar omicsmatic-0.4.1.jar diffbind-tools --help
java -jar omicsmatic-0.4.1.jar diffbind-tools --example binding > runtime.json
java -jar omicsmatic-0.4.1.jar diffbind-tools --example analysis > request.json
```

For a new machine, prepare R 4.6.2 and the exact packages in the ordered
[136-package source plan](../tools/literature/diffbind324/source-plan.csv).
It supplies official source URLs, filenames, versions, archive sizes, SHA256,
repository MD5 and license metadata. Obtain those originals only where downloads
are permitted. Before installing, check the complete archive set:

```sh
python3 tools/literature/diffbind324/check_sources.py /absolute/source-cache
```

An administrator can install those source packages in the listed order using
`R CMD INSTALL --library=/absolute/new-private-library ARCHIVE`, with
`R_LIBS_USER` selecting that private library and with separate retained logs for
each package. Use an existing permitted R 4.6.2 installation and its configured
C, C++ and Fortran toolchains, make and pkg-config. This validation used isolated
libuv 1.52.1 and libxml2-devel 2.15.4 prerequisites; the R package compiler logs
must show successful original builds and namespace loading. Do not install into
an existing scientific runtime. Supply all resulting private library and native
dependency roots in the binding request. A current unpinned package installation
can differ from this source plan and will fail the binding's version checks.
The source checker installs nothing. If installation is forbidden or a required
compiler/library cannot be supplied, skip the dependent analysis and retain the
SKIP3 warning with the missing prerequisite and required action.

Replace every example path, synthetic metadata value, parameter and capacity.
Use absolute canonical paths. Each BAM index must be present at `BAM_PATH.bai`.
Inputs are copied and fully hashed; shared control files are captured once and
remain referenced by every declared sample. Samples themselves are never
deduplicated. The BAM audit checks complete BGZF framing/CRC, headers and every
record. It does not independently classify peak counts or qualify a BAI index.
BED inputs must contain plain BED3 through BED12 records with valid intervals.

The binding request names original unmodified source, the selected Python,
explicit runtime search paths and complete runtime roots. Use empty owned files
for the three R startup settings. Include R, its libraries and prerequisites,
configuration and fonts; runtime binding also inventories the selected Python
standard library, Java and active native-library closure. It verifies the
complete original DiffBind source population, all 136 locked dependency versions
and every DiffBind namespace function body. Full file bytes, modes, directory
populations, links and selected program paths are bound. Keep installation and
download caches outside runtime roots. Bindings perform health checks without
running a user scientific analysis.

```sh
java -jar omicsmatic-0.4.1.jar diffbind-tools --bind runtime.json --out binding-1
java -jar omicsmatic-0.4.1.jar diffbind-tools --request request.json --out capture-1
java -jar omicsmatic-0.4.1.jar diffbind-tools --verify capture-1 --scratch replay-1
```

Every output path must be new. Analysis requests identify the completed binding.
Saved verification requires the exact producing JAR and unchanged bound runtime.
It rechecks the complete saved file population, native audits and result
projection, then reruns the full original workflow from captured inputs in a new
directory. It compares complete canonical output bytes, including fitted RDS
objects and both PNG plots. It never deserializes a user-supplied RDS object.
Original input paths are not needed for this captured-input replay.

## Scientific choices

The supported design is an ordered list of source metadata factors, combined as
an additive formula. The source factors are Tissue, Factor, Condition,
Treatment, Caller and Replicate; Caller is fixed to the BED peak caller in this
workflow and cannot be supplied as an arbitrary covariate. A request can provide
the other factors consistently for every sample. Declare the contrasted factor,
numerator and denominator levels and minimum membership. Arbitrary R formulas,
interactions, supplied executable code and input R objects are outside this
workflow. An explicit design can still be statistically invalid or rank
deficient; the original model must succeed.

Declare DESeq2 or edgeR, LIB normalization or engine-specific RLE/TMM, FULL or
PEAKREADS library size, overlap, mapQ, fragment size, duplicate and control
policies, minimum counts, count filter, summit handling and seed. `summits=-1`
selects the original `FALSE` setting; a nonnegative value selects the original
numeric setting. These are user choices, not paper defaults. Counting uses
original `summarizeOverlaps`, with serial execution and blacklist/greylist,
background, spike-in and offsets disabled explicitly. Parallel and other source
workflows require separate qualification.

**`test_fold` changes both the source hypothesis test and its report filter.**
Use zero for the ordinary source test. A paper's post-hoc absolute fold-change
cutoff does not establish a nonzero null hypothesis. This adapter does not
silently turn one into the other. The threshold uses FDR unless `use_pvalue` is
explicitly true. The all-results report uses threshold 1 and zero fold; the
selected report uses the requested threshold and test fold.

Source report counts are rounded by DiffBind even with report precision zero.
Complete counted and fitted objects remain in their original RDS files.
Original reports repeat sample column names for normalized counts and called
detail. `engine/RESULTS.json` describes each column by ordinal, name and type;
read TSV columns by position. Do not use a dictionary keyed only by header name.
Numeric tokens retain source `NA`, `NaN`, `Inf` and `-Inf` rather than inventing a
replacement value. The Java DESeq2 projection implements the source's specific
NA/NaN probability-to-one rule and stable adjusted-probability ordering while
preserving fold bits. It is separate from final report sorting and model fitting.

The process controller enforces per-process address-space, CPU, file-size and
open-file limits, and monitors wall time, workspace, process/thread counts and
aggregate sampled RSS. Sampled RSS is not a hard aggregate memory limit. A
capacity failure admits no result and retains the owned process diagnostics.

## Paper provenance

The [source audit](literature/diffbind-source-audit/README.md) records the four
papers' actual versions, roles and missing details. The current 3.24.0 workflow
does not reproduce historical author runtimes by relabeling them. In particular,
P020's engine is unreported, P046's cross-section design description does not
provide a complete formula, P155's citation identifies a package page rather
than a fixed release, and P163's one-replicate GoodpeaksScript branch is separate.

Original source and package documentation:
[DiffBind 3.24](https://bioconductor.org/packages/3.24/bioc/html/DiffBind.html).
DiffBind uses the Artistic 2.0 license. Runtime sources and failed build attempts
are retained privately; the project publishes its own code, reviewed provenance
and synthetic validation fixtures.
