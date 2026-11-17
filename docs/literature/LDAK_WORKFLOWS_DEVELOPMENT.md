# LDAK 5.2 workflow development checkpoint

**Source-build development feature; catalog qualification is pending.**
The released OmicsMatic 0.4.0 artifact does not contain this command.
The 250-paper implementation goal remains open. LDAK has not been added to the
admitted integration count of 127 by this checkpoint.

**SKIP3 means the dependent analysis was skipped.** Supply the named missing
runtime/reference or a permitted Linux x86_64 environment, then retry using a
new output directory. Native Windows can verify a captured bundle; original
execution requires Linux x86_64, including an explicitly invoked permitted WSL.

**FAIL2 means no complete result was accepted.** Read `FAILURE.txt`,
`WARNINGS.md`, `DIAGNOSTICS.tsv` and both original consoles when present. Fix
the reported input, convergence, capacity or integrity problem and retry using
new output. Keep failed attempts. Do not edit a bundle or refresh its hashes
to obtain a pass. Require exit 0, `COMPLETED.properties`, and successful verification.

## Implemented commands

Build the current checkout with `./gradlew executableJar` (Windows:
`./gradlew.bat executableJar`). The default application output is
`build/cli/omicsmatic-0.4.0.jar`. Preserve the exact application JAR used to
produce a bundle: verification checks matching method bytecode.

```bash
java -jar build/cli/omicsmatic-0.4.0.jar ldak-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar ldak-tools run --request /absolute/path/analysis.properties --out /absolute/path/new-run
java -jar build/cli/omicsmatic-0.4.0.jar ldak-tools verify --bundle /absolute/path/new-run --out /absolute/path/new-verification
java -jar build/cli/omicsmatic-0.4.0.jar ldak-tools replay --bundle /absolute/path/new-run --out /absolute/path/new-replay
```

`run` captures the exact original executable, license, literal inputs, request,
process arguments, outputs and diagnostics. `verify` checks complete directory
membership and hashes and regenerates native audits without launching LDAK.
`replay` launches the captured original pipeline again and requires identical
scientific output and native audit bytes. Hash integrity and replay do not
establish producer authenticity or biological validity.

Prepare the pinned original 5.2 runtime with the separately invoked
[runtime helper](LDAK_KINSHIP_DEVELOPMENT.md#prepare-the-original-runtime).
Analysis never installs software or downloads a reference implicitly.

## Explicit request choices

Requests use Java `.properties` syntax. Every key for the selected mode is
required; duplicate and unknown keys fail. Use `/` in Windows paths or escape
backslashes according to Java properties syntax. `ldak-tools --help` describes
the execution and scientific restrictions; the exact accepted keys are in
[LdakWorkflowRequest.java](../../src/main/java/org/jlinalg/cli/LdakWorkflowRequest.java).

| Group | Required keys |
|---|---|
| Both modes | `mode`, `executable`, `license`, `execution`, `wall_ms`, `max_bytes`, `max_files`, `upstream_adjustments`, `max_iterations`, `tolerance` |
| `mode=sumher` | `tagging`, `summary`, `check_sums`, `allow_ambiguous`, `inflation`, `solver`, `prevalence`, `ascertainment`, `cutoff`, `max_predictors` |
| `mode=kinship-reml` | `bfile`, `phenotype`, `covariates`, `weights`, `predictors`, `thinning`, `window_r2`, `window_kb`, `alpha_grid`, `alpha_selection`, `constrain`, `hwe_stand`, `block_variants`, `max_numeric_bytes`, `max_samples`, `max_variants`, `max_metadata_bytes`, `max_line_bytes` |

`execution` is `allow` or `forbid`. `upstream_adjustments` is `forbid` or
`allow-recorded`; the latter preserves original behavior and reports every
observed replacement without rewriting the captured inputs. The tutorial's
original SumHer run replaces 18 zero statistics with `1e-6` and floors four
tagging values at one. The forbid policy refuses completion after such a run.

SumHer requires a complete uncompressed original tagging file and prepared
summary columns `Predictor A1 A2 Direction Stat n`. Specify `YES` or `NO` for
both `check_sums` and `allow_ambiguous`. Set `inflation` to `none`, `additive` or
`multiplicative`, and `solver` to `maximum-likelihood` or
`weighted-least-squares`. `prevalence` and `ascertainment` must both be `-` or
both be explicit proportions. `cutoff` is `-` or an explicit proportion.
**LDAK 5.2 does not support `--truncate` for SumHer.** Newer-version documentation
does not change the captured binary's interface; a `truncate` key is rejected.
No unsupported seed option is fabricated.

For kinship/REML, optional `covariates`, `weights` and `predictors` use `-` to
mean absent. `thinning=YES` requires unit weights (`weights=-`) and explicit
`window_r2` and `window_kb`. With `thinning=NO`, both window fields must be `-`.
`alpha_grid` lists explicit comma-separated values. `alpha_selection=all`
retains every fit. `max-alt-likelihood-first-grid-tie` selects the largest
original printed alternative likelihood, retaining the first declared grid
position on a printed tie and requiring common null likelihood/sample count.
The adapter does not infer a paper's grid or selection policy.

## Scientific checks and present limits

Native Java recomputes every captured kinship entry in FP64 and compares the
original float32 matrix using absolute tolerance `2e-7` plus relative tolerance
`2e-7`. It checks exact FID/IID order, predictor selection, weights, scaling and
normalization. Thinning verification checks the disjoint retained/lost/trivial
handoff; it does not independently implement the thinning algorithm. REML and
SumHer checks cover convergence, finite reported estimates, input counts and
declared liability conversion; they do not independently refit those models.
Original negative unconstrained estimates remain unchanged.

The complete public BLD-LDAK reference used in development has 9,997,230 declared
reference predictors, 1,168,975 regression/tagging rows, 9,898,999 heritability
predictors and 66 categories. These are different populations. The current
input audit records each count separately. The public file's 2025 snapshot does
not establish the earlier papers' author reference identity. Full-model controls
use synthetic summaries and do not reproduce a study or calibrate biological
inference. Earlier prototype readers used a misleading metadata label for the
tagging-row count; those readers and evidence are retained separately.

Capacity bounds refuse a workload rather than truncate it. Numeric-buffer
bounds are not JVM RSS limits. Original stage workspace/time monitoring can
overshoot between polls; it is not an operating-system quota. Provide sufficient
heap and disk space for input capture, native audits and complete replay.

Validation is incremental. The latest development build passed 57 Java tests
with no failures or skips. Earlier matching reader artifacts completed genuine
original SumHer and genotype/thinning/REML controls, replay and Windows/Linux
verification, including the complete 66-category BLD-LDAK synthetic control.
Their saved evidence is not silently reassigned to a newer reader. Final
paper-role qualification, evidence closure and the guarded catalog update remain
pending; see the six roles in the [native development guide](LDAK_KINSHIP_DEVELOPMENT.md#six-reviewed-source-roles).
