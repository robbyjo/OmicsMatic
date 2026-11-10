# GENESIS: native ranks and extended workflow development

This checkpoint adds a native Java rank normalisation component and a separate development adapter for original GENESIS 2.42.0. GENESIS remains **missing** in the tool catalog until workflow coverage, examples, the root command and catalog admission are complete. The project still has 96 new scoped integrations (19 native and 77 upstream adapters), plus three existing adapters. The 250-paper implementation goal remains open.

The earlier [partition component](GENESIS_DEVELOPMENT.md) and [six-workflow checkpoint](GENESIS_WORKFLOW_DEVELOPMENT.md) retain their own matching sources and evidence. This extension uses new classes and a new R worker; it does not change those frozen implementations. The released 0.3.7 executable is unchanged.

## Warnings and required actions

**Missing or forbidden dependencies: SKIP3; skip the dependent analysis.** Original execution requires the exact private Windows x86_64 R 4.6.1 / GENESIS 2.42.0 profile. Score.SPA also requires SPAtest 3.1.2; BinomiRare requires poibin 1.6. A potentially mixed CMP request additionally requires COMPoissonReg 0.8.2. Supply permitted prerequisites in a NEW private library and profile; if installation is unavailable or forbidden, leave the analysis skipped. The adapter checks the optional prerequisites before original analysis. Linux can verify saved bundles without R.

**True mixed-model CMP currently returns FAIL2 in the original package. No result is admitted.** Three predetermined synthetic binary family controls converged with positive kinship variance, then original GENESIS failed with an out-of-bounds `n.carrier` assignment. In the retained original `testGeno.R`, the CMP output column is declared `ncoln.carrier` and later written as `n.carrier`. The worker leaves that source unchanged. Preserve the full attempt and report the issue upstream. A different test or software edition requires an explicit new analysis decision and its own validation. Ordinary-regression CMP requests can take the original BinomiRare fallback; that is reported as BinomiRare and does not establish that the true mixed CMP calculation succeeded.

**An original probability outside 0..1 returns FAIL2. No clipping is performed.** Recalculating all BinomiRare probabilities produced values slightly above 1 in a synthetic control. The original values and complete failed attempt remain available. Preserve the attempt and inspect the named variant, original PMF, logs and scientific audit. Do not change a threshold or substitute an edition merely to make that failed analysis pass.

**Read effective methods before interpreting results.** GENESIS may estimate all variance components as zero and use ordinary GLM. It may execute ordinary Score for a non-binomial Score.SPA request, or BinomiRare for an ordinary-model CMP request. These outcomes appear in `scientific-audit.json` and `WARNINGS.md`. SPA flags also distinguish unrecalculated, converged and nonconverged variants; a nonconverged calculation retains the original Score P value.

**FAIL2 admits no partial result.** Nonconvergence, invalid identities, incomplete populations, malformed projected PCs and unassessable required fields are retained and refused. Preserve warnings, stderr, original objects and the complete output tree. Resolve the stated cause and retry into a NEW directory with explicit settings. Successful source-consistency checks do not establish biological validity, historical-paper reproduction or source authenticity.

## Native Java rank normalisation

[`GenesisRankNormalisation`](../../src/main/java/org/jlinalg/genetics/GenesisRankNormalisation.java) implements the finite-input GENESIS `.rankNorm` formula:

```text
qnorm((rank(x, ties.method="average") - 0.5) / length(x))
```

It preserves original row order, merges the two signed zeros as a numeric tie, and supports explicit per-group ranking. Group metadata retains first appearance, including literal `NA`, `NaN` and Unicode labels. Returned arrays are defensive copies. Nonfinite residuals are outside this native profile even when R would assign them ranks. The method fits no null model and infers no groups, missing values or residual variance scaling. Normal quantiles use the project's pinned JDistlib 0.10.2 dependency.

```java
var limits = new GenesisRankNormalisation.Limits(100_000, 100_000_000);
var result = GenesisRankNormalisation.normalize(new double[]{3, 1, 1, 5}, limits);
double[] ranks = result.averageRanks(); // 3, 1.5, 1.5, 4
double[] scores = result.scores();
```

`normalizeByGroup(values, labels, limits)` ranks independently within each caller-supplied group. Capacities cover the complete vector and are checked before sort storage is allocated. They are logical limits, not operating-system memory quotas.

From PowerShell at the repository root, build isolated Java 17-compatible API and external-consumer JARs with the selected installed compiler:

```powershell
python tools/literature/build_genesis_rank_native.py `
  --compiler javac21 `
  --out build/literature-review/genesis-runtime/my-rank-build-1
```

