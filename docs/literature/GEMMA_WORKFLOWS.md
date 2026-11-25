# GEMMA kinship and univariate association workflows

Use `gemma-tools` to construct centered or standardized kinship from PLINK hard
calls, or to run a separately installed original GEMMA release for univariate
association. Java implements kinship and independently checks all SNP quality
filters, sample identities and output populations. Original GEMMA performs the
association fit. The workflow checkpoint records this bounded qualification;
the separate catalog checkpoint records admitted paper-role coverage. Neither
establishes complete software or paper reproduction.

**Build the current source for this command. Earlier 0.4.1 release downloads do
not contain it.** From the checked-out repository, run `./gradlew executableJar`
on Linux or `./gradlew.bat executableJar` on Windows, then use the produced
`build/cli/omicsmatic-0.4.1.jar`. The examples below refer to that source-built JAR.

**A constant phenotype can make original GEMMA exit successfully with `nan`
p-values. This adapter rejects that input and every nonfinite association result.
An exit code alone never admits a result.**

The selected releases are the original Linux x86_64 **0.98.1, 0.98.3 and 0.98.5**.
Native Java kinship works on Windows and Linux. Original execution on Windows
requires Linux Java inside WSL. Analysis never downloads or installs software.
PanGEMMA is a separate project and is not substituted.

## Start with prepared input files

Keep the original BED/BIM/FAM files. Supply these additional files explicitly:

| File | Required contents |
| --- | --- |
| Sample IDs | Two columns, FID and IID, no header, exactly matching every FAM row in order |
| Phenotype | One column, no header, one row per FAM row; finite decimal values or literal `NA` |
| Covariates, when used | One row per FAM row, no header, first column all ones on complete cases, followed by explicit covariates |
| Kinship, for association | Full square matrix in FAM order, without header or row IDs |
| Kinship IDs, for association | Separate two-column FID/IID sidecar matching all FAM rows |

**Numeric `-9` in an explicit phenotype file is an observed number under these
original source versions. Use literal `NA` for missing values.** Interpret any
sentinels in the original study data before preparing this file. The adapter does
not silently recode them or extract a phenotype from FAM.

The sample sidecars assert positional identity. Matching them cannot establish
that a phenotype or external matrix was generated correctly. No sample or SNP ID
is repaired, folded to another case, reordered or silently removed.

Use uncompressed, variant-major diploid PLINK BED with header `6c 1b 01`, unique
variant IDs, exact FID/IID pairs, distinct resolved allele labels and valid BIM/FAM
metadata. The adapter accepts one phenotype and at most 64 covariate columns,
including the intercept. It refuses redundant or constant additional covariates;
it does not insert an intercept or remove columns. `covariates: null` explicitly
selects an intercept-only model.

Association also requires phenotype variation after covariate projection. A zero
or numerically unresolved residual, at or below 1e-24 of the original squared
phenotype norm, is refused before fitting. Any centering/rescaling or phenotype
change must be an explicit, separately retained data-preparation decision.

## Run native Java kinship

Save this request as `kinship.json`, replacing paths with canonical absolute local
paths. On Windows, use JSON paths such as `D:/data/study`; on Linux use `/data/study`.
The BED prefix has no extension. All named files must already exist.

```json
{
  "schema_version": 1,
  "edition": "0.98.5",
  "engine": "native",
  "mode": "gk2",
  "genome_build": "GRCh38-declared-by-input-provider",
  "bed_prefix": "/data/study",
  "sample_ids": "/data/sample-ids.tsv",
  "phenotype": "/data/phenotype.txt",
  "covariates": "/data/covariates.txt",
  "kinship": null,
  "kinship_ids": null,
  "optimizer": null,
  "qc": {
    "minimum_frequency": 0.01,
    "maximum_missingness": 0.05,
    "minimum_hwe": 0,
    "maximum_covariate_fraction": 0.9999
  },
  "capacity": {
    "samples": 2000,
    "variants": 100000,
    "input_bytes": 1073741824,
    "output_bytes": 268435456,
    "numeric_bytes": 1073741824,
    "multiply_adds": 1000000000000,
    "native_wall_millis": 600000
  },
  "process": null
}
```

