# Hail development: native sample QC

**Development component only. Hail is still missing from the implemented-tool count.**
The native Java sample-QC component has passed comparisons against the unchanged
original Hail 0.2.139 runtime. The released/canonical application JAR does not
contain this development work. There is no public `hail-tools` command yet.
Do not use this checkpoint as evidence that a paper or its complete analysis
has been reproduced.

The catalog remains at **250 papers, 82 new scoped integrations (17 native and
65 upstream adapters), 85 including three pre-existing adapters, and 249 missing
tools**. This component does not increase those counts. The eight Hail paper-role
drafts have not yet replaced the catalog's existing unreviewed entries.

## What the native component does

`org.jlinalg.genetics.HailSampleQc` accepts a complete declared matrix as an
iterable of variant rows and returns sample metrics in the original sample order.
Each row has a reference/alternate allele list and one entry per sample. An entry
contains a haploid or diploid call, optional integer DP/GQ, and an explicit filter
flag. A null call means missing; filtering is a separate state. Duplicate sample
names remain separate by their ordinal position.

The component computes the original call, genotype, singleton and allele-type
counts, call rate, three ratios, and population DP/GQ statistics. A schema flag
controls whether each integer DP/GQ field is present. Hail omits these statistics
for an absent field or a field whose type is not `int32`. Empty statistics retain
NaN means/standard deviations and missing extrema. The original runtime returns
floating-point extrema even for integer inputs; the Java API does the same.

For example, from a build containing the new Java source:

```java
import java.util.List;
import org.jlinalg.genetics.HailSampleQc;

var variant = new HailSampleQc.Row(List.of("A", "G"), List.of(
    new HailSampleQc.Entry(new HailSampleQc.Call(List.of(0, 1), false), 20, 40, false),
    new HailSampleQc.Entry(new HailSampleQc.Call(List.of(0, 0), false), 30, 50, false)
));
var qc = HailSampleQc.compute(
    List.of("sample-A", "sample-B"), new HailSampleQc.Schema(true, true),
    List.of(variant), HailSampleQc.Limits.defaults()
);
System.out.println(qc.get(0).nSingleton()); // 1: the G allele occurs once across samples
System.out.println(qc.get(0).callRate());   // 1.0
```

Java 17 or later is sufficient for this calculation. Python, Spark and Hail are
not required by the native method. It does not read MatrixTable/VDS files, align
reads, choose a paper's QC masks, run GWAS, perform liftover, or submit Batch jobs.
Hail's exact uppercase allele classification is retained; strings and identifiers
are not normalized or imputed. Some multi-base allele pairs are classified as SNPs
by the original Hail function. A star allele is retained as a star allele.

Zero-ploidy and more-than-diploid calls are outside this native input profile.
Missing allele strings, invalid call indices, ragged rows and capacity overflows
are rejected. No result list is returned after a late invalid row. The defaults
permit at most 1,000,000 samples, 10,000,000 rows and 1,000,000,000 entries; callers
can supply explicit positive limits appropriate to their memory and input sizes.

## Validation and provenance

The retained evidence is in
[`hail-sample-qc-development/VALIDATION.json`](hail-sample-qc-development/VALIDATION.json)
and its complete content-addressed `controls.zip`. It includes the fixtures,
all original and native output records, original schemas, actual JVM process
receipts, source snapshots, failed attempts and their recovery descriptions.

The original Python package is Hail **0.2.139-5e5eedb87ff8**, revision
`5e5eedb87ff814916ef08037199fec7b158f999b`, with its original MIT license. The
official PyPI wheel and the critical Python sources were checked against the
tagged upstream repository before execution. The private reference environment
used Python 3.12, Java 21 and Spark 3.5.3. System Python and the existing JDK were
unchanged. The full wheel, dependency archives and runtime stay in private build
storage; this evidence archive does not redistribute those runtimes.

The comparison covers 12 complete matrices, 67 returned samples and 56 allele
pairs on each of Windows Java 21, Windows Java 25 and Linux Java 21. It checks every
field, exact counts, original sample order, optional-field presence, missing and
nonfinite states. Finite statistics use relative and absolute tolerances of
`2e-12`. Each JVM also verifies nine native profile/capacity rejections. These are
synthetic source-behavior checks, not evidence of biological accuracy or cohort
scale. The portable verifier rereads the saved evidence and current source; it
does not rerun the original runtime or Java analysis.

From the repository root, verify the saved component evidence with:

```powershell
python -B tools/literature/hail_sample_qc_evidence.py
python -B -m unittest discover -s tools/literature -p test_hail_sample_qc_validation.py
```

The development collector/build/validation scripts require their explicitly named
private preparation and parent-artifact receipts. They are not a clean-checkout
installer or a public analysis interface. Preserve the receipts and use new output
directories when repeating a run.

## Warnings, failures and next actions

**FAIL2 means the complete requested batch failed.** Earlier successful cases in
that batch are provisional and must not be admitted as results. Keep the original
inputs, outputs, error logs and failed receipts. Inspect `WARNINGS.md`, the named
traceback and process receipt, correct the demonstrated error, and repeat into a
new output folder. Never update a saved hash simply to accept scientific drift.

Development errors are retained: the first reference fixture used an expression
from the wrong MatrixTable source; the first native comparison returned integer
DP/GQ extrema where the original schema returns floating-point extrema. The next
attempts corrected these errors and repeated the complete comparison. An additional
JVM-version receipt check incorrectly required Java 25's version string to contain
a dot; the corrected check accepts the actual `version "25"` spelling. The final
complete comparison includes explicit version receipts from all three JVMs.

**SKIP3 means a required runtime is unavailable or cannot be installed.** Skip the
dependent analysis. If installation is permitted, prepare a separate compatible
environment and rerun health checks before repeating analysis. Do not install into
or modify a previously validated environment. If installation is forbidden, the
user must provide an allowed runtime or keep that analysis skipped. Native Java
sample-QC calculation itself requires no external Hail runtime.

The broader Hail adapter is unfinished. The paper roles include Hail Batch for
RCTD orchestration (P055), variant liftover with Hail 0.2.130 (P191), and reuse of
published MatrixTable summary statistics (P220). These are distinct from genotype
QC or association fitting. Local sample-QC validation establishes none of those
workflows. Batch Service additionally needs the user's permitted cloud credentials,
configuration and billing context before cloud execution can be evaluated.
Do not submit a cloud job from this checkpoint.
