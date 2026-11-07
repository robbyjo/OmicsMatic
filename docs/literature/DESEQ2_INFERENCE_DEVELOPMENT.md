# Original DESeq2 inference: development workflow

The project now has a typed adapter to original DESeq2 1.52.0 on R 4.6.1. It runs multifactor Wald/LRT analyses, explicit contrasts and fold-change thresholds, original VST/rlog transformations, and normal/apeglm/ashr shrinkage. It captures raw inputs, model settings, original warnings/messages, full result tables and runtime provenance. The separate [Java size-factor implementation](DESEQ2_NORMALIZATION_DEVELOPMENT.md) remains available for its documented low-level normalization subset.

**Scope:** this is a local standalone development workflow. The main dispatcher, catalog promotion and semantic review of the 47 DESeq2 paper uses remain pending. It does not certify whole-paper reproduction, older DESeq2 editions, quantifier import/length offsets, pseudobulk assembly, wrapper pipelines or biological design adequacy. The catalog still credits 69 new scoped integrations, with DESeq2 classified as method overlap until the broader integration/review is completed.

## Start and recover

Use the copyable build, private-install and analysis commands in [examples/deseq2-inference](../../examples/deseq2-inference/README.md). They include three synthetic examples and Java-only verification requests. The executable is produced through normal Gradle `executableJar` with an isolated destination, preserving the canonical executable.

The public setup script requires three arguments: **new library**, **new report directory**, and `allow` or `forbid`. It refuses existing paths. With `allow`, it uses the fixed Bioconductor 3.23 repository and CRAN, verifies exact DESeq2 1.52.0 availability, installs into the new private library and runs a small synthetic smoke fit. On Windows it uses package binaries; source installation on other systems also needs the packages' build requirements. No user dataset is analyzed by setup. Review its `STATUS.tsv`, `WARNINGS.md`, installation logs and package versions before using the runtime. Missing or forbidden installation leaves dependent analysis skipped.

Every captured analysis has prominent `WARNINGS.md` and `analysis-status.json`:

| State | Meaning and required action |
| --- | --- |
| `completed-with-warnings` | The original request completed and outputs are admitted. Review original warnings, source missing values, design/contrast settings and provenance before interpretation. |
| `SKIP3_DEPENDENT_ANALYSIS_SKIPPED`, exit 3 | A declared Rscript, library, package, supported edition or source guard is unavailable. Provide/install the named requirement only where permitted. Otherwise skip the dependent request. Choose a new output path when retrying. |
| `FAIL2…`, exit 2 | No result is admitted. Read the named input, identity, rank, contrast, fitting, capacity or process error. Partial tables are retained evidence, not results. Correct the cause and use a new output path. |
| Existing output directory | The command refuses it without overwriting its contents. Preserve that attempt and select a new `out`. |

For a timeout, the adapter terminates its owned worker/process descendants. It does not stop unrelated jobs. Increase `timeout_seconds` only after reviewing the model, data and logs. For a capacity failure, use suitable explicit limits and Java/R memory resources; never silently remove rows, samples or covariates to make a request pass.

## Input and model contract

Paths resolve against the UTF-8 request file's directory. The examples use ASCII request filenames; Unicode gene/sample/factor identities and paths stored inside JSON/TSV were tested. Input TSVs are unquoted tab-delimited records with no control characters in identities. Counts and sample metadata must have unique exact identities in matching order; the adapter never aliases, sorts or renames biological IDs.

`counts` starts with `gene` and sample IDs. Here “gene” can identify a gene, peak, genomic bin or guide: the caller supplies the correct count universe and experimental unit. Values must be nonnegative signed-integer counts. Mathematically integral decimal/scientific notation is accepted without changing its value. Fractional raw counts, negative/nonfinite values, duplicate IDs and inconsistent table widths are errors.

`samples` starts with `sample` and contains one row per count column, in the same order. Declare modeled columns in `variables`, using `kind: "factor"` with ordered exact `levels`, or `kind: "numeric"` with finite values. Numeric covariates are not automatically centered, scaled, imputed or residualized. Extra metadata columns remain in the captured input and are explicitly reported as unmodeled.

Model-column names must be plain identifiers such as `donor`, `condition` or `mitochondrial_fraction`. Designs contain typed terms rather than user R code:

```json
{
  "variables": [
    {"name": "donor", "kind": "factor", "levels": ["d1", "d2", "d3"]},
    {"name": "condition", "kind": "factor", "levels": ["control", "treated"]}
  ],
  "design": {"intercept": true, "terms": [["donor"], ["condition"]]},
  "fit": {"test": "Wald", "fit_type": "parametric"}
}
```

