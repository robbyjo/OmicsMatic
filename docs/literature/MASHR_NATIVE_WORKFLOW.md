# Public native mash workflow

The current source and the validated candidate JAR provide `mashr-tools` for
native fitting and posterior prediction with complete captured populations,
portable replay and scoped result admission. Java performs the analysis; no
external installation or R process is required for this native contract.

**This is not yet the complete mashr integration.** Automatic upstream PCA/ED
preparation, public study routes, sharing/composite hypotheses and eight paper-role
reviews remain open. The catalog still reports 61 integrations and marks mashr
missing. Original-model prediction is supported through the explicit expanded
model format; an RDS file is not directly accepted.

Validation: [machine-readable proof](mashr-native-workflow/VALIDATION.json).
The accompanying `mashr-native-workflow/controls.zip` contains source and public
workflow evidence. Its `BASELINE-DEPENDENCY.json` identifies the existing
`mashr-native-execution-controls.zip` by exact SHA-256; keep both archives for the
complete dependency/reference closure. Existing proofs and their artifacts were
preserved without refreshing their hashes.

## Run a complete example

From the repository root, use the candidate at
`build/literature-review/mashr-runtime/public-native-workflow-1/package-4/omicsmatic-0.3.7-mashr-development.jar`.
The executable under `build/cli` has not been replaced. All example, request and
result directories below must be new; preserve an earlier attempt and change the
names when retrying.

```powershell
$mashJar = (Resolve-Path 'build/literature-review/mashr-runtime/public-native-workflow-1/package-4/omicsmatic-0.3.7-mashr-development.jar').Path
java -Xmx128m -jar $mashJar mashr-tools --export-example build/mash-native-example
java -Xmx128m -jar $mashJar mashr-tools --input build/mash-native-example/fit-input --out build/mash-native-fit
java -Xmx128m -jar $mashJar mashr-tools --verify build/mash-native-fit
```

The packaged example has one actual feature, not padded copies. Both effects are
10 and both SE are 1. The prior consists of an explicit zero-effect component
and `100 * ones(2,2)`, with identity residual and Dirichlet parameters `(10,1)`.
Normalization is false and the scale grid is explicitly 1. Fitted weights are
approximately `(0.9,0.1)`; both posterior means are `2000/201` and SD are
`sqrt(100/201)`. This feature intentionally belongs to both training and target
populations; target-only rows cannot train the prior.

To predict a different feature without refitting:

```powershell
Copy-Item -LiteralPath build/mash-native-example/predict-input -Destination build/mash-native-predict-input -Recurse
Copy-Item -LiteralPath build/mash-native-fit/execution/model -Destination build/mash-native-predict-input/model -Recurse
java -Xmx128m -jar $mashJar mashr-tools --input build/mash-native-predict-input --out build/mash-native-prediction
java -Xmx128m -jar $mashJar mashr-tools --verify build/mash-native-prediction
```

Copy `execution/model`, rather than an entire output directory. Its complete
manifest, condition identities and `fully_expanded` representation are required.
Prediction does not renormalize the covariance bases, reapply a scale grid, fit
weights or update the residual matrix. The example target effects are `(2,-3)`.

If rebuilding from source is necessary, preserve the existing executable by
using the development output override with a new directory:

```powershell
.\gradlew.bat -I tools/literature/mashr-development.init.gradle -PmashrDevelopmentOut=build/mash-native-user-build executableJar --no-daemon --no-parallel --no-configuration-cache
```

Set `$mashJar` to that newly printed JAR path. This does not publish a release.

## Prepare inputs and choose methods

