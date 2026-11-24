# Prepared original Genomic SEM workflow

Build the current Java21 executable with `gradlew.bat executableJar` and use
`build/cli/omicsmatic-0.4.0.jar`. The published v0.4.0 release asset has not been
replaced. The command is `genomicsem-tools`.

This workflow calls the original pinned GenomicSEM R implementation for
prepared covariance-model, GWAS and T-SEM fits. The separate
[preparation command](GENOMICSEM_PREPARATION.md) covers original munging,
multivariable LDSC, summary-statistic alignment, stratified enrichment and
FUSION preparation, with its own qualification checkpoint. Historical paper editions and complete paper
analyses remain unqualified; the catalog admission has not changed at this
development checkpoint. The [five source roles](literature/genomicsem-source-audit/ROLES.json)
retain distinct version declarations and uses.

**Warnings and required actions:** exit **3** means the dependent analysis was
**skipped**. Read stderr and `analysis-status.json`; obtain the exact named
prerequisite using an installation method permitted by your environment. If
installation is forbidden, leave the analysis skipped. Exit **2** means **no
complete admitted result**: preserve the attempt, read `FAILURE.md`,
`scientific-audit.json` when present and `worker/` logs, fix the stated cause and
retry a **new output path**. Partial tables are retained for diagnosis and must
not be used as completed results. Existing output is never overwritten.

The [saved workflow checkpoint](literature/genomicsem-workflow-checkpoint/STATUS.json)
records the exact producer, validation scope and unchanged catalog totals.

## Install once, then run a synthetic example

Use Windows x86_64 R **4.6.1**, Matrix **1.7-5** and the explicit private installer.
The pinned GenomicSEM revision is `da95d431a4709693d07eae55e172ade55526948b`
(DESCRIPTION0.0.5; README0.0.5c), with original lavaan **0.6-19**. The separate
[foundation guide](literature/GENOMICSEM_SOURCE_RUNTIME.md) records the source
audit, installation recipe and compatibility failure under lavaan0.7-3. The
analysis command never installs, downloads or upgrades software. Linux original
R execution is currently skipped with exit3; Java saved inspection is portable.

Change these example paths to your permitted R installation and workspace:

```powershell
New-Item -ItemType Directory -Force -Path 'D:\omics-runtime','D:\omics-checks' | Out-Null
python tools/literature/install_genomicsem_runtime.py `
  --rscript 'C:\R\R-4.6.1\bin\Rscript.exe' `
  --out 'D:\omics-runtime\genomicsem-1'
python tools/literature/qualify_genomicsem_runtime.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --out 'D:\omics-checks\runtime-qualification-1'

python tools/literature/genomicsem_workflow/make_example.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --out 'D:\omics-checks\example-1'
java -Xmx4g -jar build/cli/omicsmatic-0.4.0.jar genomicsem-tools `
  --request 'D:\omics-checks\example-1\model-request.json'
java -Xmx4g -jar build/cli/omicsmatic-0.4.0.jar genomicsem-tools `
  --request 'D:\omics-checks\example-1\gwas-request.json'
```

The example generator creates synthetic inputs and fully explicit JSON
requests. Its complete sampling covariance is simulated for this example.
Real studies need the original full multivariable sampling covariance and
properly scaled/aligned effects. Pairwise LDSC estimates do not supply the
off-diagonal sampling dependencies of a full multivariate matrix.

Open `model-run/results.tsv` or `gwas-run/results.tsv` only after the command
succeeds and saved inspection passes. `COMPLETED.json`, its bound manifest and
`analysis-status.json` with `results_admitted=true` must all be valid.

## Inputs and operations

Every RUN request declares `schema_version=1`, `mode=RUN`, the exact `profile`,
`operation`, `runtime_home`, a new `out`, `inputs`, `options` and optional `limits`.
Paths resolve relative to the request file. Outputs and external runtimes must
be disjoint, and the output parent must already exist without junction aliases.

