# Native SNPRelate KING-robust and variant QC

Two Java components reproduce the arithmetic of SNPRelate 1.46.0 over an explicitly supplied hard-call population: `SnprelateKingRobust` calculates pairwise KING-robust kinship and IBS0, and `SnprelateVariantQc` calculates allele frequencies, missing rates and QC decisions. They use the existing native KING count kernel. The standalone API needs Java 17+ and no R installation.

**Scope and warnings:** this is a scalar/QC component milestone. The root analysis CLI, GDS transport admission, LD pruning, GRM construction and GENESIS workflows remain unfinished. This checkpoint earns no additional catalog integration credit. The established count remains 95 new scoped integrations, or 98 including three existing integrations. These components do not reproduce a paper's biological results or infer its unreported settings.

Run the supplied example from the repository root in PowerShell, using a new output directory:

```powershell
javac --release 17 -cp docs/literature/snprelate-native-development/snprelate-native-javac21.jar -d build/snprelate-example-1 examples/native-king/SnprelateProfileExample.java
java -cp "build/snprelate-example-1;docs/literature/snprelate-native-development/snprelate-native-javac21.jar" SnprelateProfileExample
```

The example prints a within-family coefficient of zero and a between-family NaN for the same counts. It also demonstrates a missing-rate threshold whose decision depends on GDS layout. On Linux/macOS, use `:` between classpath entries.

## Scientific contract

Supply native calls `0`, `1`, `2`, and `-1` for missing, in the declared sample and variant order. Invalid calls fail. `KingRobustKinship.Accumulator` accepts complete variant rows or packed BED rows and retains the five disjoint pair-count categories. Select the sample/variant population before counting; the scalar estimator performs no filtering or family inference.

Choose `WITHIN_FAMILY` only for an explicitly known common family; otherwise choose `BETWEEN_OR_SINGLETONS`. With `S = IBS1 + 4*IBS0`, the source formulas are `0.5 - S / (2*(het1+het2))` within families and `0.5 - S / (4*min(het1,het2))` between families. Heterozygote counts use jointly called loci. The result retains the raw IEEE coefficient, source-normalized coefficient, IBS0 proportion, chosen denominator and information disposition. SNPRelate converts every nonfinite raw coefficient to NaN. Negative finite values remain negative. A zero smaller heterozygote count can still give a finite within-family result.

**Do not interpret or replace NaN values.** SNPRelate assigns diagonals kinship `0.5` and IBS0 `0` even without genotype information; these are software conventions. The Java profile rejects combinations that would overflow SNPRelate's unsigned 32-bit squared-difference counter. Variant statistics reject signed 32-bit source allele-count/denominator overflow. Neither component silently substitutes corrected arithmetic for wrapped source arithmetic.

`SnprelateVariantQc` requires explicit `SNP_BY_SAMPLE` or `SAMPLE_BY_SNP` layout. The original uses `(samples-called)/samples` for the former and `1-called/samples` for the latter. For nine calls among ten samples these differ in binary floating point. The profile retains that difference, including its effect on a near-boundary filter.

The installed 1.46.0 KING defaults are autosomes only, monomorphic removal, MAF `NaN`, missing-rate cutoff `0.01`, singleton families, one thread and full matrices. These are version-specific software defaults, not a paper's settings. The QC class handles frequency/missingness only; chromosome selection belongs to its caller. Infinities and NaN disable numeric cutoffs. If both cutoffs are nonfinite and monomorphic removal is false, the source bypasses QC, retaining even all-missing variants. Any active QC rejects nonfinite MAF. Finite cutoffs retain ordinary source comparisons, including out-of-range values; equality is included.

## Identifiers and empty selections

The reference controls retain the raw BED/BIM/FAM bytes, Unicode identifiers, repeated IIDs across families, and literal `NA`/`NaN` IDs. The original BED importer conditionally combines family and individual IDs when IIDs repeat. It turns a literal BIM SNP ID `NA` into an empty GDS SNP ID. The evidence records these representations; it does not silently repair the source or admit the resulting analysis. A separate, explicitly requested row-index GDS transport preserves the original FID/IID and SNP IDs in sidecar maps. It is a transport experiment, not a fallback after a failed analysis.

