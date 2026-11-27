# velocyto spliced and unspliced count workflows

The source build adds `velocyto-tools` for counting from actual coordinate-sorted
BAMs and a compatible GTF. It runs unmodified velocyto 0.17.17, captures the
original Loom file and all logical count values, and exports exact integer
spliced, unspliced, ambiguous and **spliced-plus-unspliced** matrices with native
Java audits. Native optional preparation supports explicitly declared MA-to-UB
UMI-tag conversion and antibody-contig exclusion.

**Warnings and required action:** missing or forbidden software/input prerequisites
return **SKIP3**, skipping dependent analysis. Supply the named input or install
the permitted runtime privately, then create a **new** binding and capture.
Changed bytes, invalid preparation, failed model/data checks, cancellation or
exhausted capacities return **FAIL2** with no complete result. Retain every
attempt. Read `FAILURE.json`, `WORKER-*-FAILURE.json`, input/annotation/mask audits
and every original stdout/stderr; resolve the named cause and retry new paths.
The CLI installs nothing. A binding alone admits no counting analysis.

Use Java 17 or newer. Original-tool execution and replay require Linux x86_64
Java; Windows users can run Linux Java inside WSL. The two public native APIs
also run on Windows. These commands belong to the current source build; this
change does not publish a release binary.

```sh
java -jar omicsmatic-0.4.1.jar velocyto-tools --profiles
java -jar omicsmatic-0.4.1.jar velocyto-tools --example binding > binding.json
java -jar omicsmatic-0.4.1.jar velocyto-tools --example analysis > analysis.json
# Replace every illustrative path, preparation statement, setting and capacity.
java -jar omicsmatic-0.4.1.jar velocyto-tools --bind binding.json --out NEW_BINDING
java -jar omicsmatic-0.4.1.jar velocyto-tools --request analysis.json --out NEW_CAPTURE
java -jar omicsmatic-0.4.1.jar velocyto-tools --verify NEW_CAPTURE --scratch NEW_REPLAY
```

Require a completed capture **and** successful fresh replay using the exact
retained producing JAR and unchanged runtime. Read stderr warnings on successful
runs too. Original counting uses CB/UB droplet tags and the source Default
(Permissive10X) logic. This command starts from supplied alignments; it does not
install or execute Cell Ranger. RNA velocities, scVelo models and exact author
recipes require separate inputs, methods and qualification.

## Choose the counting integer behavior explicitly

| Request engine | Original operation | Count storage and interpretation |
| --- | --- | --- |
| `original-cli-uint16-buffers` | Original `velocyto run` | Internal counters are uint16 even with `--dtype uint32`; native molecule bounds must establish safety |
| `original-counter-api-uint64` | Original `ExInCounter(..., loom_numeric_dtype="uint64")` | Explicitly wider original API allocation, with uint64 stored layers; integer behavior differs from the default CLI |

`--example analysis` selects the original CLI mode. The source's `--dtype`
controls stored layers and is not passed into the counting-buffer constructor.
In the retained original control, 65,536 distinct spliced molecules became
zero with uint32 output. The original Counter API with explicit uint64 returned
65,536. Scientific source was unchanged in both runs. The adapter retains this
discrepancy and refuses complete original-CLI admission when a selected cell
exceeds its safe distinct-UMI bound. It never drops reads or cells to satisfy
that bound.

For an intentionally wider analysis, generate
`--example analysis-uint64`, review its different integer behavior and actual
preparation, then run a **new** capture. The original transcript models,
intron markup, molecule assignment and Default logic remain upstream in that
mode. The orchestration selects the constructor's dtype argument; it does not
change package constants or patch scientific code. This mode is not proof of
an author's original integer settings.

## Input and preparation contract

Supply one or more coordinate-sorted BAMs. Actual record order is checked; an
SO header alone is insufficient. Every selected mapped read in original unique
mode requires an actual integer `NH` tag. The original uses NH rather than
substituting mapping-quality, duplicate or primary-read filters. A request can
explicitly include multimappers. Original missing CB/UB records and long-span
handling remain reported, with all original logs retained.

