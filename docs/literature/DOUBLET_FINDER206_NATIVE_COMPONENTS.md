# Native DoubletFinder 2.0.6 components

**Review required:** these Java methods cover three named operations. Full
artificial-doublet generation, count preprocessing, PCA, parameter sweeps,
SCTransform, original plots and paper reproduction require separately tested
original workflows. They are not established by the native arithmetic tests.
The selected source is [original DoubletFinder 2.0.6](https://github.com/chris-mcginnis-ucsf/DoubletFinder/tree/1b244d8f0d54b4b1cb4365639931bbb16f01e1cd),
whose DESCRIPTION declares CC0. It is not an inferred author environment.

`org.jlinalg.singlecell.DoubletFinder206` provides:

- `modelHomotypic(String[])`: the original sum of squared annotation frequencies.
  Java `null` represents an R missing annotation. Missing annotations stay in the
  denominator, while the literal string `"NA"` remains an observed label. Empty
  and all-missing vectors return zero. Labels are never trimmed or repaired.
- `findPk(String[], double[], double[])`: the original five-column or six-column
  metric table for caller-supplied character pK labels and sweep statistics.
  Supply `null` as the last argument for the branch without AUC. Rows remain in
  first-seen pK order. Variance follows `sd(BCreal)^2`, including the square-root
  and resquare operations. No pK maximum, tie policy, PC subset, expected count
  or rate is inferred. Factor attributes, numeric pK column types and original
  plots require original R execution.
- `classifyExistingPann(double[], int)`: reuse supplied pANN scores to call the
  requested number of doublets in original row order. Ties retain input order,
  signed zeros tie, and unavailable scores sort last. The domain is a nonempty
  score vector with an expected count between zero and the number of cells.

**Original zero-count behavior:** `doubletFinder` indexes `[1:nExp]`; when nExp
is zero, R evaluates `1:0` and still selects the first ranked score. The Java
method preserves this behavior. Choose the expected count explicitly. Do not
assume that zero produces an empty call set.

```java
double homotypic = DoubletFinder206.modelHomotypic(
        new String[]{"typeA", "typeA", "typeB", null}); // 0.3125
var metrics = DoubletFinder206.findPk(
        new String[]{"0.2", "0.01", "0.2", "0.01"},
        new double[]{0.5, 0.2, 0.7, 0.6}, null);
String[] calls = DoubletFinder206.classifyExistingPann(
        new double[]{0.4, 0.8, 0.8, 0.1}, 2);
```

Nonfinite output metrics remain nonfinite. Check
`metrics.requiresNonFiniteReview()` and inspect every affected row before using
the table. A pK row with a missing label has an empty equality subset, matching
original R. If the complete table lacks a finite plotting range, the method
refuses it because the original `find.pK` plotting step errors before returning.
Dimension mismatches, unsupported count domains and finite accumulation overflow
also refuse the whole calculation; use the original workflow for other domains.
Native overflow refusal does not claim that every possible original domain errors.

Finite accumulation uses high precision arithmetic and converts to binary64.
R can use platform extended precision. Native bit equality is not claimed;
Java NaN does not preserve the distinction between R NA and NaN payloads.
The prespecified comparisons use absolute tolerance `1e-14` for homotypic
proportions and `1e-12 * max(abs(reference), 1e-300)` for pK numeric fields.
Missing/nonfinite classifications, complete labels, row order, column names,
population sizes and call vectors are checked separately without numeric tolerance.

The frozen fixture `src/test/resources/doubletfinder206-native-reference.zip`
retains 388 actual original executions: 378 returned component results and 10
original errors. The Java comparison covers all 3,535 numeric/classification
values, every table row and label. Complete original inputs, returned Seurat
objects for reuse-pANN controls, RDS conditions, generated plots, raw consoles,
source archive and source audit remain in the fixture. Portable value trees cover
native-scope inputs/results; reading original conditions and Seurat objects needs
the R runtime. The JUnit test fixes the complete fixture SHA-256.

Run the focused project test in an isolated checkout:

```powershell
.\gradlew.bat test --tests org.jlinalg.singlecell.DoubletFinder206Test --no-daemon
```

The existing `DoubletScores` class implements Scrublet's Bayesian score, a
different method. It is not relabeled as DoubletFinder. The modified
scrattch.hicat 1.0.9 implementation in P059 and the literal reported
DoubletFinder 3.0 edition in P096 remain separate source-resolution work.
This component adds no catalog-family credit by itself; the full 250-paper
review and implementation objective remains open.