| Operation | Input roles | Additional explicit options |
| --- | --- | --- |
| `COMMONFACTOR` | `covariance`, `sampling_covariance` | Original unit-variance common-factor fit. |
| `USERMODEL` | Above plus `model` | `std_lv`, `CFIcalc`, `fix_resid`, `tolerance` (positive or null), `Q_Factor`. Implied/residual covariance is retained. |
| `COMMONFACTOR_GWAS`, `COMMONFACTOR_TSEM` | Covariance matrices plus `intercepts`, `associations` | `cores`, `parallel`, `GC`, `SNPSE`, `tolerance`, `effects_aligned=true`. |
| `USER_GWAS`, `USER_TSEM` | Above plus `model` | Above plus `std_lv`, `fix_measurement`, `Q_SNP`, `sub`. |

Common options are explicit: `estimation=DWLS|ML`, integer `seed`,
`covariance_scale=observed|liability|correlation`,
`sampling_order=lower-column-including-diagonal`, `input_reviewed=true`,
`smoothing_policy=FAIL|ALLOW_UPSTREAM` and
`warning_policy=FAIL_FIT_WARNINGS|RETAIN_AND_FLAG`.

S, V and I are finite, exactly symmetric UTF-8 tab-delimited matrices. The first
header is literal `id`, followed by ordered labels; each row begins with its
matching label. S/I labels are unique simple lavaan identifiers such as `MDD`
or `Trait_2`. V uses the lower triangle of S by columns, including diagonals:
`T1:T1,T1:T2,T1:T3,...,T2:T2,T2:T3,...`. For symmetric S this is the same
sequence as the upper triangle by rows. V has `k(k+1)/2` rows and columns.
No symmetry averaging, matrix reordering or missing-value imputation occurs.
A declared correlation matrix needs unit diagonal within1e-10; it is never
rescaled automatically.

GWAS associations begin with exactly `SNP,CHR,BP,MAF,A1,A2`. T-SEM begins with
`Gene,Panel,HSQ`. These must be the first columns in that order: upstream relies
on their positions. The remaining columns are exactly `beta.<trait>` and
`se.<trait>`; their relative beta and SE orders must each match S. Grouped and
interleaved beta/SE columns are accepted. Every effect SE is positive, MAF lies
in `(0,0.5]`, BP is a positive canonical integer, and T-SEM gene variance HSQ is
positive. SNP IDs or Gene/Panel pairs must be unique. Alleles, identifiers and
effects are never repaired, swapped, aligned, standardized or relabeled.

User models are literal UTF-8 lavaan syntax. GWAS uses predictor `SNP`; T-SEM
uses **Gene**. `sub=[]` retains all original parameter rows; otherwise supply
unique association paths such as `['F ~ SNP']` in JSON double-quote syntax.
`parallel=false` requires `cores=1`; parallel operation uses explicit1..64 cores.
`GC` is `standard`, `conserv` or `none`. `SNPSE` and GWAS `tolerance` are either
false (the upstream default) or positive numbers. The source iterative path is
used; analytic mode and MPI are not exposed here.

## Original fields, warnings and result admission

`results.tsv` is the readable selected inference table. `inference.tsv` retains
typed audit tokens; `worker/tables/`, `worker/table-index.json` and
`worker/original-result.rds` preserve the original returned structure and fields.
The Java reader interprets the typed exports and retains the opaque RDS bytes.
Its structural verification does not parse RDS or independently refit the model.

Original fixed/missing-inference rows remain unavailable. Saturated models can
legitimately return all model-fit indices as `NA` with df0 while parameter
inference remains available. No chi-square, P value, CFI or AIC is filled in.
Original `< 5e-300` P strings stay bounded strings, and source zero endpoints
stay zero. The derived normal/chi-square tail checks use1e-315 absolute plus1e-8
relative tolerance; coordinate, allele and numeric input metadata are exact.
Selected scalar relationship checks do not replace independent optimization.

