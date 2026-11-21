# Original TwoSampleMR file workflows

The source-built JAR provides `org.jlinalg.cli.TwoSampleMrWorkflowCli` for
original-package **RUN**, offline **VERIFY**, and immutable-parent **REPLAY**.
The pinned profile is TwoSampleMR **0.7.14**, source commit
[`c08ab8a6`](https://github.com/MRCIEU/TwoSampleMR/tree/c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6),
on **R 4.6.1**. The worker checks all 152 source-defined namespace functions
against the original bodies/formals before execution and checks them again
afterwards. It never rewrites the installed namespace.

This is separate from the [native prepared-statistics API](TWOSAMPLEMR_NATIVE_COMPONENTS.md).
The earlier native checkpoint retains its historical scope. Existing MR commands
and their numerical rules remain unchanged; this workflow has no implicit engine
fallback and is not yet registered as a new global command. The released 0.4.0
predates both additions: build current source.

| Operation | Literal input | Original calls |
| --- | --- | --- |
| `MR` | Canonical harmonized package table | `mr`, plus explicitly requested sensitivity calls |
| `HARMONISE` | Canonical exposure and outcome tables | `harmonise_data`, with explicit action 1, 2 or 3 |
| `HARMONISE_AND_MR` | Same two canonical tables | Harmonization followed by MR and requested sensitivity calls |
| `MV_HARMONISE` | Multiple exposures and one outcome | `mv_harmonise_data`, preserving original matrices, names and absent members |

Requested sensitivity stages are `HETEROGENEITY`, `PLEIOTROPY`, `SINGLE_SNP`
and `LEAVE_ONE_OUT`. Their original function defaults are retained in the saved
source and arguments. The source method list is exported exactly; provider-specific
MR-PRESSO, CAUSE, SIMEX, MVMR and TEMR are not renamed as TwoSampleMR estimators.
The accepted method names follow original `mr()`. Positive controls cover the
default estimators and deterministic IVW variants, UWR and sign concordance.
Other method/settings combinations need their own numerical qualification before
paper-specific claims. The current private runtime lacks optional `mr.raps`;
requesting it is a tested dependent-analysis skip, with no substituted estimator.

## Quick start

Use the [three copyable examples and synthetic inputs](../../examples/twosamplemr-workflow/README.md).
Build the executable JAR, set each request's exact `rscript` and `r_libraries`,
and choose a NEW output directory:

```powershell
.\gradlew.bat executableJar
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --request D:/work/mr-demo/mr.json
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --verify D:/work/mr-demo/output/mr-001
```

The [official installation instructions](https://mrcieu.github.io/TwoSampleMR/articles/introduction.html)
can prepare an R library. Reproducing this validation requires the separately
transferred checksummed private runtime, or an independently qualified equivalent.
Check the exact package version and `RemoteSha`; installing whatever a repository
currently serves does not establish this profile. The adapter checks R and
TwoSampleMR identities and source definitions; dependency versions and native
library identities are recorded in the original session provenance, rather than
authenticated as an entire host. A changed dependency environment needs separate
qualification. Downloads and private installs are permitted where the environment
allows them; a missing package is not evidence that installation is forbidden.

Paths in RUN JSON are relative to the request file. Input files are captured
before parsing. No file is overwritten, and output cannot be nested within an
existing retained analysis. General controls use this shape:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "D:/work/mr-demo/output/mr-001",
  "out": "D:/work/mr-demo/output/mr-replay-001"
}
```

REPLAY first verifies its parent, copies its captured input/settings into NEW
output, and records the parent's seal hash. Optional `rscript` and `r_libraries`
bindings can be supplied explicitly when relocating a capture. Parent evidence
and exports remain unchanged. VERIFY needs Java and the source-built JAR; it
does not start R, install software or contact a service.

## Input and output contract

Inputs are UTF-8 TSV with unique column names. SNP and trait identities and trait
labels must be explicit and nonblank. Duplicate `(SNP, trait)` rows and conflicting
labels are refused; no arbitrary duplicate or first-label selection occurs.
Extra text metadata is retained, including empty descriptive cells. Tabs/newlines
within cells are refused. Numeric cells use literal decimals or explicit `NA`,
`NaN`, `Inf`, `-Inf`; numeric missing values are not inferred from empty strings.
SEs must be positive when finite, and finite frequencies/P-values must lie in
`[0,1]`. No clamping or imputation occurs.

Exposure input requires `SNP`, `id.exposure`, `exposure`, `beta.exposure`,
`se.exposure`, `effect_allele.exposure`, `other_allele.exposure`, `eaf.exposure`.
Outcome input uses corresponding `.outcome` names. Multivariable preparation
also requires `pval.exposure`. Already harmonized MR input requires `SNP`, both
trait IDs/labels, four effect/SE columns, and literal `mr_keep` TRUE/FALSE.
Kept MR rows must have finite effects/SEs. Effect scales, units, population,
selection, reference build and earlier preparation belong in `preparation_note`;
the workflow does not exponentiate a beta or interpret a gene identifier as a SNP.

Original harmonization is allowed to change alleles, signs, frequencies and
eligibility according to its declared action. Captured input, the full original
return, harmonized table and original log preserve those changes and exclusions.
These are original-package operations, rather than undeclared Java repairs.
Multivariable `outcome_pval` remains original `NULL` when no outcome P-values were
supplied; the member and warning remain explicit, and no P-value vector is imputed.

Read these files together:

- `analysis-status.json`, `manifest.json`, `WARNINGS.md`: admission, errors and recovery.
- `source-request.json`, `request.json`, `effective-request.json`, `prepared.json`:
  original/captured/effective settings and literal population/capacity summaries.
- `worker/objects/`: complete original call arguments, returns or raised errors,
  and RNG states before/after each call, in RDS.
- `worker/report.json`, `worker/warnings.txt`, `worker/messages.txt`, worker logs:
  original conditions, call/export metadata, runtime identity and populations.
- `results/method-population.tsv`: every requested method/group, retained
  instruments, returned estimate or explicit no-result reason.
- `results/`: exact table copies of original outputs; no synthetic replacement results.
- `capture-files.json`: complete file inventory and SHA-256 integrity, without authentication.

The seed and R RNG configuration are explicit. Default source parameters include
1,000 bootstrap replicates; examples retain that default. RUN limits input bytes,
rows, columns, entries, candidate join rows, conservative work, output growth and
elapsed time. Request capacities are recorded and never met by reducing the
instrument population, methods or bootstrap count. They are logical/monitored
limits, not operating-system memory/disk quotas. Choose adequate R/Java memory
for the declared problem. Timeout cleanup targets only the owned worker tree.

## Verification and limits

The final focused suite has 667 tests: 638 existing source-statistic controls
plus 29 workflow tests. These include seven actual original-package fixtures,
changed values/provider maps/populations after deliberate resealing, missing
returns, changed source/runtime/settings, hidden warnings and parent protection.
Native checks recompute seven deterministic `mr()` estimators and simple/weighted
median points, and verify reported populations and harmonization exclusion flags.
Original complete returns, classes/attributes and RNG endpoints are compared
separately with direct unmodified namespace calls. Actual RUN/REPLAY and failure
controls qualify the normal JAR; see the [current checkpoint](TWOSAMPLEMR_WORKFLOW_CHECKPOINT_2026-11-21.md).

VERIFY does not parse binary RDS, refit stochastic estimators, independently redo
all allele/indel harmonization or detect a coordinated replacement of every
source/input/result. For those numerical claims use the retained original runtime
and a fresh REPLAY/direct-original comparison. Original R execution is qualified
on Windows R4.6.1; Linux evidence here covers saved verification on Java21, not a
Linux R deployment. This is not an author-runtime or whole-paper reproduction.

## Warnings and required actions

**A completed bundle can contain methods with no estimate.** Original `mr()`
omits all-NA/inapplicable methods and multi-SNP Wald when multiple methods are
requested. `method-population.tsv` names every such case. Those rows have no
admitted estimate. Resolve the instrument/design question and choose a NEW
analysis; never infer a result from an absent row. Intentional NA SE for sign
concordance and original absent multivariable P-values must not be filled in.

**FAIL2: NO COMPLETE RESULT.** Invalid design/data, original raised errors,
nonfinite core fields, integrity changes, capacity violations or process failures
retain raw inputs, full available returns/errors and partial outputs. Read the
named cause and original logs, correct it, and retry NEW output. Empty original
returns and precision warnings remain retained; no generic estimator takes over.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted input,
R runtime, package or separately qualified edition, or retain the skip. If the
environment prohibits a needed installation, report that specific prohibition
and skip the affected analysis. Historical paper editions are not silently
replaced with 0.7.14. Cell Ranger remains excluded.

Current missing work includes exact local clumping, historical editions,
additional optional-provider/method qualification, global CLI registration and
guarded catalog admission. The catalog remains at **143 scoped families, 1,616
reviewed uses and 1,104 pending**, with zero new TwoSampleMR family/review credit.
Both literature reports remain off Pages; the full goal remains unfinished.
