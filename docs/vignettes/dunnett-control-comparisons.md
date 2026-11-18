# Dunnett treatment-control comparisons

`posthoc-tools dunnett` compares every declared treatment with one explicitly
named control. Java fits and independently checks the ordinary one-way model;
original R **multcomp 1.4-29** computes two-sided single-step adjusted
probabilities and simultaneous confidence intervals. Treatment effects and
intervals mean **treatment minus control**. Group order follows first appearance
in the complete input.

**Required runtime:** a permitted R installation and a dedicated library containing
multcomp 1.4-29, mvtnorm, sandwich, TH.data, zoo and their R dependencies.
Missing/forbidden software produces **SKIP3: dependent analysis skipped**.
An original numerical warning, missed integration tolerance or failed native
check produces **FAIL2: no complete family admitted**. Read `FAILURE.txt`,
`WARNINGS.md` and `worker/original-error.txt`; preserve the failed directory and
retry a **new** output after correcting the stated problem. A successful R
process alone does not admit a result.

Use this profile for independent Gaussian observations with a common variance.
Every declared group contributes to the pooled residual variance, including
singleton or individually constant groups when the complete model has positive
residual variance and degrees of freedom. The command does not infer independent
units from technical replicates. Repeated-measures, paired, mixed, nested,
covariate-adjusted and unequal-variance designs require separate qualified
methods. No observations are dropped, imputed or transformed.

## Prepare the observations and request

Save `observations.tsv` as UTF-8 with literal tabs and the exact header below.
Labels are literal and unquoted; blank rows, missing values and nonfinite
numbers fail the complete analysis.

```text
group	value
Control	2.9
Control	3.0
Control	2.5
Control	2.6
Control	3.2
A	3.8
A	2.7
A	4.0
A	2.4
B	2.8
B	3.4
B	3.7
B	2.2
B	2.0
```

Save `dunnett-request.json` beside it, replacing the runtime/library paths with
your accessible installation. `out` must not exist. Relative paths resolve from
the request file. Labels may contain Unicode, quotes or backslashes; the fixed
R worker receives numeric group identifiers rather than executable label text.

```json
{
  "schema_version": 1,
  "input": "observations.tsv",
  "out": "dunnett-result-1",
  "control": "Control",
  "design": "independent-common-variance",
  "confidence_level": 0.95,
  "data_provenance": "Replace with the source of these complete observations",
  "assumptions_declaration": "Replace with the independent units and reason for the common-variance model",
  "r_home": "C:/Program Files/R/R-4.6.1",
  "rscript": "C:/Program Files/R/R-4.6.1/bin/Rscript.exe",
  "library": "D:/omics-r-library",
  "startup_warning_policy": "FAIL",
  "seed": 19870624,
  "maxpts": 1000000,
  "absolute_tolerance": 0.000001,
  "interval_probability_tolerance": 0.00001
}
```

Build current source first; the previously released JAR does not contain this
new command. On Windows use `gradlew.bat` in the first command.

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar posthoc-tools dunnett --request dunnett-request.json
java -jar build/cli/omicsmatic-0.4.0.jar posthoc-tools dunnett --help
```

The selected original two-sided quantile routine supports confidence levels
from 0.5 inclusive to 1 exclusive. The complete input limit is 64 MiB, two million
observations and 256 groups. The native API
`DunnettModel.fit(double[][] groups, int control)` exposes the ordinary fit,
unadjusted Student-t/log probabilities and contrast correlation; it does **not**
itself compute multivariate Dunnett inference.

## Install a permitted provider

Installation is separate from analysis and never silently modifies an existing
runtime. In a permitted R session, use a new dedicated library and the official
source release:

```r
lib <- "D:/omics-r-library"
dir.create(lib, recursive=TRUE)
.libPaths(c(lib, .libPaths()))
install.packages(c("mvtnorm", "sandwich", "TH.data", "zoo"),
                 lib=lib, repos="https://cran.r-project.org")
install.packages(
  "https://cran.r-project.org/src/contrib/Archive/multcomp/multcomp_1.4-29.tar.gz",
  lib=lib, repos=NULL, type="source")
