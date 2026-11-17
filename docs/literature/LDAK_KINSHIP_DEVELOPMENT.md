# LDAK development: native kinship and original runtime

**Development checkpoint. The full LDAK workflow adapter and catalog admission
are unfinished. No LDAK integration credit is added by this checkpoint.**
The 250-paper objective remains open. Build the current source to use these Java
classes; released OmicsMatic 0.4.0 does not contain them.

**Missing or forbidden original runtime: SKIP3; skip its dependent analysis.**
Use a permitted Linux x86_64 environment, including WSL where allowed, and obtain
the exact runtime below. Native Java kinship construction works without LDAK.
**Integrity, format or recording error: FAIL2; no prepared runtime or accepted
analysis.** Preserve partial files and the warning/error record, resolve the
named cause, and retry a **new** output path. Never normalize an original input
or refresh a checksum merely to make a check pass.

**Observed original SumHer substitutions:** its tutorial replaced 18 zero test
statistics with `1e-6` and floored four tagging values at one. Review the
[exact warnings and required actions](ldak-workflow-development/WARNINGS.md).
The current controls do not admit a paper analysis.

## What is implemented

`LdakKinship` constructs the selected LDAK 5.2 kinship model in Java using FP64
arithmetic. It supports caller-selected diploid BED hard calls, nonnegative
predictor weights, an explicit power, and either HWE or observed-variance
standardization. It centers each predictor on its observed mean and mean-imputes
missing calls. The final mean diagonal is one.

