# Native diploid genotype scores

The canonical source build passed 1,593 Java tests with zero failures/errors,
five optional skips and all 72 required suites without skips. All 217 current
policy tests passed without skips, all 28 earlier-tool regression groups passed,
and separate original Trim Galore/Unicode/fresh-Python checks passed this JAR.
Native score checks include 41 API/reader/CLI tests, six original comparison
workflows, 2,007 reported values, 670 native sample rows and 23 packaged controls.
This is an unreleased source build. The broader 250-paper implementation goal
remains active; original PLINK QC/PCA/GWAS adapters are separate work.

`genotype-score` applies supplied weights to resolved biallelic, diploid human
autosomal hard calls in a variant-major BED/BIM/FAM set. It needs only Java.
It retains exact family/individual identities, requires an explicit missing-data
policy and reports both the weighted sum and its allele-denominator average.
It does not learn weights, estimate target-cohort frequencies or calibrate risks.

## Inputs and a command

Preserve `cohort.bed`, `cohort.bim` and `cohort.fam` together. `--bfile` takes their
common prefix. Every requested variant must appear exactly once in BIM. Model
rows may have a different order: accumulation follows BIM order and records both
ordinals. Sample identity is the exact case-sensitive `(FID, IID)` pair; repeated
IIDs in different families are allowed, duplicate pairs fail.

Supply this header and tab-separated columns in `weights.tsv`:

```text
variant	ea	oa	weight	ea_frequency
rs10000	C	A	0.5	0.25
rs10001	T	G	-0.25	0.75
```

`ea` is the allele counted by the supplied weight; `oa` is its other allele.
The pair must match BIM A1/A2 exactly or in the opposite order. For the latter,
the code counts `2 - A1 dosage`. It never guesses strand complements or changes
case. Palindromic alleles still require correct upstream model/genotype identity;
matching letters do not establish strand or assembly provenance. Resolved
multibase allele strings are accepted without normalizing indels.

`ea_frequency` is the supplied frequency of **ea**, not necessarily BIM A1 or the
minor allele. It must be finite in `[0,1]` and is mandatory for mean imputation,
even when a particular genotype row happens to be complete. Other policies allow
the column to be absent, or a missing cell encoded as blank, `NA`, `NaN` or `.`.
Every weight must be finite. Zero weights remain model variants and contribute
to denominator/count accounting; an all-zero model warns. `.csv` selects CSV
parsing, including quoted fields. Unknown/duplicate columns and duplicate IDs fail.

In this audit checkout, the validated source executable is under
`build/literature-review/validated-cli/`. A standard `gradlew.bat executableJar`
source build writes `build/cli/jlinalg-0.3.7.jar` instead; use that path for a
new source build. For an appropriately matched real cohort/model, run:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar genotype-score `
  --bfile cohort --weights weights.tsv --missing mean-impute `
  --genome-build GRCh38 --out output/genotype-score
