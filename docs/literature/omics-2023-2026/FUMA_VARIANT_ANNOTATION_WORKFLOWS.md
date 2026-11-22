# FUMA supplied variant annotation postprocessing

`fuma-tools --method annotate-variants` supplies a native Java implementation
of the postprocessing in pinned
[SNPannot.R](https://github.com/vufuma/FUMA-webapp/blob/bdfbf06b5dd24037ec3c7497d773fad3c2656150/scripts/snpannot/SNPannot.R).
It joins supplied annotations, finds nearest gene bodies, summarizes chromatin
states and selected BED overlaps, recounts LD rows, and computes functional
category enrichment. Start with the
[runnable synthetic example](../../../examples/fuma-variant-annotation-workflow/README.md).
Exact development bytes and evidence are in
[fuma-variant-annotation-validation.json](fuma-variant-annotation-validation.json).

**Supply resources explicitly.** The command requires already generated
consequences, CADD/RegulomeDB/state scores, gene coordinates, LD pairs and
reference counts. It does not execute ANNOVAR, acquire its databases, generate
scores, estimate LD or reconstruct the full hosted FUMA workflow. Source
`annovar.py` invokes an independent annotation program; source `getLD.getAnnot`
reads precomputed caches and can filter candidates without cache coverage.
The admitted candidate population here is supplied explicitly. An assembly or
resource ID records a declaration; it does not prove compatibility.

This is a local development milestone. It has no canonical release, deployment,
historical human-resource, whole-paper or full FUMA equivalence claim. FUMA
remains `missing` at tool level and the whole goal remains active.

## Command and required input

```text
fuma-tools --method annotate-variants --annotation-options-file OPTIONS.json
```

All carrier JSON values are strings. Paths resolve relative to that carrier;
direct CLI options override carrier values. Unknown/recursive/nonstring options
fail. The carrier and inputs are hashed and rechecked during execution. Unicode
input/output parents work through an ASCII carrier path on Windows. The output
directory must be new. Native postprocessing requires Java 17+; it needs no
external annotation runtime.

| Option | Required declaration |
| --- | --- |
| `build` | Simple assembly label, 1–80 letters/digits/underscore/dot/hyphen |
| `annotation-id` | Supplied gene coordinate resource identity |
| `consequence-id`, `cadd-id`, `rdb-id`, `state-id`, `reference-id` | Supplied resource identities, printable and at most 512 characters |
| `snps`, `genes`, `annotations`, `consequences`, `ld`, `independent`, `reference-counts` | Paths to UTF-8 TSV inputs below |
| `states` | Ordered comma-separated distinct state column names |
| `duplicates` | `error` or explicitly `source-first`, for gene/score joins |
| `single-state` | `error` or explicitly `retain-matrix` |
| `bed-manifest` | `none` or a track manifest path |
| `out` | New writable output directory |

Input tables have a header and consistent tab-separated widths. Required
columns are case-sensitive. No implicit gene ID, contig or allele normalization
is performed. Finite decimal/scientific numeric values are accepted, with exact
`NA` only where allowed. Empty numeric fields, NaN, infinity and hexadecimal
numeric syntax fail. Coordinates must fit positive signed 32-bit positions.

| Table | Columns |
| --- | --- |
| `snps` | Needs `uniqID`, `chr`, `pos`, `gwasP`; other columns retained |
| `genes` | Needs `ensembl_gene_id`, `external_gene_name`, `chromosome_name`, `start_position`, `end_position`; all raw columns snapshotted |
| `annotations` | Exactly `uniqID`, `CADD`, `RDB`, followed by the declared state columns in order |
| `consequences` | Needs `uniqID`, `gene`, `annot`; other columns retained |
| `ld` | Needs `SNP1`, `SNP2`; all original columns snapshotted |
| `independent` | Needs `uniqID`; other columns retained; `nSNPs`/`nGWASSNPs` recomputed |
| `reference-counts` | Exactly `annot`, `count`, `prop`; positive total reference population required |

Chromatin states are integers 1–15 or `NA`. RDB is a raw text label, including
`1a` and similar source labels; blank summary RDB becomes `NA`. It is never
silently coerced into the numeric-only contract of a separate mapping method.
With `source-first`, repeated gene/score join keys use the first resource row;
nearest-gene search still uses **all gene rows**, including duplicate rows.

The BED manifest has exactly `resource_id`, `path`, `format`, with format `bed`
or `bed-gzip`. Track paths resolve relative to the manifest. Files are
unheadered BED3+; only their first three fields define intervals. Format is
explicit, independent of filename. Source-style output track names apply the
source `.bed.gz` regular expression and slash replacement; collisions fail.
Gzip reads verify integrity and obey expanded-byte limits.

## Scientific semantics and boundaries

SNP/gene coordinates are one-based closed; BED coordinates are zero-based
half-open. Contig labels match exactly, except literal gene chromosome `X`
becomes `23`, as in the source. There is no `chr` stripping or other aliasing.
Nearest distance is unstranded distance to a **gene body**, with all minimum
ties in original subject-row order. Overlap and direct adjacency both have
distance zero under the tested original GenomicRanges behavior. This is not a
nearest-TSS analysis.

Gene symbols use the first source match; a missing symbol falls back to the
gene ID while an empty symbol stays empty. Function summaries preserve the
first occurrence of each distinct label. Enrichment deduplicates
**variant/category pairs**; a variant assigned to multiple categories
contributes separately to each category. Reference labels without a tested
category are omitted with a warning. Supplied reference proportions are used
as supplied, not recomputed from counts. Invalid negative contingency cells
fail. No extra reference categories or multiple-test correction are inferred.

State minima propagate any `NA`; modes exclude `NA`, resolving numeric ties
toward the lower state. An all-missing mode fails, preserving the source's
serialization failure rather than inventing a value. The source one-state
dimension failure is retained by default. Explicit `single-state=retain-matrix`
is a documented native extension.

LD recounts count **rows**, including repeated pairs, without applying another
r-squared filter. Absent aggregate groups remain `NA`. Targets outside the
candidate population do not count as GWAS candidate targets. Empty LD or an
empty GWAS aggregate fails as in the source. Review `annotation-disposition.tsv`
and the consequence ledger for resource coverage and omitted categories.

Two-sided Fisher tests use the central hypergeometric distribution and the
tested R minimum-likelihood inclusion cutoff of `1e-7`. Direct-double
underflow is retained as zero, with a warning. Undefined/infinite enrichment is
retained and flagged. These values require interpretation even after exit 0.

**A measured numerical source difference remains visible.** A large synthetic
complementary-table pair printed original R P values
`0.0487720021576437` and `0.0487720021575545`. Native Java returned
`0.04877200215764386` for both; independent 100-digit product arithmetic gives
`0.048772002157643783128551449529007581871…`. Native relative error there is
about `1.58e-15`; source complement asymmetry is about `1.83e-12` relative.
Original outputs are retained unchanged. Comparisons use `2e-12` relative
tolerance, **no absolute allowance**, and require every source zero to remain
exactly zero. This is measured numerical agreement, not bit identity.

The source accepts a zero-background population and unknown consequence IDs
that produce missing coordinates. Native admission rejects both explicitly.
The source can leave `annot.txt` untouched when no BED tracks hit; native
`annotations.tsv` always contains the prepared first-match candidate view.
Exact raw source inputs are separately retained, so this declared table
difference does not erase duplicate or outside-candidate resource rows.

## Outputs, parent stage and recovery

Results include `snps.tsv`, `annov.tsv`, `independent.tsv`,
`annotation-statistics.tsv`, `reference-population.tsv`, `gene-resource.tsv`,
`annotations.tsv`, `nearest-gene-evidence.tsv`, `consequence-ledger.tsv`,
`annotation-disposition.tsv`, `bed-tracks.tsv` and `bed-hits.tsv`.
`annot.bed` appears only when hits exist. `source-inputs/iNNNN.raw` retains
exact original bytes. `resource-ledger.tsv` binds each role/path/snapshot to
its byte count and SHA-256. Session/manifest/status/warnings record choices,
admitted populations, retained bytes and limitations.

Typed `fuma-tools --method workflow` accepts an optional `variant_annotation`
object with the same string options; the method is fixed and build is inherited
from the parent. See the runnable `parent.json`. It is an independent stage:
no automatic raw-RDB conversion, locus assembly or mapping bridge occurs.
Other independent stages can finish after an annotation failure or external
MAGMA skip, while the parent retains its failed/partially-skipped status.

**Exit 2 means failure.** Read `WARNINGS.md`, `session.json`,
`analysis-status.json` and the named input/phase cause. Failed tables/snapshots
are quarantined under `partial/` for diagnosis. Correct the data, population,
capacity or runtime cause and rerun to a **new** directory. Existing outputs
are preserved. A missing supplied resource is invalid input, not a software
installation request.

**Unavailable or forbidden required software means skip the affected analysis.**
The public verifier returns exit 3 with the missing Java cause and Required
action: provide Java 17+ on a permitted supported host, then rerun to a new
directory. Analysis never installs software. For independent upstream resource
generation, supply valid generated inputs or install/use the required software
only where permitted; otherwise retain that generation analysis as skipped.
The postprocessing command never substitutes invented biological annotations.

## Default capacity limits

| Option | Default |
| --- | ---: |
| `max-bytes` | 268,435,456 raw bytes per input |
| `max-expanded-bytes` | 268,435,456 expanded bytes per BED |
| `max-rows` | 1,000,000 per input family, combined for BED rows |
| `max-states`, `max-tracks` | 256, 64 |
| `max-line-bytes` | 1,048,576 |
| `max-cells` | 50,000,000 across retained input cells |
| `max-snapshot-bytes` | 1,073,741,824 total raw snapshots |
| `max-output-bytes` | 1,073,741,824 scientific files plus snapshots |
| `max-evidence`, `max-operations` | 2,000,000; 100,000,000 |
| `max-fisher-states` | 5,000,000 cumulative hypergeometric support |
| `max-reference-population` | 100,000,000 |
| `timeout` (seconds) | 300 |

Limits reject whole populations rather than truncate them. Byte accounting
includes scientific tables, ledgers and raw snapshots, with overwritten ledger
bytes accounted for. Diagnostic status/warning metadata remain writable after
the scientific byte limit. Periodic operation/time checks are admission and
work bounds, not operating-system memory/CPU quotas; provision sufficient heap.

## Evidence

The combined strict Java 17 gate passed 1,158 tests in 87 suites, including 55
new API/CLI tests, with clean Javadoc. The exact JAR is tested separately on
Windows Java 21/25. Twenty-nine unmodified full original R protocols include
22 successes and seven retained source failures. Across Windows Java 21/25
and Linux Java 21, native comparison covers 20 complete source populations,
six matching failure rejections, one explicit singleton extension and two
stronger admission rejections. Per profile the strict table audit covers
90,741 rows, 725,043 fields, 392,999 numeric fields and 90,448 exact zeros.

Ninety-three actual main-entry commands test the same source populations and
independent parent/Unicode cases. Nine packaged public recipe runs cover all
three JVM profiles. These tests use synthetic inputs and pinned original
R4.2.3/data.table1.15.4/GenomicRanges1.50.0 output; they do not reproduce a
paper's historical biological resources. Read the validation JSON for exact
hashes, retained failures and the scope of each proof. Cell Ranger remains
excluded; broader FUMA, 268 integrations and paper-wide review remain open.
