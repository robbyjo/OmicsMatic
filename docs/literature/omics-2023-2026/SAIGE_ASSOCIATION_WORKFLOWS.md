# Original SAIGE single-variant association

**Development command available:** `saige-tools` operation `single` runs original SAIGE 1.5.2 on explicitly declared diploid autosomal PLINK hard calls, using a completed typed `fit-null` bundle. Binary, quantitative IRNT, full-GRM LOCO, sparse calibration, both allele orders, missing-call methods, SPA, approximate Firth effects and efficient resampling have controlled original-runtime checks. Use the current candidate in `saige-numerical-validation.json`; `saige-association-validation.json` preserves the prior112-test milestone. Read [numerical findings and required actions](SAIGE_NUMERICAL_FINDINGS.md) for original sparse storage and nonconvergence limits.

**Still unfinished:** BGEN/VCF dosage input, conditional and gene association, LD export and Meta-SAIGE integration. The SAIGE-GENE and Meta-SAIGE inventory entries remain missing. This bounded tool-level integration does not reproduce every paper's genotype format, historical edition, filters or full analysis.

**Dependencies unavailable, forbidden, unsupported or changed: SKIP.** Use Linux Java inside Ubuntu 24.04 x86_64 WSL, or a matching Linux host, with the admitted private runtime described in [SAIGE_WORKFLOWS.md](SAIGE_WORKFLOWS.md). Analysis never installs dependencies. Exit3 and `WARNINGS.md` identify the dependency and required recovery. Cell Ranger remains excluded by user request.

## Fit once, then reuse the complete bundle

Run the typed sparse-GRM/null workflow first. Keep the entire completed null output directory, including its manifest, recipe, admission/audit files, `null-model.rda`, `variance-ratios.tsv`, `original-model.json` and any staged sparse-GRM inputs. Supply that directory as `model_bundle`. The association command verifies the manifest and member hashes, original source/runtime family, assembly, trait and LOCO settings before passing the model to original R. It snapshots inputs and checks their hashes again before publication. An arbitrary RDA path or user R script is not accepted.

Every model IID must be present in the association genotype dataset. Original SAIGE matches IID alone: duplicate IIDs across different FIDs are rejected. Choose `extra_genotyped_samples: error` or `allow-with-ledger`. Allowed extra participants are explicitly listed as not analyzed; they do not enter cohort AF/MAC calculations. Missing model participants are an error. Refit a suitable complete model cohort rather than silently using the original, incompletely evaluated subsampling option.

## Run the example recipe

Copy `examples/saige/single.json` beside your analysis inputs and edit its paths and scientific declarations. It expects `binary-null-run/` and `association-cohort.bed/.bim/.fam` relative to the recipe. Its GRCh38 label must match the actual reference declaration in your null bundle; this label is not independently established by the wrapper. The chromosome string must match exactly and currently be `1` through `22`. No chromosome alias, sex/ploidy, ancestry or allele-strand conversion is inferred.

```sh
java -Xmx4g -jar /path/to/development-candidate.jar saige-tools \
  --recipe /analysis/single.json \
  --home /home/researcher/.local/share/omics-saige-v1 \
  --out /analysis/single-run
```

Use the candidate named in `saige-numerical-validation.json`. Existing output directories are preserved. An ASCII-named UTF8 `--options-file` object containing string `recipe`, `home`, `out` and optional `timeout` values supports Unicode paths; direct flags override those values. Resource limits bound each model/genotype input group, samples, variants, genotype work, model-export cells, result bytes and owned R-process duration. Set them and the Java heap for the intended data size.

`allele_order` declares how the two BIM labels are supplied to original SAIGE. Output `Allele1`/`Allele2` and signs follow that declaration; they do not establish biological REF/ALT or authorize complementation. Complete sample/coordinate/allele identities are checked.

## QC and imputation are explicit

Original single-marker QC has two stages. Initial missingness, MAF and MAC use observed calls and original floating AF/MAC arithmetic. After imputation and cleanup, the original code checks MAF and MAC again. Exact observed MAC and original floating QC MAC can disagree at a boundary; the wrapper preserves the source operation order and does not add a tolerance.