The complete schema-2 normalized TSV contract and every setting are documented
in [the native file-execution reference](MASHR_NATIVE_EXECUTION.md#prepare-a-request).
Use the exported files as a runnable starting point. Effects, SE, unique ordered
condition labels and feature identities must agree across files. Numeric-looking
identities such as `0001` remain strings. Missing data, nonfinite values,
nonpositive SE, unknown settings and unused files fail explicitly.

The public native contract includes:

- Explicit training, strong and target flags, allowing overlapping training/target
  membership. Strong rows are recorded but do not perform covariance learning.
- Canonical or supplied covariance bases; explicit normalization, squared scale
  grid, optional zero-effect component and Dirichlet prior.
- Plain native EM with a concave-objective simplex dual-gap convergence gate,
  rather than upstream mixSQP/SQUAREM identity. Full histories are retained.
- Fixed residual, strict all-condition null selection with sample covariance or
  correlation, and ad hoc covariance/correlation EM.
- Complete target means, SD, sign/zero probabilities, LFSR, posterior weights,
  likelihoods, standardized conditional-noise means and full covariance matrices.
- Reusable explicitly expanded models, including models whose covariance bases
  were learned upstream and supplied through the typed model envelope.

A residual proposal that decreases the full unpenalized likelihood is retained
as rejected and the preceding accepted model is returned with a prominent
`RESIDUAL_ROLLBACK` warning. This stopping rule does not certify a global residual
optimum. Reaching a weight or residual iteration limit without the declared
convergence criterion fails instead of admitting the current candidate.

There is no implicit P-value-to-effect/SE conversion, missingness policy,
significance cutoff, factor-sharing test or composite contrast. Negative
probabilities exclude zero-effect mass; LFSR includes it and can exceed 0.5.
An explicitly requested automatic `canonical_pca_ed`, `pca`, `ed` or
`original_mashr` basis yields SKIP3 in this native-only workflow. That is an
unavailable integration capability, not evidence that R installation was denied.
No replacement basis is selected. Skip dependent analysis or supply a justified,
separately prepared explicit basis/model until upstream preparation is integrated.

## Understand completion and verification

**Require exit 0, top-level `COMPLETED.json`, and successful `mashr-tools --verify`**
before downstream use. `execution/result.tsv` remains a provisional development
stage record with `public_bundle_admitted=false`. Only the verified top-level
marker admits the declared native fit/posterior scope. Its
`original_tool_executed=false` is deliberate: Java executed this analysis.

The bundle preserves the full input snapshot, requested limits, population
identities, expanded model, all weight histories, every residual proposal and
accepted/rejected step, complete posterior tables and warnings. Root and model
manifests cover their complete file populations. Empty extra directories, missing
or extra files, changed warnings and altered identities are rejected.

Creation runs the complete native fit/prediction twice, including its admission
replay. Each later verify runs it once from the captured inputs in new, disjoint
scratch space. It does not access the original producer input path or install
software. Successful scratch is removed; failed scratch is retained and named
in the error. Source bundle bytes are checked again after replay and are not
rewritten by verification.

All numeric output/history cells are compared using fixed absolute `1e-10` plus
relative `1e-9` tolerance. Identities, choices, warnings and discrete states are
exact. The verifier also checks the nested manifests, complete file/directory
graph and reported scope/result claims. Rehashing an altered mean, covariance,
model, weight, history or identity does not replace semantic replay. Changes
inside the stated numeric tolerance are allowed and the maximum error is reported
by the verification API. SHA-256 checks integrity, not authenticity or execution
history. Replay agreement does not prove universal numerical accuracy or original
mashr equivalence; inspect every stage numerical warning and the retained
extreme-scale source-boundary evidence.

Posterior tables have no header. Rows follow `execution/target-ids.tsv`, condition
columns follow `execution/conditions.tsv`, and component columns follow
`execution/model/component-names.tsv`. Covariance flattening is column-major.
Each `posterior/loglik.tsv` row contains original-scale log likelihood,
scaled-data log likelihood and retained prior mass. Read all
`execution/WARNINGS.md` and `execution/fit` diagnostics.

## Limits and recovery

Print the complete limits document with:

```powershell
java -jar $mashJar mashr-tools --default-limits
```

Save/edit it as UTF-8 without BOM and pass `--limits LIMITS.json`. All nested
fields are required; misspelled, missing or nonintegral capacities fail. Native
execution defaults to 100,000 rows, 16 MiB total input and 128 MiB output, plus
explicit allocation, Gaussian-work and optimizer-work limits. The public bundle
defaults to 256 MiB and 20,000 total file/directory entries. Planned allocations
are also checked against the Java heap. These are admission limits, not an OS
sandbox. `limits.execution` applies separately to each complete production or
replay; bundle/census limits apply per evidence tree. No prefix or subsample is
used to fit a request inside the limits.

**SKIP3 means analysis skipped.** Read the named unavailable capability and use a
supported workflow; there is no admitted result. **FAIL2 means no result admitted**
for invalid input, integrity, numerical, convergence, capacity, cancellation or
finalization failure. Read the error and the top-level `WARNINGS.md`, then
`execution/WARNINGS.md` where present. All completed partial diagnostics remain
available. Correct the named cause or raise only permitted limits/heap; retry
into new output paths. A complete development-stage table, exit-zero inner stage,
provisional marker or setup record alone cannot admit the public result.

## Validation scope and remaining integration

The actual candidate passes 325 focused tests on each of Windows Java 21,
Windows Java 25 and Linux Java 21 with 128 MiB heaps and no skipped, aborted or
failed tests. They include prior original Gaussian/optimizer/workflow references,
native singleton/residual fitting, complete file execution, public admission,
portable replay, expected skips/failures, resealed semantic tampering and rejection
of an output changed after replay during finalization.
The whole project test suite was not rerun for this checkpoint.

Public command checks export examples, fit and verify a singleton, reuse a model,
verify prediction, reject invalid SE and skip unsupported automatic learning.
Additional checks compare complete native public predictions against an original
mashr PCA/ED-learned model and verify copied fit, prediction and learned-model
bundles across every different producer/verifier JVM pairing. Original R was not
rerun in this public matrix; its earlier complete source references remain pinned.

The earlier fastp evidence and exact dispatcher inverse were also rechecked.
All existing dispatch/scientific bytes reconstruct exactly; no scientific source
was added to the catalog compatibility allowlist. No catalog promotion, release,
deployment or GitHub push was performed. Next work is managed original covariance
preparation, study integration, sharing/composite roles, supplement review and
paper-role curation; Cell Ranger remains excluded by the user's instruction.