Every requested association must be present with finite estimates, positive
SE and usable P. Requested heterogeneity needs usable degrees of freedom and P;
an unavailable requested Q test leaves the operation unadmitted. Nonfinite
numeric fields, source errors/nonconvergence messages and violated policies also
leave no complete admitted result. Negative variances, smoothing diagnostics,
unavailable fit indices and warning occurrences are explicitly counted. Source
warning counts can include repeated parameter-row warnings.

`ALLOW_UPSTREAM` permits the original source's smoothing and records it; `FAIL`
refuses when S/V or SNP-level diagnostics indicate smoothing. No Java smoothing
or clipping is applied. `RETAIN_AND_FLAG` retains nonfatal source warnings;
`FAIL_FIT_WARNINGS` refuses any captured fit warning. Original import/dependency
warnings remain in raw logs. Some original fitting calls force convergence flags;
these checks are not a global-optimum or complete convergence certificate.

The runtime handshake checks 44 original GenomicSEM and1,012 selected lavaan
namespace functions,2,865 original binary-package payloads and the actual
declared R/core/package files before fitting and after execution. This establishes
the selected source/runtime bindings, not complete vendor authenticity, all OS
loader dependencies or host attestation. The producer is the entire executing
JAR; saved inspection requires that same producer's bytes.

## Inspect, verify and replay

```powershell
java -Xmx4g -jar build/cli/omicsmatic-0.4.0.jar genomicsem-tools `
  --inspect 'D:\omics-checks\example-1\gwas-run'
```

Inspection is read only and recomputes input/output structural relationships
from captured files. It needs the exact producer JAR, not a live external R
installation or original input files. Windows captures can be inspected using
that same JAR on Linux.

VERIFY and REPLAY both run a **fresh original upstream fit** into new output and
compare input audits, scientific tables, selected inferences and normalized
runtime bytes with the read-only parent. Supply a separate control JSON:

```json
{
  "schema_version": 1,
  "mode": "VERIFY",
  "analysis": "D:/omics-checks/example-1/gwas-run",
  "out": "D:/omics-checks/example-1/gwas-verify-1"
}
```

Use `REPLAY` for the corresponding replay request. Optional `runtime_home`
selects a restored byte-equivalent runtime; it never changes scientific options.
Fresh numerical discrepancies are retained as failure, with no automatic
tolerance relaxation. Raw timings, paths, logs and opaque RDS serialization are
not compared. The old parent and producer remain intact.

## Repeat the synthetic qualification

The qualification runner supplies 31 synthetic cases covering all six
operations, DWLS/ML, conditional and Cholesky models, higher-order and correlated
factors, factor-specific heterogeneity, genomic-control choices, two-worker
execution and explicitly allowed upstream smoothing. It runs the Java adapter,
a separate direct call to the original R API, a zero-tolerance comparison of
the entire returned R object including attributes, and Java saved inspection.
It installs nothing. This checks the adapter against the same original fitting
implementation; it does not provide an independent statistical optimizer.

```powershell
python tools/literature/genomicsem_workflow/qualify.py `
  --jar build/cli/omicsmatic-0.4.0.jar `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --rscript 'C:\R\R-4.6.1\bin\Rscript.exe' `
  --out 'D:\omics-checks\synthetic-qualification-1'
python tools/literature/verify_genomicsem_workflow_checkpoint.py
```

The second command checks the published checkpoint source bindings and declared
scope; it performs no fit. Optional `--private TASK_EVIDENCE --jar EXACT_JAR`
additionally checks the retained local evidence closure and all saved captures.
Raw runtime bindings, process records and operational receipts stay local.

Limits bound input bytes, members, combined numeric cells, traits, association
rows, result rows, workspace files/bytes and wall time. Java heap remains an
explicit JVM setting. The supervisor observes the owned root and birth-checked
descendants and closes those on failure/timeout. Monitoring is periodic; it is
not an OS memory quota, job container or guarantee of observing escaped children.
