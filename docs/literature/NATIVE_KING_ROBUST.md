# Native Java KING-robust between-family kinship

`org.jlinalg.genetics.KingRobustKinship` counts aligned diploid hard calls and
computes the **PLINK2 between-family KING-robust estimator** in Java. It supports
explicit participant pairs, byte-vector hard calls and original packed BED rows.
It uses blocks of 64 variants and `Long.bitCount`, preserving joint missingness
and all count categories without estimating allele frequencies or imputing calls.

This is a validated native component. The broader PLINK, PLINK2 and KING
workflows remain unfinished, and their catalog status remains `method-overlap`.
The catalog still records **95 new scoped integrations, 98 including existing
adapters, and 246 missing tools**. No new whole-tool credit is taken here.

## Warnings and required actions

| Result or condition | What to do |
| --- | --- |
| `FINITE` | The formula produced a finite number. Review the joint call count, marker population, data quality and estimator choice before interpreting it biologically. A finite number alone does not establish a relationship. |
| `NO_JOINT_CALLS` | The pair has no jointly called variants. Keep `NaN` and the zero counts; this is **unreportable**, not an unrelated pair or a zero coefficient. |
| `ZERO_SMALLER_HETEROZYGOSITY` | The between-family denominator is zero. Keep the raw `NaN` or `-Infinity` and its disposition. **Do not use the value as a valid relationship estimate.** |
| Negative finite coefficient | Preserve it. The API does not clip or recode negative values to zero. |
| Wrong shape, invalid hard-call code, duplicate pair or exceeded capacity | The API throws an exception. Correct the named inputs or explicit capacity; do not truncate the population, deduplicate pairs or impute calls automatically. |
| Missing or forbidden upstream runtime | **Skip the dependent original analysis.** Download/install its exact prerequisites only where permitted. The Java component itself needs Java 17 or later and does not install or require PLINK/KING. |

**Original filtered output can still be unreportable.** In the retained
PLINK2 2.0.0-b.1.2 fixture, all three tested `--king-table-filter` cutoffs retained
16 `NaN` rows. At cutoff 0.5 the output contained one finite duplicate-pair
coefficient and those 16 `NaN` rows. Do not assume that presence in a filtered
table makes a value valid, or turn an undefined pair into an unrelated pair.

