# Native Java MR-PRESSO: prepared associations and auditable results

**The prepared-association native workflow is qualified within the tested source
profile; catalog admission remains open.** Use the exact retained producer
recorded in `mrpresso-workflow-qualification/STATUS.json`.
Released JARs may predate this command. This guide describes one outcome with one
or more exposures; it does not claim whole-package or whole-paper reproduction.

The native implementation follows MRPRESSO 1.0 at
[`3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87`](https://github.com/rondolab/MR-PRESSO/commit/3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87).
It implements the original leave-one-out Monte Carlo global test, conditional
per-variant outlier test and conditional distortion/corrected analysis. The older
analytic `org.jlinalg.mr.MrPresso` and multiple-outcome `MultivariateMrPresso`
implement different methods. This command never selects either as a fallback.

## Warnings and required actions

**SKIP3 means the dependent analysis was skipped.** Supply the exact permitted
input or complete producer JAR named by the diagnostic, with Java 17 or later.
If installation is prohibited or a prerequisite is unavailable, retain the skip.
R is not a prerequisite for native RUN, VERIFY or REPLAY.

**FAIL2 means no complete result was admitted.** Preserve the request, input,
raw/partial results and failure evidence. Resolve the reported input, numerical,
capacity, deadline or integrity problem, then retry in a NEW output directory.
Read `WARNINGS.md` and `analysis-status.json`. A missing admission seal or completion
marker in an existing capture is an integrity failure. Missing an entire requested
capture or input is a missing prerequisite. Do not interpret either as a result.

**Some original multivariable distortion values exceed one.** Two independently
captured original-R witnesses return 1.7081712062256809 and 1.755813953488372.
The original numerator can count across multiple exposure columns while its
denominator remains the number of simulations; R also recycles coefficient
vectors in column-major order. Java retains this behavior and the original value.
Such a value is **unreportable as a P value**. Do not clamp it to one, add a
pseudocount, silently change the formula, or use it as evidence about causality.

**An essentially perfect fit does not justify finite coefficient inference.**
At floating-point roundoff scale, original/native SE, t and P may differ greatly
while the coefficient agrees. Java preserves its raw diagnostics and withholds
that inference. Its admission guard compares weighted residual and fitted norms
in the same units, with a ratio threshold of 1e-15. This guard is explicitly native;
it does not assert that the original R function emitted the same warning.

The default policy withholds completion when computed fields are unreportable.
An explicit `"policies":{"allow_unreportable_fields":true}` permits retaining a
complete diagnostic capture. Its status is `COMPLETED_WITH_UNREPORTABLE_FIELDS`,
and invalid fields keep `inference_admitted:false`. This setting never makes those
fields valid inference. Expected conditional corrected NA is separately labelled
`SOURCE_NOT_ESTIMATED` and does not require this override.

Do not repair IDs, replace NA/NaN/Inf, impute omitted associations, replace corrected
NA by the raw fit, or turn a source upper bound into zero. Keep original, failed,
undefined and superseded evidence. Hashes establish byte integrity, not signatures
or the authenticity of the study/input. Verification cannot establish causality.

## A complete synthetic example

From this development checkout, select the exact local producer. The path below
is a build output, not a GitHub release asset:

```powershell
$taskJar = 'build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
java -jar $taskJar mrpresso-tools --help
java -jar $taskJar mrpresso-tools --request examples/mrpresso-native/run.json
java -jar $taskJar mrpresso-tools --verify build/NEW-mrpresso-native-example
java -jar $taskJar mrpresso-tools --request examples/mrpresso-native/replay.json
java -jar $taskJar mrpresso-tools --verify build/NEW-mrpresso-native-example-replay
```

The [example input](../../examples/mrpresso-native/associations.tsv) is synthetic.
The [RUN request](../../examples/mrpresso-native/run.json) requests B=1000 with both
tests enabled and an explicit seed/sample kind. The [REPLAY request](../../examples/mrpresso-native/replay.json)
creates a new capture from saved input/options/state; it actually recalculates.
Relative paths resolve against the request JSON's parent directory. Existing
output directories are refused; edit `out` to a new path before repeating.

For a successful capture, inspect these files:

| File | What to check |
| --- | --- |
| `WARNINGS.md`, `analysis-status.json` | Required actions, completion and invalid inference |
| `mr-results.tsv` | Raw then conditional corrected estimates, with admission flags |
| `global-test.tsv` | Global residual sum of squares, empirical count and bound |
| `outlier-test.tsv` | Present only when the original outlier branch runs |
| `distortion-test.tsv` | Present only when the original distortion branch runs |
| `native-result.json` | Typed finite/nonfinite values, literal IDs, complete-case mapping, exact RNG states |
| `canonical-request.json`, `request-binding.json` | Exact effective settings and original path binding |
| `producer.json`, `source/` | Exact JAR and pinned original R source/license provenance |
| `capture-files.json`, `COMPLETED.json` | File seal and completion marker; require a passing saved VERIFY |

A failing analysis can retain `native-result.json` as raw diagnostics while
omitting `COMPLETED.json`. The presence of a result-shaped file is not admission.
Keep the exact original JAR for later VERIFY/REPLAY; a different producer is
refused rather than silently treated as equivalent. A saved capture can be moved
for VERIFY because historical Windows/POSIX request addresses are interpreted
lexically. REPLAY needs the actual current parent path when creating the child.

## Supply prepared, harmonized associations

Use an unquoted UTF-8 TSV with a unique header and unique, nonempty literal row IDs.
Every row must have the full width; whitespace is not trimmed. Numeric fields
accept decimal/scientific notation and explicit NA/NaN/Inf spellings. Identifiers
such as `NA`, `NaN` and `0001` remain identifiers. Selected mathematical NA/NaN
cells cause original complete-case omission across outcome, exposure and error
columns. Input ordinals and row IDs remain in the result. Nonfinite retained
associations, nonpositive outcome errors, negative exposure errors, aliased or
unresolved designs are refused; they are not repaired or silently dropped.

Bind every column explicitly:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "engine": "native",
  "profile": "MRPRESSO_1.0_3e3c92d",
  "operation": "ANALYZE",
  "out": "NEW-analysis",
  "inputs": {
    "associations": "associations.tsv",
    "id_column": "row_id",
    "beta_outcome": "Y",
    "se_outcome": "SY",
    "beta_exposure": ["X1", "X2"],
    "se_exposure": ["SX1", "SX2"]
  },
  "rng": {
    "kind": "Mersenne-Twister/Inversion",
    "sample_kind": "Rejection",
    "seed": 19
  },
  "options": {
    "OUTLIERtest": true,
    "DISTORTIONtest": true,
    "SignifThreshold": 0.05,
    "NbDistribution": 1000
  }
}
```

This multivariable request has one outcome and two exposures. Column labels in the
prepared Java interface are literal; it does not evaluate R formula expressions.
All selected bindings must be distinct. Harmonize the alleles/effects first using
the intended separately recorded workflow. The original sign folding by the first
exposure is retained; it does not perform allele harmonization, instrument
selection or weak-instrument filtering. Multiple-testing corrections and
study-specific method decisions remain the caller's responsibility.

## Original branches and random streams

Omitted scientific options retain the original defaults: OUTLIERtest=false,
DISTORTIONtest=false, SignifThreshold=.05, NbDistribution=1000. The retained row
count must exceed exposures+2 and be less than B. Outlier tests run only when
requested and global P is below the threshold. In the pinned source, requesting
outlier tests without distortion testing does **not** produce a corrected fit.

Empirical comparisons use strict `>` and count/B, without a +1 correction. A zero
global/distortion count displays `<1/B`; a zero Bonferroni outlier count displays
`<n/B`. The count and resolution remain explicit. If n/B exceeds the significance
threshold, retain the source precision warning and increase B in a new analysis
when appropriate. A bound does not provide a precise smaller P value.

Choose the sampling kind explicitly. `Rejection` and older `Rounding` have distinct
distortion draw streams. Do not infer an author's RNG profile from the paper's
date. To continue a stream, replace `seed` by `state`, an exact 626-integer R
Mersenne-Twister/Inversion `.Random.seed`, including kind and cursor. Kind 10403
requires Rejection; kind 403 requires Rounding. Seed and state are mutually
exclusive. Valid seeds exclude R's `NA_integer_`. Both before/after states are
saved. Complete-case filtering, zero exposure SD and the original distortion
sampling/truncation convention affect draw consumption and remain explicit.

## Capacity, deadlines and provenance

| Optional `limits` field | Default | Maximum |
| --- | ---: | ---: |
| `maximum_input_bytes` | 8,388,608 | 67,108,864 |
| `maximum_rows` | 20,000 | 100,000 |
| `maximum_cells` | 1,000,000 | 8,000,000 |
| `maximum_estimated_work` | 1,000,000,000 | 1,000,000,000,000 |
| `timeout_seconds` | 1,800 | 86,400 |

There are at most 64 exposures, one million distributions and eight million native
distribution/exposure cells. The work estimate includes the effective original
default B. Limits refuse work before calculation where possible; no truncation
or automatic method change occurs. A deadline interrupts and joins the owned
native compute thread. These are workflow checks, not an OS hard memory quota or
process-containment guarantee. Computation uses the Java CPU backend.

Original source, metadata, manuals and GPL-3 license are bundled under
`src/main/resources/org/jlinalg/omics/mrpresso-3e3c92d/`. The weighted model uses
the retained Reference BLAS/LAPACK attribution in `LICENSES/LAPACK-3.12.1-BSD.txt`.
R 4.6.1 original-runtime witnesses are synthetic and generated by unchanged
`MRPRESSO::mr_presso`, with function body/formals checked against the pinned file.
Historical paper editions and settings remain unreported where the primary
source does not establish them. This validation does not reconstruct an author's
whole software environment or reproduce biological findings.

The independent [original-reference comparator](../../tools/literature/mrpresso_workflow/compare_original.py)
compares known synthetic original-R JSON with a native result; it is not a general
saved-capture verifier. Its reports separately retain the perfect-fit and
zero-denominator numerical discrepancies and do not claim inference equivalence
for those fields. The Java saved VERIFY instead checks the whole capture and
recomputes its bounded native analysis without R or network access.
