# GenomicSEM workflows and five paper uses

The GenomicSEM integration uses the original pinned R APIs through two Java
commands: [`genomicsem-prep`](../GENOMICSEM_PREPARATION.md) prepares data and
[`genomicsem-tools`](../GENOMICSEM_WORKFLOW.md) fits prepared models, GWAS and
T-SEM. GenomicSEM is one upstream family. The earlier related native
`GenomicFactor` method remains separately identified; it is never substituted
when the required R runtime is missing.

**Warnings and required actions:** exit **3** means **dependent analysis
skipped**. Read the named missing runtime, add-on, input or qualified edition;
obtain it only through an allowed method. If installation is forbidden, retain
the skip. Exit **2** admits **no complete result**: preserve partial output and
logs, resolve the stated cause and retry a **new output path**. Read
`WARNINGS.md`, `FAILURE.md`, `scientific-audit.json` and row/annotation status
tables before using products. Source nonfinite or missing values stay literal.

Build the current Java21 source with `gradlew.bat executableJar`. Current
release assets have not been replaced. Original execution uses private Windows
x86_64 R4.6.1, Matrix1.7-5 and lavaan0.6-19 with GenomicSEM revision
`da95d431a4709693d07eae55e172ade55526948b` (DESCRIPTION0.0.5, README0.0.5c).
The preparation guide has copyable installation and synthetic-run commands.
Linux saved inspection is qualified; this pinned original-execution profile
requires Windows and reports exit3 on Linux.

## What each paper used

The [frozen source audit](genomicsem-source-audit/ROLES.json) retains exact
paragraph identities and settings. The catalog applies reviewed copies while
keeping all original observation fields, including raw near-name versions.

| Paper | GenomicSEM role | Source limits retained |
| --- | --- | --- |
| [P035](https://doi.org/10.1038/s41586-025-09820-3) | Psychiatric genetic covariance, common/five-factor models, stratified variance enrichment and multivariate GWAS with factor heterogeneity. | Literal 0.5.0 is preserved. EFA is separately stats::factanal. Even/odd chromosome roles, 22 excluded/162 retained annotations and manuscript GC wording remain explicit; exact historical scripts/options are unqualified. |
| [P125](https://doi.org/10.1038/s41588-025-02396-8) | eoMDD/loMDD correlations and subtype-adjusted associations. | A paired-subtype conditional model is required. Edition, original syntax and matrices are not established by the source paragraph. |
| [P131](https://doi.org/10.1038/s41588-026-02735-3) | Multivariate LDSC, multiple-regression/mediation and higher-order models, multivariate GWAS and FUSION-based T-SEM. | Distinct 0.0.5 contexts are preserved. FUSION training/association, ACAT and exploratory methods remain separate. Exact invoked historical T-SEM API and author scripts are unqualified. |
| [P140](https://doi.org/10.1038/s41588-025-02391-z) | Educational-attainment-adjusted GWAS by Cholesky subtraction and dependent heritability comparisons. | Preserve 0.0.3f/R4.3.2. Separate PCA/PC-GWAS is not relabeled as GenomicSEM CFA. The complete historical model has not been reexecuted. |
| [P150](https://doi.org/10.1038/s41588-024-01933-1) | Genetic/sampling covariances, comparison of single/correlated/hierarchical MetS models and multivariate GWAS with Q_SNP. | Preserve 0.0.5 contexts. Parallel-analysis wording does not establish use of the newer paLDSC API; exact code/seed and original models remain unqualified. |

DOI/PMCID links for every role are retained in the catalog and source audit.
The first four raw near-name version fields remain null; P150 remains0.0.5.
Contextual declarations do not overwrite those original captures. The current
tested source is not assigned to a historical paper merely because both show
the same DESCRIPTION version.

Use the study's correct ancestry, genome build, sample-size definitions,
prevalences, LD/reference files, effect/SE scales and model identification.
The original package controls OR detection, MAF folding, allele alignment and
other transformations. For example, P035 reports INFO>0.9 while the current
original MUNGE argument applies an inclusive threshold. Matching a published
strict boundary requires explicit input selection; no equivalence is inferred.
Full joint sampling covariance comes from one multivariate LDSC call, not
independent pairwise fits. Original enrichment P values are one-sided upper
tails for enrichment above1.

## Qualification and catalog accounting

Preparation has 24 exact original-object comparisons: 22 admitted and two
original returns rejected by explicit warning/unavailable policies. There are
20 actual refusal controls, 44 focused Java tests on each platform, three fresh
executions, six documented example runs and four downstream fitting checks.
The separate earlier fitting milestone has 31 direct-original comparisons and
its own preserved producer. The final preparation producer is identified in
its [checkpoint](genomicsem-preparation-checkpoint/STATUS.json). Full readers
checked 35 saved captures and 250 primary XMLs in immutable source contexts on
Windows and Linux. These are scoped synthetic/software checks, not original
paper analyses or full external-package/optimizer certification.

Optional Rmpfr/GMP binaries report BuiltR4.6.2. Selected arithmetic and the
actual tiny-P SUMSTATS branch are checked under unchanged R4.6.1; startup
import/build warnings remain visible. Current analysis commands do not install
or upgrade software. A required unavailable package leaves analysis skipped.

The intended admission moves 159 to 160 scoped families (30 native + 130 upstream),
1,760 to 1,765 reviewed uses and 964 to 959 pending uses. Missing tools stay 254;
method overlap changes 18 to 17. The 250 papers, 446 named labels, 485 provider
definitions, 2,724 observations and all earlier reviews stay unchanged.
Cell Ranger remains excluded by the user. The full 250-paper goal remains open.

## Maintainer admission checks

Run the public saved catalog checker after admission:

```powershell
python tools/literature/verify_genomicsem_catalog_checkpoint.py
```

For plan/inspect/apply/verify, use the exact local original producer, full
private evidence, primary XML cache and a physical source snapshot from commit
`9bf1aeb93ca5196dd1de321c640d02b5387d511f`. The snapshot also retains its own
FILES.json/COMPLETED.json context records. An example plan command is:

```powershell
python tools/literature/close_genomicsem_catalog.py --plan `
  --jar 'D:\omics-checks\original-producer.jar' `
  --private-root 'D:\omics-checks\preparation-evidence' `
  --workflow-source-root 'D:\omics-checks\original-context\source' `
  --cache 'D:\omics-checks\primary-xml' `
  --out 'build\genomicsem-catalog-plan-1'
```

Use `--inspect` with `--plan-dir` and a new `--out` before `--apply`. Both
recheck deterministic preview bytes, source, metadata, qualified evidence and
registry. `--verify` checks the installed transition without reapplying it.
The full prior 35-capture reader was actually run before admission. Each mode
rehashes its unchanged 109 source-context and 5,875 private evidence files and
freshly checks 250 primary XMLs; it verifies that prior actual receipt without
relaunching 35 identical Java inspections. No statistical analysis is rerun.

Public evidence excludes raw runtime/command/process/browser/cleanup records.
Keep original producers, source contexts, scientific results, failures, inputs
and licensed runtimes at their recorded paths. Never update an old expected
hash to fit a newer checkout. Later metadata changes require the original
context when checking an older checkpoint.
