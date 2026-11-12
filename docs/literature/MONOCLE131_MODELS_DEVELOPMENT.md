# Original Monocle3 1.3.1 gene models

This standalone Java workflow calls original Monocle3 1.3.1 `fit_models()` and
`coefficient_table()` on complete prepared expression/metadata populations. It
retains returned models, coefficients, size factors, source, warnings, RNG and
process receipts. Java audits coefficient statistics, their original t/normal
tail probabilities and complete grouped Holm adjustments. Optional Java
Pearson filtering preserves strict original-q and positive-correlation rules.

**Development component; not part of the canonical executable or a whole
Monocle implementation.** Other observed Monocle paper roles remain unfinished.
The full 250-paper objective remains open, with no new catalog badge granted
by this component alone.

## Paper/source findings and boundaries

Catalog P085 is the [2024 Nature Genetics study](https://doi.org/10.1038/s41588-024-01961-x)
of spatial and single-nucleus transcriptomics in Alzheimer's disease. The
[author repository](https://github.com/swaruplabUCI/DSAD_Spatial_Miyoshi_Morabito_2024/tree/5775f130506d80917461fb27226f44130e2ba783)
contains notebooks that pass Seurat's `data` slot to a cell-data-set constructor,
fit an amyloid hotspot score with sample and UMI covariates, and subsequently
use positive Pearson correlation. Human grey-matter and mouse region-specific
populations are prepared separately. These are code-path observations, not
proof of the actual saved study inputs or executed notebook state. The active
Seurat assay/transformation and paper commit are not established by this review.
The author scripts are reviewed privately; they are not executed or redistributed.

The notebook calls omit `expression_family`. The selected
[official 1.3.1 source](https://github.com/cole-trapnell-lab/monocle3/tree/b545460966874948eb11a57a225594a107f1694d)
defaults to quasi-Poisson. Its qualified speedglm version uses t-based
coefficient probabilities for that family. `coefficient_table()` calls
`stats::p.adjust()` without a method, giving grouped **Holm**, despite the
upstream function documentation describing BH. The adapter preserves the
executed source behavior and original `q_value` column.

The native Pearson component follows the selected GPL Hmisc `rcorr` Pearson
primitive at commit `8683675a8e40cf8f0e3e36ff7129912cc2e95857`. Controls use its
original R/Fortran/C source, not `stats::cor()` as an unstated replacement.
This does not establish full Hmisc, Spearman, or the paper's unreported Hmisc
patch. The primitive reference is separate from the original Monocle model
runtime. Native coefficient and Pearson audits do not refit a GLM or authenticate
its producer. Original model statuses and complete returned populations must
also pass the RUN admission gate.

## Build and runtime requirements

```powershell
python tools/literature/monocle131_models/build.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --jdistlib-jar build/dependencies/jdistlib-all-0.10.2.jar `
  --out build/literature-review/monocle-runtime/new-model-build
```

Alternatively select `--download-jdistlib` where downloading is permitted.
Keep the exact adjacent `jdistlib-all-0.10.2.jar`; its SHA256 is
`8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81`.
The original R/Monocle runtime is separate and is not bundled.

Original model execution requires **Linux Java and the qualified private
R4.3.3/Monocle3 1.3.1 libraries**. The current host uses the dependency base
from `tools/literature/monocle133/setup_reference.py`, which contains the pinned
original 1.3.1 package, together with a separate GenomeInfoDbData-only library.
It does not use the 1.3.3 or 1.4.27 namespace. The model profile checks exact
package versions and nine original function definitions. Preserve existing
libraries when provisioning a new profile. A required installation/download or
execution that is unavailable or forbidden means **SKIP3: skip the dependent
analysis**, provide the named permitted prerequisite, and retry a new directory.

To prepare the private source/data layer from the existing locked dependency
base, select one of these explicit options. The first uses local reviewed
source/data files; the second permits their pinned official downloads.

```powershell
python tools/literature/monocle131_models/setup.py `
  --base build/literature-review/monocle-runtime/monocle133-reference-setup-1 `
  --source-root build/literature-review/monocle-runtime/source-intake-1/1.3.1/source `
  --genome-data-package build/literature-review/monocle-runtime/monocle133-original-install-1/R-lib/GenomeInfoDbData `
  --out build/literature-review/monocle-runtime/new-model-runtime `
  --allow-install

python tools/literature/monocle131_models/setup.py `
  --base build/literature-review/monocle-runtime/monocle133-reference-setup-1 `
  --out build/literature-review/monocle-runtime/new-downloaded-model-runtime `
  --allow-install --allow-download
```

These are host examples: replace existing input paths and choose an unused
output path. If the dependency base is absent, first use its documented
`tools/literature/monocle133/setup_reference.py --out NEW --allow-install`
recipe. That host recipe uses the existing WSL distribution and private pixi
bootstrap; an unavailable prerequisite must be supplied through a permitted
installation or the dependent analysis skipped. Neither model setup command
installs Monocle 1.3.3. `RESULT.json` records the exact Linux `rscript`, library
order and `source_root` to copy into a model request. Readiness alone admits no
analysis. Setup writes `WARNINGS.md` and a prominent required-action message;
omitting `--allow-install` returns SKIP3 without launching the installer.

## Complete expression and metadata

| Input | Required form |
| --- | --- |
| Gene metadata | UTF-8 TSV: `gene_id`, `gene_short_name`, one row per supplied gene |
| Cell metadata | UTF-8 TSV: `cell_id`, then exactly the declared metadata columns |
| Dense expression | TSV: `gene_id`, then every cell ID in cell-table order; gene rows match gene metadata exactly |
| Sparse expression | Matrix Market coordinate `real general` or `integer general`; dimensions match gene/cell axes; one-based unique coordinates; omitted entries mean zero |

Raw-count declarations require exact nonnegative integers. Prepared normalized
expression accepts nonnegative finite values without rounding them into
counts. Declare its provenance, including the actual assay/slot/transformation
when known. No additional normalization is substituted. Original Monocle still
estimates `Size_Factor` from the supplied matrix, as the constructor normally
does; the UMI covariate is supplied separately and is not inferred from that
normalized matrix. Store the complete population prepared for the intended
analysis, including its full testing family. There is no implicit row filtering,
ROI selection, gene-name repair, duplicate-coordinate summing or sampling.

Declare each covariate as numeric or as a factor with explicit ordered levels.
The supported additive formula interface accepts literal metadata column names,
never executable R expressions. Model terms and factor reference levels are
caller choices. Population preparation and spatial hotspot calculation remain
upstream steps.

## RUN request

Create a UTF-8 JSON request with **Linux paths**, then run the JAR inside Linux
or the existing WSL distribution:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "version": "1.3.1",
  "expression": "/path/to/prepared-expression.mtx",
  "expression_format": "MATRIX_MARKET",
  "expression_units": "nonnegative-normalized-expression",
  "expression_provenance": "Caller-provided Seurat data-slot export; declare actual assay and transformation",
  "population_declaration": "Complete preselected ROI and gene testing family",
  "cells": "/path/to/cells.tsv",
  "genes": "/path/to/genes.tsv",
  "metadata_types": {
    "Gi": {"type": "numeric"},
    "Sample": {"type": "factor", "levels": ["A", "B", "C", "D"]},
    "UMI": {"type": "numeric"}
  },
  "analyses": [{
    "id": "hotspot",
    "seed": 2051,
    "family": "ORIGINAL_DEFAULT",
    "clean_model": "ORIGINAL_DEFAULT",
    "intercept": true,
    "terms": ["Gi", "Sample", "UMI"],
    "selection": {
      "term": "Gi",
      "correlation_column": "Gi",
      "maximum_q": 0.05,
      "minimum_pearson": 0
    }
  }],
  "out": "/path/to/new-result",
  "rscript": "/path/to/dependency-base/.pixi/envs/default/bin/Rscript",
  "r_libraries": [
    "/path/to/genome-data-only/R-lib",
    "/path/to/dependency-base/.pixi/envs/default/lib/R/library"
  ],
  "source_root": "/path/to/official-monocle-1.3.1/source",
  "execution_policy": "ALLOW"
}
```

```bash
java -Xmx2g -jar /path/to/monocle131-models-development.jar \
  --request /path/to/request.json
```

Replace the synthetic/example covariate names and levels with the real declared
metadata. `ORIGINAL_DEFAULT` omits that argument in the original call; it does
not silently select a current-edition default. Explicit families are
`quasipoisson`, `poisson`, `negbinomial`, and `gaussian`; `clean_model` may also
be a boolean. Only source-returned successful complete model batches are
admitted. The retained original Gaussian control returns non-OK statuses;
numeric coefficient output alone does not make that analysis successful.
No alternative family or edition is substituted after a failure.

Each analysis has its own complete term/component testing families; q values
are not pooled across requests, ROIs, stains or models. Selection is strictly
original `q_value < maximum_q`, followed by native
`Pearson(expression, score) > minimum_pearson`. It adds no coefficient-sign or
Pearson-p-value condition. The supplied expression is used for correlation;
it is not replaced by residuals. Undefined correlations retain their original
value kind and fail the positive predicate. Zero selected genes is a complete
result, not a failure or permission to widen the population.

## Capacity and result admission

Optional controls are `maximum_input_bytes`, `maximum_output_bytes`,
`maximum_entries`, `maximum_json_bytes`, `maximum_native_operations`,
`timeout_seconds`, `maximum_address_space_bytes`, and
`maximum_sampled_rss_bytes`. The input limit covers all three complete tables.
Entry capacity covers nonzero dense entries or declared sparse coordinates.
Increase explicit limits and Java heap only when the operating environment
permits the complete job. No prefix is accepted as the requested population.
The owned Linux supervisor records process cleanup, hard per-process limits,
wall time, workspace size and sampled RSS. Sampled aggregate RSS is not a hard
aggregate memory quota. Full-size study resource execution is not established
by synthetic controls.

**Read `analysis-status.json` and `WARNINGS.md` first.**

| Outcome | Meaning and recovery |
| --- | --- |
| `result_admitted=true`, exit 0 | Complete original model/status/roundtrip and native coefficient/filter checks passed |
| `SKIP3`, exit 3 | Runtime, dependency, install/download permission, platform or execution prerequisite unavailable; provide the named permitted prerequisite and retry NEW paths |
| `FAIL2`, exit 2 | Source/input/integrity, original-model, numeric or capacity failure; retain all diagnostics and resolve the named cause before retrying NEW paths |

Original errors and warnings are retained in `worker-error.txt`,
`worker-result.json`, and `analyses/*/source-status.json`. Complete returned model
objects are saved in `models.rds`, with cleaning exactly as declared. Original
atomic coefficients, statuses, size factors, parameters, native audit and
all-gene selection ledgers remain available. Cleaning can remove prediction
state in the original model; a roundtrip does not certify every downstream
Monocle operation. Undefined original `normalized_effect` values remain typed
in the table and are not an additional selection rule.

Portable `--audit-statistics JSON --out NEW_DIRECTORY` checks supplied
coefficient tails and grouped Holm values. `--audit-pearson JSON --out
NEW_DIRECTORY` compares supplied primitive controls. These repeat no original
fitting, authenticate no producer and admit no original model-analysis result.

## Saved controls and verification

The [complete control bundle](monocle131-models-development/README.md) retains
all synthetic expression/metadata, original R source, full returned model/RNG
objects, coefficient tables, size factors, process receipts and original
failure statuses. Its separate author-code review records immutable source
URLs and hashes without redistributing the author notebooks. The old R4.6.1
attempt in the primary catalog remains historical; the new Linux R4.3.3
runtime qualified here is a separate successful original-version provider.

Nine complete original-model requests exercise dense/sparse equivalence,
omitted versus explicit family/cleaning defaults, raw Poisson/negative-binomial
models, empty selection, confounded positive-correlation selection, original
non-OK models, atomic mixed-batch failure and post-fit native-capacity failure.
Windows Java21/25 each pass 46 packaged controls. Linux Java21 passes those
controls plus 11 checks that refuse bad inputs or missing prerequisites before
launching R. Both permitted source/data provisioning paths pass; four setup
controls verify permission, missing-base and source/lock failures.

```powershell
python tools/literature/monocle131_models/verify.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --jdistlib-jar build/dependencies/jdistlib-all-0.10.2.jar `
  --out build/literature-review/monocle-runtime/new-model-replay
```

On Linux use the permitted Linux JDK path and Linux filesystem paths. Where
permitted, `--download-jdistlib` may replace `--jdistlib-jar`. The verifier
checks every archive member, rebuilds the saved source and replays all 46
portable coefficient/Pearson/refusal checks. It **does not rerun original R
fitting, reload RDS objects or authenticate producers**. RDS roundtrips were
checked during the separate live original-model requests. Original study
populations, active assays, source commits and biological results are not
established by these synthetic controls.
