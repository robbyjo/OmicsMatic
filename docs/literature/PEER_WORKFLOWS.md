# Original dense PEER workflows

The source-built JAR exposes original-runtime PEER RUN, offline VERIFY and
REPLAY through `peer-tools` and standalone `org.jlinalg.cli.PeerWorkflowCli`.
Catalog admission is a separate guarded step. The released 0.4.0 predates
these commands.

The qualified target is [PMBio PEER](https://github.com/PMBio/peer) source commit
[40bc4b2](https://github.com/PMBio/peer/tree/40bc4b2cd92459ce42f44dfe279717436395f3f6),
advertised version 1.3. The source is
[GPL-2.0-or-later](https://github.com/PMBio/peer/blob/40bc4b2cd92459ce42f44dfe279717436395f3f6/LICENSE).
The builder checks the complete archive and 2,526 upstream files, compiles the
unchanged dense core and bundled Eigen/alglib with an owned observer, and emits
an exact local executable binding. It installs no system packages.

The existing native Java `Peer` API remains separate. It uses FP64 and Java's
random generator; its initial X second moment adds a covariance term absent
from this original core's initialization. It has no stock-output qualification
and is not a fallback of this adapter. The original core stores FP32 matrices.
Do not infer native/upstream identity from the shared PEER name.

## Build and run

Build current source with `gradlew.bat executableJar` or `./gradlew executableJar`.
Create a NEW private runtime with the [tested builder and six examples](../../examples/peer-workflows/README.md).
The build needs CMake and g++; Windows WSL transport also needs Python 3 in the
explicit distribution. Download/private installation is authorized where the
environment permits. A missing dependency is not an installation prohibition.

Run a configured request:

```text
java -jar build/cli/omicsmatic-0.4.0.jar peer-tools --request request.json
```

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "profile": "PEER-1.3-40bc4b2-original-fp32",
  "matrix": "expression.tsv",
  "factors": 2,
  "seed": 17,
  "runtime": {
    "executable": "/absolute/path/to/peer_capture",
    "sha256": "COPY_THE_EXACT_SHA256_FROM_RUNTIME_JSON"
  },
  "preparation_note": "Describe normalization, the selected population and the scientific reason for this factor count.",
  "out": "NEW-output"
}
```

Replace the entire runtime object with the builder's runtime.json contents. A
Windows WSL binding contains a Windows drive-backed executable path and explicit
`wsl_distribution`. It is not silently converted to a host-native binding when
moving to another host. Paths in a request are relative to that request file.

The expression TSV starts with `feature_id`, followed by literal sample IDs.
Rows contain unique feature IDs and finite prepared continuous values. Raw RNA
counts are not normalized by this adapter. Optional `covariates` is a sample-row
TSV starting with `sample_id`; rows must already match expression columns
exactly, in order. Optional `measurement_variance` has the same feature/sample
identities and order as expression and nonnegative values. No automatic sample
matching, row dropping, imputation, rank normalization or factor-count choice.

Scientific options are `initialization` (`randn` or `pca`), `add_mean`,
`max_iterations`, `tolerance`, `variance_tolerance`, `alpha_a`, `alpha_b`,
`epsilon_a`, `epsilon_b`, `covariate_precision` and `verbose` (0-3). Defaults are
the source values: randn, false, 1000, 0.001, 1e-8, 0.001, 0.1, 0.1, 10, 100 and 1.
`factors` is the positive number of hidden factors; optional known covariates
and the mean are additional columns. The total must be below both sample and
feature counts. Seed must fit unsigned C srand. C-library random streams vary
by host; the same seed is not a cross-library output guarantee.

The source only monitors bound/variance stopping when verbosity is at least 2
or bound tolerance is positive. A zero tolerance at lower verbosity does not
silently enable monitoring here. A run reaching its iteration limit is FAIL2,
with its finite or failed raw model retained. Verbose mode 3 retains additional
intermediate bound observations.

Capacity controls are `timeout_seconds`, `maximum_input_bytes`,
`maximum_output_bytes`, `maximum_cells` and `maximum_line_bytes`. Defaults are
3600 seconds, 512 MB input, 1 GB worker output, ten million dense cells per
matrix and two million bytes per input line. The driver maximum is 50 million
cells. Exceeding a declared capacity fails explicitly; values are not truncated.

## Results and verification

The capture retains exact input bytes, requests/effective options, worker
command/runtime binding, source/observer resources, initial/final model states,
full histories, original warnings and process diagnostics. prepared.json records
each FP32 conversion count, underflow count and maximum absolute change.
Conversion to the original matrix type is explicit; nonfinite/overflowing
input is refused and no missing value is imputed.

Exports are latent factors, latent loadings, joint fitted-model residuals,
precisions for all source factor columns (known/latent) and feature-noise
precisions. Full original X/W and posterior sufficient statistics remain in
worker/capture. Exported latent columns omit the fixed known-covariate prefix;
they are not a relabeling of all original X columns as hidden factors.

On convergence, the original reported counter/getter history omits the final
update. Both original values and the observed executed-update count are
retained. The observer records original double bounds returned to the unchanged
loop; it does not replace them with the rounded FP32 history for stopping tests.
Across 24 actual source runs, adding this observation preserved all 120 earlier
original raw files byte for byte. Eighteen fixed-iteration cases are retained
partials, not complete analyses.

```text
java -jar build/cli/omicsmatic-0.4.0.jar peer-tools --verify output
```

VERIFY is runtime-free. It checks complete file/hash inventory, packaged source
identity, original/effective request bindings, deterministic intake, all raw
state field populations/dimensions/finite FP32 values, phenotype/known-covariate
intake, selected second-moment/residual/precision arithmetic, monitoring/counter
consistency and every export. It does not refit PEER or authenticate coordinated
replacement. SHA-256 records integrity, not a digital signature.

A REPLAY request is `{"schema_version":1,"mode":"REPLAY","analysis":"parent",
"out":"NEW-replay"}`. An explicit `runtime` object can override the saved
binding for another permitted host. It verifies the parent first, retains
lineage, creates new output and preserves every parent file. A runtime-free
VERIFY control also accepts mode VERIFY and analysis in schema version 1.

## Warnings and remaining scope

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted source,
input, executable or execution environment and retry new output, or retain the
skip if unavailable. Unknown editions are not mapped to this profile.

**FAIL2: NO COMPLETE RESULT.** Preserve raw inputs, initial/fitted/null/partial
objects, histories, warnings and failed attempts. Correct the named data/design,
numerical/convergence, source/integrity, capacity or process cause and retry new
output. Inspect analysis-status.json, WARNINGS.md and every original worker log.
If an owned cleanup error is reported, preserve its exact ledger and token and
inspect only that recorded process group. An interrupted supervisor without
final completion status has no complete admitted result.

The prepared dense scope does not supply sparse prior-guided PEER, author
normalization, factor-count/QTL search, downstream OLS/LMM models, author seeds,
unreported editions or whole-paper reproduction. Supplied GTEx-factor reuse and
sensitivity-only analyses remain distinct source roles. Read the
[18-role primary crosswalk](peer-source-review-proposal/README.md).
Ignored exact runtimes, JARs, primary full text and complete private evidence
need a separate checksummed transfer. The full toolbox goal remains unfinished.