Use a NEW output path. The thin API and consumer require the pinned `build/dependencies/jdistlib-all-0.10.2.jar` separately. A successful build grants no analysis result. The retained original-reference validation covers 74 cases, including four explicit nonfinite refusals, complete vectors up to 100,000 rows, average ties, groups and tail quantiles. Each of Windows Java 21, Windows Java 25 and Linux Java 21 compares all 252,088 finite scores. Native unit tests exercise API and capacity contracts separately.

## Development workflow adapter

Build this extension independently of the released executable:

```powershell
python tools/literature/build_genesis_workflow_tools.py `
  --compiler javac21 `
  --out build/literature-review/genesis-runtime/my-workflow-build-1
java -jar build/literature-review/genesis-runtime/my-workflow-build-1/genesis-workflows-development.jar --help
```

The builder uses the installed Java 21 or Java 25 compiler with `--release 17`. It includes unchanged `jdistlib/` entries and dependency licensing from the pinned project JAR. It downloads and installs nothing. The root `omicsmatic ... genesis-tools` dispatcher is not added by this checkpoint.

The development main class is `org.jlinalg.cli.GenesisWorkflowToolsCli`. Its modes use the explicit request schema described in the earlier [workflow guide](GENESIS_WORKFLOW_DEVELOPMENT.md). This extension adds checks for original two-stage Gaussian normalisation, one-covariate GxE Wald tests, optional binary tests and their effective source dispatch. No arbitrary R expressions are accepted.

```powershell
java -jar build/literature-review/genesis-runtime/my-workflow-build-1/genesis-workflows-development.jar `
  --request path/to/explicit-request.json
java -jar build/literature-review/genesis-runtime/my-workflow-build-1/genesis-workflows-development.jar `
  --verify path/to/completed-bundle
```

Require exit 0, `result_admitted=true` and successful complete `--verify`. Read `WARNINGS.md`, the effective method and all variant dispositions before downstream use. Source requests, inputs, full typed outputs, raw RDS objects, messages and process receipts remain in the bundle. Saved verification launches no R and performs no model refit.

Two-stage validation records an additional explicitly disclosed original first-stage fit. Native Java independently checks its finite average ranks and normal quantiles, sample/group order, declared residual scaling and transformed outcomes. Other original model estimators remain upstream calculations. Positional row labels introduced by the original second-stage object are retained; a separate original genotype-to-model index proves the phenotype order instead of repairing those labels.

GxE `GxE.Stat` and `Joint.Stat` are the original square roots of their Wald quantities. The audit checks the corresponding squared quantities against one- and two-degree-of-freedom chi-square probabilities. It does not relabel the saved statistics as chi-square values.

For BinomiRare, the native audit checks conditional probabilities, carrier counts, expected cases and a JDistlib Poisson-binomial PMF against a separately recorded original `poibin` calculation (absolute tolerance 2e-12). Original FFT entries and the strict smaller-density tail rule are preserved. Tiny negative auxiliary FFT entries and density-order roundoff differences are reported; native values are not substituted for original P values.

## Optional private dependencies

The helper clones the supplied pinned private library into a NEW output tree, retains all existing package bytes, and adds the three exact optional packages from the selected official CRAN Windows R 4.6 repository. It checks archive/member integrity, required dependencies, R minor version and licensing. It refuses a different edition instead of upgrading the existing environment.

```powershell
python tools/literature/extend_genesis_runtime.py `
  --profile path/to/base-runtime-profile.json `
  --repository https://cloud.r-project.org/bin/windows/contrib/4.6/ `
  --out build/literature-review/genesis-runtime/my-optional-runtime-1 `
  --allow-download --allow-install
```

Review `COMPLETED.json`, `WARNINGS.md` and installation logs. This produces dependency setup only; analysis remains unperformed. Use the existing `prepare_genesis_runtime.py` helper with the NEW library to create its own exact profile before requesting a dependent analysis. Never edit a saved profile's file hashes manually. Network errors and unavailable or forbidden packages produce SKIP3. Corrupt or incompatible evidence produces FAIL2. Both require retaining the original attempt and using NEW paths for a corrected attempt.

## Evidence and remaining work

The separate `genesis-extended-workflow-development` evidence bundle retains the matching source snapshots, isolated artifacts, complete synthetic controls, failed attempts and cross-platform saved replays. It does not contain participant data, article prose/PDFs, installed R libraries or downloaded runtime archives. SHA-256 protects byte integrity; it is not an authenticity signature.

Remaining work includes broader branch validation, copyable complete analysis examples, root-command integration, the five GENESIS paper-role reviews and catalog admission. P128 Supplementary Note 7 explicitly names historical GENESIS 2.16.1 `pcairPartition` with cutoff 0.044; other function/version/settings gaps remain unresolved. Running current 2.42.0 controls does not recreate that historical analysis.
