# Fisher enrichment with complete correction families

Use `fuma-tools --method fisher-enrichment` to fit declared two-by-two count
tables or to construct those tables from a completed native known-association
bundle. All planned comparisons enter their declared families before adjusted
values are selected. Every original count, fitted table, family and exclusion
remains visible.

**REVIEW REQUIRED AFTER EXIT 0.** Counts, group definitions, complete-family
membership, baseline comparability and independence are investigator assertions.
Empty groups and overlapping observations can produce mathematical statistics
without establishing an observed independent biological comparison. Inspect
`WARNINGS.md` and the full evidence tables before interpretation.

## Run the synthetic examples

Use Python 3 and Java 17+. In this workspace, the validated development artifact
is `build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar`, SHA-256
`844e4cf3d9aad0834b692cdfac9e3af992fe5e772faae8dd2c6649f3fd6eae24`.
The published v0.3.7 JAR predates this implementation. No R, MAGMA or upstream
FUMA runtime is required to run supplied-result Fisher analyses.

From the repository root, choose new output directories:

```powershell
python examples/fuma-fisher-workflow/prepare_inputs.py --out build/fisher-example-NEW
$jar = 'build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar'
java -jar $jar fuma-tools --fisher-options-file build/fisher-example-NEW/counts.json
```

For grouped counts, prepare the original classifications first:

```powershell
java -jar $jar fuma-tools --known-options-file build/fisher-example-NEW/upstream-exact.json
java -jar $jar fuma-tools --fisher-options-file build/fisher-example-NEW/exact.json
java -jar $jar fuma-tools --workflow-options-file build/fisher-example-NEW/parent.json
```

The [example README](../../../examples/fuma-fisher-workflow/README.md) lists all
six Fisher recipes and their upstream dependencies. They include 0/1
pseudocounts, all supported corrections, missing counts, empty original groups,
probability underflow, duplicate contributors, unassessable evidence, shared
keys and unassigned targets. Inputs and references are synthetic.

**WINDOWS JAVA 21 PATH ENCODING.** The local launcher damaged non-ASCII paths in
command-line arguments. Keep the entire command-line carrier path ASCII; put
Unicode input/output paths inside the UTF-8 JSON carrier. That route passed on
Windows Java 21/25 and Linux Java 21. Paths inside a carrier are relative to that
carrier. Direct command-line paths are relative to the working directory.

## Table orientation and statistical output

| Group | Novel relative to the supplied baselines | Known in the supplied baselines |
| --- | --- | --- |
| Finer group | `finer_novel` | `finer_known` |
| Comparator group | `comparator_novel` | `comparator_known` |

The null odds ratio is one. The module uses central two-sided fixed-margin
Fisher inference, a conditional maximum-likelihood odds estimate and a central
exact confidence interval. Its odds estimate is not the sample cross-product
ratio. This statistical distinction is described in the
[R Fisher test documentation](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/fisher.test.html).

An explicit nonnegative integer `pseudocount` is added to every cell before
fitting. Both original and tested cells are retained. An original row containing
zero observations stays flagged even if its pseudocount fit is finite or
selected. A table with four unavailable cells is missing data, not four
observed zeros.

## Supply count tables

Required options are `--mode counts --analysis-id ID --families FAMILIES.tsv
--plan PLAN.tsv --counts COUNTS.tsv --cutoff Q --out NEW_DIRECTORY`.

`families.tsv` declares each correction family and its scientific policies:

```tsv
family	correction	encoding	missing	empty	pseudocount	confidence
F	BH	NATURAL_LOG	EXCLUDE	RETAIN_SOURCE	1	0.95
```

`plan.tsv` declares every planned test ID independently of its availability:

```tsv
family	test_id
F	observed
F	missing
```

`counts.tsv` supplies one row for every planned ID:

```tsv
family	test_id	trait	context	finer_novel	finer_known	comparator_novel	comparator_known
F	observed	demo	comparison	3	1	1	3
F	missing	demo	comparison	NA	NA	NA	NA
```

