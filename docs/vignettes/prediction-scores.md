# Polygenic and multi-omics prediction scores

Released in v0.3.6. Train a Gaussian ridge/elastic-net score, save portable
weights, and apply frozen weights to a separate cohort. Imported genetic
weights can be used without training in OmicsMatic. Score training can also learn
genotype-to-expression/protein weights for [TWAS/PWAS](predicted-omics.md).

Source builds also register a [held-out study workflow](study-pipeline-tutorial.md#held-out-risk-scores-and-missing-related-phenotype-work)
with explicit group-safe development/tuning/evaluation partitions, direct cohort
matrices, portable complete frozen models, conditional cluster-bootstrap
performance, subgroups and a separate related-trait testing family. Supplied
performance can be reused while only missing related analyses execute. The
standalone commands below retain their documented independent-row CV and file
contracts; study support is newer than the released command functionality.

The study workflow also supports [native binary risk-score development](study-pipeline-tutorial.md#native-binary-risk-score-development-and-frozen-probabilities)
with a joint binomial elastic net, an explicit covariate ridge penalty, tuning
log loss, frozen probabilities/comparator, complete model reuse and the same
held-out related-trait analysis. Tuning identities remain development records
even without final refitting. Predictive weights do not establish association
effects or population absolute-risk calibration.

## Train a score

The input is a CSV/TSV with sample rows and named numeric feature columns. Each
sample ID is unique. Explicitly list the predictors, excluding the outcome and
ID. All features and outcomes must be finite and complete.

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar score-train --input examples/xwas/train.tsv --features x1,x2 --outcome y --lambdas 1,0.1,0.01 --alpha 0 --folds 5 --seed 42 --out build/xwas/score.tsv
```

Alpha 0 fits ridge, alpha 1 fits lasso, and values between them fit elastic net.
A single lambda is prespecified; multiple descending lambdas use shuffled,
seeded K-fold CV confined to the training table. Selection minimizes CV squared
error. Predictor standardization is fitted within each fold, followed by a
full-training fit at the selected lambda. The output coefficients and intercept
are converted back to the original input scale.

All feature selection, batch correction, imputation and other preprocessing
performed outside this command must also respect training/validation boundaries.
The CV splitter assumes independent observations; use externally designed
training/tuning partitions and a prespecified lambda for related subjects,
repeated measurements or temporal data. Gaussian training is for quantitative
outcomes; it is not penalized logistic absolute-risk modeling.

## Save and reuse the model

`OUT` is a weights table with `model variant ea oa weight intercept lambda alpha
training_mean`. Here `variant` means feature ID, including non-genetic omics
features. Non-genetic features have dot placeholders for both alleles.
`OUT.training-ids.tsv` retains training sample IDs for overlap checks;
`OUT.metadata.tsv` records selection, seed, penalty and sample/feature counts.
Keep these sidecars with the model when moving it.

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar score-apply --input examples/xwas/test.tsv --weights build/xwas/score.tsv --outcome y --out build/xwas/predictions.tsv
```

Application joins model feature names to table columns, preserving sample order.
It never estimates new feature means, retunes penalties or refits predictions.
Unknown extra input columns are ignored; missing model features, missing numeric
values or multiple models in one weight file reject.

`OUT` contains sample and score. `OUT.evaluation.tsv` is written when `--outcome`
is supplied. Quantitative metrics are:

- RMSE of the frozen predictions.
- Predictive R2: `1 - SSE_score / SSE_baseline`. By default the baseline predicts
  the training outcome mean. Negative values are retained.
- Descriptive calibration intercept and slope from regressing held-out outcome
  on frozen score. These do not replace predictions or change reported RMSE/R2.

For incremental prediction beyond covariates, fit a baseline model in the
training cohort, carry its frozen predictions into a test-table column, and
pass `--baseline COLUMN`. The R2 then measures reduction in held-out squared
error relative to that baseline. Compare baseline and augmented models on the
same prespecified test cohort. Do not use in-sample baseline predictions.

Evaluation rejects overlapping IDs when the training sidecar is available.
Trained OmicsMatic model evaluation requires its sidecar (or `--training-ids FILE`).
External weight files may lack IDs; independence then remains the caller's
responsibility. Different IDs do not detect relatives or cohort-level overlap.

## Apply genetic weights

For training on genotypes, add `--alleles FILE`, a table with `variant ea oa`.
Every genotype feature must be an effect-allele dosage in [0,2]. The same
training workflow can predict a quantitative phenotype or a molecular trait.

Imported genetic weights need `variant ea oa weight` columns, optionally
`model` and `intercept`. Use exactly one model per file. Preselect/clump variants
or train LD-aware weights upstream as appropriate; the existing `clump` command
can prepare variant selections. The scorer itself does not perform LD pruning.

At application, `--alleles` describes the target table's dosage allele:

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar score-apply --input examples/xwas/target-dosages.tsv --weights examples/xwas/polygenic-weights.tsv --alleles examples/xwas/target-alleles.tsv --outcome case_status --family binary --out build/xwas/polygenic.tsv
```

An allele swap uses `2 - dosage`, preserving the constant term needed for raw
genetic scores. No strand complements are inferred. Resolve genome build and
ambiguous variants upstream. The current genotype contract is diploid.

Binary evaluation reports rank AUC with half credit for ties. It leaves RMSE,
predictive R2 and linear calibration fields NaN because a raw polygenic score
is not necessarily an absolute-risk probability. Both cases and controls must
be present. For quantitative external weights without a training mean, supply
a frozen `--baseline` prediction column.

## Java API and boundaries

```java
PredictionScores.Model model = PredictionScores.train(
    trainingOutcome, trainingFeatures, descendingLambdas, alpha, folds, seed);
double[] predictions = model.predict(testFeatures);
PredictionScores.Evaluation metrics = PredictionScores.evaluate(
    testOutcome, predictions, frozenBaseline, false);
```

Import `org.jlinalg.xwas.PredictionScores`. CLI and Java training use the existing
validated penalized-regression implementation. Dense participant matrices are
held in memory. The new workflow does not implement LDpred/PRS-CS, native PGS
Catalog downloads, genome-wide genotype streaming, grouped CV, logistic score
training or absolute-risk calibration. External weights and phenotype units
must be suitable for the target ancestry and assay.

See [validation](../xwas-followup-validation.md). For broader polygenic workflow
context, see [PRSice](https://github.com/choishingwan/PRSice); this implementation
does not claim its full feature set.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Arthur E. Hoerl and Robert W. Kennard (1970) — Ridge regression: biased estimation for nonorthogonal problems](../CITATIONS.md#hoerl-kennard-1970)
- [Hui Zou and Trevor Hastie (2005) — Regularization and variable selection via the elastic net](../CITATIONS.md#zou-hastie-2005)
- [Jack Euesden, Cathryn M. Lewis, and Paul F. O'Reilly (2015) — PRSice: polygenic risk score software](../CITATIONS.md#euesden-prsice-2015) — [PMID: 25550326](https://pubmed.ncbi.nlm.nih.gov/25550326/) · [PMCID: PMC4410663](https://pmc.ncbi.nlm.nih.gov/articles/PMC4410663/)
- [Jerome Friedman, Trevor Hastie and Robert Tibshirani (2010) — Regularization Paths for Generalized Linear Models via Coordinate Descent](../CITATIONS.md#friedman-glmnet-2010) — [PMID: 20808728](https://pubmed.ncbi.nlm.nih.gov/20808728/) · [PMCID: PMC2929880](https://pmc.ncbi.nlm.nih.gov/articles/PMC2929880/)
- [Sudhir Varma and Richard Simon (2006) — Bias in error estimation when using cross-validation for model selection](../CITATIONS.md#varma-simon-nested-cv-2006)
- [scikit-learn developers (2026) — Cross-validation: nested model selection and grouped observations](../CITATIONS.md#sklearn-grouped-nested-cv)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
