# Native CADD1.6 linear scoring core

`org.jlinalg.genetics.CaddLinearScoring` evaluates the complete CADD1.6 linear decision function in Java after the original feature annotation and imputation steps. It supports the original non-centering, standard-deviation scaler with float32 features and float64 coefficients/intercept. The actual GRCh37 and GRCh38 models have1,029 and1,028 features respectively.

**This is a model-scoring library component. Full prediction from VCF remains pending until the complete original annotation/reference bundle and workflow are qualified.** The existing `cadd-tools` command continues to provide its separately qualified lookup and calibration components. This library does not create annotations, infer missing features, read or reinterpret an arbitrary model pickle, select transcripts, or establish clinical significance.

```java
// Load the complete original assembly-specific coefficients, trained scales
// and intercept from a trusted, separately verified model definition.
var model = new CaddLinearScoring(coefficients, scales, intercept);
float[] features = completeImputedFeaturesInOriginalOrder;
var prediction = model.predict(features);
double raw = prediction.rawScore();
double uncertainty = prediction.floatingPointEnvelope();
```

The caller must bind the original model, assembly, complete feature order, source imputation and conversion table. Licensed original model parameters remain private and are not bundled. The constructor captures arrays defensively and rejects missing/mismatched arrays, nonfinite parameters and nonpositive scales. Prediction requires the full feature vector. NaN, infinity, float32 scaling overflow, weighted/accumulation overflow or an unbounded numerical envelope throws an explicit exception and returns no complete prediction. A workflow using this component must retain diagnostics and treat these as FAIL2; missing permitted prerequisites must skip dependent analysis with SKIP3. Do not silently fill zeros, drop features/rows or change the model to obtain a result.

The scaler divides each float32 feature by its trained float64 scale and rounds the result to float32 before multiplication. It does not subtract a mean: both original CADD1.6 scalers have `with_mean=false`, `with_std=true`, `copy=false`. `transform(original, destination)` exposes the same values and supports explicit in-place operation. `predict` preserves its input and uses compensated FP64 accumulation. The output is an uncalibrated **decision score, not a logistic probability**.

Source BLAS accumulation order can change final double bits. `Prediction` retains a conservative floating-point envelope for ordinary IEEE754 source dot-product orderings; `lower()` and `upper()` expand its endpoints outward. This envelope excludes subsequent decimal serialization, PHRED lookup and transcript hierarchy. Do not claim exact source raw-bit equality or an unchanged clinical/calibration decision solely from a small raw difference. Validate downstream source formatting and boundaries, preserve any ambiguity/failure and use a separately qualified complete workflow. No automatic probability conversion, pathogenicity threshold, assembly conversion or author-specific mask is applied.

Validation covers both entire original models and both complete published annotated examples:18 GRCh37 annotation rows and12 GRCh38 rows, representing six variants in each assembly. The expected RawScore/PHRED outputs are kept separate from input annotation fields when reconstructing source scoring, preventing circular validation. All original input annotation fields and every row remain intact.

On each of Windows and Linux,8,252 complete feature rows include all-feature positive/negative probes, dense controls, signed zero, tiny/large finite values and all published annotation rows. All **8,487,186 scaled float32 values** match original scikit-learn bit for bit. Java outputs are byte-identical across hosts. Original raw decisions fall inside the recorded numerical envelopes. For the published examples, the maximum absolute raw difference is6.94e-17 (GRCh37) and2.22e-16 (GRCh38). Feeding those Java decisions into unchanged original hierarchy and PHRED stages reproduces **every published field in all30 annotation rows on each host**.

Large artificial feature magnitudes deliberately stress accumulation: their absolute differences can be large while remaining inside the recorded relative numerical envelope. These are full mathematical controls, not biological predictions or proof of identical six-decimal output for every possible input. The qualification retains those differences rather than hiding them. Ten Java contracts and twelve complete real-model failure comparisons check shape, float32 rounding, parameter capture, cancellation, missing/nonfinite data and overflow.

See the [source-bound qualification](literature/cadd-linear-validation/QUALIFICATION.json) and [handover](literature/cadd-linear-validation/HANDOVER.md). The broader250-paper goal remains active, and this expansion receives **zero additional family or paper-review credit**.

Primary sources: [original prediction script](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/src/scripts/predictSKmodel.py), [original complete imputation](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/src/scripts/trackTransformation.py), [original hierarchy](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/src/scripts/max_line_hierarchy.py), [complete workflow](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/Snakefile).