Declare `best_guess`, `mean` or `major-homozygote`. The last maps to the original option named `minor`, which actually fills minor dosage0, meaning major-allele homozygotes. Declare `dosage_zero_cutoff` and `dosage_zero_max_mac` too. The original cleanup uses an inclusive dosage threshold when its MAC trigger applies; it can affect small mean-imputed calls even with hard-call input. A cutoff0 disables cleanup. A controlled rare variant had observed AC2, AC2.0202 after mean imputation without cleanup, and AC2 with cleanup. Another variant passed initial MAF but failed post-imputation MAF. These cases are retained in the validation evidence.

`variant-ledger.tsv` names every source marker's eligibility/exclusion, exact observed MAC, original floating initial QC MAC/AF, missingness, post-imputation MAC/AF and missing-call minor-dosage fill. All-missing or unresolved-allele markers follow the explicit `invalid_marker_policy`; no such marker is silently repaired. No eligible variants is an error, with its ledger retained. Empty exit0 output is never admitted as a successful association analysis.

## Review the test path and calibration

The recipe declares Firth use/cutoff, SPA cutoff, fast testing, genotype adjustment, low-MAC efficient-resampling cutoff and numerical reportability policy. `center-only-fast` requires fast testing; original fast mode can later retest with covariate projection. `project-covariates` uses the projected path. Some combinations are rejected because the original program would override them. LOCO and the reused sparse variance-ratio choice come from the validated model bundle; the wrapper does not silently switch models or recalibrate ratios.

Native Java checks the complete eligible variant set, cohort counts and post-imputation AF/dosage summaries. It independently computes permitted centered/projected scores from raw selected BED calls and model fields, checks applicable nonsparse variances/ratios, and propagates printed score/variance precision into normal-P bounds. Effect/SE/P algebra is checked where applicable. Final printed P does not independently identify the fast-retest branch; a score-path ambiguity within printing precision is recorded explicitly. The source's reported-AF centering arithmetic and sign flip are retained, including major-allele cases.

Original sparse-PCG, SPA, approximate Firth and efficient-resampling estimators remain upstream computations. These native checks do not independently re-fit those estimators or certify population, ascertainment, ancestry, rare-variant calibration or historical paper equivalence. A single variance ratio calibrated from MAC>=20 markers can be extrapolated below20 by the original program; such use produces a prominent warning. Consider explicitly suitable categorical MAC ratios and assess their calibration. Review original resampling precision/settings and convergence; no historical seed is inferred.

## Reportability, failures and outputs

Successful runs retain `original-associations.tsv` unchanged, plus `associations.tsv` with appended `native_status` and `native_reason`, `association-audit.tsv`, `variant-ledger.tsv`, `genotype-sample-ledger.tsv`, native audit/results/status, source/staged hashes, runtime/process provenance, original logs and prominent `WARNINGS.md`.

With `non_estimable: error`, nonfinite/nonpositive uncertainty, a genotype contrast absorbed by null covariates, or unresolved Firth convergence prevents admitted results. With `retain-with-ledger`, the affected canonical statistical fields are blank and the row is explicitly unreportable; original fields remain in the unchanged source output. If original Firth logs show a convergence deficit without identifying individual failed markers, all potentially affected Firth rows are withheld. Original PCG iteration-limit messages likewise make every potentially affected sparse-path estimate unreportable; strict mode fails, while explicit retention blanks the canonical statistics. Native-checked nonsparse paths remain separately identified. Do not interpret an unreportable row or a source printedP1 as an estimable null effect. `results.json` reports the number of reportable variants and explicitly names a run with none.

Probabilities are validated and retained as decimal text, including values below the binary64 range. Use decimal or log-probability readers downstream; converting such text to a Java/R/Python double can produce0. The independent numerical reference includes original R log-probability behavior; it is separate from the controlled GWAS fixtures.

Exit0 means the workflow completed with review warnings. Exit2 means invalid input, missing/changed model provenance, execution/timeout, output-set/statistical audit or requested reportability failure. Exit3 skips unavailable/unsupported/changed runtime dependencies. Failed/skipped partials are quarantined under `partial/`; top-level status/session and `WARNINGS.md` name the cause and required action. Correct that cause and use a new output directory. Review all original diagnostics and ledgers before scientific use. The canonical executable and public release are unchanged.
