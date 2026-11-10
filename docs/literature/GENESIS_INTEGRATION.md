# GENESIS workflows

The `genesis-tools` development command runs original **GENESIS 2.42.0** for PC-AiR, PC-Relate, null-model fitting and single-variant association. Java checks complete inputs, identities, sample order and stated scientific invariants. It also implements matrix partitioning and finite rank normalisation natively. Original PCA/projection, kinship and model estimators remain upstream calculations.

**Require exit 0, `COMPLETED.json` with `result_admitted=true`, and successful complete `--verify` before using a result.** Read `WARNINGS.md`, `scientific-audit.json`, original logs and variant dispositions. Missing or forbidden dependencies return **SKIP3** and skip dependent original analysis. Input, process, integrity or scientific failures return **FAIL2** and admit no result, including partial original output. Preserve each attempt and use NEW paths for a corrected attempt.

This integration uses a separate source-build artifact. The released `omicsmatic-0.3.7.jar` is unchanged and lacks this command by itself. The root supplement requires that separately pinned JAR as a dependency. Earlier GENESIS development documents describe the capabilities and catalog status of their own recorded checkpoints; this guide describes the current scoped command. The full 250-paper implementation goal remains open.

## Quick start with a prepared private runtime

Run from the repository root. The original-analysis profile requires **Windows x86_64**, R **4.6.1** with a 64-bit long-double significand, GENESIS **2.42.0**, and the complete explicitly pinned private dependency library. The examples use 64 synthetic people and 512 variants; they contain no participant data or biological findings.

If the exact private runtime profile already exists, prepare a NEW example workspace using it:

```powershell
python tools/literature/prepare_genesis_examples.py `
  --profile path/to/runtime-profile.json `
  --out build/literature-review/genesis-runtime/my-examples-1
```

The preparer copies the profile without editing its hashes, extracts all eleven original synthetic input files, and copies thirteen complete JSON requests. It runs no R, installs nothing and admits no result. Recipe paths are relative to each JSON file. Review `my-examples-1/WARNINGS.md` and `COMPLETED.json` before continuing.

Run the PC-AiR example through the main CLI and verify the complete bundle:

```powershell
$genesisCp = "docs/literature/genesis-tools-development/genesis-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar"
java -Xmx2g -cp $genesisCp org.jlinalg.cli.JLinAlgCli genesis-tools `
  --request build/literature-review/genesis-runtime/my-examples-1/requests/pcair-10.json
java -Xmx2g -cp $genesisCp org.jlinalg.cli.JLinAlgCli genesis-tools `
  --verify build/literature-review/genesis-runtime/my-examples-1/results/pcair-10
```

An existing output directory is refused. Do not delete or overwrite it to rerun the example; prepare another NEW workspace. `pcair-20.json` explicitly requests twenty components. Neither example reconstructs a historical paper's unspecified parameters.

Use `pcrelate.json`, `null-gaussian.json` or `association-gaussian.json` in the same `requests` directory for the other core modes. The complete [example manifest](../../examples/genesis-tools/manifest.json) identifies expected outcomes and all source-input hashes. **The supplied PC-Relate PCs are literal synthetic inputs, not an export from these PC-AiR runs.** No automatic ancestry/covariance provenance is inferred.

The standalone `genesis-workflows-javac21.jar` in [the extended checkpoint](genesis-extended-workflow-development/README.md) accepts the same `--request` and `--verify` arguments without a root command prefix or canonical dependency. Keep the matching artifact with every saved bundle. Java 17+ can replay completed bundles on Windows or Linux without R; original execution is admitted only on the declared Windows runtime.

## Prepare missing prerequisites only where permitted

If the exact R installation or required packages cannot be installed, **leave dependent analyses skipped**. The helpers explain the missing prerequisite and required action in prominent warnings. They never substitute another version, ordinary PCA, a generic model or an unrequested compiler/toolchain.

For a new core private runtime, first download/check the official payload, then install into a NEW library:

```powershell
python tools/literature/intake_genesis_runtime.py `
  --out build/literature-review/genesis-runtime/my-intake-1 --allow-download
python tools/literature/setup_genesis_reference.py `
  --intake build/literature-review/genesis-runtime/my-intake-1 `
  --out build/literature-review/genesis-runtime/my-runtime-1 `
  --allow-download --allow-install
java -cp $genesisCp org.jlinalg.cli.JLinAlgCli genesis-tools `
  --export-runtime-helper build/literature-review/genesis-runtime/my-helper-1
python build/literature-review/genesis-runtime/my-helper-1/prepare_genesis_runtime.py `
  --setup build/literature-review/genesis-runtime/my-runtime-1/COMPLETED.json `
  --out build/literature-review/genesis-runtime/my-profile-1
