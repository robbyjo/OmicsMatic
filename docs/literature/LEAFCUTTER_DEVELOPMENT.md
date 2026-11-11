# LeafCutter native clustering development

The Java class `org.jlinalg.sequence.LeafCutterClusters` implements the
RegTools-BED12 clustering and intron-count stages from the unchanged original
LeafCutter script at commit
`2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb`. It requires Java 17 or newer and
does not require Python, R, Stan or an external program when called from Java.

**LeafCutter remains missing in the public tool catalog.** This component does
not finish the seven papers' workflows. Junction extraction, phenotype
preparation/normalization/PCA and R differential-splicing models remain separate
work. The canonical application JAR has not been replaced or released.

## Use the Java API

Supply complete RegTools BED12 lines and the sample order explicitly. The following
example contains two alternative introns with 30 and 40 supporting reads:

```java
import java.util.List;
import org.jlinalg.sequence.LeafCutterClusters;

var sample = new LeafCutterClusters.Sample("donor-3", List.of(
    LeafCutterClusters.parseRegtools(
        "chr1\t92\t207\tj1\t30\t+\t92\t207\t0\t2\t8,8\t0,107"),
    LeafCutterClusters.parseRegtools(
        "chr1\t92\t307\tj2\t40\t+\t92\t307\t0\t2\t8,8\t0,207")));
var result = LeafCutterClusters.compute(
    List.of(sample),
    new LeafCutterClusters.Options(100_000, 30, 0.001, true),
    LeafCutterClusters.Limits.defaults());
// result.rows(): chr1:100:200:clu_1_+ => 30/70,
//                chr1:100:300:clu_1_+ => 40/70.
```

`Options` requires maximum intron length, read threshold, minimum within-cluster
ratio and chromosome filtering. The example's settings are a declared example;
they are not inferred paper parameters. Read `result.warnings()` and retain
`inputDispositions()`, pooled/refined clusters and complete numerator/denominator
families. Duplicate supplied sample IDs remain distinct by ordinal; this API
does not reproduce the original program's filesystem filename collapsing.

## Source behavior that affects interpretation

- The coordinate arithmetic is exactly `BED start + left block size` and
  `BED end - right block size + 1`. These are LeafCutter's source coordinates;
  no independent interval convention or reference-allele repair is imposed.
- Introns need at least three pooled reads before overlap clustering.
  The read threshold also filters **each intron** during refinement. Two introns
  with 25 reads each do not survive a threshold of 50 merely because the cluster
  contains 50 reads.
- Overlap includes touching endpoints. Linked refinement recognizes either
  shared endpoint. Source singleton and recursive-refinement behavior is retained.
  This component does not emit constitutive singleton introns.
- Chromosome/strand groups keep their first-seen order. Final introns within a
  cluster keep the source's lexical coordinate ordering, including `100` before
  `20`. The component does not replace it with numeric ordering.
- The original `?` strand is excluded from pooling. Extra-block rows and filtered
  chromosome/length rows retain an explicit input disposition and original text.
  Chromosome filtering uses the original exact `1`–`22`, `X`, `Y`, and corresponding
  `chr` names; disabling it is explicit. Identifiers are not case-normalized.
- Duplicate junction rows contribute their original counts. Counts and totals
  use checked int64 arithmetic; values above `2^53` remain exact integers.
  A zero denominator stays the literal count state `0/0`. No pseudocount,
  replacement, imputation, PSI transformation or statistical test is added.

## Warnings and required actions

**Invalid input or exhausted capacity returns no `Result`.** Keep the original
inputs, correct the named malformed-field, negative-count, identifier, overflow
or capacity cause, and retry the whole call. Never retain a partial family,
omit an intron/sample or convert counts through float64 to bypass a failure.
The native parser's valid-input profile requires exactly 12 BED fields, two
blocks, nonnegative coordinates/counts, two block sizes without a trailing
comma, and identifiers without colon, whitespace or NUL. Extra-block rows are
explicit source exclusions before coordinate/count parsing. Some malformed
inputs are printed/skipped by the original script; the native API rejects them
and does not claim malformed-input equivalence.

Use `Limits` to bound samples, source rows, distinct introns, refinement/sort work
and complete output cells. Increasing a bound is an explicit resource decision;
subsampling is not a recovery action. A valid empty result carries a warning and
does not establish absence of biological splicing. These controls do not certify
cohort-scale performance or human transcript interpretation.

The native component needs no installation. For future original-runtime stages,
**SKIP3 means dependent analysis must remain skipped** when exact permitted
software is missing, different or forbidden. Preserve preparation errors and
provide an allowed runtime or install it separately where permitted.
**FAIL2 means no whole-batch result is admitted**, including earlier successful
cases. No R/Stan analysis is admitted by this component's proof.

## Version and validation evidence

The official `v0.2.6` and `v0.2.7` Git tags both contain the same captured
clustering, phenotype and differential-splicing source text, and both declare
R package version `0.2.6`. There is no official `0.2.9` tag in the captured tag
list. The current source declares R package `0.2.9` and is identified by the full
commit above. An R package version does not establish a paper's exact Python
script revision. This Java component follows that pinned current RegTools
script; it does not claim historical paper binary identity.

The complete original script is run unchanged over 88 literal cases on Windows
and Linux. Every native pooled/refined cluster, intron, sample, count and order
is compared exactly on Windows Java 21/25 and Linux Java 21. Each JVM checks
5,456 source rows, 602 retained output rows, 3,718 independent count-conservation
values and 17 invalid/overflow/capacity rejections. There is no floating-point
comparison tolerance for count/order fields. Ten comparator tests ensure that
integer precision loss, changed missing states, omitted/duplicated families and
reordered samples/introns are rejected.

The additive development artifact and complete archived controls are recorded in
[`leafcutter-clusters-development/VALIDATION.json`](leafcutter-clusters-development/VALIDATION.json).
Run the portable evidence reader without installing or executing LeafCutter:

```bash
python -B tools/literature/leafcutter_clusters_evidence.py
```

Original source/Apache notices, R package license declarations, complete control
outputs and earlier failed local intake/build/validator attempts remain retained.
Hashes establish byte integrity, not biological accuracy or authenticated signatures.
The complete integration and the broader 250-paper objective remain open.