These are plain UTF-8 TSV files with actual tab separators. No quoting, ID
normalization, trimming or alias conversion is performed. Additional columns
remain in exact raw snapshots. Counts must be exact nonnegative decimal
integers; fractions, scientific notation, signs, whitespace and overflow are
refused. Missing tables require all four literal `NA` cells; partial missing
tables are invalid.

The plan must contain every comparison exactly once within its family, and the
supplied tables must contain the same IDs. Duplicate IDs, undeclared rows and
omitted planned rows fail. The same test ID may appear in different explicitly
declared families. A zero-test family can be declared with a family row and no
plan or table rows; it remains an empty family in the audit.

## Declare correction and missingness policies

| Field | Allowed values | Meaning |
| --- | --- | --- |
| `correction` | `NONE`, `BH`, `BY`, `HOLM`, `BONFERRONI` | Explicit complete-family correction. `NONE` leaves statistical values uncorrected. |
| `encoding` | `P_VALUE`, `NATURAL_LOG` | Choose probability arithmetic or the finite natural-log extension for adjustment. |
| `missing` | `ERROR`, `EXCLUDE` | Refuse unavailable tables, or retain them while excluding them from the computed denominator. |
| `empty` | `ERROR`, `RETAIN_SOURCE` | Refuse an original empty group, or retain its flagged mathematical fit. |
| `pseudocount` | Integer at least zero | Add this value to every cell; keep all original cells. |
| `confidence` | Finite decimal strictly between zero and one | Confidence coverage for the conditional odds interval. |

`--cutoff` is required, lies in `(0,1]` and is inclusive. Every available fit is
corrected before selection. Probability families select `adjusted_p <= cutoff`;
natural-log families select `adjusted_ln_p <= log(cutoff)`. Missing rows retain
unavailable adjusted and selection fields. The available denominator and
missing population are separate audit columns.

The raw finite `ln_p` is retained when displayed `p` underflows to zero. With
`NATURAL_LOG`, adjusted log values also remain finite through underflow. With
`P_VALUE`, adjustment uses the displayed binary64 probabilities; an adjusted
zero consequently has `adjusted_ln_p=-Inf`. That does not make an underflowed
probability an exact mathematical zero. The finite-log extension is separate
from original R probability output.

BH, BY, Holm and Bonferroni represent different error-control choices; choose
the intended family and method before interpreting results. The
[R adjustment documentation](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html)
describes their distinctions. Merely changing display groups or matching keys
does not authorize changing an upstream association family.

## Build counts from known associations

Run [known-association review](FUMA_KNOWN_ASSOCIATION_WORKFLOWS.md) first, then use
`--mode groups --known-bundle COMPLETED_BUNDLE --groups GROUPS.tsv
--comparisons COMPARISONS.tsv`, together with families, plan, cutoff, output,
analysis ID, gene namespace, trait namespace, original matching mode and
`--unassessable error|exclude`.

The importer requires a completed native `fuma-known-associations` operation,
consistent status markers, all required ledgers and every manifested output.
It verifies hashes, path membership, original source/test/target provenance,
selection decisions and classification keys. Original adjusted association
values are reused without recomputation. SHA-256 verifies integrity, not an
independent scientific certification or signature.

`--matching gene-trait|gene-across-traits` and both namespaces must equal the
original bundle contract. To change matching, recompute the upstream review
explicitly. Per-source annotation, genome-build, model and resource labels are
preserved independently; this is a coordinate-free gene-ID join.

Only original `TARGET` sources enter groups. If the same underlying study is
used as both baseline evidence and a target comparator, declare separate source
IDs and roles upstream and retain that shared-data provenance.

`groups.tsv` columns are `group,source,trait,context,expected_rows`. Each domain
names an exact original target source, trait and context. `expected_rows`
includes missing, unselected and repeated rows. It must match the complete
domain population. Explicit zero-row domains are permitted. A group can union
several domains, including several traits or cell contexts.

