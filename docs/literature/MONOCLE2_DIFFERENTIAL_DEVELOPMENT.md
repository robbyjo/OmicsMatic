# Monocle2 differential testing: development adapter

This separate development adapter runs original **Monocle2 2.26.0 `differentialGeneTest`** with typed Java input validation and atomic result admission. It supports original size-factor/dispersion estimation, fixed-size negative-binomial differential tests, and continuous normal responses. Dense TSV and sparse coordinate Matrix Market inputs preserve literal gene and cell order. Java independently checks finite probabilities and the original BH adjustment; it does not refit the VGAM models.

**Development availability:** this adapter is in its own JAR, built with `tools/literature/build_monocle2_differential.py`. The canonical `build/cli/omicsmatic-0.3.7.jar` is unchanged. The existing root `monocle-tools` command serves Monocle3; invoke this separate adapter by the class name below. The previous [reference-only checkpoint](MONOCLE2_CURRENT_READINESS.md) remains a historical record. Monocle's remaining paper roles are still open and no additional catalog integration credit is awarded by this checkpoint.

## Warnings and required actions

- **SKIP3:** the selected runtime requires Linux Java, R 4.2.1, Monocle2 2.26.0 and the exact dependencies in [SOURCE-PROFILE.json](../../src/main/resources/org/jlinalg/omics/monocle2-differential/SOURCE-PROFILE.json). Windows Java skips this profile; Windows users must invoke Linux Java through WSL with Linux paths. Missing, forbidden or mismatched runtime/dependencies skip the dependent analysis. Analysis installs nothing. Provision only where permitted, otherwise retain the skip and do not report results.
- **FAIL2:** any failed original gene, original error, input mismatch, timeout, capacity breach or Java audit failure rejects the **whole batch**, including earlier completed cases. Original failed-gene p/q values of 1 remain failed rows, not valid null findings. Preserve the attempt, correct the named cause, and use new request/output paths. Do not drop failed genes, repair IDs, impute values or replace nonfinite results to force success.
- **Known original-family failure:** free-dispersion `negbinomial` and `binomialff` on both binary and proportion responses fail in the original helper with `the condition has length > 1` under this exact VGAM profile. Their typed requests are accepted for transparent diagnostics, but these controls admit no result. No original function or family vector is patched. Fixed-size `negbinomial.size` and continuous `uninormal` controls pass.
- Original warnings remain visible, including packages built under R 4.2.3 running under R 4.2.1 and deprecated dplyr calls during dispersion estimation. Read the generated `WARNINGS.md`, `analysis-status.json`, `worker/worker-report.json`, per-case `source-status.json` and stdout/stderr. Require `result_admitted=true` before interpreting `results/`.

Successful synthetic controls do not establish biological validity, the unspecified patch/formula used by paper P099, or compatibility with every model/data combination. P099's TPM filtering threshold does not establish that TPM is suitable as raw counts. Monocle3, Cicero, py-monocle and modified paper-specific graph tests are separate methods.

## Build and run the tested example

From the repository root in PowerShell, with the existing JDK 21 and Python available:

```powershell
python tools/literature/build_monocle2_differential.py --out build/literature-review/monocle-runtime/my-differential-build
```

The builder requires the retained validated `root-build-1` parent artifact and adds only new adapter classes/resources. It refuses an existing build directory. It never overwrites the parent or canonical JAR. On another checkout, reconstruct and validate the earlier development parents first; development JARs and installed runtimes are not distributed in Git. The public evidence contains their fingerprints, entry inventories, owned class bytes and source snapshots.

The selected private runtime was provisioned using [the recorded legacy manifest/lock](../../tools/literature/monocle2-legacy-reference/) and a separate original HSMMSingleCell 1.18.0 library. See the [original readiness record](MONOCLE2_CURRENT_READINESS.md) for installation failures and recovery. The setup helper's `--use-recorded-lock` option requires that lock; a fresh solve is a new candidate requiring validation. Existing caches/bootstrap and Windows/WSL paths in those helpers are local prerequisites, not a portable installer. Do not use a shared/system library as a replacement or silently select a newer R/dependency profile.

In the WSL Linux shell, replace the following paths with the actual **Linux** paths to the validated runtime and newly built JAR. Use an ASCII request pathname; input paths and literal identifiers can contain Unicode. Create the request's parent directory first.

```bash
cd /mnt/d/projects/JLinAlg
PREFIX=/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime/monocle2-linux-reference-setup-3/.pixi/envs/default
EXTRA=/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime/monocle2-hsmm-reference-setup-1/R-lib
JAR=/mnt/d/projects/JLinAlg/build/literature-review/monocle-runtime/my-differential-build/omicsmatic-0.3.7-monocle2-differential-development.jar
python3 examples/monocle2-differential/prepare_request.py \
  --rscript "$PREFIX/bin/Rscript" --r-home "$PREFIX/lib/R" \
  --r-library "$EXTRA" --r-library "$PREFIX/lib/R/library" \
  --request /mnt/d/projects/JLinAlg/build/my-monocle2-request.json \
  --out /mnt/d/projects/JLinAlg/build/my-monocle2-result
/usr/bin/java -Xmx1g -cp "$JAR" org.jlinalg.cli.Monocle2DifferentialCli \
  --request /mnt/d/projects/JLinAlg/build/my-monocle2-request.json
```