```text
java -Xmx2g -jar /software/omicsmatic-0.4.1.jar gemma-tools --request /data/kinship.json --out /results/NEW_kinship
java -Xmx2g -jar /software/omicsmatic-0.4.1.jar gemma-tools --verify /results/NEW_kinship
```

The output parent must already exist; the result directory must be new. Retain
the exact producing JAR for verification. `gk1` selects centered kinship; `gk2`
selects standardized kinship. Edition selection is explicit, including for native
compatibility. A build label is a declaration and does not verify genome identity.

The completed native matrix is `outputs/kinship.txt`. `samples.tsv` records every
FID/IID and its QC complete-case status. `QC.jsonl` records every BIM row, its
first rejection reason or use, and applicable numeric quantities. Uncomputed
quantities are JSON `null`, not invented zeros or replacement p-values.

### The two sample populations matter

SNP QC uses individuals with observed phenotype and all covariates. Kinship
construction subsequently uses **all FAM individuals**, including those excluded
from QC. Missing genotypes use the observed mean in the appropriate population.

For each admitted SNP, let `z` be its centered dosage across all FAM rows, with
missing calls represented by zero after centering. Then:

- `gk1`: average `z z'` across admitted SNPs.
- `gk2`: average `z z' / v`, where `v` is observed genotype variance after mean
  imputation, with the full FAM sample count as denominator.

The variance is **not** `2p(1-p)`, and the matrix is not normalized to mean diagonal
one. Frequencies count BIM A1 and can exceed0.5. QC uses strict `>` missingness,
strict outside-range frequency rejection, unconditional constant-genotype
rejection, the original exact HWE probability ordering and the uncentered
covariate projection fraction. The projection includes the intercept contribution;
it is not the usual centered R-squared. HWE is disabled when its threshold is0.
The standalone Java API additionally supports the original `minimumFrequency=-1`
rule; that extended setting is outside this command's qualified request profile.

## Install an original runtime separately

```text
java -jar /software/omicsmatic-0.4.1.jar gemma-tools --export-runtime-setup /private/NEW_gemma_setup
```

Read the exported README and GPL license. On permitted Linux x86_64 with
Python>=3.11, `setsid`, `prlimit` and readable `/proc`, run:

```text
python3 -B /private/NEW_gemma_setup/setup.py --home /private/NEW_gemma_home --report /private/NEW_gemma_report --cache /private/existing_cache --allow-install --allow-download
```

Create the cache directory first. The two flags explicitly permit this separate
installation and download. For offline installation, supply all three exact
archives named in the exported README and omit `--allow-download`. Every archive
and uncompressed program must match its locked full byte count and SHA-256.
Official source URLs, commits, source-archive hashes and licenses remain recorded.

Require the setup report's `COMPLETED.json`, its warnings and all 9 original
synthetic controls across the 3 releases. Health checks are artificial and admit
no user analysis. Rechecking an existing completed home requires a **new report**
and the matching setup export, without installation/download flags. Changed or
incomplete homes are refused; recheck never refreshes their seals.

```text
java -jar /software/omicsmatic-0.4.1.jar gemma-tools --inspect-runtime /private/NEW_gemma_home --setup-report /private/NEW_gemma_report
```

## Run the original association model

Prepare a new request based on the native example. Set:

```json
{
  "engine": "original",
  "mode": "lmm4",
  "kinship": "/data/kinship.txt",
  "kinship_ids": "/data/kinship-ids.tsv",
  "optimizer": "source-defaults-1e-5-1e5-10-regions",
  "process": {
    "wall_millis": 600000,
    "address_space_bytes": 4294967296,
    "cpu_seconds": 600,
    "file_bytes": 268435456,
    "open_files": 256,
    "workspace_bytes": 2147483648,
    "file_count": 1000,
    "processes": 8,
    "threads": 128,
    "sampled_rss_bytes": 2147483648,
    "monitor_millis": 100
  }
}
```

