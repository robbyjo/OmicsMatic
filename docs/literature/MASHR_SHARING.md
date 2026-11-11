# Native mashr sharing summaries

`org.jlinalg.shrinkage.MashSharing` implements upstream-style significance
selection, significant-condition counts and posterior-mean pairwise sharing.
`MashSignScores` separately implements products of marginal sign scores with an
explicit nominal-P gate. Both are native Java library APIs.

**Development status:** these APIs extend the fitted-posterior toolkit. They do
not yet provide a public summary command, study route, full paper-role coverage,
or the formal composite hypothesis test referenced by P024. Mashr remains missing
in the catalog of 61 completed scoped integrations. No release or deployment is
claimed. The earlier native-fit and original-preparation proofs remain unchanged.

The retained [validation proof](mashr-sharing/VALIDATION.json) records 371 tests
per JVM on Windows Java21/25 and Linux Java21 at 128 MiB. This includes 325
protected tests, 32 fresh original-R reference cases and 14 new unit/stress tests.
The 26,000-feature, 20-condition test covers the complete input population.
[The source audit](mashr-sharing/method-audit.json) pins the original source,
paper paragraphs and reviewed supplementary methods.

## Run the library example

Use PowerShell from the repository root and JDK21. Choose a new class-output
directory when retrying; preserve earlier runs.

```powershell
$sharingJdk = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin'
$sharingJar = (Resolve-Path 'build/literature-review/mashr-runtime/sharing-development-1/package-2/omicsmatic-0.3.7-mashr-development.jar').Path
New-Item -ItemType Directory -Path build/mashr-sharing-example-classes-1 -ErrorAction Stop | Out-Null
& "$sharingJdk/javac.exe" --release 17 -cp $sharingJar -d build/mashr-sharing-example-classes-1 examples/mashr-sharing/Example.java
if ($LASTEXITCODE -ne 0) { throw 'Example compilation failed; retain the diagnostics.' }
& "$sharingJdk/java.exe" -Xmx128m -cp "build/mashr-sharing-example-classes-1;$sharingJar" Example
if ($LASTEXITCODE -ne 0) { throw 'Example execution failed; no example validation passed.' }
```

The first pair has two selected features and one shared feature, giving `0.5`.
Another condition has no selected feature. The deliberately permissive diagnostic
threshold selects a zero/zero ratio, whose result stays undefined. The final
feature has opposite effect directions but a high product of marginal sign
scores, illustrating why these calculations answer different questions.

## Preserve the reported definition

`MashSharing.significance` takes a complete significance matrix, an explicit
condition count, selected zero-based condition indices, a threshold, boundary
mode, limits and cancellation callback. It returns selected zero-based row
indices, ordered stably by the minimum significance, plus counts and minima.
`Boundary.STRICT` matches upstream `get_significant_results` and
`get_n_significant_conditions`. `INCLUSIVE` is an explicit extension for separately
reported rules such as P019's `LFSR <= 0.05`. Repeated selected condition indices
retain the original count behavior; no hidden deduplication occurs.

`MashSharing.pairwise` takes complete posterior-mean and LFSR matrices. For each
upper-triangle pair it selects the union of rows with `LFSR < threshold` in
either condition. It evaluates `factor < FUN(mean_i)/FUN(mean_j) < 1/factor`,
then mirrors that cell to the lower triangle. `Transform.IDENTITY` preserves
signs; `ABSOLUTE` explicitly ignores them. No posterior sampling occurs.

Both inequalities are strict: ratios exactly equal to either boundary do not
share. Factor zero retains literal source division, including infinities,
overflow and underflow; it is not replaced by a generic comparison of signs.
Factor one has no defined shared ratio. No selected rows produces
`EMPTY_SELECTED_POPULATION`; selected `0/0` produces
`UNDEFINED_SELECTED_RATIO`. Both have a `NaN` fraction with a distinct status and
retained selected/shared/undefined counts. Undefined rows are not removed from
the denominator. **Do not convert either state into zero sharing or a biological
nonreplication claim.**

`MashSignScores.calculate` takes LFSR and separate nominal-P matrices, explicit
groups of zero-based condition indices, nominal threshold, minimum nominal
count, score threshold, limits and cancellation callback. A condition occurs
once per group; groups may overlap. The P021 definition uses `prod(1-LFSR)`,
nominal `P < 0.05` in at least two subclasses and a strict score `> 0.9`.
The score, nominal count, eligibility and final decision are returned separately.
Its EN/IN/glial group definitions are paper-specific inputs.

**This product is not an inferred joint-event probability, probability of a
nonzero effect, or proof that all effects have the same direction.** No
independence assumption or paper-specific missing-data rule is inferred.
P024's unresolved formal test is not replaced by this product, and its literal
reported `>=0.05` threshold is not silently changed to `0.95`.

The native product is rounded once from exact binary-double factors, with a
compensated log score retained. This avoids cumulative double-product rounding;
it does not claim bit-for-bit identity to every platform's extended-precision R
accumulator. The reference cases check numeric tolerance and exact decisions.
Exact zero factors and representational product underflow are distinct. A
positive product that rounds to zero has `underflowToZero=true` and a finite log
score. Threshold decisions use the rounded product, so underflow is visible
rather than silently interpreted as a mathematically zero probability.

## Input and resource failures

Inputs must be complete rectangular finite matrices; LFSR and nominal P values
must lie in `[0,1]`. A declared zero-row population is supported. Condition
dimensions, selectors and group membership must remain explicit. The APIs do
not infer NA values, effect directions, unsigned-P conversions or nominal
significance from posterior LFSR.

`MashSharing.Limits` bounds rows, conditions, complete input/selector cells,
estimated work and owned storage. Defaults allow 1,000,000 rows, 256 conditions,
32,000,000 cells, 1,000,000,000 work units and 128 MiB of estimated owned storage;
planned owned storage must also fit half the current Java heap. Each request is
admitted in full. These are capacity checks, not an OS sandbox.

Invalid input or capacity raises an exception; cancellation propagates the
callback exception. No output prefix is returned. Required action: preserve
the request and diagnostics, correct the named cause or explicitly raise only
permitted capacities/heap, and rerun the complete request. The library has no
external dependency to install. The example requires the identified development
JAR; if it is unavailable, skip the example until an independently validated
artifact is provided. The canonical executable under `build/cli` is unchanged.