With `hweStandardization=false`, the scaling variance includes the mean-imputed
missing samples in its denominator. This matters when calls are missing; using
the variance of only called samples gives a different matrix. A conventional
average of HWE-standardized outer products also differs from LDAK's normalization.
The [upstream kinship documentation](https://dougspeed.com/calculate-kinships/)
describes how predictor selection, weights and power define the model.

`LdakBedDataset` preserves all six FAM fields literally and uses exact FID/IID
pairs. The official LDAK tutorial has `NA` in the sex field. Sex, parents and
phenotype are unused metadata for this kernel, so the reader does not convert
them to numeric values. The general PLINK reader remains separate. Both readers
decode BIM A1 counts, but their metadata contracts serve different analyses.

Zero-weight, all-missing and constant predictors have separate dispositions.
The result lists every selected predictor. Unknown weight IDs, exhausted
predictors, malformed input and numeric-capacity failures produce an error;
samples or variants are never silently truncated. Unlisted weight-map IDs get
weight zero; a null map explicitly means unit weights for every selected row.
An unthinned unit-weight matrix is **not** automatically LDAK-Thin.

These native classes construct and read kinship matrices. The separate
[development workflow adapter](LDAK_WORKFLOWS_DEVELOPMENT.md) now provides
captured original LDAK 5.2 thinning, REML, SumHer, liability conversion and
explicit alpha-grid selection through `ldak-tools run/verify/replay`.
Its paper-role and global catalog qualification remain unfinished.

## Use the Java API

Run from the checkout containing these files. Build with `./gradlew executableJar`
on Linux, or `./gradlew.bat executableJar` on Windows. Start JShell with the
resulting executable JAR on its classpath:

```text
jshell --class-path build/cli/omicsmatic-0.4.0.jar
```

```java
import java.nio.file.Path;
import org.jlinalg.genetics.LdakBedDataset;
import org.jlinalg.genetics.LdakKinship;

// data/genotypes.bed, .bim and .fam must be the matching original BED set.
// null selects every BIM row; a Set<String> instead selects explicit IDs.
try (var bed = LdakBedDataset.open(Path.of("data/genotypes"), null)) {
    var options = new LdakKinship.Options(-0.25, true, 128, 1_073_741_824L);
    var result = LdakKinship.calculate(bed, options, null);
    System.out.println(result.samples().size());
    System.out.println(result.predictorsUsed());
    double[] rowMajorMatrix = result.matrix();
}
```

The last option bounds the estimated principal numeric buffers, not total JVM
or process memory. Metadata, backend overhead and the returned matrix copy are
separate. A dense matrix uses quadratic sample storage; block size bounds the
genotype working buffer. Default metadata limits are 100,000 samples, 1,000,000
variants, 512 MiB of FAM/BIM input and 16,384 bytes per line. Supply an explicit
`LdakBedDataset.Limits` when a separately assessed workload needs different
bounds. A capacity error is a refusal, not a partial matrix.

## Prepare the original runtime

The [official repository](https://github.com/dougspeed/LDAK) publishes LDAK under
GPL-3.0-or-later. The helper retains its exact upstream license notice. It selects
the archived **5.2** Linux executable at repository commit
`78573c112d47e1a5e93fa1aa9764cebcf1ad74a4`; it does not substitute the current
major version. Matching an edition does not establish an author's binary hash.
No vendor executable is committed to this project.

Run in Linux x86_64 or a permitted WSL Linux shell, from this source checkout,
using Python 3.11 or later:

```bash
python3 tools/literature/ldak_original/setup.py --allow-download --out build/ldak52-runtime
```

For offline preparation, supply the exact downloaded archive:

```bash
python3 tools/literature/ldak_original/setup.py --archive /path/to/ldak5.2.linux.zip --out build/ldak52-offline
```

Omitting `--allow-download` forbids network access. Native Windows invocation
returns SKIP3 before downloading because the selected executable is Linux-only.
The helper neither launches WSL automatically nor installs or runs an analysis.
It prepares and hashes the executable, then performs an intentional no-argument
usage probe: the original program prints version 5.2 and exits **1**, which is
the expected health-probe result. This exit convention is not an analysis pass.
Require helper exit 0 and its `COMPLETED.json` runtime receipt.

The helper's [source profile](../../tools/literature/ldak_original/source-profile.json)
pins archive and executable hashes. Output must be new. Setup consoles and
SKIPPED/FAILURE records stay with failed attempts.

## Six reviewed source roles

All 250 frozen XML identities were checked. There are ten named LDAK/SumHer
paragraphs across these six papers; supporting methods paragraphs retain their
own hashes in [PAPER-ROLES.json](ldak-workflow-development/PAPER-ROLES.json).

| Paper | Reported role | Settings still requiring care |
|---|---|---|
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | SumHer 5.2, BLD-LDAK, DCM liability heritability. | Precomputed tagging from 2,000 White British participants; effective case/control sample size; population prevalence 0.004 for DCMNarrow and 0.008 for DCM. Exact files/hash and full options remain unresolved. |
| [P109](https://doi.org/10.1038/s41588-024-01967-5) | LDAK 5.2, LDAK-Thin; SNP/SV kinships and alpha likelihood comparisons. | Trial grid, thinning thresholds and other unreported settings must not be invented. |
| [P120](https://doi.org/10.1038/s41588-025-02410-z) | SumHer BLD-LDAK for hypothyroidism, fT4 and TSH; liability correction for hypothyroidism. | Engine edition, exact tagging snapshot and prevalence values are unresolved here. A precomputed tagging file remains an external input. |
| [P171](https://doi.org/10.1038/s41588-026-02678-9) | LDAK 5.2, LDAK-Thin for SNPs, indels and SVs separately. | Reported pruning: 0.98, 100 kb; alpha interval -0.5 to 1; constrain YES. Exact grid, selection/tie policy and original genotype coding remain unresolved. |
| [P238](https://doi.org/10.1016/j.ajhg.2024.06.001) | GCTA/LDAK heritability comparison in the SpliTWAS results. | Main methods specify GCTA REML. LDAK edition/model/commands are not recovered; the pinned five-file author repository has no LDAK mention. This newly found observation awaits the guarded catalog update. |
| [P245](https://doi.org/10.1016/j.ajhg.2024.05.015) | Prior SumHer comparisons and LDAK scores discussed as context. | Current described analyses use GENIE/LDSC. These mentions do not establish current-study LDAK execution. |

For SumHer, original [liability-scale options](https://dougspeed.com/snp-heritability/)
require population prevalence and ascertainment; effective sample sizes imply
ascertainment 0.5. Caller inputs and the exact model/tagging must stay explicit.
Do not substitute another ancestry or heritability model for a missing reference.

## Validation and remaining work

Thirty Java tests passed, including eight original-binary golden comparisons
across four powers and both standardizations, LDAK-specific input admission,
and the existing general PLINK reader tests. Original fixtures are independent
synthetic inputs with missing calls, constant predictors and non-unit weights.
The original GRM is float32; the native result remains FP64 and is compared using
the recorded absolute/relative float32 tolerance.

The unmodified upstream tutorial's 424 samples and 3,289 variants produced 90,100
lower-triangle comparisons. Windows and Linux Java both agreed with every original
entry; maximum absolute difference was 5.95e-8. The raw FAM was unchanged. The
earlier general-reader refusal remains retained as a failed attempt.

Four setup-policy tests passed on both Windows and Linux. Real Linux download
and offline preparation passed; unsupported Windows, forbidden download, bad
archive and existing-output controls returned their expected refusal codes.
The existing successful runtime was unchanged.

The original 5.2 engine also completed independent kinship, thinning, REML,
linear-summary, tagging and SumHer probes on its tutorial. Those probes are
evidence for adapter development. They do not validate a paper's BLD-LDAK
reference, recover its alpha grid, reproduce biology, or establish a full
captured workflow. See [validation facts](ldak-workflow-development/VALIDATION.json).

The kernel validation records above describe the original native checkpoint;
they have not been rewritten for later source changes. Subsequent captured
workflow development is described in the
[adapter development note](LDAK_WORKFLOWS_DEVELOPMENT.md). The guarded six-role
catalog update remains pending. The admitted count remains **127 scoped
integrations** (23 native, 104 upstream); no global catalog metadata changed here.
