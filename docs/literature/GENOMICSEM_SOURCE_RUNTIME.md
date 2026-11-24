# Genomic SEM: source audit and runtime foundation

**The Java analysis adapter and catalog admission are unfinished.** This checkpoint
does not increase the implementation count: **159 scoped families** remain
qualified (30 native Java and 129 upstream), with 1,760 reviewed and 964 pending
paper-tool uses. The five roles below are frozen preparatory interpretations;
their canonical review status remains pending. No paper analysis was rerun.

The [source census](genomicsem-source-audit/CENSUS.json) checks all 250 original
XML files and their DOI identities. It finds 52 eligible prose occurrences and
eight figure-caption occurrences, all within the same five papers. Captions
support the existing structural-model, regression, multivariate-GWAS and
stratified-enrichment descriptions; they do not add eight executions or papers.
The [role records](genomicsem-source-audit/ROLES.json) preserve raw intake fields
and distinguish reported settings from unresolved historical execution details.

| Paper | Reported use | Edition context and remaining qualification |
| --- | --- | --- |
| [P035](https://doi.org/10.1038/s41586-025-09820-3) | Multivariable LDSC; common/five-factor psychiatric models; stratified factor/residual enrichment; multivariate GWAS and factor-specific Q_SNP. | Code availability literally reports **0.5.0**. Preserve this discrepancy with upstream DESCRIPTION **0.0.5**. No inferred correction. |
| [P125](https://doi.org/10.1038/s41588-025-02396-8) | Marginal and mutually adjusted eoMDD/loMDD genetic associations with other phenotypes. | Edition and exact model code remain unknown. |
| [P131](https://doi.org/10.1038/s41588-026-02735-3) | LDSC, factor/regression/mediation models, higher-order GWAS and T-SEM with FUSION results. | **0.0.5** appears in processing and T-SEM sections. A version number alone does not identify a commit. FUSION and ACAT are separate tools. |
| [P140](https://doi.org/10.1038/s41588-025-02391-z) | Educational-attainment GWAS by subtraction using Cholesky models; dependent heritability comparisons; LDSC correlations. | **0.0.3f**, R **4.3.2**. Separate R PCA and genomicPCA GWAS are not Genomic SEM CFA. |
| [P150](https://doi.org/10.1038/s41588-024-01933-1) | Covariance matrices for parallel analysis; common/correlated/hierarchical MetS factors; multivariate GWAS and Q_SNP. | **0.0.5**. Do not infer use of the recently added `paLDSC` API from a historical parallel-analysis description. |

## Runtime source and compatibility

The [pinned profile](../../tools/literature/genomicsem/PROFILE.json) selects the
[original upstream revision](https://github.com/GenomicSEM/GenomicSEM/tree/da95d431a4709693d07eae55e172ade55526948b)
`da95d431a4709693d07eae55e172ade55526948b`, GPL-3.0. Its DESCRIPTION is
0.0.5, while its README uses the 0.0.5c label. Keep both declarations.
The source archive, all 82 extracted files, 52 dependency ZIP files and the
lavaan archive have exact SHA-256/length pins. Runtime qualification compares
all **44** original namespace function bodies and formals with the unmodified
source. Binary runtime bundles and full installation receipts remain private.

**Compatibility failure:** unmodified `commonfactor(..., estimation="DWLS")`
fails with lavaan **0.7-3**: that dependency now requires explicit
`ordered=FALSE` for continuous DWLS, which the upstream commonfactor call omits.
The profile explicitly installs original lavaan **0.6-19**, whose R
`packageVersion()` string is **0.6.19**. No upstream function is patched and no
estimator is substituted. Other versions and Linux require separate validation.
Some pinned Windows dependencies were built under R4.6.2; compatibility with the
selected R4.6.1 environment is limited to the declared checks.

**Input-order failure:** GWAS functions select metadata and effect columns by
position. A reordered input can produce plausible numbers with incorrect
CHR/BP/allele labels. The first exploratory GWAS outputs using incorrectly
ordered metadata are retained locally as invalid evidence. The qualified GWAS
fixtures use `SNP, CHR, BP, MAF, A1, A2`, then `beta.<trait>` and `se.<trait>`
columns in covariance-trait order. T-SEM uses `Gene, Panel, HSQ` and aligned
effect/SE columns; its user model names **Gene**, not SNP.

## Stage and qualify a private runtime

Use Python **3.10+** and Windows x86_64 R **4.6.1** with Matrix **1.7-5**. Obtain that permitted R
runtime first; this installer does not replace shared R or package libraries.
Create the parent directories before using these examples. Paths are examples.

```powershell
New-Item -ItemType Directory -Force `
  -Path 'D:\omics-runtime','D:\omics-checks' | Out-Null

python tools/literature/install_genomicsem_runtime.py `
  --rscript 'C:\R\R-4.6.1\bin\Rscript.exe' `
  --out 'D:\omics-runtime\genomicsem-1'

python tools/literature/qualify_genomicsem_runtime.py `
  --runtime 'D:\omics-runtime\genomicsem-1' `
  --out 'D:\omics-checks\genomicsem-1'
```

The explicit installer downloads pinned artifacts and installs into a **new
private library**. For offline or restricted environments, supply
`--source-archive`, `--lavaan-archive`, `--cache` (all 52 pinned ZIP files) and
`--offline`. Artifact URLs and exact identities are in PROFILE.json. An existing
cache is read only. Installation uses an explicit allowlist of ordinary Windows
platform/profile variables and R's binary directories; credentials and arbitrary
environment settings are not forwarded. Neither installer nor qualifier is
called automatically by an analysis command.

The installer reports **STAGED_NOT_SCIENTIFICALLY_QUALIFIED**. The separate
qualification executes 13 synthetic cases: common-factor and user SEM in DWLS/ML,
joint regression, two-trait Cholesky, higher-order SEM, common/user GWAS and
T-SEM, and two-core common/user GWAS. Its independent checks include a direct
lavaan fit with explicit full-V sandwich algebra, model-implied covariance,
planted association effects, metadata identities and selected Z/P/Q relations.
The comparator shares lavaan; it is not an independent optimizer or a complete
statistical calibration. Fixed-parameter NA fields, zero P values, bounded P
strings and warnings retain original semantics. No genome-wide throughput,
arbitrary-model convergence or historical-edition equivalence is established.
The qualifier runs R in the foreground; it has no automatic timeout or process
termination facility. Process/capacity supervision for production analyses is
part of the unfinished Java adapter.

**Warnings and required actions:** exit **3** means the dependent installation
or qualification is **skipped**. Read stderr and `FAILURE.json`; obtain the exact
named dependency only through permitted methods. If installation is prohibited,
leave the analysis skipped. Exit **2** means **no qualified result**: retain the
attempt, read `WARNINGS.md`, worker logs and per-case RESULT.json, fix the stated
cause and retry a **new output directory**. A successful installation alone is
not a numerical qualification. Synthetic qualification does not complete the
Java integration or authorize replacing a paper's unspecified inputs/edition.

Recheck the primary sources without changing the catalog:

```powershell
python tools/literature/audit_genomicsem_sources.py `
  --cache 'D:\paper-cache\xml'
```

## Remaining implementation work

1. Add the Java request/CLI workflow with strict covariance/effect order and
   scale validation, exact producer/runtime bindings, owned process supervision,
   monitored capacities, fresh verification/replay and read-only inspection.
2. Connect and test original munging, multivariable LDSC (including full sampling
   dependencies), summary-statistic alignment, stratified LDSC/enrichment and
   FUSION-to-T-SEM preparation as required by the five roles. Pairwise LDSC output
   must not be assembled into an invented full sampling covariance matrix.
3. Validate packaged behavior and actual refusal cases, preserve historical
   source/runtime evidence, and perform guarded catalog admission only for the
   qualified scope. Resolve supplementary model/input/version gaps separately.

Cell Ranger remains excluded. The pre-existing literature website hold and its
33 citation gaps are unchanged. The full 250-paper implementation goal remains
active.