```

Setup/profile success alone admits no scientific result. Require each helper's successful exit and completion receipt; inspect its warnings and logs. The profile binds every file in the supplied R distribution and private library. Windows system DLLs outside those roots are not independently pinned. Existing/shared libraries remain unchanged.

Core PC-AiR, PC-Relate, null models, Score and GxE do not need the three optional test packages. To run all optional examples, clone the core library into another NEW private tree and add **SPAtest 3.1.2**, **poibin 1.6** and **COMPoissonReg 0.8.2**:

```powershell
python tools/literature/extend_genesis_runtime.py `
  --profile build/literature-review/genesis-runtime/my-profile-1/runtime-profile.json `
  --repository https://cloud.r-project.org/bin/windows/contrib/4.6/ `
  --out build/literature-review/genesis-runtime/my-optional-runtime-1 `
  --allow-download --allow-install
python build/literature-review/genesis-runtime/my-helper-1/prepare_genesis_runtime.py `
  --setup build/literature-review/genesis-runtime/my-optional-runtime-1/COMPLETED.json `
  --out build/literature-review/genesis-runtime/my-optional-profile-1
```

Pass `my-optional-profile-1/runtime-profile.json` to the example preparer in a NEW workspace. An unavailable exact package edition or forbidden download/installation requires SKIP3. The clone retains every base package byte; it does not upgrade the existing environment. Analysis checks optional prerequisites before starting the original method and installs nothing itself.

## Supported requests and input contracts

Every request declares version, mode, inputs, scientific settings, single-thread execution, GDS layout and complete logical capacities. Unknown keys, arbitrary R expressions/formulas and free-form command flags are refused. The literal JSON files are complete copyable starting points; review every parameter before applying one to real data.

| Mode | Explicit inputs | Result and limits |
|---|---|---|
| `pcair` | PLINK BED/BIM/FAM; optional labelled kinship/divergence matrices, selection and forced unrelated samples | Original ordered partition, PCs/eigenvalues, source signs and variance placeholders. Exact DSPEVX/DSPEV and declared randomized options are supported. |
| `pcrelate` | BED/BIM/FAM; caller-supplied PC columns; optional training/selection | Original kinship/IBD and optional scaled connected-component covariance. PCs are not recomputed. |
| `null-model` | FID/IID phenotype table, named outcome/covariates, optional labelled covariance matrices/group | Original Gaussian/binomial/Poisson fit, convergence, effective engine and fixed effects; Gaussian two-stage transformation is optional. |
| `association` | Complete genotype/phenotype population and explicit null-model inputs | Original single-variant Score, SPA, BinomiRare or one-covariate GxE Wald output with complete variant dispositions. CMP dispatch is retained with the original limitations below. |

Tables identify a participant by separate literal `FID` and `IID` columns. Repeated IIDs in different families and literal `NA`/`NaN` identifiers remain distinct. Phenotype rows determine model order. Association requires the same complete participant set as the genotypes; it does not silently drop phenotype rows. Numeric missing/infinite model inputs are refused.

Matrices use a complete `row`, `column`, `value_hex` table plus a separate ordered `FID`, `IID` sample table. `covariance_alignment="phenotype-order"` explicitly permits a derived reordering while retaining the original input. `require-order` refuses mismatches. No identifier repair, inferred subset or silently symmetrized covariance is performed.

Genotypes use variant-major BED plus six-field BIM/FAM sidecars, sorted numeric autosomes 1–22 and distinct A/C/G/T alleles. The profile supports at most 5,000 participants. Original missing calls stay missing in captured inputs; GENESIS can mean-impute derived association inputs according to its own method. The complete hard-call audit and variant disposition report preserve that distinction.

`transport/sample-map.tsv` maps numeric source aliases back to original FID/IID pairs. Association `variant.id` is the one-based input BIM row; use `native-association-variant-dispositions.tsv` with the retained BIM to recover original variant identities. Do not interpret a numeric alias as a repaired original ID.

## Warnings, effective methods and known refusals

**True mixed CMP: original-source failure, FAIL2, no result.** GENESIS 2.42.0 declares a `ncoln.carrier` output column and later writes `n.carrier`, causing an out-of-bounds error in the retained positive-kinship-variance controls. The worker leaves that source unchanged. Preserve the complete error, report it upstream and keep that attempt unusable. A different test or edition needs an explicit new analysis decision and validation.

**Original probability outside 0..1: FAIL2, no result.** A BinomiRare control recalculating every P value produced values slightly above 1. The original value, variant and full source output remain retained without clipping. Do not alter a threshold or replace source values merely to make the failed attempt pass.

