# Dunnett ordinary one-way development qualification

The [command guide](../../vignettes/dunnett-control-comparisons.md) describes the
input, original runtime, model assumptions, precision settings and recovery
actions. `posthoc-tools dunnett` adds a Java ordinary one-way fit audit with
original **multcomp 1.4-29** single-step, two-sided inference for all treatments
versus one declared control. It supplies a reusable statistical component for
six ordinary-model contexts among nine Dunnett papers in the frozen cohort.

**Warnings remain part of the result.** Missing/forbidden prerequisites skip
dependent analysis with SKIP3. A missed integration tolerance, other original
numerical warning or failed Java check produces FAIL2 and no admitted family.
The reference provider uses R 4.6.1 with mvtnorm/TH.data binaries built under
R 4.6.2; the two original build warnings are retained. Only the explicit
`RECORD_BUILD_WARNINGS` policy permits these startup messages. The `FAIL`
control refuses them. Install matching permitted builds and select `FAIL`
to avoid that mismatch; do not edit the saved warnings away.

The standalone Java API fits the complete population and reports means,
counts, pooled SD/df, treatment effects/SE/t, unadjusted/log Student-t tails and
the shared-control correlation. Its fixtures independently use base R 4.6.1
`lm`, `vcov`, `sigma`, `df.residual` and `pt` for 64 synthetic models across
unbalanced groups, different control positions and scales `1e-100`, 1 and
`1e100`. The fixed original worker supplies multivariate probabilities and
intervals; the Java audit is not an independent multivariate integrator.

Thirty focused tests pass on both Windows and Linux. These are the same 30
tests on two platforms, not 60 different scientific controls. The original R
execution is qualified on Windows only. Linux checks the Java fits, CLI
input/refusal paths and recorded-result integrity without executing R.

`VALIDATION.json` records the actual packaged original-R cases, result integrity
checks, artifact identities and evidence archive. The archive includes the
actual synthetic inputs and original fit/diagnostic outputs, source snapshots,
source census, fixture generator/results, process/test records and failed
attempts. The original provider trees and downloaded dependency archives remain
private and are fingerprinted rather than bundled. This result therefore needs
the same permitted provider for replay; it is not a self-contained R deployment.

The first build retained a mistaken test expectation that a representable
near-maximum residual SD must overflow. The corrected tests separately retain
that valid model and reject an actually overflowing contrast. No production
algorithm was changed to satisfy the mistaken expectation. Earlier provisioning
attempts retained an absent CRAN index MD5 field assumption and a version-string
post-check (`1.4-29` versus R's normalized `1.4.29`); installation itself had
succeeded. The literal package DESCRIPTION version and all intakes remain
recorded.

The first five-group execution failed with the original warning
`Completion with error > abseps` at `maxpts=1e6`, absolute tolerance `1e-6`.
Its original logs and unadmitted outputs are preserved. Two tighter interval
attempts were cancelled through the owned result's `CANCEL` marker; process
cleanup and partial files are retained. A separate request completes at
`maxpts=1e6`, absolute tolerance `1e-4` and interval-probability tolerance `1e-3`.
That wider probability target is explicit, and does not establish the precision
requested by the failed/cancelled analyses. These settings do not
establish that every allowed 256-group input will converge within its budget.

The new source crosswalk checks all 250 primary identities and preserves exact
paragraph hashes and paraphrases. P026/P029/P031/P051/P084/P087 are ordinary-model
component candidates; P087 explicitly names multcomp 1.4-29. P006's method menu,
P034's repeated-measures/Geisser-Greenhouse model, and P217's unequal-variance /
Dunnett-variant ambiguity remain separate work. Exact author observations,
independent units, control groups and historical settings remain required.
The nested older common-statistics census retains its original pending labels;
the new crosswalk distinguishes component availability from paper reproduction.

No Prism/multcomp family badge, whole-paper reproduction, deployment, release
or completion of the full literature goal is claimed. Admitted catalog counts
remain 127 new integrations and 260 missing families.