```

The R installation must also provide survival, MASS and codetools. Follow the
actual install errors if those dependencies are absent. Package binaries must
match the chosen operating system and R installation. If installation is not
permitted, skip this dependent analysis and retain its SKIP3 record. A current
multcomp release is not automatically accepted as this selected historical
profile.

`startup_warning_policy=FAIL` stops on package warnings. The explicit alternative
`RECORD_BUILD_WARNINGS` retains only original warnings saying that a package was
built under another R version. It does not suppress other model or numerical
warnings. A successful run with these warnings prints `RUNTIME-WARNING.txt` in
its completion message. Install matching builds and rerun with `FAIL` to remove
the mismatch; never edit the saved warning away.

## Read and verify the result

Require exit 0, `COMPLETED.json` and `analysis.json` with
`result_admitted=true`. `manifest.json` hashes every retained result payload;
the completion file pins that manifest. Hashes provide integrity, not publisher
authentication.

To check the recorded result without executing R:

```text
python -B tools/literature/common_statistics/verify_dunnett_evidence.py --result dunnett-result-1
```

This read-only integrity check rejects missing/changed/unlisted payloads. It does
not reexecute the provider or independently recalculate multivariate inference.

- `native-groups.tsv` maps original labels to numeric indices, counts and means.
- `native-comparisons.tsv` records effects, standard errors, signed t statistics
  and unadjusted/log probabilities from Java.
- `worker/original-comparisons.tsv` records original adjusted probabilities and
  simultaneous confidence limits. Treatment indices map to `native-groups.tsv`.
- `worker/original-fit.rds` preserves the original data, fitted model,
  hypotheses, summary, intervals, RNG state and runtime session. Failed workers
  that catch an R error attempt to retain `partial-original-fit.rds` with the
  objects available then. Cancellation/process termination may leave only the
  already written native fit, inputs and logs.
- Original correlation, model diagnostics, coverage check, session, startup
  warnings and process stdout/stderr remain under `worker/`.
- Original request/input, fixed worker/settings and prepared numeric input are
  captured and checked for changes. Both provider trees are fingerprinted
  before and after execution; provider bytes are not bundled. Replay requires
  the same permitted R installation and package library.

Java checks every original group/count, residual SD/df, treatment effect, SE,
t statistic and correlation entry. It checks probability bounds using independent
Student-t tails and Bonferroni, interval construction using the common critical
value and its univariate/Bonferroni bounds, and an additional original multivariate
coverage calculation. Fit comparisons use relative tolerance `1e-9`, an explicit
SD/mean scale for near-zero fits and 64 ULPs. This audit is independent of the
original fit; it is not an independent multivariate-integration implementation.

The Genz-Bretz integration has a fixed recorded seed and `releps=0`.
`maxpts` and `absolute_tolerance` apply to original adjusted probabilities and
the quantile's probability evaluations. The quantile uses `maxiter=500` and
`interval_probability_tolerance`; its separate coverage check must be within
that probability tolerance plus twice the absolute integration tolerance.
Estimated integration errors are diagnostic estimates, not guaranteed bounds.
An insufficient budget fails the family. Increase `maxpts` explicitly and retry
new output when appropriate; changing a requested tolerance is an explicit
analysis decision. A five-group reference case fails at the default `1e6` points /
`1e-6` tolerance. Tighter interval attempts were cancelled and retained. A separate
request completes at `1e6` points, absolute tolerance `1e-4` and interval-probability
tolerance `1e-3`. Its permitted probability error is wider; it does not establish
the precision requested by those earlier failed/cancelled analyses.

Original numeric adjusted-p zeros and values at most ten times the requested
absolute tolerance trigger `NUMERIC-WARNING.txt`. Their relative precision is
unresolved. Preserve the original values, error estimates and native unadjusted
log probabilities; do not replace zeros with a fabricated floor or claim exact
zero probability.

The worker defaults to a 600-second timeout and a sampled 256-MiB output cap;
both are configurable within `--help` bounds. Create a `CANCEL` file in the
result directory to cancel. These are process supervision and sampled output
checks, with cleanup of discovered owned processes by start identity. They are
not hard memory reservations or an OS sandbox. The explicitly selected trusted
R runtime executes with account permissions.

Qualification and the nine-paper source crosswalk are in
[the development evidence](../literature/dunnett-development/README.md).
Six ordinary one-way source contexts are component candidates; author inputs,
control definitions and historical settings still prevent whole-paper claims.
P006 remains ambiguous, P034 uses repeated measures/Geisser-Greenhouse, and
P217 has unequal-variance/variant ambiguity. Prism and multcomp family-level
admission and the full literature goal remain separate work.
