# LDpred2 workflow v2: native Java and original R

**Warnings and required actions:** use only the scope recorded in
[the saved status](ldpred2-workflow-v2-development/STATUS.json). This is a source
build, with no new literature-catalog admission or full-paper replication.
Original auto controls use eight synthetic variants; the 1.1 million variant
workflow controls cover native inf/grid. They do not establish original-auto
performance at that scale, realistic LD behavior or biological validity.

The v2 profile calls Matrix's explicit diagonal and symmetry methods during
original-R input validation. The first v1 original run failed at symmetry
dispatch before model execution. That failed bundle, the published v1 source,
its R worker and all old evidence remain retained. Existing v1 bundles require
their original matching artifact. Current verification deliberately refuses a
different implementation; do not refresh old fingerprints to bypass refusal.

The numerical native implementation and its separate original-source evidence
remain unchanged. See [the native checkpoint](LDPRED2_NATIVE_DEVELOPMENT.md).
The v2 source transition and new controls have separate saved records.

## Build and run

Java 17 or later is needed to build/run the project; these controls used Java 21.
No main-command alias has been added. Use the explicit class:

```powershell
.\gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --help
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --request request.properties --out new-analysis
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdPred2WorkflowCli --verify new-analysis --out new-verification
```

The released v0.4.0 downloads do not include this development workflow. Preserve
the exact source-built artifact and runtime identities needed for later replay.
Output directories must be new. Accept a collection only after its root
`COMPLETED.properties` and successful genuine recomputation; `WORKER_DONE.tsv`
alone is insufficient. A completion marker is not an authenticity signature.

Example native grid request (paths relative to `request.properties`):

```properties
engine=native
method=grid
summary=summary.tsv
ld_variants=variants.tsv
ld_pointers=pointers.bin
ld_rows=rows.bin
ld_values=values.bin
genome_build=synthetic
ld_population=synthetic
models=models.tsv
seed=4096
burn_in=50
iterations=100
java_heap_mb=1024
```

These are illustrative software settings. Supply scientifically justified
heritability, GWAS effective sample sizes, LD population/build and model choices
for a real analysis. Heritability grid entries are actual values, not multipliers
of an unstated estimate. No estimate, chain averaging, best-model choice,
phenotype tuning, downstream prediction or LD resource is supplied automatically.