For all-missing controls with active QC, the original SNP-first layout errors with `Invalid SnpStart and SnpCount`; the sample-first layout returns an empty SNP population and an all-NaN off-diagonal matrix. Both outcomes are retained. An empty population is not a usable relatedness result. Future analysis admission must reject it explicitly.

## Validation and replay

The public evidence contains only project-owned synthetic inputs and upstream-generated results, source snapshots, manifests and Java artifacts. It bundles no original R packages, KING programs, author example data or article prose. SHA-256 checks provide integrity, not authenticity or independent biological validation.

The final reference collection contains 128 declared original SNPRelate cases across four synthetic cohorts, both GDS layouts, explicit BED/row-index transports, family contexts and QC boundaries. Ten cases preserve original empty-selection errors; ten preserve empty-selection matrix returns. Complete genotypes, sample/variant order, allele frequencies, missing rates, selected populations and all matrix cells are checked. Two isolated compilers, JDK 21 and 25 targeting Java 17, are each replayed on Windows and Linux: 512 case checks and 59,776 complete matrix-cell comparisons. Finite values are bit-identical, and R NA/NaN flags are checked separately.

Twenty-three new JUnit methods plus nine existing count-kernel methods pass on Windows 21/25 and Linux 21: 96 executions, no skips, including 2,880 prior original-reference pair checks. Four fresh Java consumers use only the standalone API JAR. The released executable remains unchanged.

Replay saved results without R or a network connection:

```powershell
python tools/literature/verify_snprelate_evidence.py --out build/literature-review/snprelate-runtime/offline-user-1 --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe"
```

Use a new output path for each attempt. The verifier checks complete fields and deliberate mutations, not just stored checksums. Its output is evidence replay, not a fresh R execution.

## Original runtime availability

The original reference collection used a private Windows R 4.6.1 library with SNPRelate 1.46.0, gdsfmt 1.48.2 and RhpcBLASctl 0.23-42. The setup helper downloads official Windows binaries and installs into its new workspace directory only. It requires explicit permission flags:

```powershell
python tools/literature/setup_snprelate_reference.py --out build/literature-review/snprelate-runtime/private-user-setup-1 --allow-download --allow-install
python tools/literature/collect_snprelate_king_reference.py --setup build/literature-review/snprelate-runtime/private-user-setup-1 --bfile path/to/cohort --transport row-index --out build/literature-review/snprelate-runtime/user-reference-1
```

**If installation is forbidden or an exact required runtime/package is unavailable, skip the dependent original SNPRelate analysis.** The helper/collector writes a prominent `WARNINGS.md`, a structured disposition and required recovery instructions. Supply the exact permitted runtime, then retry into a new directory. Do not install into shared R libraries, substitute a different version or replace SNPRelate with standalone KING/PLINK2 to suppress the warning. The native scalar API remains independently usable within its documented scope. The collector intentionally marks project analysis and new catalog credit false even after successful reference collection.

The source arithmetic is attributed to Xiuwen Zheng's GPL-3.0 SNPRelate implementation, especially `src/genKING.cpp`, `src/dGenGWAS.cpp`, `R/IBD.R` and `R/Internal.R`. The exact 1.46.0 source archive is recorded in the evidence; see the [official package page](https://bioconductor.org/packages/release/bioc/html/SNPRelate.html) and [versioned source archive](https://bioconductor.org/packages/3.23/bioc/src/contrib/SNPRelate_1.46.0.tar.gz).

## Paper roles still requiring integration

The complete 250-paper source census and reviewed [primary-role supplement](omics-2023-2026/snprelate-genesis-primary-roles.json) identify SNPRelate in P130, P191 and P216, and GENESIS in P128, P130, P142, P191 and P219. P191 uses SNPRelate-hosted KING for GENESIS PC-AiR; P216 explicitly uses `snpgdsLDpruning`; P130 supplies a collective GRM/null-model package list without uniquely assigning its steps. GENESIS roles include PC-AiR, relatedness-adjusted models, PC-Relate-informed association models and principal components with unreported function names. Two ordinary-word matches for “genesis” are excluded. Exact paper versions and unresolved settings remain explicit. Registering these additional package roles and completing their remaining workflows is still required for the full goal.
