# Original GenomicSEM preparation

`genomicsem-prep` calls six original GenomicSEM R APIs from a complete
OmicsMatic executable JAR. It preserves original objects, logs, unavailable
fields and source transformations. The separate
[prepared fitting workflow](GENOMICSEM_WORKFLOW.md) consumes its projected
covariance matrices and aligned association tables.

**Warnings and required actions:** exit **3** means **dependent analysis
skipped**. Read stderr and `analysis-status.json`, obtain the exact named
permitted prerequisite, and retry a **new output path**. If installation is
prohibited, retain the skip. Exit **2** means **no complete admitted product**:
preserve the attempt, read `FAILURE.md`, `scientific-audit.json` when present,
and `worker/` logs. Resolve the stated input, numerical, integrity or capacity
problem before retrying. Partial files are diagnostic evidence.

Use products only after exit0, a valid `COMPLETED.json`/manifest seal,
`products_admitted=true`, and successful saved inspection. Read `WARNINGS.md`
and the status tables. `RETAIN_SOURCE_AND_FLAG` admits usable units while
preserving and identifying unavailable units; it does not make every retained
row usable. No result is repaired, clipped, deduplicated or imputed by Java.

## Install and run a synthetic example

Build with Java21: `gradlew.bat executableJar`. The executable is
`build/cli/omicsmatic-0.4.0.jar`; previously published release assets are not
replaced by a source build. Original execution requires Windows x86_64 R4.6.1,
Matrix1.7-5, lavaan0.6-19 and the pinned private runtime. See the
[foundation installation guide](literature/GENOMICSEM_SOURCE_RUNTIME.md).
The analysis command never downloads, installs or upgrades dependencies.

Optional Rmpfr1.1-3/GMP0.7-5.1 support the original SUMSTATS branch for
P<1e-307. The source package does not declare this lazy dependency. Their
pinned CRAN binaries were built under R4.6.2; selected arithmetic is tested
under R4.6.1 and the build-version warnings remain visible. The base runtime
is unchanged. Set `runtime_addons=null` to omit them; a call requiring a
missing package exits3, retains any partial original attempt, and admits no
complete product. GPL package terms are retained with the private packages.

Change the paths for your permitted installation:

```powershell
New-Item -ItemType Directory -Force -Path 'D:\omics-runtime','D:\omics-checks' | Out-Null
python tools/literature/install_genomicsem_runtime.py `
  --rscript 'C:\R\R-4.6.1\bin\Rscript.exe' `
  --out 'D:\omics-runtime\genomicsem-1'
python tools/literature/qualify_genomicsem_runtime.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --out 'D:\omics-checks\runtime-qualification-1'
python tools/literature/install_genomicsem_preparation_addons.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --out 'D:\omics-runtime\genomicsem-addons-1'
python tools/literature/genomicsem_prep/make_example.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --addons 'D:\omics-runtime\genomicsem-addons-1' `
  --out 'D:\omics-checks\preparation-example-1'
