# LDpred2 prepared-input workflows: development snapshot

**Warnings and required actions:** the native Java inf/grid workflow has focused
original-R comparisons and replay tests. The original-R adapter, including auto,
is unfinished and awaits actual end-to-end analysis and replay qualification.
Runtime adoption and syntax checks do not establish analysis correctness. This
snapshot adds no literature-catalog credit and does not complete the 250-paper
objective. Use a matching source build; the published v0.4.0 artifacts do not
contain these additions.

Saved scope and checks are in
[ldpred2-workflow-development/STATUS.json](ldpred2-workflow-development/STATUS.json).

The native numerical kernel has separate, frozen evidence in
[LDPRED2_NATIVE_DEVELOPMENT.md](LDPRED2_NATIVE_DEVELOPMENT.md). The workflow adds
integer R seeds, independent L'Ecuyer streams, the original grid model ordering,
captured inputs, owned worker processes and genuine recomputation. It has no
main-command alias yet. Invoke the class explicitly from a new source build:

```powershell
.\gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --help
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --request request.properties --out new-analysis
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --verify new-analysis --out new-verification
```

All output directories must be new. Preserve failed attempts and retry elsewhere.
`--verify` checks every captured file and recomputes scientific results with the
same implementation. A worker receipt alone does not establish completion.
Original-R replay additionally requires the matching adopted runtime identity.

## Native request and inputs

Example `request.properties`, with paths relative to that file:

```properties
engine=native
method=grid
summary=summary.tsv
ld_variants=ld-variants.tsv
ld_pointers=ld-pointers.bin
ld_rows=ld-rows.bin
ld_values=ld-values.bin
genome_build=GRCh38
ld_population=caller-declared-matched-population
models=models.tsv
seed=4096
burn_in=50
iterations=100
java_heap_mb=1024
```

The summary has exactly these tab-separated columns:
`variant`, `effect_allele`, `other_allele`, `beta`, `beta_se`, `n_eff`.
The LD labels have exactly `variant`, `effect_allele`, `other_allele`, in exactly
the same order. IDs must be unique; labels must match exactly. The three binary
files contain little-endian int32 column pointers, int32 row indexes (zero based),
and float64 correlations. Use full CSC with both triangles, sorted unique row
indexes and unit diagonals. No matching, allele flipping, LD repair or sample-size
conversion occurs. Genome build and population are caller declarations.

Grid `models.tsv` has exactly `model`, `h2`, `p`, `sparse` columns. For example
(replace the spacing with tabs):

```text
model   h2    p     sparse
dense   0.3   0.1   false
sparse  0.3   0.1   true
```

`h2` is the actual heritability value, not a multiplier of an omitted estimate.
For native inf, use `method=inf`, replace grid-only keys (`models`, `seed`,
`burn_in`, `iterations`) with `heritability=0.3`. Optional solver settings are
`relative_tolerance` and `solver_iterations`. Unknown or unused keys are refused.
Complete defaults and capacity settings are captured in `effective.properties`.
Raise limits only when permitted resources are available; never alter the LD
dependencies or silently thin variants to fit.

## Original adapter work in progress

The adapter accepts `engine=original`, `method=inf|grid|auto` and an explicit
`runtime` adoption directory. It requires a Linux x86-64 JVM, `prlimit`, `setpriv`,
and an installed original R runtime with dependencies. Auto additionally requires
`heritability`, `seed`, `models`, and a model table headed `model`, `p_init`.
Original R objects retain diagnostics, sampled standardized coefficients and
legitimate alpha NA values when MLE is disabled. No chain averaging, best-model
selection or phenotype tuning is supplied. Original adapter analysis remains
unqualified in this snapshot.

`tools/literature/ldpred2_workflows/adopt_runtime.py --help` describes adoption of
an existing permitted runtime. It does not install software or run an analysis.
It checks the selected archive, three original function bodies and the recorded
runtime files. Supported source identities are CRAN bigsnpr 1.12.2 and official
development commit `3585b48e1be4d868a06e093ae22aac5be8a6e291` reporting 1.12.4.
The latter is not a claimed CRAN release or the paper author's exact binary.
Retain failed builds and old fingerprints; qualify a replacement in new paths.

**SKIP3:** a required permitted prerequisite is unavailable. Skip the dependent
analysis, supply the prerequisite named in the console/`FAILURE.txt`, then retry
with a new output directory. If installation is prohibited, leave it skipped.

**FAIL2:** analysis, verification, capacity or recording failed. Preserve the
inputs, raw consoles, diagnostics and failure record; correct the named cause
and retry new paths. Earlier files are provisional. Rejected grid/auto candidates
have a refusal record and no accepted weights; a mixed collection reports these
explicitly, while a collection with no accepted candidate fails. Never replace
nonfinite results or average rejected chains.

The workflow monitors wall time and work size; work-size monitoring is not an OS
disk quota. JVM heap and R address-space limits differ. Normal cancellation stops
owned children. A hard-killed Windows parent has no process-job guarantee; inspect
the recorded worker command and stop any remaining owned worker.

## Scoped checks

```powershell
.\gradlew.bat :test --tests org.jlinalg.genetics.LdPred2Test --tests org.jlinalg.genetics.LdPred2OriginalTest --tests org.jlinalg.genetics.LdPred2SeedsTest --tests org.jlinalg.cli.LdPred2WorkflowCliTest
```

The workflow fixture records an actual original bigsnpr 1.12.2 whole-grid call
under R 4.3.3, including final RNG state, plus 512 original R seed/stream states.
Tests cover captured-input replay, exact allele-order refusal, mixed/all model
rejection, owned-worker timeout and late recording failure. These are synthetic
software controls; original auto analysis, full-scale workflow qualification,
paper-specific admission and cumulative catalog review remain pending.
