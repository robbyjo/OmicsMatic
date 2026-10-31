# FAVORannotator: upstream and native audit development

**A scoped offline FAVOR adapter is now validated.** The new
`org.jlinalg.genetics.FavorCsvJoin` source API models and audits the offline
annotation join. `FavorCsvReader` now provides bounded streaming CSV admission;
`FavorXsvSupervisor` runs the original script's exact staged xsv command plan
with terminal process events. `FavorVcfReader` now admits bounded variant-only
or explicit diploid-GT VCF inputs. `FavorVcfAudit` computes complete streaming
identity/genotype/phase hashes. The internal `FavorGdsAudit` validator and read-only
R helper verify converted values and every original GDS node, including hidden
nodes and attributes. The private upstream runtime has run the original
annotation scripts through this supervisor on synthetic inputs. The new
`favor_annotation_audit.R` helper and internal `FavorAnnotationAudit` Java API
verify complete new scalar columns and export quoted TSV values with cell states,
canonical files and a provenance manifest. `FavorXsvLedger` now verifies every
expected terminal nested command against its exact plan and actual parent time
window. `FavorRuntimeFingerprint` now checks the complete protected software
inventory before launch and rechecks it afterward. The internal
`FavorAnnotatorWorkflow` now connects fresh scientific health admission and the
complete protected-copy parent workflow. The `favor-annotate` main command now
passes typed request, real failure/shutdown and local executable-JAR scientific
checks. Repository runtime preparation and the generated public example also pass;
see [FAVOR_WORKFLOW.md](FAVOR_WORKFLOW.md). Broader FAVOR and historical paper
reproduction remain open. The sealed transcript-site candidate does
not contain these new FAVOR source modules. The
validated unique-tool count is now 52 new integrations, including this scoped
adapter (55 with the three pre-existing adapters; 267 remain missing).

**Original export failure — required action:** with the tested R 4.4.3,
gdsfmt 1.42.0, SeqArray 1.46.0 and readr 2.2.0 stack, the original
`FAVORaGDSToText.R` fails on the synthetic aGDS. Each stored annotation column
contains eight values, but the restored tibble/data-frame metadata reports zero
rows. Do not treat that exporter as working or its output as complete. The
failure, original nodes and diagnostics are retained. Scalar-column reads verify
the stored values separately; they do not repair or replace the original nodes.
An integration must declare its supported export path and verify every value and
identity before admitting an analysis. No upstream script has been patched.

**Date/time attribute loss — required action:** the tested original GDS writer
retains underlying double values but drops `Date`, `POSIXct`/`POSIXt` and
`hms`/`difftime` classes and their attributes. The scalar export contains those
stored numbers. Its manifest retains the original classes, UTC timezone and
seconds units where supplied. Interpret R `Date` numbers as days since
1970-01-01, POSIXct numbers as seconds since that UTC epoch, and the tested hms
values as seconds. Do not claim that those classes survived in the original GDS
or silently restore them in that file.

**Unsafe attachment — required action:** reject a positional GDS attachment if
the source join drops, multiplies or reorders query rows. Inspect the complete
partition, query, database and output-row ledgers. Do not silently reorder rows,
select one duplicate or discard variants to obtain a passing result. Missing
database matches retain explicit no-match provenance; empty annotation values do
not establish absence of biological effect.

**Unsafe genotype conversion — required action:** the tested original converter
shifts later phase flags when a single-dot `.` call occurs among diploid calls.
The diploid admission profile rejects single-dot, haploid and polyploid GTs.
Supply an input appropriate to the explicit diploid profile, or use a separately
supported analysis for other ploidies. Do not fabricate alleles, pad calls or
rewrite phase flags to obtain admission. A FORMAT without GT can produce sample
IDs without genotype nodes; that input is also rejected for genotype analysis.

**Original null-genotype utility fails — required action:** the unmodified
`convertVCFtoNullGenotypeGDS.r` exits 1 because its export output is the same file
that it already has open. The failure and partial GDS are retained. Do not use
that utility as a working dependency. The ordinary CSV converter successfully
handles an actual eight-column variant-only VCF without inventing sample or
genotype nodes; this is a separate, explicit input mode.

## Verified upstream source and runtime