An interaction is a term such as `["sex", "condition"]`, in addition to the desired main-effect terms. `intercept: false` represents a model without an intercept. For an LRT, provide the full `design` plus `reduced_design`; the original package checks the models and rank. The adapter does not remove confounded columns or fabricate biological replication.

R transport uses ASCII gene/sample indices and factor-level codes, avoiding locale-dependent corruption of identifiers. `prepared-inputs.json` records every original identity and mapping; published tables restore original gene/sample identities and order. Factor-level codes are explicit transport metadata. They are not replacements for biological labels. `coefficients.tsv` lists original runtime coefficient names in their actual order, and `model-matrix.tsv` retains its columns.

## Counts, normalization and replication

The default is `units: "raw_counts"` and `count_preparation: "integer_only"`. For an explicitly intended preparation of quantifier-estimated gene counts, declare `units: "estimated_counts"` and `count_preparation: "R_round"`. The original R `round()` operation then runs before the DESeqDataSet constructor. Original estimates and prepared integers are retained. No TPM/CPM-to-count conversion is inferred. The two catalog papers describing TPM input retain that source discrepancy; this adapter does not silently reproduce it as raw counts.

Optional `collapse_by` names a separate metadata grouping column for **technical** replicates. Original `collapseReplicates` sums their prepared integer counts before normalization, using first-appearance group order. Modeled covariates must agree within each group; the adapter refuses ambiguous aggregation. It does not infer technical groups or turn single cells into biological replicates. Explicit sample size/normalization factors cannot be combined with this collapse step; collapse first and supply an unambiguous prepared table if external factors are needed.

Normalization is prepared on **all supplied input rows** before optional `keep_genes` selection. This supports estimating factors on an unfiltered count universe, then fitting a declared subset. The one-column `keep_genes` TSV has header `gene`; listed identities must exist and be unique, and its order is retained.

Available normalization inputs are:

| Input | Contract |
| --- | --- |
| `fit.size_factor_type` | Original dataset-level `ratio`, `poscounts` or `iterate`. |
| `size_factors` | TSV headed `sample` and `size_factor`, in exact sample order; positive finite final factors. |
| `normalization_factors` | A final positive finite gene-by-sample factor matrix with exactly the input count identities/order. It replaces sample-only factors. It does not run EDASeq or tximport for you. |
| `geometric_means` | TSV headed `gene` and `geometric_mean`, in exact gene order. Zero and source nonfinite values are retained for original-function handling. |
| `control_genes` | One-column `gene` TSV selecting exact input genes. Repeats are retained as repeated entries. An empty selection is passed to the original function and may fail its factor assignment. |

Dataset-level `poscounts` computes its own modified means and geometrically centers its factors. This differs from calling the low-level Java function without supplied means. The original dataset method also overwrites supplied geometric means for `poscounts`; the adapter reports that behavior rather than substituting another estimator. The iterative estimator is executed by R and is not independently replayed by Java verification.

## Results, transformations and shrinkage

Each result has a safe filename `id` and an explicit selector. A typical factor contrast is:

```json
{
  "id": "treated_vs_control",
  "selector": {"kind": "factor", "factor": "condition", "numerator": "treated", "denominator": "control"},
  "alpha": 0.05,
  "lfc_threshold": 1,
  "alternative": "greaterAbs",
  "independent_filtering": false,
  "cooks_cutoff": false
}
```

Other selectors are `{"kind":"coefficient","index":2}` with a **one-based** original `resultsNames` index, or `{"kind":"numeric","weights":[0,0,1,1]}` in that coefficient order. Use the actual model/coefficient tables for interaction contrasts; do not guess their order. LRT p-values describe the full-versus-reduced model comparison. A selector can choose the displayed fold change without changing that global LRT null hypothesis.

Result settings include original `alpha`, `lfc_threshold`, `alternative`, `independent_filtering`, `cooks_cutoff`, `p_adjust_method`, `add_mle` and optional result `test`. Supported alternatives are the names in the 1.52.0 source, including `greaterAbs`, `lessAbs`, `greater`, `less` and explicit historical-formula variants. Current `greaterAbs` semantics must not be assumed identical to a paper's older DESeq2 edition. `alpha` controls the original independent-filter optimization; it does not silently filter the exported rows to significant findings. Full selected-row tables are retained.

`cooks_cutoff` can be omitted/`"source-default"`, `false`, `"Inf"` or a nonnegative numeric cutoff. Original all-zero-gene, Cook's-distance and independent-filtering `NA` values remain visible. A finite p-value with missing adjusted p-value can be original filtering behavior. The adapter never replaces missing values, recomputes BH from displayed rows or treats them as successful null tests. Review each result's `-metadata.txt` and `-columns.tsv` files.

