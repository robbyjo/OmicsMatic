# FUMA supplied MAGMA gene/set reports

`fuma-tools --method magma-report` performs native Java postprocessing from
pinned
[magma_gene.R](https://github.com/vufuma/FUMA-webapp/blob/bdfbf06b5dd24037ec3c7497d773fad3c2656150/scripts/magma/magma_gene.R).
It joins exact gene symbols, corrects the complete supplied set family and
selects the source's displayed rows. It requires supplied results and does
not fit or certify MAGMA models. Start with the
[runnable synthetic example](../../../examples/fuma-magma-report-workflow/README.md).
Exact development bytes and evidence are in
[fuma-magma-report-validation.json](fuma-magma-report-validation.json).

**Pbon means Holm.** The source calls `p.adjust(P)` without specifying a method,
whose default is [Holm](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html).
The field name does not make it Bonferroni. This source report does not infer
a paper's BH-FDR, per-dataset, pathway or cross-trait correction policy. Declare
the intended scientific analysis and retain its complete tested family.

This is a local development capability. Historical MAGMA/FUMA versions,
biological resources, paper results, full hosted FUMA, the entire ROOT checkout,
canonical release and deployment are not certified. Broader FUMA remains
`missing`, 268 integrations remain open and the whole goal is active.

## Command and inputs

```text
fuma-tools --method magma-report --magma-report-options-file OPTIONS.json
```

All JSON carrier values are strings. Carrier paths resolve relative to the
carrier file; direct options override carrier values. Unknown, recursive and
nonstring options fail. On Windows use an ASCII carrier path with Unicode
resource/output paths inside; Unicode parents are supported. The output must
be new. Java 17+ is required; no external runtime is used for postprocessing.

| Option | Required value |
| --- | --- |
| `build` | Explicit simple assembly label: 1–80 letters/digits/underscore/dot/hyphen |
| `annotation-id`, `analysis-id` | Printable resource/model-result declarations, each at most 512 characters |
| `gene-results` | UTF-8 TSV with exact `GENE` column; other columns retained |
| `symbols` | UTF-8 TSV with `ensembl_gene_id`, `external_gene_name`; other raw columns snapshotted |
| `gsa`, `sets` | Each a file path or literal `none`; at least one file must be declared |
| `sets-format` | `tsv` or `magma-skip3`, for the selected set file |
| `duplicates` | `error` or explicit `source-first` for repeated symbol-resource keys |
| `missing-p` | `error` or explicit `source-na` |
| `placeholders` | `error` or explicit `source-rows` |
| `out` | New writable output directory |

The selected set table must contain `FULL_NAME`, `NGENES`, `BETA`, `BETA_STD`,
`SE`, `P`; extra fields are retained in `sets-all.tsv`. `magma-skip3` requires
exactly three bounded initial `#` comment lines followed by the TSV header.
It deliberately rejects uncommented leading data rather than silently dropping
three rows. `tsv` starts with the header. These are explicit supplied TSV
contracts; arbitrary whitespace-formatted files must be prepared separately.

Declared `gsa` takes precedence over `sets`. An unused fallback is unparsed and
not snapshotted; the selected source and ignored fallback are recorded. Unlike
the original's file-existence switch, a missing **explicitly declared** GSA
input fails. To use fallback alone, declare `gsa=none`. Both `none` fails.
Review the selected file hash before interpretation.

Tables require distinct nonempty headers, consistent tab-separated widths and
valid UTF-8 without control characters. P values must be finite decimal or
scientific values in `[0,1]`, or exact `NA` with the requested missing policy.
Blank P, NaN, infinity, hexadecimal and out-of-range P fail. Other supplied fit
fields are copied, not re-estimated or statistically certified by this report.
An assembly/model ID records a declaration, not proof of resource compatibility.

## Source joins, correction and report selection

Gene rows remain in their supplied order, including repeated IDs. The first
matching annotation row supplies the symbol. Unmatched IDs and missing `NA`
symbols remain `NA`; an empty symbol stays empty. A first missing symbol is
never replaced by a later duplicate. `gene-joins.tsv` distinguishes these cases
and records one-based input/resource rows. There is no ID normalization,
alternate-symbol selection or fallback to the gene ID. Existing `SYMBOL`
values are overwritten in place; original bytes remain snapshotted.

The complete finite P family is Holm-adjusted before display filtering.
In the tested R4.2.3 function, lazy default `n=length(p)` is evaluated after
missing-P removal; the family size is the number of nonmissing P values.
Missing rows remain in `sets-all.tsv` and `full-tests.tsv` with missing adjusted
P. Existing `Pbon` values are overwritten; raw source bytes retain them.

The display sorts by P ascending, placing missing P last, and preserves input
order within ties. It counts adjusted P **strictly below 0.05**:

1. Fewer than ten significant sets: display the first ten sorted rows,
   including nonsignificant tests. If fewer than ten source rows exist, the
   original adds all-NA padding rows.
2. At least ten significant sets: display all significant rows. The original
   logical subscript also creates an all-NA report row for each missing adjusted
   P; those rows are retained only under explicit `source-rows` policy.

**Placeholders are not tests.** `display-rows.tsv` identifies every source row,
top-ten padding row and missing logical-index row. Source padding beyond the
population has source row zero. A missing logical index records the originating
input row while its displayed fields are all `NA`. `sets-all.tsv` and
`full-tests.tsv` retain the complete population independently of this report.

All-missing P can produce an explicitly requested report with **zero finite
tests and no statistical evidence**. Its warning requires valid original model
results or leaving interpretation unperformed. Supplied zero P, including
numeric underflow/rounding, is retained and flagged; it is not an exact
probability claim. Nothing imputes missing fit values.

**Stronger native admission is explicit.** The original can accept negative
or greater-than-one P, or an empty set population that yields ten all-NA rows.
Native admission rejects all three with exit 2. Original accepted outputs are
retained as evidence; native behavior is not described as full source identity.
Two original missing-column failures are also retained and rejected.

Native result tables retain supplied raw field formatting, while R serializes
numeric columns in its own format. Complete numerical comparisons use `1e-14`
relative tolerance, no absolute allowance, exact zero equality, identical
row/field populations and exact text/NA equality. This is measured agreement,
not byte identity or proof of historical paper equivalence.

## Outputs and recovery

| Output | Content |
| --- | --- |
| `genes.tsv` | Complete supplied gene rows with first symbols |
| `sets-all.tsv` | All source set rows in input order plus recomputed `Pbon` |
| `full-tests.tsv` | Every source row's P, Holm P, strict significance and family size |
| `gene-joins.tsv` | Exact input/resource row and missing/empty/unmatched status |
| `sets-top.tsv` | The selected seven-column source-style report |
| `display-rows.tsv` | Provenance and placeholder status for every display row |
| `resource-ledger.tsv`, `source-inputs/iNNNN.raw` | Selected raw inputs/carrier, SHA-256, byte counts and exact snapshots |
| `session.json`, `analysis-status.json`, `manifest.yaml`, `WARNINGS.md` | Choices, populations, source identities, status and required actions |

Sources and raw snapshots are rechecked before/after result publication. The
resource ledger records every consumed role, even when two roles share one
source file. An unused declared fallback is not consumed.

Typed `fuma-tools --method workflow` accepts an independent `magma_report`
object with the same string options; the method is fixed and build inherited.
See `parent.json`. It does not silently connect generated MAGMA fits or infer
their correction family. Other independent stages can complete after a failure
or unavailable external runtime while the parent retains failed/partially
skipped status. Existing MAGMA fitting workflows are documented in
[FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md).

**Exit 0 still requires review.** Read `WARNINGS.md` and every full-test,
gene-join and display-row ledger. Check the original model/resource identities
and family before using a reported value.

**Exit 2 means failure.** Inspect the named input, preparation, publication or
capacity cause in warnings/session/status and retained logs. Failed command
tables/snapshots are quarantined under `partial/` as diagnostics. Correct the
cause and rerun to a **new** output. Existing outputs are preserved. Errors
before output creation are reported on stderr with Required action.

**Unavailable or forbidden required software means skip the affected analysis.**
The public verifier returns exit 3 for missing, inaccessible or too-old Java,
with the cause and Required action: supply Java 17+ on a permitted supported
host, then rerun to a new directory. No R or MAGMA installation is needed for
supplied report fixtures. If upstream MAGMA fitting software cannot be
installed/used where permitted, skip that affected fitting analysis and supply
valid permitted results. Analysis never installs software or invents fits.

## Default capacities

| Option | Default |
| --- | ---: |
| `max-bytes` | 268,435,456 per consumed file |
| `max-rows` | 1,000,000 per input table |
| `max-line-bytes` | 1,048,576 |
| `max-cells` | 50,000,000 across retained input headers/data |
| `max-snapshot-bytes` | 1,073,741,824 total raw snapshots |
| `max-output-bytes` | 1,073,741,824 scientific TSV files plus raw snapshots |
| `max-output-rows` | 10,000,000 across six result tables/ledgers |
| `max-operations` | 100,000,000 native interpretation operations |
| `timeout` | 300 seconds |

Limits reject complete populations rather than truncate them. The scientific
byte budget includes `resource-ledger.tsv`, accounting for replaced ledger
bytes correctly. Diagnostic metadata remain writable after exhaustion.
Periodic work/time checks and population bounds are not OS memory/CPU quotas;
provision sufficient JVM heap for all supplied rows.

## Evidence and remaining scope

The combined strict Java 17 gate and clean Javadoc passed 1,202 tests in 89
suites, including 44 new API/CLI tests. All 1,202 tests also passed against the
exact JAR on Windows Java 21/25, with production classes loaded from that JAR.
Twenty-one unchanged full original R protocols include 19 successes and two
retained source failures. Sixteen complete original populations per JVM match
across Windows Java 21/25 and Linux Java 21, with three explicit stronger native
admission rejections. The complete audit per profile covers 71,049 rows,
481,407 fields, 403,259 numeric fields and 44 exact zeros. Large cases cover
16,992/15,485 sets and 19,034 gene rows, including missing values and cases near
the Holm 0.05 threshold. Maximum observed relative difference is about `1.58e-15`.

Sixty-nine private actual main-entry checks and 18 exact packaged command
comparisons passed. Three initial harness calls used a wrong workflow option;
those attempts are retained and corrected parent invocations passed without
repeating the unaffected 66 commands. The old SNP example README option was
also corrected and tested against its original JAR. Fifteen public recipe runs
cover the five new examples on all three JVM profiles, with separate missing
Java skip-3 and altered-archive failure controls.

These are synthetic current-source report comparisons, not original model fits
or historical biological results. Tissue driver/model/reporting protocols,
resource acquisition and other FUMA role gaps remain separate work. The whole
goal stays active. Cell Ranger remains excluded.