```

A complete synthetic 13-sample/8-variant fixture is provided under
`examples/native-genotype-score/inputs/`, with input hashes and provenance. Try
the command above with `--bfile examples/native-genotype-score/inputs/cohort`,
`--weights examples/native-genotype-score/inputs/weights.tsv`,
`--genome-build unspecified` and a new output directory. Coordinates are synthetic;
the expected unspecified-build warning must be reviewed. See the
[example instructions](../../../examples/native-genotype-score/README.md).

Choose a new output directory. `GRCh37`, `GRCh38` and `unspecified` are caller
declarations, not assembly validation or liftOver. `unspecified` warns. Selected
chromosomes must be human autosomes 1–22, optionally prefixed by `chr`; FAM sex
does not establish ploidy. The diploid contract is the caller's responsibility.
Sex chromosomes, dosage/PGEN/VCF imports, multiallelic records and reference
harmonization need separate workflows.

## Missing data and score units

For weight `w[j]` and its counted-allele dosage `d[i,j]`, the sum is
`sum(w[j] * d[i,j])`. The average divides that sum by the reported
`denominator_alleles`; it is not a standardized polygenic risk score.

| `--missing` | Missing contribution | Average denominator |
|---|---|---|
| `error` | Fail the analysis | Twice all model variants, after completeness is checked |
| `mean-impute` | `w * (2 * supplied ea_frequency)` | Twice all model variants, including imputed ones |
| `omit` | Exclude that variant for that sample | Twice the observed model variants for that sample |

Observed alleles are reported separately. Mean-imputed averages can therefore
use a different denominator from the observed-allele count. Omitted sums are
partial models and sample averages can use different denominators; review them
before comparison. Fully imputed samples warn because their scores are
conditional reference expectations, not measured individual differences.

**No observed scored genotypes under `omit` means an unreportable score.** That
sample has blank sum/average fields, denominator zero and status
`NO_OBSERVED_DOSAGES`. The code preserves the sample row. A whole cohort in this
state fails instead of publishing an apparently successful table. This explicit
handling is not a claim that every original PLINK version prints the same empty
or zero representation.

## Output, bounds and required actions

Successful output contains `results.tsv`, `variant-metadata.tsv`,
`sample-metadata.tsv`, `manifest.yaml`, `session.yaml` and
`analysis-status.json`. QC warnings are printed prominently to stderr and written
to `WARNINGS.md`. Review every observed/imputed count, denominator, allele
orientation, model ordinal, assembly declaration and warning before interpretation.

**Exit 2 means failure.** The warning names the cause and required correction.
Scientific streams written before the failure move to `partial/`; there is no
successful root `results.tsv`. Preserve those diagnostics and inputs, correct the
named ID, allele pair, frequency, file dimension or resource limit, and rerun to a
new writable directory. Existing output directories are protected. Native scoring
requires no external installation, so a missing original PLINK binary is not a
dependency failure for this command.

Defaults bound samples to 1,000,000, total BIM variants to 50,000,000, scored
variants to 1,000,000 and scored sample-by-variant cells to 1,000,000,000. Metadata
input bytes are capped at 8,192 MiB, estimated retained BED metadata and retained weights metadata each at 256 MiB, each
metadata/weights line at 16,384 bytes, weights at 128 MiB and BED at 65,536 MiB.
Use the corresponding `--maximum-*` options shown by `genotype-score --help` only
after assessing available memory/work. Retained-byte limits are separate estimates for the reader and weights, not
a combined process-memory limit or operating-system reservation. Scoring arrays
and temporary decoded rows also require memory. Core accumulation uses O(samples) memory;
only selected BED rows are decoded. Strict header, file dimensions and selected
padding bits are checked. Input SHA-256 checks run before and after scoring,
which scans the full BED file for hashing; keep every input immutable during use.

For Unicode Windows paths, put string-valued options in UTF-8 JSON and pass an
ASCII-named recipe with `--options-file options.json`. Direct CLI options override
the recipe. This avoids native launcher encoding loss; it is tested on actual
Java 21 and 25. Input metadata itself is decoded strictly as UTF-8.

## Java API

`org.jlinalg.genetics.DiploidScores` accumulates one already-oriented dosage row
per variant, with explicit weights, frequencies, limits and missing policies.
It supports finite diploid dosages in `[0,2]` or `NaN`; the BED CLI supplies hard
calls only. Every prospective row is validated before state changes, and rejected
rows leave the accumulator unchanged. Compensated double sums reject nonfinite
intermediate values. The accumulator is not thread safe.

`PlinkBedDataset` provides bounded read-only selected-row access and immutable
sample/variant metadata. Its A1 dosages are `2,1,0,NaN`; apply allele orientation
and the intended biological/ploidy contracts in the caller.

The [complete Java example](../../../examples/native-genotype-score/DiploidScoreExample.java)
shows hand-calculated imputation and all-missing omission. Compile and run it with
a Java 17-compatible source target and Java 21, for example:

```powershell
javac --release 17 -Xlint:all -Werror -cp build/literature-review/validated-cli/jlinalg-0.3.7.jar `
  -d build/score-example examples/native-genotype-score/DiploidScoreExample.java
java -cp 'build/score-example;build/literature-review/validated-cli/jlinalg-0.3.7.jar' DiploidScoreExample
```

The optional BED argument in that example is specifically for the packaged
verifier's synthetic complete fixture; omit it for the self-contained hand cases.

## Evidence and source scope

The 41 focused Java tests include 388 frozen actual original sample rows: 291
sums and 291 averages across missing policies/releases, with all composite IDs
and allele counts checked. A separate actual-JAR campaign used 12 original PLINK
processes over complete, missing and external-frequency synthetic inputs: six
workflows, 2,007 reported numeric fields and 670 native sample rows. Native values
also match independent known-call mathematics. Original comparisons respect the
programs' displayed precision; unreportable all-missing omission rows are retained
without claiming numerical equivalence. Another 23 packaged controls cover
scientific errors, quarantine, limits, ordering, CSV, existing outputs and Unicode
recipes. An external Java 17 API consumer runs on actual Java 21/25 separately
from JUnit counts.

Reference executables were official Windows x86_64 PLINK 1.9.0 (27 Sep 2026) and
2.0.0-b.1.2 (24 Oct 2026), privately downloaded with GPL notices and pinned hashes.
Current PLINK2 requires at least 50 samples for its implicit frequency estimation;
all comparisons here load explicit supplied frequencies. Earlier rejected
reference attempts are retained. This is not historical-version, PRS-CS/LDpred2
weight-training, QC, PCA, GLM or complete paper-pipeline validation.

For paper-specific cohorts and score origins, read the
[PLINK source review](PLINK_SOURCE_REVIEW.md), the searchable catalog and its
linked primary papers. PLINK and PLINK2 keep their broader method-overlap status
until those distinct upstream operations are implemented and validated.

Original scoring semantics: [PLINK 1.9 score](https://www.cog-genomics.org/plink/1.9/score),
[PLINK 2 score](https://www.cog-genomics.org/plink/2.0/score), and
[BED layout](https://www.cog-genomics.org/plink/1.9/formats#bed).