This fragment replaces those fields in the complete request; it is not a complete
request by itself. Increase complete input/output capacities when the matrix or
data requires it, and ensure workspace capacity covers both plus 16 MiB of metadata.
Execution fixes original library thread settings to 1 for the qualified profile.

Modes are `lmm1` Wald, `lmm2` likelihood ratio, `lmm3` score and `lmm4` all three.
The explicit optimizer profile uses the original defaults, lambda 1e-5..1e5 with
10 regions. It does not establish any paper's unreported settings. `lmm1`'s
`logl_H1` is a REML quantity; `lmm2`/`lmm4` export ML likelihood. In score mode,
0.98.1/0.98.3 export an **uncomputed zero placeholder** in `logl_H1`; it is not a
fitted likelihood. Version 0.98.5 omits that column. The adapter preserves each
original schema and labels the distinction in `OUTPUT-AUDIT.json`.
Native kinship can be supplied to original fitting, with an explicit
FID/IID sidecar prepared from `samples.tsv`'s first two columns without its header.

```text
java -Xmx2g -jar /software/omicsmatic-0.4.1.jar gemma-tools --request /data/association.json --home /private/NEW_gemma_home --setup-report /private/NEW_gemma_report --out /results/NEW_association
java -Xmx2g -jar /software/omicsmatic-0.4.1.jar gemma-tools --verify /results/NEW_association
```

Original matrix construction is also available with `engine: original` and mode
`gk1` or `gk2`, with null kinship/kinship-ID/optimizer fields. It requires the same
private runtime and process capacities. Every matrix entry is compared with Java.

## Read warnings before using a result

**SKIP3 — dependent analysis skipped:** the exact permitted runtime, platform,
download/install permission, access or monitoring prerequisite is unavailable.
Provide the named exact prerequisite through separate permitted setup, or retain
the skip and exclude the dependent analysis. No alternate version/model is chosen.

**FAIL2 — no complete result:** invalid or changing inputs/runtime, cancellation,
capacity/process failure, unexpected source diagnostic, incomplete population,
nonfinite number, inconsistent output or finalization failure. Preserve the attempt,
read `FAILURE.json`, `WARNINGS.md`, the QC ledger, original logs and process stderr.
Correct the cause and use new output paths. Do not overwrite or reseal evidence.

An admitted association retains every source output, checks all exported numeric
fields and probabilities, allele orientation, frequency, missingness and SNP
population, and checks Wald probabilities against beta/SE and complete-case
degrees of freedom. Source allele frequencies have 3 decimal places. The known
near-zero-eigenvalue warning remains visible; unexpected warnings/errors refuse
admission. Lambda boundary fields are counted in `OUTPUT-AUDIT.json`.

**Finite output and exit0 do not certify optimizer convergence.** Review source
warnings, boundaries and model suitability. No phenotype BLUP estimation, BSLMM,
multivariate LMM, LOCO, imputation, pruning, ancestry/ploidy inference or entire
study reproduction is claimed. The existing Java REML scanner uses another method
and is not substituted for GEMMA fitting.

Supplied matrices must be symmetric and positive semidefinite within the declared
text-rounding validation tolerance `1e-6 * N * max(1,maxAbsEntry)`. Validation uses
that tolerance; the matrix bytes passed to the original program remain unchanged.
Numeric/work bounds estimate principal arrays and multiply-adds, not JVM RSS or
available heap. Metadata is bounded separately. Linux process limits combine hard
per-process AS/CPU/file/nofile limits with wall/workspace/process/thread monitoring
and sampled aggregate RSS, which is not a hard aggregate RSS limit.

Portable readback works on Windows and Linux using the **exact producing JAR**.
It checks complete bytes and recomputes native sample/QC/output consistency, without
rerunning GEMMA, refitting the model or consulting original host paths. SHA-256
establishes integrity, not authenticity or biological correctness. Keep producers,
source contexts, private runtimes, inputs, warnings and failures.

## Paper roles and source boundaries