All tables are UTF-8 with exact tab-separated headers. Summary columns are
`variant`, `effect_allele`, `other_allele`, `beta`, `beta_se`, `n_eff`.
LD labels are `variant`, `effect_allele`, `other_allele`, with unique IDs and the
exact same allele/order declarations. Literal ID `NA` is preserved. The CSC files
contain little-endian int32 column pointers, zero-based int32 row indexes and
float64 correlations. Store both triangles, sorted unique rows, unit diagonals,
and exact symmetry. No matching, allele flipping, LD repair or sample-size
conversion occurs. Population/build labels are caller declarations.
The [upstream reference](https://privefl.github.io/bigsnpr/reference/LDpred2.html)
explains trait-specific effective sample sizes and the distinction between
standardized samples and allele-scale effects.

Grid model columns are `model`, `h2`, `p`, `sparse`; booleans are `true` or `false`.
Model IDs must be unique portable names. Native inf uses `method=inf` and
`heritability=...`; remove grid-only `models`, `seed`, `burn_in`, `iterations`.
Native inf optionally accepts `relative_tolerance` and `solver_iterations`.
Original inf uses its original solver settings and refuses those native options.
Unknown/unused request keys are refused. All defaults and explicit capacities
are captured in `effective.properties`.

## Original runtime setup

The original adapter requires a permitted Linux x86-64 JVM, `prlimit`, `setpriv`,
R, bigsnpr and all its dependencies. Run Java inside Linux/WSL. Analysis never
installs software or substitutes an edition/method. Setup is separate:

```bash
python3 -B tools/literature/ldpred2_workflows/adopt_runtime.py \
  --edition 1.12.2 --archive /absolute/path/bigsnpr_1.12.2.tar.gz \
  --rscript /absolute/prefix/bin/Rscript \
  --r-library /absolute/prefix/lib/R/library \
  --out /absolute/path/new-runtime-record
```

Adoption checks the exact source archive, three original public function bodies
and the recorded installed files twice; it modifies no runtime and runs no model.
It can take several minutes on a mounted Windows filesystem. Keep the installed
runtime fixed and retain the adoption directory. A conda package was used for
1.12.2; the selected 1.12.4 source was compiled with dependencies already present.
The latter needed permitted zlib development headers and the matching library
search path. Failed builds remain retained. The Armadillo build selected its
RcppArmadillo 14.6.3 fallback under C++11; no original scientific source was patched.
An automated clean installation recipe is still pending.

For 1.12.4, use the exact official development archive at commit
`3585b48e1be4d868a06e093ae22aac5be8a6e291`; this is not a claimed CRAN release or
the paper author's exact binary. The archive hashes and source notices are in
the saved native/source-intake evidence. Source and package terms remain GPL-3.
The selected archives are [CRAN 1.12.2](https://cran.r-project.org/src/contrib/Archive/bigsnpr/bigsnpr_1.12.2.tar.gz)
and [official development source reporting 1.12.4](https://codeload.github.com/privefl/bigsnpr/tar.gz/3585b48e1be4d868a06e093ae22aac5be8a6e291).

An original request uses `engine=original`, `runtime=/absolute/path/new-runtime-record`
and `method=inf|grid|auto`. Auto also requires `heritability`, `seed`, and a model
table headed `model`, `p_init`. Optional fields are `burn_in`, `iterations`,
`sparse`, `report_step`, `allow_jump_sign`, `shrink_corr`, `use_mle`,
`alpha_min`, `alpha_max`; paired `p_min`, `p_max` are supported only for the
selected 1.12.4 edition. Original auto's sparse follow-up retains its original
fixed 50 burn-in/100 retained iterations. All original result fields are saved
in `results/original-results.rds`, including standardized sampled coefficients
and legitimate missing alpha values when MLE is disabled. Exported effects use
the allele scale. The adapter does not repair nonfinite estimates.

## Failures, limits and evidence

**SKIP3:** a permitted prerequisite is missing. Skip dependent analysis. Read
`FAILURE.txt` and the console, supply the named permitted runtime/helper and
retry new output. If installation is prohibited, leave that analysis skipped.

**FAIL2:** analysis, verification, capacities or recording failed. Earlier files
are provisional; retain captured inputs, raw R objects, consoles and failure
records. Correct the named cause and retry new paths. A source-divergent model
has a refusal and no accepted weights. Mixed collections report rejections
prominently; zero accepted candidates fails the entire collection. Never fill
nonfinite values or average rejected chains.

JVM heap and original R address-space limits differ. Work/time limits are
monitored; work monitoring is not an OS disk quota. Use adequate permitted
resources; never thin variants or split LD dependencies to fit. Normal
cancellation stops owned children. Hard-killing a Windows parent has no process
job guarantee; inspect its recorded worker command and stop remaining owned work.

The controls compare complete original R objects with independent original
public calls, every exported weight/path/posterior, RNG states, and genuine
recomputation. They cover MLE on/off, sparse follow-up, bounds/sign/shrinkage
options, inf/grid and source nonfinite rejections. Negative controls retain a
deliberately altered owned copy: resealing checksums cannot make its false
weights pass actual recomputation. Genuine source bundles/runtimes are preserved.

Read saved records (not a new analysis) with:

```powershell
python -B tools/literature/ldpred2_workflow_records.py --require-current-source
python -B tools/literature/ldpred2_workflow_records.py --private-base build/ldpred2 --require-current-source
```

Actual original controls require the adopted runtime and can be regenerated into
new directories with `ldpred2_workflow_validation/qualify.py --help`.
The native scale runner is `ldpred2_workflow_scale/qualify.py --help`.
Their saved outputs establish only the explicitly recorded software controls.
Paper-specific versions/settings, a main-command alias, automatic runtime setup,
cumulative catalog review and remaining paper-tool families still require work.