The seeded synthetic example has 12 genes and 200 cells, including literal whitespace, quotes, Unicode and `NA` identifiers. It uses original size factors, explicitly fixed NB size 8, relative response handling, and the full condition + progress + sex model versus progress + sex. It does not estimate dispersion. These example settings are not recommendations for biological data. The helper verifies input hashes and writes an explicit request; it installs no software.

Exit 0 with `result_admitted=true` publishes complete per-case `tests.rds`, `tests.tsv`, `tests.json`, original constructed/final CDS objects, argument and RNG ledgers, cell size factors, source status and `java-audit.json`. Dispersion runs additionally retain the original intermediate CDS and dispersion table. The original differential function does not return internal fitted VGAM models; the adapter does not claim to retain unavailable objects. Both the worker captures and admitted copies remain available. There is no portable `VERIFY` request; use the public evidence checker for this checkpoint.

## Request reference

Run `java -cp "$JAR" org.jlinalg.cli.Monocle2DifferentialCli --help` for the closed root fields. Unknown fields and executable R/formula strings are refused. Required request fields are schema version 1, mode `DIFF`, exact version, matrix format, expression units, three input paths, typed metadata, output path, Rscript, R home, private R libraries and 1–32 analyses.

| Input | Required representation |
| --- | --- |
| Gene metadata | Literal unquoted UTF-8 TSV, exactly `gene_id` and `gene_short_name`; unique gene IDs. |
| Cell metadata | Literal unquoted UTF-8 TSV, `cell_id` followed by exactly the declared numeric/factor covariates; unique cell IDs; explicit ordered factor levels. |
| Dense expression | `matrix_format: "dense-tsv"`; first header `gene_id`, then cell IDs in metadata order; gene rows in metadata order. No reordering. |
| Sparse expression | `matrix_format: "matrix-market"`; general coordinate integer/real banner, one-based coordinates, exact dimensions/entry count. Axes come from ordered metadata. Duplicate coordinates, symmetric expansion and pattern matrices are refused. Explicit zeros are retained in the input ledger. |
| Values | Finite explicit numbers. NB requires raw nonnegative exact integer counts ≤2^53−1. Normal requires declared continuous or raw counts. Binary requires 0/1; proportions require [0,1]. No trial counts, dichotomization or transformations are inferred. |

Each analysis requires `id`, positive integer `seed`, `family`, nonnegative `lower_detection_limit`, `estimate_size_factors`, `dispersion`, `relative_expr`, `full_model` and `reduced_model`. Optional `size` applies only to `negbinomial.size`; omission preserves VGAM's original default. Size factors require raw counts and positive finite cell totals. Relative expression requires an NB family with size-factor estimation. Non-NB analyses explicitly set it false; unavailable size factors remain original `NA` values.

Models are objects with an explicit `intercept` boolean and `terms` array. Each term names a declared metadata `column`; numeric terms can optionally declare `spline_df` for original `sm.ns`. Only additive terms are supported. Full/reduced models use the same intercept and the reduced terms must be a strict subset of identical full terms. This establishes syntactic nesting, not design rank or scientific suitability. Original singular-model failures remain failures.

Dispersion is either `{"estimate":false}` or `{"estimate":true,"model":{"intercept":true,"terms":[]},"relative_expr":true,"min_cells_detected":1,"remove_outliers":true}` with every field explicit. The latter requires NB counts and size factors. Original calls use one core and preserve their rounding, dispersion hints, likelihood-ratio testing and BH only across original OK genes.

Default logical limits: total inputs 64 MiB, 20,000 genes, 100,000 cells, 1,000,000 dense or stored sparse entries, each JSON export 64 MiB, worker output growth 512 MiB and 600 seconds. Explicit request limits are bounded by the parser. These are logical checks, not OS memory/disk quotas; use an appropriate Java heap and external resource limits. Timeout/capacity failures retain diagnostics and verify cleanup of the owned worker tree. Temporary files disappearing during original R cleanup are tolerated; symlinks, other file errors and unsupported nodes remain rejected.

## Validation evidence

The [public evidence bundle](monocle2-differential-development/VALIDATION.json) records the exact additive development artifact, original sources and all retained attempts. Dense/sparse controls compare all original test fields and saved counts/axes/size factors/dispersions across 720 differential rows. Further controls match fixed-NB relative/unscaled and continuous normal responses, and match three original-family failures without admission. Ten Java contract tests run without skips on Windows Java 21, Windows Java 25 and Linux Java 21. Actual runtime controls cover unavailable edition/data dependency, input/JSON/output capacity, timeout, and a two-case batch whose second case produces original failed gene rows. Atomic TSV exports are checked against their token-preserving JSON ledgers.

```powershell
python tools/literature/monocle2_differential_evidence.py
```

Checksums prove retained content consistency, not authorship or an independent scientific refit. Runtime guards verify exact selected package versions and eleven original R bodies/formals, not every native library or dependency's source. Paper role review and full 250-paper implementation work continue separately.
