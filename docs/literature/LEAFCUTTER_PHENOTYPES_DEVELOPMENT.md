# LeafCutter phenotype preprocessing development

`org.jlinalg.sequence.LeafCutterPhenotypes` provides native Java 17+ phenotype
preprocessing for the pinned current LeafCutter source
`2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb`. The API needs no Python, R or Stan.
It retains original counts, identifiers, column order and every filtering
decision alongside derived matrices. Native clustering/counts are documented
separately in [LEAFCUTTER_DEVELOPMENT.md](LEAFCUTTER_DEVELOPMENT.md).

**The full LeafCutter integration remains open and uncounted.** This API performs
preprocessing. PCA, original-runtime execution, compression/indexing, QTL mapping
and R/Stan differential-splicing are separate stages. The canonical JAR and
released application have not been replaced by these development artifacts.

## Call the Java API

```java
import java.util.List;
import org.jlinalg.sequence.LeafCutterPhenotypes;

var a = new LeafCutterPhenotypes.Row("chr1:100:200:clu_1_+", List.of(
    new LeafCutterPhenotypes.Counts(0, 10),
    new LeafCutterPhenotypes.Counts(5, 10),
    new LeafCutterPhenotypes.Counts(10, 10)));
var b = new LeafCutterPhenotypes.Row("chr1:120:200:clu_1_+", List.of(
    new LeafCutterPhenotypes.Counts(10, 10),
    new LeafCutterPhenotypes.Counts(1, 10),
    new LeafCutterPhenotypes.Counts(8, 10)));
var result = LeafCutterPhenotypes.compute(
    List.of("donor-3", "donor-1", "donor-2"), List.of(a, b),
    LeafCutterPhenotypes.Options.sourceDefaults(),
    LeafCutterPhenotypes.Limits.defaults());
```

Read `originalRows()`, `decisions()`, `kept()` and `warnings()`. Each kept row
contains its original ordinal, coordinate text, imputed ratios, standardized
values, inverse-normal values and source statistics. `sortedForSourceFiles()`
provides the original QQ-file row order; it does not reorder the normalized
matrix used as the original PCA input.

## Exact source choices

1. A denominator below one is missing. Original `0/0` counts remain intact.
   Other source ratios are `(numerator + 0.5) / (denominator + 0.5)`; the
   pseudocount is added to **both** terms. This is a derived phenotype, not a
   replacement for the raw intron excision ratio.
2. A row is removed when missing values exceed 40% of samples. Exactly 40%
   is allowed. Missing values in remaining rows receive the observed ratio mean.
3. Rows with population standard deviation below `0.005` are removed. Retained
   rows are centered/scaled across samples using population standard deviation.
4. Inverse-normal ranks are then computed **across retained introns within each
   sample column**. Ties receive average ranks. The plotting-position constant
   is `3/8` for at most ten retained introns and `1/2` otherwise. This differs from
   rank-target quantile normalization and from per-intron inverse-normal scores.

The native reduction preserves NumPy 1.26.4's contiguous float64 pairwise sum
order. Existing JDistLib supplies inverse-normal quantiles. Normalized values
are compared to the original SciPy/scikit-learn runtime with `2e-14` absolute
and relative tolerances. Counts, IDs, missing states, filtering decisions and
row/column order are compared exactly.

**The default chromosome blacklist is literal `X` and `Y`.** It does not match
`chrX` or `chrY`. Supply the desired literal list explicitly; identifiers are
never stripped or changed. Coordinate text, including leading zeroes, is retained.
Source output sorting uses Unicode code point order, numeric start position,
then the original coordinate/ID text. Every chromosome seen in the input remains
recorded, including chromosomes with no retained rows.

Counts above `2^53` remain exact int64 in the original matrix. The source phenotype
algorithm converts them to float64 before adding the pseudocount; derived values
retain that rounding and carry a warning. No silent integer repair is performed.

## Warnings and required actions

**Invalid input or exhausted capacity returns no `Result`.** Preserve the full
input matrix, correct the named cause and retry the entire call. Never omit a
row/sample, round fractional estimates into integer reads, overwrite original
counts or retain a partial family. The native valid-input profile requires
physical nonnegative integer counts with numerator no greater than denominator,
unique nonempty sample/intron IDs, complete column families and portable unchanged
chromosome filenames. The source `chrom` header key cannot be a sample name.
Malformed or ambiguous headers are rejected rather than reproducing silent
dictionary replacement.

`Limits` bounds samples, rows, complete input cells and rank comparisons. These
are algorithm capacities, not operating-system memory quotas or cohort-scale
performance guarantees. Source preprocessing is undefined when every intron is
filtered; this API throws instead of inventing an empty phenotype/PCA result.

**Original PCA warnings remain explicit.** Saved controls include two undefined
explained-variance ratios from zero-variance matrices, represented as `NaN` states.
Do not replace them with zero or interpret arbitrary zero-rank components as
biological covariates. Supply informative data and inspect the original diagnostics
before using a separate PCA workflow. The source PCA treats retained introns as
observations and original samples as features. Its `auto` solver can be randomized;
the control runs use an explicit public NumPy seed and preserve the original solver.

For separate original-runtime stages, **SKIP3 means the dependent analysis stays
skipped** while exact permitted dependencies are missing, different or forbidden.
Install separately where permitted or provide an allowed matching runtime.
**FAIL2 admits no whole-batch result**, including earlier successful stages.
Read and preserve every original warning/error and process receipt.

## Validation evidence

The unchanged original phenotype/PCA script executes 57 literal controls in a
private Python 3.11 environment with NumPy 1.26.4, SciPy 1.11.4 and scikit-learn
1.3.2. All Java results match on Windows Java 21/25 and Linux Java 21: 1,510 source
rows, 1,346 retained rows, 90,872 floating fields and 68,447 exact fields per JVM.
The largest observed floating difference is `1.3322676295501878e-15`. Eighteen
invalid-input/capacity cases are rejected. The original PCA controls retain both
full and randomized solvers, 2,896 geometric equations and explicit undefined
variance states; the Java API does not itself compute PCA.

The artifact, source/notices, complete original/native outputs, runtime identities
and earlier local failures are recorded in
[`leafcutter-phenotypes-development/VALIDATION.json`](leafcutter-phenotypes-development/VALIDATION.json).
Verify retained evidence without installing or executing scientific dependencies:

```bash
python -B tools/literature/leafcutter_phenotypes_evidence.py
```

This component does not establish historical paper script identity, paper-specific
thresholds, biological calibration, an application release or completion of the
full 250-paper objective.