The first retained BAM must contain **more than 1,000** mapped CB/UB-tagged
alignments for original chemistry detection. Smaller inputs fail whole-workflow
admission; the original initialization is not patched. Multiple BAMs require
distinct cell populations. Reads from one cell cannot be split across BAMs;
prepare an actual documented merged input or count distinct samples separately.

`umi_tag:"UB"` preserves prepared tags. `umi_tag:"MA"` explicitly copies existing
MA strings into UB and removes MA. Existing conflicting UB values fail. Only
alignments on the exact declared `excluded_contigs` are removed, with complete
record accounting. No antibody names, sample identities, mapper settings or
reference builds are inferred. Actual BD Rhapsody preparation can require
additional author-specific changes that this literal preparation does not certify.

Original velocyto splits CB at its first hyphen. Distinct input cells/GEM groups
that collapse to the same prefix fail. Count actual sample/GEM groups separately
and preserve their identities. Source CellID generation is retained: multiple
selected prefixes can receive an `x` suffix rather than the original GEM suffix.
The output preserves those identifiers and supplies an explicit mapping back
to observed literal BAM CB tags in `engine/matrices/cells.json` for downstream
joins. It does not silently rename cells to match Seurat or other software.

`barcode_file` accepts the actual plain or gzip barcode list, or null for the
original no-selection mode. Without a supplied list, original counting retains
cells whose **S+U is strictly greater than 80**. Missing/filtered potential input
cell identities are listed; no empty cells are invented.

Supply the actual compatible plain GTF. The original parses/sorts transcript
models and can infer missing exon numbers; its logs and parsed gene metadata
remain retained. BAM-side and GTF-side chromosome conversions differ for some
names, including mitochondrial and underscore forms. The adapter checks observed
eligible names against the actual parsed annotation and checks compatible
sequence lengths/ranges. Unannotated observed contigs require an explicit
`allowed_unannotated_contigs` declaration. Matching names do not prove identical
genome builds. The adapter does not repair identifiers or genomic coordinates.

An optional `repeat_mask` is checked against the original parser's actual
applied intervals. This original version can omit its final interval group.
Unapplied requested intervals produce FAIL2, retaining `MASK-AUDIT.json` and
the source inputs. A complete repeat-masked result is not claimed from an
incomplete mask. Resolve masking through a separately validated actual preparation
when available and retain its provenance before another counting analysis.

The original CLI constructs an unquoted internal Samtools command, so original
CLI captures/replays require output paths without whitespace. Original input
files are copied under literal owned names. The explicit Counter API uses
argument arrays for its Samtools calls.

## Outputs and complete native checks

| Artifact | Content |
| --- | --- |
| `engine/original/<sample>.loom` | Unmodified original CLI output, or clearly labelled explicit Counter API uint64 output |
| `engine/matrices/spliced.mtx` | Exact integer spliced layer |
| `engine/matrices/unspliced.mtx` | Exact integer unspliced layer |
| `engine/matrices/ambiguous.mtx` | Exact integer ambiguous layer |
| `engine/matrices/spliced_unspliced_total.mtx` | Exact S+U expression total, excluding ambiguous molecules |
| `engine/matrices/genes.json`, `cells.json` | Ordered original identifiers/attributes and literal cell-identity mapping |
| `INPUT-AUDIT.json`, `ANNOTATION-AUDIT.json` | Complete input/preparation accounting and actual original transcript metadata |
| `REFERENCE-ANNOTATION-AUDIT.json`, optional `MASK-AUDIT.json` | Actual source compatibility and repeat-parser results |
| `NATIVE-COUNT-AUDIT.json`, `OUTPUT-AUDIT.json` | Every logical matrix value/zero, totals, input molecule bounds and warnings |

The original Loom `matrix` contains **S+U+A** in float32; the native expression
total contains **S+U** as exact integers. These are different objects. Float32
rounding of integer source totals is reported, with original storage retained.