foreach ($operation in 'munge','ldsc','sumstats','fusion','stratified','enrich') {
  java -Xmx4g -jar build/cli/omicsmatic-0.4.0.jar genomicsem-prep `
    --request "D:\omics-checks\preparation-example-1\$operation-request.json"
  if ($LASTEXITCODE -ne 0) { throw "Preparation stopped: $operation; read its warnings and logs." }
}
java -Xmx4g -jar build/cli/omicsmatic-0.4.0.jar genomicsem-prep `
  --inspect 'D:\omics-checks\preparation-example-1\ldsc-run'
```

The example has four traits and 5,000 artificial SNPs. Artificial LD scores
and annotations demonstrate software behavior; they are not an ancestry or
genome-build reference, biological calibration, or a paper reproduction.
Every output path is new. Existing output is preserved, including failures.
An offline installer can use `--cache PATH --offline` with the exact pinned
CRAN archives. An unavailable or prohibited download does not authorize a
substitute version.

The [qualification checkpoint](literature/genomicsem-preparation-checkpoint/README.md)
records exact original-object comparisons, actual refusal controls, fresh
execution and platform checks. It grants no additional catalog credit.

## Request schema and original behavior

RUN schema1 declares `mode=RUN`, the exact profile
`genomicsem-preparation-da95d43-r461-win64-lavaan0619-v1`, `operation`,
`runtime_home`, `runtime_addons` (path or null), NEW `out`, `bundle`, `paths`,
`options`, and optional monitored `limits`. Paths resolve relative to the
request file; paths inside `paths` are literal slash-separated addresses
relative to the bundle. No wildcards, traversal, aliases or arbitrary RDS
inputs are accepted. Only the selected source files are captured. Runtimes
and output must be disjoint; the physical output parent must exist.

All operations declare ordered unique `trait_names`, integer `seed`,
`upstream_behavior_reviewed=true`,
`warning_policy=RETAIN_AND_FLAG|FAIL_OPERATION_WARNINGS`, and
`unavailable_output_policy=FAIL|RETAIN_SOURCE_AND_FLAG`. The example generator
provides complete copyable JSON. Unknown or omitted scientific options fail.

The warning policy gates warnings raised during the original API call.
Initialization warnings remain visible separately in
`worker/initial-conditions.json` and logs, including namespace import and
dependency build-version warnings; they are not hidden by an operation's
zero-warning count. A missing primitive cell in metadata, such as compact
R row names, is distinct from an unavailable scientific output unit.

| Operation | Paths | Additional explicit options and source behavior |
| --- | --- | --- |
| `MUNGE` | ordered `files`, `hm3` | `N` (positive or null per trait), `info_filter`, `maf_filter`, `column_names`, `parallel`, `cores`. Original aliases, NEFF doubling, MAF folding, rounded-median odds-ratio detection, allele matching/sign flips and filters apply. P=0 may yield infinite Z; raw munged files are intermediates. |
| `LDSC` | ordered munged `traits`, `ld`, `wld` directories | paired `sample_prevalence`/`population_prevalence` (two nulls for continuous traits), `separate_weights`, `chromosomes`, `blocks`, `standardized`, `select=false|ODD|EVEN|[chromosomes]`, `chisq_max` (positive or null for original adaptive behavior). One original multivariate call produces full joint V. Original first-trait adaptive cutoff and >18-trait block override are retained. |
| `SUMSTATS` | ordered `files`, `reference` | `N`, per-trait `OLS`, `linprob`, `se_logit`, `betas`, `info_filter`, `maf_filter`, `keep_indel`, `parallel`, `cores`, `remove_ambiguous`, `direction_filter`. Original duplicate removal, scaling and allele alignment apply. A character entry in `betas` promotes the original R vector to character; mixed types are not silently corrected. Tiny-P MPFR output uses the source approximation, not an exact inverse chi-square tail. |
| `READ_FUSION` | ordered `files` | positive `N`, per-trait `binary`, `permutation`. Original binary/continuous scaling, panel-path shortening and first-trait HSQ retention apply. Permutation endpoints are converted using PERM.N. Original zero-Z missing-SE omission is retained. |
| `S_LDSC` | munged `traits`, `ld_prefixes`, `weights_prefix`, `frequency_prefix` | paired prevalences, `blocks`, `exclude_continuous`. Exact source prefix populations and matching chromosomes are required. Plain/compressed LD scores are supported, with both forms for the same chromosome refused. All nine original components remain in the typed product. |
| `ENRICH` | `stratified` typed product directory, `model` text | `annotations=ALL` or ordered labels retaining the original baseline first, `parameters`, `fix=regressions|covariances|variances`, `std_lv`, `remove_flanking`, `tau`, `include_baseline`, `tolerance` (positive or null), `fixed_parameters`, `smoothing_policy=FAIL|ALLOW_UPSTREAM`. Selection projects source values without recomputation. Original eligibility and residual-bound retry behavior apply. |

Inputs use rectangular UTF-8 whitespace/tab-delimited records with double
quotes; multiline quoted records and ragged rows are outside this profile.
GenomicSEM itself uses several source parsers. This adapter does not promise
every external parser dialect. Use the correct ancestry, build, effect/SE
scale, sample-size definition, prevalences and FUSION weights for your study.
No Java rule substitutes for those scientific decisions.

## Products, inspection and fresh execution

LDSC `products/S.tsv`, `V.tsv`, `I.tsv` and optional standardized matrices
project original numeric cells with 17 significant digits. V labels are the
lower-column triangle including the diagonal, in the requested trait order.
Independent pairwise fits cannot supply a joint multivariate sampling V.
Java FP64 spectral diagnostics use a separate lower-triangle copy and never
change original cells. Nonpositive or marginal estimates remain flagged;
the diagnostic is not a convergence or biological certificate.

SUMSTATS/FUSION `products/associations.tsv` explicitly orders the named
metadata/effect/SE columns for prepared fitting. Every original field remains
in the typed export. `association-status.tsv` identifies rows usable across
all requested traits. Stratified results are in `products/stratified/` with
`annotation-status.tsv`. Enrichment exports `results.tsv` and the optional
`baseline-model.tsv` under `products/`. **Enrichment P values are one-sided
upper tails for enrichment above1; baseline enrichment1 has P=0.5.**

Original `worker/original-result.rds`, lossless primitive typed exports,
warnings and logs remain retained. Java reads typed values and attributes;
it does not independently decode RDS or reproduce the original optimizer.
The exact executing whole JAR is recorded and required for saved inspection;
never refresh an old producer hash after rebuilding.

Fresh VERIFY and REPLAY both execute the original API again using captured
inputs and scientific options. The parent stays read only. For example:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"ldsc-run","out":"ldsc-verify-1"}
```

Run that JSON with `--request`. Optional `runtime_home` and `runtime_addons`
can point to an exact permitted runtime copy. Scientific products, typed
objects and runtime bytes are compared; opaque RDS envelopes, timings and
operational paths are not. Original munged gzip bytes remain unchanged;
their uncompressed content identity is compared. `--inspect` only checks
saved evidence and performs no upstream call. Linux original R execution is
unavailable in the pinned Windows profile and exits3; saved Java inspection
is qualified separately.

The pinned upstream revision is `da95d431a4709693d07eae55e172ade55526948b`
(DESCRIPTION0.0.5, README0.0.5c). Distinct historical declarations, including
literal0.5.0 and0.0.3f, remain distinct in the
[five-paper role audit](literature/genomicsem-source-audit/ROLES.json).
This scope does not establish historical-edition equivalence, full external
package qualification, or a whole-paper analysis. Catalog admission is a
separate guarded transition.
