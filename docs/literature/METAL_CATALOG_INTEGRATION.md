# METAL catalog integration

METAL now has one **scoped upstream integration** and **24 individually reviewed
positive uses** in the 250-paper catalog. It supports explicitly declared
original METAL 2020-05-05 workflows. This is not whole-tool parity or reproduction
of the papers. The native Java inverse-variance and weighted-Z methods remain
separate implementations with their own conventions.

Current totals are **130 new scoped integrations: 24 native Java + 106 upstream**,
or 133 including three existing adapters. There are 1,290 reviewed positive uses,
1,405 observations awaiting individual review, 259 missing definitions and 45
method-overlap definitions. The 250 papers, 446 named families, 485 effective
definitions and 2,695 observations remain unchanged. The fixed literature
window is 2023-10-23 through 2026-10-23. The full goal remains unfinished.

## Start a synthetic example

Use Java 17+ and a current source-build JAR. The public 0.4.0 release predates
this adapter. The standalone entry point is `org.jlinalg.cli.MetalWorkflowCli`;
the generic `meta` command does not automatically select original METAL.

First build from the repository root in Windows PowerShell:

```powershell
.\gradlew.bat --no-daemon --no-parallel :executableJar
```

Then, from the same repository root inside permitted Linux x86_64/WSL:

```bash
python3 -B tools/literature/metal_workflows/setup.py --out build/metal-private-new
python3 -B examples/metal-workflow/run-example.py --runtime build/metal-private-new/RUNTIME.tsv --jar build/cli/omicsmatic-0.4.0.jar --out build/metal-dna-example-new --mode dna
python3 -B examples/metal-workflow/run-example.py --runtime build/metal-private-new/RUNTIME.tsv --jar build/cli/omicsmatic-0.4.0.jar --out build/metal-gene-example-new --mode gene
```

Every output path must be new. Preparation needs Python 3.9+, CMake, CTest,
a C++11 compiler, make and zlib development files. It downloads a checksum-pinned
official archive, builds 136 unchanged source files and runs the official tests
in a private workspace. It does not install system packages. Download/install
is authorized only where the operating environment permits it. Missing or
forbidden prerequisites skip the dependent analysis; follow the named recovery
action rather than substituting a different engine.

**Keep original METAL source, archives, binaries, notices, author examples and
complete runtime-containing bundles private. Redistribution is restricted.**
For offline preparation, permitted workspace tools and the exact request/input
format, follow the [captured workflow guide](../vignettes/metal-captured-workflow.md).
That guide and its qualification packet were frozen before this catalog
admission; their pending-admission statements describe that earlier checkpoint.
This document and the new admission proof record the subsequent catalog state.

For your own prepared request, the workflow has distinct operations:

```bash
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli run --request prepared/REQUEST.tsv --out build/metal-analysis-new
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli verify --bundle build/metal-analysis-new
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli replay --bundle build/metal-analysis-new --out build/metal-replay-new
```

Require exit 0, `COMPLETED.tsv` with `result_admitted=true`, and successful
verification. `verify` checks structural consistency, hashes, method identity
and regenerated summary tables. It does not execute original METAL or establish
authenticity or biological truth. `replay` actually reruns the captured original
workflow and checks byte-identical scientific outputs. Retain the matching
frozen reader JAR with each historical bundle.

## Individual source roles

The [complete source census](omics-2023-2026/metal-source-role-coverage.json)
contains the XML identities, paragraph hashes, supporting evidence, literal
editions and individual unresolved settings. The census re-extracts all 250
primary XML files and accounts for all 47 named paragraphs in 24 papers.
P077 moves from its generic overview paragraph 42 to method paragraph 52;
its supporting source paragraph is retained. No observation is added or removed.

“Not reported” below means no literal edition is established by the selected
primary evidence. The project profile remains 2020-05-05; that is not an inferred
author edition. None of these rows establishes exact author settings or
whole-paper reproduction.