An original HDF5 reader exports the union of nonzero S/U/A/matrix positions,
plus logical dense SHA256 hashes covering every value and implied zero. Native
Java reconstructs all four hashes, checks the original float32 total, validates
cell molecule bounds and writes exact integer MatrixMarket files. Full original
gene/cell identifiers, scientific metadata, raw HDF5 files and all process logs
remain retained. Native audits do not independently reimplement transcript
assignment or compute RNA velocities.

The public APIs are `org.jlinalg.singlecell.VelocytoBamPreparation` and
`org.jlinalg.singlecell.VelocytoCountMatrices`. They perform native preparation,
audits and matrix export. Their contracts include actual complete input/molecule
bounds, cancellation and capacity checks. Capacities do not reserve JVM/process
memory; choose adequate heap, process budgets and disk for real data. Original
counting uses dense buffers and can need substantial memory.

Fresh replay uses saved original inputs and reruns preparation, transcript parsing,
original counting, all logical Loom values and native exports. Original HDF5
creation/modification clocks remain in the raw evidence and are the explicitly
excluded fields in scientific replay comparison. Extra or changed saved files
fail verification. Preserve exact producers, every attempt, bindings and all
effective runtime roots/nodes/symlink targets; never refresh historical seals.

Original counting can order cells through Python sets. The configured
`python_hash_seed` is applied at interpreter startup in a controlled environment
with `-s -P`; user packages and unsafe import paths are disabled. Python's `-I`
would ignore that seed. A retained development comparison exposed differing
cell order under `-I`; that attempt is unqualified and remains preserved. No
cell ordering or values are rewritten to make comparisons pass.

## Runtime and source provenance

The profile is `velocyto-0.17.17-pypi-1ad65fc5-samtools-1.19.2`, using the
[official PyPI 0.17.17 source distribution](https://pypi.org/project/velocyto/0.17.17/)
with SHA256 `1ad65fc53292ce1970a70bc742d73491b370038e0b0065761303e787bf7ffe39`.
Its scientific Python/Cython sources match the
[official 0.17.17 tag](https://github.com/velocyto-team/velocyto.py/tree/1944445ec6c6371459f2a730e973a3c651b459e8).
The distribution's generated C/packaging differ from the tag and are retained
separately; no equivalence to an author's exact build is inferred. The original
BSD2 copyright/licence is packaged under `META-INF/licenses/Velocyto-BSD-2-Clause.txt`.

Prepare a separate Python 3.11 environment with the profile's exact versions:
velocyto 0.17.17, NumPy1.23.5, SciPy1.10.1, pandas1.5.3, h5py3.8.0, loompy3.0.7,
PySAM0.21.0, numba0.57.1, Cython0.29.37, matplotlib3.7.5,
scikit-learn1.2.2 and numpy-groupies0.9.22, plus their required dependencies.
Some old packages require unmodified source builds. The retained build used
the modern system linker for the historical Python's compiler configuration,
preserving the original scientific compile flags. Do not alter package source
to bypass incompatibilities. If installation is prohibited/unavailable, skip
the dependent analysis and resolve the named prerequisite.

The binding's `source` is an exact view of the original source-distribution
contents, as listed in packaged `source_files`. Installed original `.py` files
are checked against that distribution. The tested original Samtools 1.19.2
helper binary is separately pinned; supply its actual libraries and complete
runtime roots. Binding checks package versions, original source, native-library
closures and complete declared runtime populations. Analysis commands install
nothing. The original repository is unmaintained; its recommendation of other
software does not establish equivalence to these paper-specific counts.

The [full 250 source audit](literature/velocyto-source-audit/ROLES.json) distinguishes
all four reported uses, BD preparation, P117's corrected reviewed version and
P228's velociraptor-link ambiguity. Supplementary-method completeness, exact
author recipes and whole-paper reproduction remain open. The overall 250-paper
goal remains active.