| Paper | Reported GEMMA use | Limits of this implementation |
| --- | --- | --- |
| P102, [10.1038/s41588-024-01957-7](https://doi.org/10.1038/s41588-024-01957-7) | 0.98.1 GWAS and SV-eQTL; Wald tests; first 3 PCs in GWAS; relatedness described as IBS from SNP/SV variants | The reported kinship wording does not identify a `-gk` flag. No flag, input format, Beagle step or downstream threshold/clustering rule is inferred. |
| P171, [10.1038/s41588-026-02678-9](https://doi.org/10.1038/s41588-026-02678-9) | 0.98.3 mixed-model GWAS; explicitly `gemma -gk 2` | Association mode flags are unreported. QTL/LD definitions remain separate. |
| P176, [10.1016/j.ajhg.2025.12.002](https://doi.org/10.1016/j.ajhg.2025.12.002) | Literal version **0.983** in both phenotype/exome and expansion-GWAS methods; mixed models account for relatedness/population structure | The exact edition is unresolved. It is not silently mapped to 0.98.3. PLINK QC and clinical phenotype construction remain separate. |
| P248, [10.1038/s41588-026-02682-z](https://doi.org/10.1038/s41588-026-02682-z) | 0.98.5 SNP/SV GWAS with previously computed BLUP phenotypes and first 3 PCs | Precomputed phenotype BLUPs do not imply GEMMA-BSLMM training. Phenotype derivation, Beagle, LD and selection remain separate. |

All 250 frozen article XMLs were screened for the tool name: 6 eligible method
paragraphs in 4 papers. These roles concern prepared kinship and univariate fitting;
author study data have not been rerun. The research window remains the frozen
2023-10-23..2026-10-23 cohort, rather than silently changing with today's date.

Original sources are GPL3, pinned to commits
[0.98.1](https://github.com/genetics-statistics/GEMMA/tree/2cacff6912f8d6cc045339880b339596196111d4),
[0.98.3](https://github.com/genetics-statistics/GEMMA/tree/f623b4775d47b2c2697a4925bd044412a7db49d3) and
[0.98.5](https://github.com/genetics-statistics/GEMMA/tree/c37b0445f820b682836a1d20009ce1817546493a).
The principal audited rules are `ReadFile_bed`/`PlinkKin` in `src/gemma_io.cpp`,
`ProcessCvtPhen`/`CheckCvt` in `src/param.cpp`, `CalcHWE` in `src/mathfunc.cpp` and
the test-statistic routines in `src/lmm.cpp`. Original binaries and source code
remain unmodified. Newer release fixes do not silently replace older source behavior.

## Validation and retained evidence

The scoped qualification covers60 complete captures:36 original-program analyses
and24 native kinship runs, including12 executed on Windows. Every capture is read
back on both Windows and Linux with its exact producer. Independent calculations
check all588 association rows across24 tables, including GLS coefficients/standard
errors, REML/ML likelihoods, Wald, likelihood-ratio and score probabilities.

The suite includes32 Java tests per platform,22 actual workflow refusals,
17 saved-result byte/semantic refusals on each platform, explicit platform/runtime
and installation skips, an actual offline installation from exported setup bytes,
and9 original artificial runtime health controls. All681 existing genetics classes
remain byte-identical to the preceding CrossMap producer. These are software and
synthetic-data checks; they do not reproduce author study analyses or certify
optimizer convergence.

The public workflow checkpoint contains source hashes, counts and a private-file
index. Full process traces, original runtimes, complete captures and failure
evidence remain private. Repository auditors with that retained evidence can run:

```text
python3 -B tools/literature/verify_gemma_workflows.py --private-root /private/gemma-evidence --producer /software/EXACT_PRODUCER.jar --predecessor-producer /software/EXACT_CROSSMAP_PRODUCER.jar --java /software/java --cache /private/frozen-primary-XML --out /private/gemma-evidence/NEW_READBACK
```

This audit requires the exact archived source context and producers. It verifies
complete captured contracts and retained refusals; it does not rerun GEMMA or fit
a model. Missing permitted evidence is a dependent-audit skip with a named remedy.
Preserve historical contexts rather than refreshing their expected hashes.
