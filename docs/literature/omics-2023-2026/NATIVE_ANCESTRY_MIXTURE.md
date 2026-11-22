# Native ancestry-mixture likelihood and data audits

**Available Java API:** `org.jlinalg.genetics.AncestryMixtureLikelihood` evaluates supplied ancestry-mixture parameters, estimates the one-component binomial model, and retains printed-parameter uncertainty. It needs no ADMIXTURE binary, emulator or other external runtime. Use the development candidate named in `admixture-native-validation.json`; the canonical executable and release are unchanged.

**Original fitting is available through the separate [admixture-tools development interface](ADMIXTURE_WORKFLOWS.md).** This native API itself does not fit a multicomponent Q/P model, choose K, align replicate components, run CV/bootstrap or identify social/geographic groups. The earlier native-only candidate and its138-test gate remain historical API evidence; `admixture-adapter-validation.json` records the current169-test original-tool integration and exact candidate.

## Counted allele and model units

Q has one row per sample and one column per component. P has one row per marker and the same component order. The counted-allele probability is the Q/P dot product. In the validated original ADMIXTURE 1.3.0/1.4.0 outputs, P describes **BIM allele 2**. A native PLINK reader returning allele 1 counts must therefore supply `2 - allele 1Dosage`, preserving NaN missing calls. Neither BIM label establishes biological REF/ALT or authorizes strand conversion.

Calls use 0/1/2 hard-call coding. Declare model ploidy 2 for diploid binomial weighting. For genuinely haploid weighting, declare ploidy 1 and supply observed alleles homozygously coded as 0/2; heterozygous 1 is rejected. A pseudohaploid 0/2 dataset analyzed with diploid weighting is a different model, with twice the haploid allele-copy weight. Per-sample ploidies are supported explicitly for a supplied marker. Raw tetraploid dosages are not accepted; biological ploidy and upstream encoding cannot be inferred from a BED file.

Missing calls contribute neither allele counts nor likelihood. The native likelihood is the sum of binomial log terms. The constructor explicitly chooses whether to include the constant binomial coefficient; validated ADMIXTURE printed likelihoods omit it. Q rows must be compatible with sum 1 within the caller's declared precision. The API never renormalizes Q or reorders samples, markers or components.

## Example

The complete [NativeLikelihood.java](../../../examples/admixture/NativeLikelihood.java) example uses two anonymous components and no external input files:

```java
var evaluator = new AncestryMixtureLikelihood.Evaluator(
    new double[] {.2, .8, .7, .3}, 2, 2, 0, false);
evaluator.addMarker(new double[] {0, 2}, new double[] {.1, .9}, 2, 0);
var report = evaluator.summary(0, .1);
```

The zero error radii declare exact supplied parameters. For six-decimal original files, the controlled audits use absolute Q/P radii of `0.0000005`. `summary(lambda, epsilon)` separately reports the unpenalized likelihood and the objective with the declared Q sparsity penalty; lambda 0 means no penalty. A small penalty scale is handled without overflowing the parameter/scale ratio.

`estimateOneComponent(calls, ploidy)` returns exact observed-copy counts, their frequency estimate and the unpenalized likelihood. This is the unrestricted one-component binomial MLE, including boundary frequencies 0/1; it does not reproduce every upstream optimizer floor or multicomponent constraint.

## Precision and reportability

The summary retains likelihood/objective bounds induced by the declared printed-Q/P error radii. The central likelihood is diagnostic and may differ from the original value because rounded Q/P values are not exact fitted parameters. Small central probabilities outside [0,1] due to rounding are clipped for that diagnostic and counted; parameters themselves are not changed.

**Printed zero can make a likelihood lower bound negative infinity.** Inspect `precisionUnboundedCells`; do not represent an unbounded interval as a precise reconstruction or convert it into a p value. The API also reports missing cells, observed model alleles and counted copies. It rejects invalid dimensions, impossible Q sums, nonfinite/out-of-range parameters, fractional hard calls, unsupported ploidy and arithmetic overflow. An all-missing one-component estimate or a summary with no observed cells is unavailable.

The implementation uses memory proportional to the supplied Q matrix and processes P/genotypes one marker at a time. The evaluator is intended for a serial marker stream; callers must bound matrix size, decoded work and input identity. Parameter intervals validate arithmetic compatibility, not global optimality, convergence, uncertainty calibration or ancestry interpretation.

## Validation scope

The earlier native-only candidate passed 138 focused tests in 13 suites, strict Java 17 compilation, Javadoc and executable packaging. All 138 tests also pass against its exact packaged classes on Java 21 and 25. Sixteen tests cover the new API.

Twenty original-runtime fixtures across current 1.4.0 and legacy 1.3.0 cover K=1, block relaxation, EM, supervised references, new-target projection, CV, bootstrap, haploid weighting, doubled pseudohaploid input and penalization. Per JVM, native checks cover 1,317,548 observed genotype cells, 70,320 parameter fields and 2,400 exact K=1 marker MLEs; 20 deliberately wrong allele-orientation models are rejected. The legacy EM fixture explicitly stops after100 iterations and is not a convergence certification.

Native checks retain the counted-allele model and printed-parameter precision contract, supervised numerical floors and supplied-label identities, unchanged projection P and bootstrap SE/bias matrix shapes. Original optimization, held-out CV prediction, resampling and uncertainty calibration remain upstream computations. See `admixture-native-validation.json`, [source uses](ADMIXTURE_SOURCE_REVIEW.md) and [runtime warnings](ADMIXTURE_RUNTIME_STATUS.md).