| Paper and primary source | Reported edition literal | Reviewed use |
|---|---|---|
| [P076](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786716/) | v.2011-03-25 | Meta-analyze European test-sample GWAS with the European training GWAS so the PRS-CSx comparison receives the same available data. |
| [P077](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807864/) | v2020-05-05 | Combine aortic-stenosis GWAS using effective-sample-size weighting and overlap correction; supply ALFA European frequencies where one study lacks them. |
| [P088](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631763/) | March 25, 2011 release | Fixed-effect inverse-variance meta-analysis of clinical and biobank cardiomyopathy GWAS after build/variant QC. |
| [P090](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631752/) | v.2020-05-05 | Central fixed-effect inverse-variance GWAS meta-analysis before the separate correlated-trait MTAG analysis. |
| [P105](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703676/) | v.2011-03-25 | Pool sex-specific X-chromosome results, discovery/replication and within-/cross-ancestry GWAS using fixed-effect inverse-variance fits; assess Cochran Q. |
| [P107](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649558/) | v2020-05-05 | Meta-analyze cohort GWAS, precomputed APOE-stratum effect differences, and SNP/interaction estimates separately using inverse-variance fixed effects. |
| [P112](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703690/) | Not reported | Perform ancestry-specific EUR and AFR GWAS meta-analyses using inverse-variance weighting. |
| [P114](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649597/) | v2011_03_25 | Combine EAS and EUR gene-level CNV associations using a sample-size-weighted fixed-effect model, retaining genes present in either analysis. |
| [P120](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695664/) | Not reported | Meta-analyze hypothyroidism and thyroid-function GWAS using fixed-effect inverse-variance weighting. |
| [P123](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553293/) | 2011-03-25 | Perform fixed-effects GWAS meta-analysis after genome-build, strand and variant QC. |
| [P125](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695632/) | Not reported | Use explicit SCHEME STDERR for fixed-effects meta-analysis of broad, early-onset and late-onset depression, plus case-case cohort meta-analysis. |
| [P126](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549050/) | v.2011-03-25 | Combine discovery and replication T2D GWAS; prepare external GWAS summary input for PRS-CS using inverse-variance meta-analysis. |
| [P130](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549054/) | Not reported | Fixed-effects inverse-variance pooling of cohort GWAS for variants with MAF above 1 percent in at least one cohort. |
| [P135](https://pmc.ncbi.nlm.nih.gov/articles/PMC10632131/) | Not reported | Fixed-effect inverse-variance forearm-fracture GWAS pooling after input genomic-control correction when needed. |
| [P136](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525193/) | Not reported | Fixed-effect inverse-variance pooling of sex-specific X-chromosome tests and cohort results. |
| [P140](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695639/) | Not reported | Use explicit SCHEME SAMPLESIZE to pool MoBa and FinnGen GWAS after MAF/INFO, allele, strand and sign QC. |
| [P150](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549047/) | Not reported | Fixed-effects T2D and hypertension GWAS meta-analyses, including a UKB-excluded T2D analysis. |
| [P162](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447099/) | Not reported | Fixed-effect inverse-variance multi-ancestry, sex-specific and European-ancestry meta-analyses; assess between-study I-squared. |
| [P191](https://pmc.ncbi.nlm.nih.gov/articles/PMC13384252/) | Not reported | Pool ancestry-stratified SAIGE GWAS results by inverse variance for each phenotype. |
| [P199](https://pmc.ncbi.nlm.nih.gov/articles/PMC12739644/) | Not reported | Combine biobank GWAS summary statistics after common-variant, imputation and allele QC; the source describes marginal Z scores as inputs. |
| [P200](https://pmc.ncbi.nlm.nih.gov/articles/PMC13393102/) | Not reported | Use STDERR inverse-variance pooling, apply genomic control to input datasets and again to the combined result, and calculate Cochran Q. |
| [P203](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568756/) | Not reported | Pool cis-eQTL associations across populations using METAL before SuSiEx fine-mapping. |
| [P212](https://pmc.ncbi.nlm.nih.gov/articles/PMC12614742/) | Not reported | Run a parallel METAL analysis to obtain pooled effect-allele frequencies missing from APEX output, then append those frequencies for downstream signal comparisons. |
| [P243](https://pmc.ncbi.nlm.nih.gov/articles/PMC11267522/) | Windows, released March 25, 2011 | Combine discovery and replication marker associations with explicit STDERR scheme. |

Seven rows explicitly name 2011 editions: P076, P088, P105, P114, P123, P126 and
P243. The last names Windows. This qualification does not replace those author
editions with Linux 2020. Unknown schemes remain unknown: in particular, do not
invent a command from P199's Z-score input wording or P203's eQTL description.

P076 is comparative benchmark data preparation. P114 uses gene-level CNV units;
do not manufacture SNP alleles. P212 obtains pooled frequencies alongside APEX,
rather than supplying the conditional engine. P123's Lin–Sullivan overlap
correction is in RE2C, not METAL's overlap option. P120 calls for no additional
genomic control; upstream corrected inputs must not silently receive a second
correction. P200 explicitly describes input and post-meta correction. The full
per-paper cautions remain in each catalog use and the census.

## Scientific boundaries and warnings

**SKIP3 means dependent analysis was skipped.** Supply the named permitted
runtime, reference, edition or platform, then retry into a new output path, or
leave the analysis skipped with the cause and required action prominently stated.
Original execution needs Linux x86_64/WSL; read-only verification works on Windows.

**FAIL2 means no complete result is admitted.** Retain original inputs, partials,
failure records, warnings and both original consoles. Correct the identified
cause and retry into a new path. A zero original exit alone is insufficient.
Never silently repair identifiers, NaN/Inf, P=0, missing contributors or failed
models. Recorded source warnings do not authorize lost input contributions.

Select STDERR/SAMPLESIZE and every GC/overlap/frequency option explicitly.
Neither effective sample size nor biological effect orientation is inferred.
Original log output is base 10; the native Java weighted-Z API uses natural
logs and different single-cohort heterogeneity conventions. Input P=0 and
unrepresentable probability handoffs are not silently floored.

Keep declared total N, source Weight and overlap-adjusted N distinct. The
retained overlap control has 20,000, 23,317 and 18,341.5 respectively. Original
custom declared N is printed to six significant digits. The same original
control reports Freq1 **0.2573** with all input frequencies and printed min/max
equal to 0.3000. Preserve this source behavior without clipping or claiming
independently validated uncertainty. The prior handover's 1.2573 was a
documentation typo; original outputs, frozen verifier and qualification are
unchanged. A second GC pass reloads printed data; primary heterogeneity and
frequency fields remain separate from the aggregate's second-pass fields.

## Verify this catalog admission

From this repository root, with Python 3.11+, Git and the frozen XML cache:

```powershell
python -B tools/literature/metal_catalog/catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/metal-catalog-audit-new
```

For a complete private rehash and actual frozen-Java checks of all 28 bundles,
add the following arguments, using permitted local paths:

```text
--closed-evidence build/metal-1/adapter-evidence-4 --verify-bundles --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe"
```

The public audit checks the exact eight predecessor metadata files against
actual Git commit `b25f8e15bb9af3c3da7dc5344dc7a4b1427c2a9c`, re-runs unchanged
LDAK logic against retained predecessor bytes without a source overlay, checks
both qualification archives and current scientific source, re-extracts the
250 primary identities and validates the deterministic METAL-only transition.
The review JSON has an exact reversible catalog-only delta for historical
readers. Scientific engines and old fingerprints are unchanged. These are
source/integrity checks, not an independent statistical refit or authentication.

Read [the machine-readable proof](omics-2023-2026/metal-catalog-validation.json),
[retained predecessor check](metal-catalog-admission/PREDECESSOR-CHECK.json),
[admission warnings](metal-catalog-admission/WARNINGS.md) and
[original qualification](metal-workflow-development/README.md).
The portable admission packet contains selected reports, consoles, our project
source and a private INDEX. It omits restricted software and the complete private
execution store, and cannot replace that store or its matching frozen reader.

Cell Ranger remains excluded. Both literature HTML pages remain withheld from
Pages. No release, whole-tool parity or whole-paper completion is claimed.