**Read effective engines and tests.** Original GENESIS can estimate every variance component as zero and use ordinary GLM. An ordinary-model CMP request can execute BinomiRare; a non-binomial SPA request can execute Score. `WARNINGS.md` and `scientific-audit.json` report these effective methods. Never label them as an executed positive-variance mixed model, CMP approximation or SPA calculation. SPA convergence flags distinguish unrecalculated, converged and nonconverged variants; a failed SPA recalculation can retain its original Score P value.

**PC and model refusals remain refusals.** A projected one-component PC-AiR result can lack dimensions/IDs. Forced partitions can retain related pairs; high-MAF PC-Relate filtering can leave incomplete pair populations. Nonconverged fits, omitted declared covariates and unassessable required fields admit no dependent result. Preserve original outputs; do not reshape, fill, relabel or silently change scientific choices.

PC-Relate covariance cutoffs apply **after scaling** and identify connected components; weaker edges within a component are retained. Unconstrained negative original IBD estimates remain visible. Exact PCA uncomputed variance placeholders, original eigenvector signs and boundary-variance auxiliary NAs remain in the raw objects and typed ledger. Their retention is not permission to use unassessable required fields.

Two-stage requests record an additional explicitly disclosed original first-stage fit. Java checks finite average ranks, normal quantiles, group/sample order and declared residual scaling. Positional row labels introduced by the original second-stage fit stay unchanged; a separate original genotype/model index proves phenotype order. This does not independently refit the model. GxE `GxE.Stat` and `Joint.Stat` are original square roots of Wald quantities; the audit checks squared values against the appropriate chi-square probabilities.

For BinomiRare, native Java compares a complete Poisson-binomial PMF with a separately recorded original `poibin` calculation at absolute tolerance 2e-12. It checks carrier counts and conditional probabilities while preserving the original FFT values and strict smaller-density tail rule. Tiny negative auxiliary FFT entries and density-order roundoff disagreements are disclosed. Native PMFs are not substituted for original P values.

## Inspect results and replay saved evidence

Read `scientific-audit.json` for admission/effective-method checks. Original statistical results remain in `worker/original-fixed-effects.rds`, `worker/original-null-model.rds` and, for association, `worker/original-association.rds`. Matching typed schema/row/cell TSV files support complete Java auditing. For example, in the matching R environment:

```r
result <- readRDS("build/literature-review/genesis-runtime/my-examples-1/results/association-gaussian/worker/original-association.rds")
head(result)
```

Do not add exports or edits inside a sealed bundle. Keep any explicitly derived exports separately with their input provenance. A successful process or provisional status file alone is insufficient; complete `--verify` checks the full inventory, matching worker, identities, transport, scientific contracts, scope and prominent warnings.

The [workflow evidence](genesis-tools-development/README.md) records 61 declared branch dispositions and thirteen root-routed literal examples, including expected scientific refusals. Earlier frozen controls separately cover complete native rank scores, source-field comparisons, matrix partitioning and coherently resealed scientific mutations. These execution counts reuse synthetic input families and are not independent biological datasets. Saved verification runs no R and does not independently refit PCA/projection/kinship/model estimators or every original RDS field. SHA-256 proves byte integrity, not producer authenticity. Logical capacities are not hard OS quotas or production-cohort performance guarantees.

## Five paper-use records

| Paper | Reviewed GENESIS use | Remaining limits |
|---|---|---|
| P128 | Relatedness/population structure and kinship-adjusted PRS association; historical 2.16.1. Supplementary Note 7 explicitly names `pcairPartition`, cutoff 0.044. | Downstream PCA/adjusted-kinship/model calls and full settings remain unstated. Supplementary evidence has its own receipt. |
| P130 | Historical 2.26.0 PC-AiR for ten genetic PCs | Exact partition/PCA settings remain unstated. |
| P142 | Cis/trans GLMM tests with sparse fourth-degree PC-Relate covariance | Exact version, calls and numerical sparsification settings are unstated. |
| P191 | Non-European PC-AiR using separately attributed SNPRelate KING-robust | GENESIS version/settings unstated; PLINK2 and SAIGE steps stay separate. |
| P219 | Twenty genetic PCs after reported common-HapMap3/long-range-LD selection | Exact function/version unstated; PC-AiR is not inferred. |

The [source-role coverage receipt](omics-2023-2026/genesis-source-role-coverage.json) retains primary paragraph hashes and points separately to P128's [supplement review](genesis-workflow-development/P128-supplement-source-review.json). Current 2.42.0 controls do not reproduce historical 2.16.1/2.26.0 or unstated paper analyses. No whole-package, biological-validity, whole-paper or whole-goal completion certificate is claimed.