Attach `"shrink":{"type":"normal"}`, `"apeglm"` or `"ashr"` to a result to request a separate shrunken table. Shrinkage settings can also declare `lfc_threshold` and `svalue`. Base results remain separate. apeglm requires a coefficient selector; this prevents shrinking a different coefficient from the requested base result. Original normal shrinkage rejects interaction designs. In the tested glmGamPoi LRT configuration, a standard factor contrast also failed in the original package while an explicit coefficient selector worked. That source failure is retained: inspect `worker/coefficients.tsv`, make a deliberate valid selection and rerun to new outputs rather than expecting an automatic fallback.

Transforms are requested separately with `id`, `method`, `blind` and `fit_type`. Methods are `varianceStabilizingTransformation`, fast `vst` with explicit/default `nsub`, and `rlog` with optional `beta_prior_variance`. Fast `vst` is not replaced by full VST when its gene-subset requirements fail. `blind` is passed unchanged. Use `fit.test: "none"` with no result tests for a transformation-only request; it performs normalization and the requested transforms without claiming differential inference.

Other fit settings are `beta_prior` (default false), `use_t` (false), `min_mu` (the original fit-type default) and `min_replicates_for_replace` (7 or explicit `"Inf"`). Original automatic outlier replacement/refitting is retained when requested. `counts-prepared.tsv` and normalized count tables retain the original prepared counts; the fitted R object retains DESeq2's replacement assays and model metadata where applicable.

## Outputs and verification

The capture contains full original `inputs/`, source/effective requests, `prepared-inputs.json`, source files, original worker logs/report/session/package identities, `analysis-status.json`, `WARNINGS.md`, manifests and `capture-files.json`. Public tables include prepared counts, normalized counts, factors, model matrix, coefficients, gene estimates, result tables and transforms. Requested fitted objects are saved as `worker/fitted-dds.rds`; the adapter accepts no input RDS object and Java verification never deserializes it.

`VERIFY` is portable Java-only checking. It checks the complete recorded file population/hashes, packaged source correspondence, typed settings, exact identities/order, prepared counts, published/worker agreement, normalization arithmetic and native/reference factor correspondence where supported. It does **not** rerun negative-binomial fitting/tests, transformations, shrinkage or iterative factor optimization. Coordinated replacement of worker/published statistical tables plus rehashing can fabricate a consistent capture; hashes are not signatures or proof of execution. No biological validity or complete native-DLL provenance is authenticated.

Resource defaults are 20 million count entries, 2 million genes, 50,000 samples, 128 model columns, 512 million captured-input bytes, 2 billion worker-output bytes and a 3,600-second timeout. Capacities are explicit; they are not a hard aggregate R/Java memory guarantee. R can allocate working/model memory before a later check. Use appropriate resources and manageable designs. The process monitor enforces wall time and sampled logical output-file growth, not aggregate RSS.

## Evidence and pending work

Thirteen independent original-package reference cases cover paired and continuous-covariate models, multifactor LRTs, interactions/numeric contrasts, threshold/one-sided tests, all three shrinkage methods, all three transforms, ratio/poscounts/iterative normalization, external factors/matrices, explicit rounding/technical collapse, beta-prior fitting, t-Wald settings, numeric Cook's cutoff/BY adjustment and automatic outlier replacement.

The packaged adapter completed all 13 requests separately on Windows Java 21 and 25. Across 26 original-runtime requests, 264,436 finite numeric values and 2,972 source nonfinite-kind values matched the independent calls; maximum observed relative difference was zero. Thirty-nine explicit Java-only verifications ran on Windows Java 21/25 and Linux Java 21. Thirty-eight typed-contract tests passed through normal Gradle and separately on all three JVMs. Missing runtime/optional package, timeout/owned-process cleanup and two retained original-source failures were checked, and their failed/skipped captures were refused by verification. Existing outputs were preserved.

The clean user-facing runtime installer and literal public examples are validated separately in the [saved evidence](deseq2-inference-development/VALIDATION.json). [Warnings and recovery limits](deseq2-inference-development/WARNINGS.md) remain prominent. Previous smoke/reference failures are retained, including the numeric-formatting correction and the R test-harness partial-name matching correction. No whole-project test-suite, Linux original-runtime fit, historical-paper environment, release, GitHub push or deployment is claimed for this milestone.

The [official package](https://bioconductor.org/packages/3.23/bioc/html/DESeq2.html) and retained [release source](https://github.com/bioc/DESeq2/tree/RELEASE_3_23/R) define the original behavior. The adapter guards 16 loaded function bodies/formals against the bundled source. LGPL source notices and licenses are retained. Reported paper settings/editions and unsupported pipeline roles must still be curated before catalog promotion; the full project goal remains active.