The estimator uses **the smaller of the two jointly called heterozygote counts**.
An average/sum denominator is a different estimator. PLINK IBD `PI_HAT`,
frequency-based GRMs, within-family KING, SNPRelate's configured method,
IBD-segment inference and unrelated-participant selection remain distinct.
Pedigree labels do not change this explicitly selected between-family formula.
The [official PLINK2 documentation](https://www.cog-genomics.org/plink/2.0/distance)
also describes limitations under some mixed-parent-ancestry configurations.
This component does not validate those biological assumptions or repair them.

## Use the Java API

The self-contained development library includes the new estimator and the
unchanged existing `PlinkBedDataset` reader. It is an API JAR, with no standalone
KING or PLINK executable. Source code is also part of the ordinary project tree.

```java
import java.util.List;
import org.jlinalg.genetics.KingRobustKinship;

var pairs = List.of(new KingRobustKinship.Pair(0, 1));
var counter = new KingRobustKinship.Accumulator(
    2, pairs, 1000, 16L * 1024 * 1024);

// Each vector is one caller-selected autosomal variant in the same sample order.
// Codes are 0/1/2 allele-copy hard calls; -1 explicitly means missing.
counter.accept(new byte[] {1, 1});
counter.accept(new byte[] {0, 2});
counter.finish();

var estimate = counter.estimate(0);
System.out.println(estimate.counts());
System.out.println(estimate.coefficient()); // -0.5 for these two explicit rows
System.out.println(estimate.disposition());
```

`acceptPackedBed` accepts a complete variant row in the standard two-bit BED
coding: `00`, `10`, `11` are hard calls and `01` is missing. It reads only the
declared participants; padding outside that population creates no participants
and is ignored without changing the input bytes. This low-level API does not
admit an entire BED/BIM/FAM file or infer chromosome, ploidy, allele, build or
sample identities.

The caller supplies aligned biallelic diploid autosomal genotypes and chooses
the variant/sample population. There is no automatic LD pruning, frequency
filter, imputation, relationship cutoff or sample removal. Pair order determines
the two asymmetric count columns. Reversed duplicate pairs are rejected rather
than silently collapsed. The working-storage bound is an estimate, not a host
RAM quota; the caller must keep its inputs stable and choose practical pair and
variant populations.

## Read a BED file through a fresh Java consumer

[KingBedExample.java](../../examples/native-king/KingBedExample.java) is a complete
public example. It deliberately estimates **the first two FAM participants**
using exactly the supplied variant-ID list and prints both FID/IID identities.
Choose the intended participants explicitly before adopting it for a study.
It requires numeric human-autosome labels 1 through 22, resolved biallelic
metadata and the existing BED reader's structural contracts. Unsupported inputs
fail rather than being renamed, reordered, repaired or silently excluded.

Create `selected-variants.txt` with one literal, unique variant ID per line.
Do not include a header, blank lines or surrounding spaces. Keep the complete
BED/BIM/FAM files unchanged during reading. From the repository root, on Linux:

```sh
javac --release 17 -cp docs/literature/native-king-development/native-king-api-javac21.jar -d /work/king-example-classes examples/native-king/KingBedExample.java
java -cp 'docs/literature/native-king-development/native-king-api-javac21.jar:/work/king-example-classes' KingBedExample /work/cohort /work/selected-variants.txt /work/kinship-new.tsv
```

On Windows:

```powershell
javac --release 17 -cp docs/literature/native-king-development/native-king-api-javac21.jar -d C:/work/king-example-classes examples/native-king/KingBedExample.java
java -cp "docs/literature/native-king-development/native-king-api-javac21.jar;C:/work/king-example-classes" KingBedExample C:/work/cohort C:/work/selected-variants.txt C:/work/kinship-new.tsv
```

The output must be a new file. The example writes UTF-8 IDs, joint counts,
the raw coefficient and its disposition. It is an API example, not a complete
study execution/provenance bundle. It does not select an unrelated cohort.
For more pairs, supply their explicit indices to the API after matching the
exact FID/IID tuples; do not match repeated IIDs without their FIDs.

## Source-paper distinctions

The complete intake checked all 250 cached primary XML/DOI identities and the
92 existing PLINK/KING observations: 52 PLINK, 25 PLINK2 and 15 KING. The table
below summarizes the KING uses; it does not assign unstated software flags,
versions or historical environments.

| Paper | KING use | Boundary to preserve |
| --- | --- | --- |
| [P019](https://doi.org/10.1038/s41586-026-10836-6) | Relatedness filtering at coefficient 0.0884 in separate cohort workflows. | Keep the cohorts and QC sequence separate; do not equate the coefficient with IBD `PI_HAT`. |
| [P024](https://doi.org/10.1038/s41586-025-09573-z) | Exclude samples with KING coefficient above 0.177 during genotype processing. | The paragraph does not identify the exact executable, version or complete estimation command. |
| [P041](https://doi.org/10.1038/s41586-023-06831-w) | Standalone `--unrelated --degree 2`, with additional ancestry/relatedness checks. | Selection and later ancestry masking are distinct operations; the native coefficient API does not reproduce the selection algorithm. |
| [P084](https://doi.org/10.1038/s41588-023-01600-x) | KING-robust within PLINK2; remove individuals with coefficient above 0.177. | The source names the PLINK2 host. Do not infer a standalone KING executable. |
| [P104](https://doi.org/10.1038/s41588-026-02733-5) | Genotype QC excludes high relatedness above 0.177. | The host, version and complete coefficient-estimation command are unstated. |
| [P105](https://doi.org/10.1038/s41588-023-01569-7) | Define unrelated BBJ participants with coefficient below 0.0884 for discovery and an LD reference. | These are distinct selected populations; LDstore dosage calculations remain separate. |
| [P116](https://doi.org/10.1038/s41588-025-02398-6) | Use KING `--ibdseg` to define relatives for a PCA/projection workflow. | Segment inference differs from the native between-family coefficient; bigsnpr projection remains separate. |
| [P123](https://doi.org/10.1038/s41588-026-02741-5) | PLINK2 computes KING coefficients for duplicate and relatedness filtering at 0.354 and 0.0884. | Preserve each cohort, cutoff and the source's degree wording; no unrelated-set tie policy is inferred. |
| [P130](https://doi.org/10.1038/s41588-024-01939-9) | KING 2.2.7 identifies participants shared between ROSMAP and NACC; NACC records are preferred. | Cross-cohort duplicate identification and record preference are separate from generic close-relative removal. |
| [P168](https://doi.org/10.1038/s41588-026-02697-6) | Identify probable identical-twin pairs and remove one participant per pair. | No exact estimator/version/cutoff or tie policy is specified. |
| [P180](https://doi.org/10.1016/j.ajhg.2024.10.022) | Filter second-degree or closer relatives using coefficient 0.0884. | Keep the reported degree and threshold wording; do not infer a particular source option set. |
| [P191](https://doi.org/10.1016/j.ajhg.2026.05.014) | SNPRelate computes KING-robust relatedness for GENESIS PC-AiR. | This is an R-hosted method; the standalone KING and native PLINK2-style components are not substituted for it. SAIGE GRMs remain separate. |
| [P192](https://doi.org/10.1016/j.ajhg.2024.10.001) | KING 2.3.2 `--unrelated --degree 3` defines several unrelated study/reference sets. | Preserve each population and subsequent random sampling; the numeric API does not reproduce that selection. |
| [P224](https://doi.org/10.1016/j.ajhg.2025.07.015) | KING 2.0 removes related participants at coefficient at least 0.0884 across sex strata. | The later PLINK2 sex-specific GWAS is a separate operation; preserve the inclusive comparison. |
| [P246](https://doi.org/10.1016/j.ajhg.2025.06.013) | Exclude close relative pairs at coefficient 0.0884 before a calcium-trait analysis. | The exact host/version and removal policy are unstated; phenotype aggregation and regression remain separate. |

## Validation and remaining work

The native counter matched **960 complete original pair reports** over 1, 2,
63, 64, 65, 127, 128 and 129 variants, through both packed and decoded inputs.
Each compiler build matched all 445 finite binary coefficients bit for bit;
all 515 nonfinite classes were retained. The tests include repeated IIDs in
different families, UTF-8 IDs, a nonfounder, duplicate genotypes, partial and
disjoint missingness, all-missing calls and zero-heterozygosity pairs.

Nine focused test methods passed on Windows Java 21/25 and Linux Java 21:
27 executions with no skips, including 2,880 complete original pair comparisons.
Fresh Java 17-targeted public consumers also passed with both compiler builds
on Windows and Linux. The canonical executable and shared runtime libraries
remain unchanged. These are component checks, not whole-project tests or a
release/deployment claim.

Original PLINK1 bounded/unbounded IBD reports and PLINK2 cutoff behavior have
also been captured as preparation for the remaining adapters. Split-chromosome
input rejection and the rejected `--king-cutoff 0.5` request are retained;
neither is repaired by changing options automatically. Complete standalone KING
and SNPRelate workflows, older source profiles, segment inference and study
sample-selection behavior remain unfinished.

The [official KING downloads](https://www.kingrelatedness.com/Download.shtml)
allow research use with redistribution/profit restrictions. Its original 2.3.2
archives and source are retained privately for the user's stated research use;
they are not bundled in this GPL Java library. Source intake alone admits no
standalone KING runtime or analysis. Required original analyses must stay
skipped whenever their exact permitted prerequisites are unavailable.

See [component evidence and hashes](native-king-development/VALIDATION.json).