`comparisons.tsv` columns are
`family,test_id,trait,context,finer_group,comparator_group,overlap`.
Trait and context here are output labels; the two declared groups determine
which rows are counted. All comparison IDs must match the separate plan.

Each selected matching key counts once per group, while every contributor row
is retained. Known and novel keys contribute to the two columns. Missing target
rows and selected unassessable keys either fail under `--unassessable error` or
remain visibly excluded under `exclude`. Unselected rows remain visible. Rows
outside all groups remain in `unassigned-targets.tsv`.

| `overlap` | Shared selected keys |
| --- | --- |
| `ERROR` | Refuse the comparison if the groups share selected keys. |
| `RETAIN_BOTH` | Keep shared keys in both table rows and retain all contributor links. |
| `FINER_ONLY` | Remove every selected comparator key from the finer group before counting. |

`FINER_ONLY` considers selected comparator keys even when their classifications
are unassessable. Shared-key evidence is retained under either permitted overlap
policy. Conflicting known/novel/unassessable decisions for a selected key fail;
the module never relabels them to make a comparison fit. Retaining both sides of
an overlap does not establish independence.

## Connect the parent workflow

`fisher_enrichment` is an independent typed workflow stage by default. It can
use supplied counts or an external completed known-association bundle. A
failure or skip elsewhere does not prevent an independent Fisher stage from
running; the parent retains its failure or partial-skip status.

To use that parent's new `known_associations` output, declare root field
`"fisher_known_source": "known-associations-stage"`, select both stages and
set Fisher `mode` to `groups`. Do not supply `known-bundle` in the Fisher stage;
the parent supplies the actual stable upstream path. A failed or skipped known
stage causes its dependent Fisher analysis to skip. The prepared `parent.json`
is a complete runnable example.

## Inspect the evidence

| File | Inspect |
| --- | --- |
| `planned-tests.tsv` | Every planned family/test ID. |
| `fisher-tables.tsv` | Original/tested cells, availability, raw P/log-P, conditional odds, confidence limits, support size, adjusted values and selection. |
| `family-audit.tsv` | Policies, expected/actual/available counts, computed denominator, missing/empty populations and selection. |
| `root-audit.tsv` | Conditional odds/interval root parameterization, inverse flag, root value, remaining bracket and iterations. |
| `fit-warnings.tsv` | Every fitted table's interpretation and numerical review instructions. |
| `known-association-sources.tsv` | Imported source roles and model/resource/namespace/annotation/build labels; empty in count mode. |
| `group-audit.tsv`, `group-members.tsv` | Complete original domain populations, selected rows and distinct classified keys. |
| `group-keys.tsv`, `group-contributors.tsv` | Each selected group key and all original contributing input rows. |
| `group-dispositions.tsv` | Every grouped original row, including missing, unselected and unassessable exclusions. |
| `shared-keys.tsv`, `shared-contributors.tsv` | Every shared key, explicit overlap policy and contributors on both sides. |
| `unassigned-targets.tsv` | Original target rows outside every group. |
| `resource-ledger.tsv`, `source-inputs/` | Exact source snapshots, roles, checksums and byte counts, including imported bundle members. |
| `analysis-status.json`, `session.json`, `manifest.yaml`, `WARNINGS.md` | Completion, scientific choices, counts, integrity records and required actions. |

Count mode emits the first six scientific tables. Group mode also emits all
group, overlap and unassigned-target evidence. No selection filter truncates
these populations.

## Capacities and recovery

The CLI bounds each input file (`--max-bytes`, default 256 MiB), combined input
rows (`--max-rows`, one million), line bytes (`--max-line-bytes`, 1 MiB), parsed
cells (`--max-cells`, 50 million), complete snapshots (`--max-snapshot-bytes`,
1 GiB), scientific output plus snapshots (`--max-output-bytes`, 1 GiB) and
scientific rows (`--max-output-rows`, five million).

