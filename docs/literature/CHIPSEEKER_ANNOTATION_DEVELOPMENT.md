# Original ChIPseeker annotation: development

The project now implements original `annotatePeak` execution and a separate
native Java nearest-feature audit through `chipseeker-tools`. Catalog admission
and its scope are recorded in `omics-2023-2026/chipseeker-catalog-validation.json`;
workflow evidence is in `chipseeker-annotation-development/VALIDATION.json`.
This development JAR
does not replace the canonical/released executable:

```text
build/literature-review/chipseeker-runtime/root-build-1/omicsmatic-0.3.7-chipseeker-root-development.jar
```

Start with the [executed synthetic examples](../../examples/chipseeker/README.md).
`chipseeker-tools NEAREST` runs the native prepared-range method without R.
`chipseeker-tools ANNOTATE` runs the exact requested original annotation edition
on caller-supplied peak coordinates and a TxDb SQLite database. The two modes
have distinct typed request schemas; each supports `--help`.

The four source profiles are ChIPseeker 1.22.1, 1.26.2, 1.34.1 and 1.48.1 from
their captured official Bioconductor archives. Source files retain their
original notices and Artistic-2.0 license; full license text and archive
identities are packaged. The [official package page](https://bioconductor.org/packages/ChIPseeker/)
describes the upstream package. No development-branch implementation or silently
substituted Windows binary is used.

All profiles use the measured current dependency reference: R 4.6.1,
AnnotationDbi 1.74.0, BiocGenerics 0.58.1, GenomicFeatures 1.64.0,
GenomicRanges 1.64.0, GenomeInfoDb 1.48.0, IRanges 2.46.0, S4Vectors 0.50.3,
Seqinfo 1.2.0, jsonlite 2.0.0, dplyr 1.2.1, magrittr 2.0.5 and yulab.utils
0.2.5. Upstream imports must also load. The worker verifies both package
DESCRIPTION and loaded namespace versions, then exact bodies/formal arguments
of at least 17 original annotation/conversion functions. This is not a
historical dependency environment or native-DLL source authentication.

On Windows the worker selects `English_United States.utf8` for `LC_CTYPE`; on
Linux it requires `C.UTF-8`. Numeric locale and collation remain `C`. Before and
after locale values are recorded. An isolated retained probe showed that
RSQLite failed to open a relative database path under a Unicode working
directory with C character encoding, and loaded the unchanged database after
the UTF-8 change. Missing UTF-8 support produces SKIP3. Filenames and IDs are
not renamed to avoid the issue. Original ASCII controls remain identical after
this transport change.

Requests require `schema_version: 1`, `mode: "ANNOTATE"`, an explicit `version`,
`peaks`, `txdb`, `reference_build`, `coordinates: "one-based-inclusive"`,
`rscript`, nonempty `r_libraries` and new `out`. Paths resolve against the UTF-8
request file. An optional `txdb_sha256` binds the caller's intended database.
The reference label remains a declaration: compare the captured database hash,
metadata and sequence information to the intended assembly and annotation.
The adapter never downloads a default genome or constructs a replacement TxDb.

Peak TSV headers are literal `peak_id`, `contig`, `start`, `end`, optionally
followed by `strand`. Strands are `+`, `-` or `*`; omitted strands are explicitly
prepared as `*`. Coordinates are positive 32-bit one-based inclusive integers.
No BED conversion, contig alias conversion, ID repair or deduplication occurs.
Duplicates stay ordered and carry an input ordinal.

An optional `analyses` list names 1–128 configurations. Omission creates one
`annotation` configuration. Each records its effective original settings:

- `tss_region`: two ordered integers, default `[-3000,3000]`.
- `level`: `transcript` or `gene`, default `transcript`.
- `annotation_priority`: original names/order, including explicit subsets and
  duplicates; the default is Promoter, 5UTR, 3UTR, Exon, Intron, Downstream,
  Intergenic.
- `downstream_distance`: default 3,000 bp. Historical editions retain their
  own source handling; they are not changed to modern behavior.
- `add_flank_gene_info`, `flank_distance`, `same_strand`, `ignore_overlap`,
  `ignore_upstream`, `ignore_downstream` and `overlap` (`TSS` or `all`).
- Optional `select_annotation_prefixes`: exact caller-declared label prefixes,
  applied after annotation of the complete population. Missing annotations are
  not literal matching text. No enhancer criterion is inferred.

The original annotation, detail flags, distributions, objects and feature order
remain upstream results. Java checks query identities, coordinates, retained
order and original annotation/nearest consistency. For the native method's
supported domain it independently checks each nearest query ordinal, feature
ordinal and distance. Stranded populations, integer-overflow boundaries,
reserved/wildcard domains and native capacities can skip that independent audit
with an explicit reason. No partial native result is admitted; an upstream
result is not relabelled as native verification.

**Read warnings and admission status before using a result.** Raw upstream
`geneChr` and `geneStrand` can contain numeric codes; the separate query/feature
ledger keeps literal contigs, strands and original feature names. Original RDS
outputs retain typed source objects; JSON and cell-kind metadata distinguish
`NA`, `NaN`, infinities and literal text. RDS is output only, never a user input.
Tables retain original R quoting; the Java ledger uses quoted TSV fields where
needed. Dropped queries and dummy boundaries are explicit and do not establish
biological absence.

**An annotation batch is atomic.** Any original error or failed Java check
admits no result from the entire batch. Completed R objects from another member
remain diagnostic evidence. They are not copied to an admitted `results/`
directory. Existing output directories are refused unchanged. Timeouts/output
limits terminate owned worker trees and retain process receipts. Logical
capacities and periodic monitoring are not OS memory/disk quotas.

**SKIP3 — dependent analysis skipped:** provide exact R/package/source versions,
declared libraries and the required UTF-8 locale only where permitted. If
installation is forbidden or dependencies remain unavailable, retain the skip.
Runtime annotation installs nothing. `setup_chipseeker_reference.py` and the
edition installer are separately authorized private reference-preparation
helpers; read their result/warning files and retained dependency download
records. Future repository versions may require additional exact package
provisioning rather than installing the newest available version.

**FAIL2 — no result admitted:** preserve the bundle and read `WARNINGS.md`,
`analysis-status.json`, `worker/worker-report.json`, each
`worker/cases/ID/source-status.json`, stdout/stderr and process receipts.
Correct the named input, original error, identity or capacity cause and retry
into new paths. Do not clip coordinates, drop queries, change directions or
substitute a modern edition to force success. There is no portable VERIFY mode.

The current source reference has 224 independent original annotation controls:
175 returned and 49 raised original errors. These are synthetic controls, not
paper or biological reproductions. The 200-case core spans four editions; on
each of Windows Java 21 and Java 25, the actual adapter matches all 152 returned
annotation/detail/distribution/nearest outputs and retains all 48 original
errors as failed batches. Each JVM also checks 10,488 retained nearest triples
in Java. A further Windows Java 21 matrix matches 23 returned strand, priority,
one-query/intergenic and overlap controls and one additional original error.
The mixed success/error batch rejects every member as intended.

**Original failures are retained.** Twelve core configurations per edition raise
`67 elements in value to replace 69 elements`. The ChIPseeker 1.34.1
subset-priority control additionally raises `object 'lbs' not found`; the other
editions return their own source results. Old and new downstream annotation
semantics differ, and 1.22.1/1.26.2 also differ for exon-first priorities. Exact
edition choices are necessary. The native nearest function is shared by these
four source editions; their complete classifiers are not interchangeable.

All 16 dependency/recovery controls pass, including unavailable runtimes and
libraries, wrong editions, original source-body/formal tampering with unchanged
version metadata, timeout/output cleanup, input hashes/capacities, existing
output protection, Unicode paths/duplicate/quoted IDs and literal selection.
The final root example executes a genuine annotation and native examples on
Windows/Linux Java; help, missing dependency and existing-output paths pass.
Every scientific/resource entry of the selected annotation backend is preserved
by the root build; only root/facade dispatch changes. Legacy MEME catalog and
MAFFT evidence checks still pass with the exact root-source inverse.

Retained records are under `build/literature-review/chipseeker-runtime/`:
`annotation-build-3`, `annotation-matrix-3`, `annotation-recovery-4`,
`annotation-extended-original-1`, `annotation-extended-matrix-3`,
`unicode-sqlite-probe-1`, `root-build-1` and `root-example-1`. Earlier source,
setup, native and annotation attempts remain preserved. In particular, the
first Unicode recovery failed before the UTF-8 transport fix; a later recovery
harness stopped on an incorrectly spaced negative-control fixture pattern.
Those attempts are not passing evidence. Catalog/publication records separately
state which paper-role, UI and publication gates were completed. These workflow
controls alone do not establish completion of the full 250-paper goal.