[FAVORannotator](https://github.com/zhouhufeng/FAVORannotator/tree/dbd6ffcc11b01beab32a2d46e09a0798fd335544)
declares GPLv3 in its README. The pinned commit is
`dbd6ffcc11b01beab32a2d46e09a0798fd335544`; the README's version label is
1.1.1, dated August 2022. That label does not identify P130's unstated historical
version or prove that every file in the pinned repository belongs to that release.

The inspected offline path runs unmodified `Scripts/CSV/convertVCFtoGDS.r` and
`Scripts/CSV/FAVORannotatorv2aGDS.r` against declared local CSV shards. The former
uses SeqArray and explicitly ignores a `chr` prefix. The latter partitions query
keys, performs xsv left joins, concatenates partitions and adds the resulting
columns to the GDS. It opens its GDS input writable, so an eventual adapter must
operate on a protected working copy. Neither the new Java join API nor this
development record implies a VCF allele-normalization step.

The private Linux runtime contains R 4.4.3, gdsfmt 1.42.0, SeqArray 1.46.0,
readr 2.2.0, jsonlite 2.0.0, digest 0.6.39 and xsv 0.13.0. Its lock and package
identities are retained. The package manager skipped GenomeInfoDbData's post-link
download; the exact reviewed 1.2.13 data package was installed privately after
checking its checksum. The first VCF conversion then identified the additional
`digest` dependency, which was installed privately. Failed attempts remain
retained. No global PATH, existing R environment or package-manager post-link
policy was changed. No whole-chromosome FAVOR database or study data was
downloaded, and no variant identifiers were uploaded.

The separate Essential and Cloud entry points download large database archives.
They have not been executed. SQL, cloud, structural-variant annotation and the
current FAVOR web/API service remain outside this validated development scope.
If a future required runtime is unavailable or its installation is forbidden,
skip that affected analysis with a prominent cause and required action; provide
the runtime on a permitted host. Invalid input/model/capacity or failed upstream
execution must remain failures with retained diagnostics.

## Native join contract

`FavorCsvJoin.join` accepts original-order canonical autosomal variants, an exact
build/resource/schema declaration, ordered DBsplit shards and streaming decoded
database-row callbacks. The caller must protect and hash external sources,
decode CSV correctly, scan every row exactly once and propagate callback errors.
This API is not a CSV parser or GDS reader/writer.

- Join keys are literal `chromosome-position-reference-alternate`. Uppercase
  biallelic DNA alleles and a single selected autosome are required. No allele
  swap, left normalization, build alias or implicit deduplication occurs.
- Bounds select query positions inclusively. Source shard order, original query
  order within each shard and database match order determine the output. All
  duplicate matches are retained. Shard gaps, overlaps and concatenation
  reordering remain visible.
- Every original query receives a disposition, coverage count, match count and
  complete projection into source output rows. Every declared shard receives
  counts. Other-chromosome tables are not opened and retain unavailable counts.
- Only query-matching database rows are retained in memory. All scanned rows,
  including unused rows, are validated and counted. Query, database-row/cell,
  output-row and work limits reject complete populations rather than truncating.
  Cancellation and I/O failures return no partial result.
- A positional attachment requires exactly one output row per original variant
  and identical source/query order. A no-match row can satisfy those conditions;
  missing annotation is distinct from a dropped original variant.
- At least two selected partitions are admitted. The original single-partition
  `2:1` concatenation loop invokes xsv with an invalid `NA` filename, yet the R
  script can still exit zero. That failed nested command is retained as a source
  boundary; top-level exit zero alone must never admit a completed workflow.

## VCF admission

`FavorVcfReader` reads complete UTF-8 VCF headers and original-order records from
plain or magic-detected gzip input, including concatenated gzip members. It
checks compression CRCs and independent encoded/decoded byte budgets. Header,
line, sample, variant and call capacities reject complete inputs rather than
truncate them. EOF is required for completion; failed scans cannot resume.

The caller chooses `VARIANTS_ONLY` or `DIPLOID_GT`, one autosome and an exact or
explicit case-insensitive leading-`chr` policy. Variant-only input has exactly
eight columns and no invented samples or calls. Diploid input has distinct
sample IDs, a declared string GT field first in FORMAT and exactly two explicit
allele fields in each GT, including `./.` for a fully missing diploid call.
Multiallelic, symbolic, noncanonical alleles and other ploidies are unsupported.
No sample columns, duplicate queries or out-of-order variants are silently
dropped or reordered. Positional annotation alignment remains a separate check.

Original IDs, chromosome strings, QUAL/FILTER/INFO strings, headers and supplied
FORMAT strings remain available. Missing allele values and every explicit phase
separator are retained. Literal missing ID `.` is preserved alongside its
observed source GDS representation, the empty string. INFO and non-GT FORMAT
values remain opaque; the reader does not claim complete VCF semantic validation
or replace the original converter's additional metadata interpretation.

Across all three JVM profiles, 14 admitted fixtures match complete stored results
from the original CSV converter: 75 variant rows, 436 genotype alleles and 218
phase values, plus sample/variant identity, coordinates and alleles. Five unsafe
or unsupported fixtures are refused explicitly. The single-dot phase discrepancy
remains recorded. The separate streaming and full GDS checks below extend these
small-fixture comparisons; they do not certify a complete analysis workflow.

## Streaming values and complete GDS preservation

`FavorVcfAudit.scan` retains one VCF record at a time and computes SHA-256 for
every sample ID, sequential source variant ID, chromosome, position, reference,
alternate, annotation ID, genotype allele and phase value. Each digest retains
its serialization kind and complete value count. The `favor-gds-values-v1`
schema uses signed little-endian int32 values; missing genotype alleles use
`INT32_MIN`. Strings use their exact UTF-8 bytes with an int32 little-endian byte
length prefix. Genotypes follow allele, sample, variant order; phase follows
sample, variant order. Variant-only results omit genotype and phase digests.
Cancellation, invalid input and capacity failures return no completed result.

The owned `favor_gds_audit.R` helper opens a GDS read-only and hashes its complete
semantic values in bounded variant blocks. Canonical binary diagnostics and the
complete JSON audit remain in the selected new output directory. Byte, node,
node-value, sample, variant and block capacities fail instead of truncating;
an existing output is never overwritten. These diagnostics can contain sample
and genotype data and remain local to their analysis. A successful JSON file is
published only after all values and nodes have been checked.

The helper enumerates all nodes with `include.hidden=TRUE`. For each original
node, the Java validator compares complete data digest, attributes, storage,
type, shape, compression, visibility and parameters. Root attributes and the
reported audit runtime must also stay identical. The native VCF digest must
match both the before and after GDS semantic hashes. Only additions under the
source's exact `annotation/info/FunctionalAnnotation` subtree are allowed.
The original GDS must be unannotated. This check does not certify new annotation
columns, safe positional attachment, an upstream export or runtime admission.

On Windows Java 21/25 and Linux Java 21, 15 complete inputs match the source
converter at two R block sizes each. The set includes a fresh 2,053-variant
conversion with Unicode IDs, missing alleles and explicit phase flags. Across
these inputs, 2,128 variants, 12,754 genotype alleles and 6,377 phase values are
checked. Fifteen before/after pairs cover eight original annotation outputs and
seven supervised outputs: 420 original nodes, including 75 hidden nodes, retain
their data and metadata. Their 720 genotype alleles and 360 phase values stay
unchanged. Unsafe annotation-order fixtures remain diagnostics and are still
ineligible for completed analyses.

Four actual altered GDS copies are refused: a genotype allele, a phase flag, a
hidden-node attribute and an unrelated new node. Ten R-helper controls refuse
exceeded capacities, incompatible modes, an invalid original partial GDS and an
existing output. The first corruption harness attempted an unsupported in-place
write to compressed GDS data. That failed attempt is retained; the corrected
harness changes only owned test copies by replacing the affected node. No
original scientific script, input or historical result was changed.

## CSV admission and supervised subprocesses

`FavorCsvReader` owns and closes its input handle. The complete header is admitted
before data rows are exposed. Header names are distinct and nonblank; every row
has exactly the header width. Byte, data-row, decoded-cell, column, field and
logical-record capacities refuse complete populations rather than truncate.
Only a scan that observes EOF can report completion. An error invalidates the
reader and cannot be bypassed by continuing from the next record.

Literal empty/`NA` strings, whitespace, escaped quotes and embedded line endings
remain unchanged. LF, CRLF and CR terminate records; quoted CR/LF remain cell
content. A UTF-8 BOM is recorded explicitly. Truly empty physical records require
an explicit `ERROR` or `IGNORE` policy; ignored records are counted. A source
callback can feed every decoded row directly into `FavorCsvJoin` without loading
the complete database into memory.

The reader matches actual xsv output on 44 admitted formats, including 32 seeded
random cases. Its admission rules are deliberately stricter for malformed quote
placement, unterminated quotes, duplicate headers and inconsistent row widths.
Six refusal cases retain actual upstream outcomes, including cases xsv accepts.
Do not claim that the reader reproduces xsv's entire permissive dialect or
silently rewrite a refused resource.

`FavorXsvSupervisor` is an internal subprocess entry point, not a study-analysis
CLI. It authenticates an exact request and admits only the expected staged
left-join and ordered-concatenation arguments. It checks the pinned Linux x86_64
xsv binary before and after execution, inherits CSV stdout unchanged, isolates
the child environment, records actual child exits and enforces a timeout with
owned-process termination. Each admitted attempt has a separate terminal JSON
event. Invalid requests or command plans fail; unavailable runtimes are reported
explicitly. No subprocess installs software.

**Parent admission is still required:** a nested event is not a completed study
analysis. The eventual parent must validate every expected event exactly once,
reject failed or incomplete events, verify every source and output, and enforce
native query/annotation alignment. A source R exit of zero cannot override a
failed event. The deliberately missing-database control returned R exit zero
while retaining a failed supervised command. The timeout control stopped its
real xsv child and recorded exit 137 and a failed supervisor outcome.

The internal `FavorXsvLedger` now implements the event-validation part of that
parent contract. It requires exactly one terminal event per planned join and
ordered concatenation, with exact arguments, request hash, executable path/hash,
child/supervisor exits and stopped-child state. Events must fall inside the
recorded parent execution window and follow the original sequential order.
Missing, repeated, foreign, temporary, nonterminal, failed or overlapping events
cannot pass. Complete event byte/count capacities fail rather than truncate.
Captured event hashes and the complete directory population are rechecked before
admission and remain verifiable afterward. Historical PIDs are never used to
target unrelated current processes; PID reuse alone is not a failure.

Fresh original R runs under an isolated empty user-library directory produce
seven complete source ledgers with 21 real xsv commands. The validator admits
those execution records on all three JVM profiles using the actual parent UTC
start/finish windows. Their raw CSV, genotype/phase/ID values and scalar columns
match the retained originals. Two fixtures have safe annotation alignment; five
still fail the independent scientific checks. A new missing-database run returns
R exit zero but is rejected by the parent ledger. A real xsv timeout leaves a
partial failed ledger and is also rejected. No owned child remains running.

## Runtime identity inventory

The runtime intake records 18,763 provider file/directory entries: 18,364 in the
private R environment, 390 in Java 21, six Java documentation entries and three
host-loader configuration entries. It also records 64 host files and 84 ELF
loader objects in clean and R-child execution contexts. The installed artifact
metadata for all 154 Conda packages matches the URL/SHA-256 pairs in the pinned
build lock. R and Java probes run successfully; R uses only its private package
library and an owned empty user-library directory.

The descriptor remains explicitly `inventory-only-not-admitted`. The new
Java verifier checks its protected SHA-256, every recorded tree entry and
file byte, permission, symbolic binding, host file, installed package artifact
record and inferred ELF SONAME. It rechecks the descriptor and supports a second
complete verification after use. Its result is
`fingerprint-verified-not-scientifically-admitted`. The fingerprint result alone still requires the scientific health gate
and integrated controller described below. It does not install software, alter
system libraries or certify a completed analysis. The existing Java `lib/src.zip` documentation link is
recorded as dangling; it is not an invented missing runtime dependency.

The first standalone loader scan reports `libjvm.so` missing from `libjava.so`.
Actual successful JVM process maps show it already loaded. The corrected intake
resolves needed ELF SONAMEs against the corresponding observed process maps,
retains the standalone diagnostic and refuses ambiguous/unresolved bindings.
Sixteen standalone missing bindings are resolved this way. The failed inspection
and its traces remain retained; no replacement library was installed and no
provider binary was modified.

The previous intake incorrectly assigned R's already loaded libraries to fresh
exec seeds such as `/bin/bash`. That snapshot remains retained and unadmitted.
The corrected intake uses observed bindings only when the ELF object itself
was mapped by that same process; standalone execs use their own loader
resolution. It records 836 inferred bindings. The Java verifier rejects the
old snapshot for this process-scope error. Literal RPATH aliases containing
`../` remain preserved and are checked against their canonical target bytes;
repeated needed-library entries are retained only when their identities agree.
The Java performance-data mapping and R's `base` DLL pseudo-entry are recorded
as observations and are not invented software-file dependencies.

**Unavailable runtime — required action:** before launch, an absent, changed,
forbidden or unsupported runtime produces a skip with exit contract 3 and a
specific diagnostic. Use a permitted Linux x86_64 runtime with the pinned
software and create a new verified inventory plus scientific admission; on
Windows, run Linux Java inside WSL. Do not run dependent analysis until that
admission succeeds. A change detected after launch is a failed analysis with
exit contract 2, not a successful run or a prelaunch skip.

The real installed runtime passes inspection and a second complete check.
Thirteen further Linux protocols cover an owned provider fixture, changed or
missing bytes, changed permissions, an extra file, post-inspection changes,
descriptor tampering, a wrong inventory pin, cancellation, an altered inferred
ELF SONAME and the old process-scope inventory. The fixture is retained and
restored; no provider runtime or system library was altered. These protocols
verify the internal fingerprint API and its error contracts. They do not yet
certify a packaged analysis command or scientific runtime admission.

## Evidence and remaining implementation

The [validation record](favor-join-development-validation.json) identifies all
source and proof hashes. Strict Java 17 compilation, Javadoc and 28 native tests
pass on Windows Java 21/25 and Linux Java 21. With the CSV reader and supervisor,
the focused gate now contains **251 tests per profile**: 28 join, 32 CSV,
13 supervisor, 36 VCF, 15 streaming-value, 16 GDS-admission, 26 annotation
audit/export, 22 parent-ledger, 26 runtime-fingerprint, 15 parent-workflow and
22 typed CLI/main-dispatch tests.
Nine fresh original annotation protocols use
45 R commands. Every genotype allele, phase value, sample/variant ID, coordinate,
allele key, raw annotation cell and stored scalar column is checked: 432 genotype
alleles, 216 phase values and 438 stored annotation values across the protocols.

Seven admitted protocols compare all 57 original source rows against Java on
each JVM profile, including unsafe duplicate, gap, overlap and ordering cases.
Single-shard and mixed-chromosome cases are explicitly refused. The native raw
join retains literal `NA`/empty cells. Separately observed readr/GDS behavior
converts missing character cells to empty strings while numeric/logical
missingness remains missing; that distinction and original query provenance must
survive the eventual typed integration.

The actual CSV-file reader plus native join independently matches all seven
admitted original protocols on each JVM profile. Seven further original R
annotation runs use the supervisor for 21 successful nested xsv commands and
retain identical raw annotation bytes, genotype/phase/ID values and stored scalar
columns. Only two of those seven fixtures have safe positional alignment; the
other five remain scientifically unsafe diagnostics. Missing-database and
timeout controls remain failed and no owned child remains running.

## Complete annotation-column audit and scalar export

The owned R helper first checks joined-CSV bytes, rows, columns, cells and
decoded scalar capacities in a bounded character prepass. Scientific column
inference then uses the original `read_csv` defaults. Every original column,
including `VarInfo` and `variant_vcf`, must appear in its original order. The
complete `VarInfo` sequence must equal the original GDS variant keys. Dropped,
multiplied or reordered annotation rows fail. CSV parsing problems, scalar/type
mismatches and incomplete columns cannot publish a completed audit.

For every column, the helper writes source-parsed, expected-storage and actual
stored canonical values. Logical and integer values use little-endian int32;
character values use a UTF-8 byte-length prefix with `-1` for missing values.
Doubles retain all eight IEEE bytes and a separate value/NA/NaN tag. Missing
character values project explicitly to the source writer's empty strings. Known
date/time class losses are recorded with the original attribute hash and unit
provenance. No source GDS node or table metadata is changed.

Java checks protected CSV/GDS identities, exact headers, complete counts, class
projections, missingness ledgers and every canonical file hash. It independently
compares every source/stored scalar and IEEE bit. Export reads bounded row
blocks while opening only two canonical column inputs at a time. Every row's
`VarInfo` must also match the original native VCF sequence. A failure retains
partial output and never publishes the scalar export completion marker.

A completed scalar export contains:

- `values.tsv`: quoted UTF-8 stored scalar values, with signed zero and infinity.
  Stored date/time columns contain numeric values, not restored calendar classes.
- `cell-states.tsv`: an equally shaped table distinguishing `value`, `NA`,
  `NaN` and `source-character-NA-to-empty`. Use it with the value table; empty
  annotations do not establish absence of biological effect.
- `export.json`: original class/unit/timezone provenance, protected input/audit
  identities, canonical-file references, output hashes and warnings. Its status
  is `complete-scalar-export`; `upstream_workflow_certified` is explicitly false.
- `README.txt`: prominent observed warnings and required actions. Keep the
  referenced audit/canonical files with these outputs so exact bits and original
  missingness remain available.

The latest comparison covers seven source cases at two R chunk sizes on each
JVM profile: 2,101 variant rows and 12,622 scalar values, including 12 character
missing values converted to empty strings. Cases include original and supervised
outputs, all-no-match results, quoted multiline Unicode, the special-value/date
matrix and a fresh 2,053-variant annotation run. Independent Python decoding
checks every canonical cell, missingness state and numeric export bit. Outputs
are byte-identical across Windows Java 21/25 and Linux Java 21. The matrix has
20 successful R-helper calls, including GDS checks, and 24 expected refusals for
unsafe alignment, altered values/types/columns, capacity and overwrite controls.

**Matching scalars are not workflow success — required action:** the retained
single-shard source run passes the scalar audit even though its nested xsv
concatenation failed. That is a diagnostic-only success; the native join and
supervisor still refuse the single-shard workflow. The first comparison harness
incorrectly expected that scalar audit to fail and is retained. Its corrected
classification does not change production code. The parent must require all
expected terminal command events and reject any failed or missing event, even
when every visible scalar value matches.

The focused gate now has **214 tests per profile**, including 26 annotation
audit/export, 22 parent-ledger, 26 runtime-fingerprint, 15 parent-workflow and
22 typed CLI/main-dispatch tests. Strict Java 17
compilation and Javadoc
also pass. The new scalar export remains an internal source API and is absent
from the sealed candidate JAR.

Next work is the complete upstream adapter with exact runtime admission,
immutable working copies, integration of the verified nested xsv ledger,
typed CLI and packaged/public recovery gates. This work does not change the
completed [native transcript-site workflow](TRANSCRIPT_SITE_MAPPING.md) and does
not certify the complete FAVOR/FUMA or P130 pipeline.


## Internal complete offline parent and fresh scientific health

`FavorAnnotatorWorkflow` is a Linux source API. It copies protected VCF, DBsplit
and ordered CSV shards into a new ASCII work directory, retaining original byte
hashes. The original unmodified scripts, bundled helpers and application code
are also captured and rechecked. Input paths may contain Unicode; the original
shell workflow's output/work path must use an existing canonical ASCII parent
without whitespace. Existing output directories are refused.

Every run first executes a fixed, checksummed synthetic fixture through both
variant-only and diploid modes. The same parent code requires original conversion,
native VCF/GDS identity, safe native joins, every expected terminal xsv command,
unchanged original GDS nodes and complete native scalar export. Known output
hashes, 8 rows and 48 scalar values must match in both profiles. The diploid
fixture must retain 48 genotype alleles and 24 phase values. A fresh health
marker binds those checks to the exact runtime inventory and policy; the
fingerprint alone cannot produce that marker.

The requested dataset then passes the same complete path. Original conversion
is retained as `converted.gds`; annotation receives a separate working copy.
Every raw joined CSV cell must match the independent native join, including
quotes, line breaks, Unicode, literal `NA` and generated empty no-match cells.
Native query, shard and output-row ledgers remain available. Scalar canonical
files, output tables, GDS files, configuration, command events and source/input
snapshots are rechecked before the parent publishes completion. Resource/build
labels are explicit; this does not establish biological resource provenance or
perform liftover, normalization or annotation-score computation.

Two actual parent API controls skip before launching health or analysis when
pinned source scripts are absent or the old host inventory is supplied. Both
return exit contract 3 and retain prominent required recovery text. Public CLI
exit mapping is a separate pending gate.

All 15 actual R processes in the first successful complete parent run exit zero
and have confirmed terminal owned descendants. The two fresh health profiles
and the requested diploid analysis contain 24 total variant rows and 144 scalar
values, with 9 successful real nested xsv commands. Every scalar table matches
the frozen original expectations; complete GDS checks preserve 96 genotype
alleles and 48 phase values across the two diploid jobs. Process timeout,
workspace-byte and resident-memory limits use the existing monitored runner.
Its 4,096-member workspace bound is also enforced; exceeded capacities fail
rather than truncate or silently partition inputs.

**A system update invalidated a real run — required action:** Ubuntu's existing
unattended-upgrades service replaced host libraries during the first parent
attempt. Both health value profiles passed, but the subsequent fingerprint
recheck detected changed `libz` bytes and failed with exit contract 2 before
the requested analysis started. The failed bundle, old inventory4 and system
update logs remain retained. No global update was initiated, disabled or
reverted by this work. The new inventory5 records the updated host and passes
fresh health, complete analysis and final fingerprint checks. After any future
host update, create a new inventory and repeat admission; do not change the old
pin or treat already produced scalar values as completion.

The internal parent writes `session.json` and prominent `WARNINGS.md`. Only
`complete-audited-offline-analysis` with exit code 0 admits the whole requested
run. Missing, changed or unsupported prelaunch runtimes skip with exit contract
3; invalid inputs, execution, capacity or post-launch integrity failures fail
with exit contract 2 and retain diagnostics. An inner scalar export or successful
R exit alone never admits the parent. Typed public CLI classification and its
failure protocols are still pending; this source API is not in the sealed JAR.

Next work is negative end-to-end coverage, typed main-command integration,
packaging, public examples and catalog-role validation. The unique-tool count
remains 51 new integrations until those remaining FAVOR gates pass.


## Typed command and executable validation

The current project main command is:

```sh
java -jar build/cli/omicsmatic-0.3.7.jar favor-annotate \
  --request request.json --request-sha256 REQUEST_SHA256
```

Build that local development JAR with `./gradlew executableJar` (`gradlew.bat`
on Windows). The prior transcript-site candidate and published historical JARs
do not contain this new command. The separately retained, tested current ROOT
candidate is `build/literature-review/favor-runtime/annotator-root-candidate-2/omicsmatic-0.3.7.jar`,
SHA-256 `191e148134b5ad84d8a840b61b851ce21381891fef9db39bd73a876aa18fc062`.
Its FAVOR workflow passes; this does not certify all other concurrent ROOT
features or constitute a release.

`request.json` is a UTF-8 object with this structure. Replace every path and
`*_SHA256` placeholder with the protected original file's actual SHA-256; the
runtime inventory must describe the same current Linux host. The selected
chromosome's shard files remain in original DBsplit row order.

```json
{
  "schema_version": 1,
  "runtime": {
    "inventory": {"path": "runtime/inventory.json", "sha256": "INVENTORY_SHA256"},
    "source_root": "runtime/FAVORannotator"
  },
  "vcf": {"path": "input/study.vcf.gz", "sha256": "VCF_SHA256"},
  "build": "GRCh38",
  "chromosome": 22,
  "mode": "DIPLOID_GT",
  "chromosome_policy": "EXACT",
  "blank_records": "IGNORE",
  "reference": {
    "id": "declared-FAVOR-resource",
    "build": "GRCh38",
    "split": {"path": "reference/DBsplit.csv", "sha256": "SPLIT_SHA256"},
    "shards": [
      {"path": "reference/chr22_1.csv", "sha256": "SHARD1_SHA256"},
      {"path": "reference/chr22_2.csv", "sha256": "SHARD2_SHA256"}
    ]
  },
  "output": "new-favor-output",
  "limits": {
    "timeout_seconds": 3600,
    "xsv_timeout_ms": 600000,
    "workspace_bytes": 17179869184,
    "upstream_rss_bytes": 4294967296,
    "chunk_rows": 1024
  }
}
```

All paths resolve relative to the request file. The request and its hash are
checked before processing; a byte-identical copy is retained and rechecked
before execution and completion. Unknown fields, duplicate JSON keys, implicit
policies, altered hashes, unsupported intervals and invalid capacities fail.
`VARIANTS_ONLY` requires an actual eight-column VCF. `DIPLOID_GT` requires full
explicit diploid GT fields, including `./.` for a wholly missing call. See the
genotype warning above; no padding or phase repair is performed. `SOURCE_IGNORE_CHR`
is the separate explicit source-prefix policy. `blank_records` is `ERROR` or
`IGNORE`; it does not change quoted empty cells or literal `NA` text.

Additional bounded capacity groups are `runtime`, `vcf`, `csv`, `join`, `gds`
and `scalar`. Omitted fields retain defaults; `session.json` records every
effective typed capacity. Defaults admit at most 1 GiB encoded/4 GiB decoded
VCF, one million query variants, 100,000 samples, 100 million genotype calls,
1 GiB per CSV file, 1,024 annotation columns, 16 GiB total workspace and 4 GiB
observed upstream process-tree RSS. The software copy counts toward workspace
bytes. The monitored 4,096-member workspace bound also applies. These are
capacities, not promises that a host has enough available memory.

**Runtime preparation remains required:** this command never installs software.
Use a permitted pinned R 4.4.3/private package environment, Linux Java 21 and
xsv, the unmodified source checkout and a newly verified host inventory. The
working installation and complete intake scripts are retained in the development
bundle. The repository preparation helper and independently executed public
example now pass; follow FAVOR_WORKFLOW.md. Do not use a different host's or pre-update inventory.
On Windows, run the actual analysis using Linux Java inside WSL. Native Windows
Java returns `SKIP3` with recovery instructions before starting analysis.

## Failure, cancellation and process ownership checks

Native input preparation and safe join admission now precede R health execution.
Eleven actual main-command protocols verify five unsafe joins (query order,
shard order, database duplication, shard gaps and overlaps), a wrong VCF hash,
single-dot GT refusal, missing pinned source, an obsolete host inventory, an
actual resident-memory limit and an actual process timeout. The seven scientific
input failures return `FAIL2` without starting health or R. Missing source and
obsolete runtime return `SKIP3`. Resource/timeout failures return `FAIL2` with
confirmed terminal owned children and retained stdout/stderr.

Both Windows Java 21/25 main entries pass help, unsupported-runtime `SKIP3`
and invalid request-hash `FAIL2` checks. Twenty-two new portable tests cover the
request schema, all capacity groups, build and mode policies, UTF-8 paths,
protected carrier, main dispatch and failure classification. All 251 focused
tests per JVM profile, strict Java 17 compilation and Javadoc pass. The complete
current ROOT main-source compilation and executable-JAR build also pass.

Sessions are atomically updated before each process, during monitored execution
and after termination. A shutdown guard admits descendants by the unique owned
working directory and records their birth identity; it never targets historical
PIDs alone. A real Linux JVM `SIGTERM` test stops its owned Bash child and sleep
grandchild while leaving a same-parent sibling in another directory untouched.
The test then cleans up that sibling separately. Normal successful runs remove
their shutdown hooks and produce no interruption ledgers.

The source-built command and the standalone current ROOT executable JAR each
run both fresh health profiles plus the requested diploid analysis successfully:
15 actual R processes, 9 nested xsv commands, 24 total variant rows and 144
scalar values per run. Every scalar table matches the frozen original values
and states; the request uses a Unicode input path inside the protected JSON.
The original converted GDS, annotated working GDS, native ledgers, canonical
scalars, configuration, software and inputs are rechecked. Fixture Git attributes
now disable newline conversion so their byte pins survive Windows checkouts.

The count is now 52 new validated unique integrations. The repository preparation
helper passes 22 tests and the example generator passes eight tests on each of
Windows and Linux. An actual Linux preparation verifies 18,763 software entries,
64 host files, 84 loader objects and 154 pinned Conda artifacts. Its fresh inventory
and the repository-generated example run successfully with the standalone JAR:
15 R processes, nine nested xsv commands, both scientific health modes and eight
requested variants with 48 scalar values. Value and cell-state tables match the
frozen golden hashes.

P130 describes FUMA and FAVOR jointly and names MAGMA separately. The explicit
role boundaries are recorded in favor-role-coverage.json. This validates the
current pinned offline annotation workflow and audited scalar export; it does
not identify P130's historical FAVOR revision or reproduce every jointly described
variant, gene and enrichment step. Broader FAVOR and paper-wide semantic review
remain open, as do 267 missing tool integrations.