Additional defaults are 4,096 imported members (`--max-bundle-files`), ten
million tested observations per table including pseudocounts
(`--max-population`), 200,000 support states per table (`--max-support`), two
million cumulative support states (`--max-support-states`), one billion
logical grouping/fitting/adjustment operations (`--max-operations`), 1,000 root
iterations (`--max-root-iterations`), two million grouping evidence entries
(`--max-evidence`) and 300 seconds (`--timeout`). These are admission limits,
not operating-system memory quotas. Exceeding a limit refuses the complete
analysis rather than truncating its rows, tests or contributors.

**INVALID INPUT, MODEL, IMPORT OR CAPACITY: FAILURE 2.** Read the named terminal
cause and `WARNINGS.md`, inspect `session.json` and `partial/`, correct the cause
and rerun to a NEW directory. Existing outputs are never overwritten. Partial
tables are diagnostics and are not admitted as completed results.

**JAVA UNAVAILABLE OR INSTALLATION FORBIDDEN: SKIP 3.** The public verifier names
the dependency failure and exits 3. Provide Java 17+ on a permitted host, using
`--java PATH` if needed. If installation is forbidden, retain the affected
analysis as skipped. No analysis command installs software. Unavailable or
forbidden upstream fitting software affects that upstream analysis; provide
valid permitted completed results before importing them. The Fisher module
itself needs no MAGMA or R runtime.

## Validation and source interpretation

The development candidate passed 1,395 tests in 98 suites with strict Java 17
compilation, Javadoc and executable packaging; all 1,395 tests passed against
the exact JAR on Windows Java 21 and 25. Actual count-mode main runs checked all
8,480 rows in 40 complete R-reference families, including 40 missing tables,
and 59,080 numeric fields per Windows Java 21/25 and Linux Java 21. Scientific
output bytes were identical across those profiles.

Thirty-six actual grouped main commands verified complete classification,
group, contributor, exclusion and overlap records against independent raw-input
Python definitions and original R 4.2.3 fits/corrections. Six complete Fisher
populations per JVM included parent connection, upstream failure propagation,
independent completion after MAGMA SKIP 3 and tampered-bundle refusal.

One older optional live-MAGMA parent-reuse regression was skipped on Linux
because only the Windows runtime was installed. All 23 new Fisher CLI tests
and 63 native Fisher tests ran on Linux. To claim that separate Linux upstream
MAGMA validation, supply a permitted Linux MAGMA runtime and run its regression;
Fisher itself does not require it.

Conditional odds and confidence limits reproduce the R 4.2.3 ordinary/inverse
root protocol with tolerance `0.0001220703125`. Remaining brackets are retained;
this solver tolerance does not promise relative accuracy for an odds ratio or
its reciprocal. The earlier primitive audit retains eleven specific synthetic
field differences beyond `2e-14` relative, all within `1e-13`, in the
[numerical boundary ledger](../../../src/test/resources/fuma-fisher-exact/numerical-boundaries.tsv).
They do not widen every comparison's tolerance or establish bitwise R identity.

Public synthetic references use strict relative `2e-14` comparisons for nonzero
finite P/odds/confidence fields, exact zeros/infinities/missingness/selection and
separate finite-log comparison rules. No absolute probability allowance is
used. Run the complete public verification with:

```powershell
python tools/literature/verify_fuma_fisher_workflow.py --jar build/literature-review/fuma-fisher-candidate-1/jlinalg-0.3.7.jar --work build/fisher-verification-NEW
```

The prepared references include raw inputs, independent group equations,
original R regeneration scripts and provenance. R is needed only to regenerate
references. Their finite-log extension selects tail terms in log space when
the observed normalized probability underflows; it does not treat all rounded
zeros as equally extreme.

Paper-specific grouping, correction and pseudocount choices must remain
explicit. Arithmetic consistency with published counts cannot prove an
unreported historical call. Consult the [FUMA source review](FUMA_SOURCE_REVIEW.md)
and the [known-association guide](FUMA_KNOWN_ASSOCIATION_WORKFLOWS.md) for the
separate source/model boundaries. Broader FUMA remains unfinished; Cell Ranger
remains excluded.
