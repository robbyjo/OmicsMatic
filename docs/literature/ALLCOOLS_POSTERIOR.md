# ALLCools prepared methylation posterior fractions

This source-built Java command calculates posterior methylation fractions and M values from complete prepared counts. Its comparison source is [ALLCools 1.0.8](https://github.com/lhqing/ALLCools/tree/2a77f30bf911726de10fe6cc4971331f023d9e68), specifically `calculate_posterior_mc_frac` and the transform in `MCDS.add_m_value`. It processes every supplied cell, methylation type and feature, saves fitted priors, and supports portable numerical verification.

The [source qualification](allcools-posterior-validation/QUALIFICATION.json) records 99 passing tests on each operating system, 384 original-source cases (376 matches and eight explicit numerical refusals), five complete captures per system and full foreign saved replay. The exact producer is source-built; no released binary or full ALLCools/author workflow is claimed.

## Warnings and required actions

- **Inspect missing values before using results.** Zero coverage produces a raw NaN. Constant or extreme methylation can give zero, negative, NaN or infinite fitted Beta parameters. These are retained rather than repaired. A complete calculation does not establish that its fitted priors are scientifically usable.
- **Choose the representation explicitly.** With normalization and clipping enabled, NumPy preserves NaN. The source xarray branch replaces NaN with the clipping value. Read both `posterior_fraction` and `value`, and inspect `preclip_nans_replaced` in the audit and prior table. A finite clipped value can originate from an undefined posterior.
- **Near-zero M-value normalization fails explicitly.** The source divides M values by their mean. A mean near zero can amplify tiny log-rounding differences into very large normalized values. Java refuses calculations that exceed its fixed numerical admission budget. Review the failure; select `normalize_per_cell: false` explicitly if unnormalized M values suit the analysis. There is no automatic fallback or rescaling.
- **Use complete, matching axes.** Supply all features for each cell and methylation type. Splitting a feature axis changes the fitted prior. All cells must have the same ordered types and features. The command refuses missing/extra rows, malformed counts and changed axes; it does not intersect, sort or impute them.
- **Require completion and verification.** Keep `COMPLETED.json`, the exact producing JAR, all captured inputs and the verification result. `FAIL2` means no complete result: preserve the attempt, correct the named problem and retry new paths. `SKIP3` means a required permitted prerequisite is unavailable; supply it where allowed or retain the skip. Never change saved hashes to make verification pass.
- **Respect the scope.** This command performs no runtime installation. Raw ALLC/Tabix/Zarr preparation, feature selection, clustering, consensus, pseudobulk merging, SeuratIntegration, label transfer, BaseDS and DMS/DMR analyses remain separate. An unavailable or forbidden runtime must cause dependent analyses to be skipped with the prerequisite and required action identified.

## A first calculation

Use a JAR built from the qualified source checkpoint. Historical released binaries do not acquire this command when the source is updated.

Create `counts.tsv` with literal tabs:

```tsv
cell	mc_type	feature	mc	cov
cell1	mCG	region1	1	5
cell1	mCG	region2	2	5
cell1	mCG	region3	3	5
cell1	mCG	region4	4	5
cell1	mCG	region5	0	0
```

Generate an editable request:

```powershell
java -jar C:/absolute/omicsmatic-0.4.1.jar allcools-posterior --example |
    Set-Content -Encoding utf8 C:/absolute/request.json
```

In `request.json`, set `input.counts` to the canonical absolute path of the TSV and replace `preparation` with the actual dataset, reference, region and count-preparation provenance. The example dimensions are one cell, one methylation type and five features, matching the table above. The settings are explicit examples, not inferred paper settings. Increase the complete input/output byte and cooperative wall limits if the dataset requires them.

```powershell
java -jar C:/absolute/omicsmatic-0.4.1.jar allcools-posterior `
    --request C:/absolute/request.json --out C:/absolute/new-capture
java -jar C:/absolute/omicsmatic-0.4.1.jar allcools-posterior `
    --verify C:/absolute/new-capture
```

The output directory must be new. On Linux, use canonical absolute Linux paths in the same commands. During saved verification, original host paths are provenance only; replay uses the complete captured input. Use the exact producing JAR on either host.

## Inputs and calculations

Counts must be literal integers satisfying `0 <= mc <= cov <= 4294967295`. This is an explicit prepared-count subset of the upstream API. Native input is strict UTF-8 TSV with the exact header shown above, an optional header BOM, and CR/LF record delimiters. Quoting is literal. Blank records, unknown columns, invalid UTF-8, signed or decimal counts, leading zeros and extra fields fail. Exact original input bytes are retained.

Rows form cell blocks, then methylation-type blocks, then complete feature axes. `dimensions.cells`, `dimensions.mc_types` and `dimensions.features` declare the expected population. The first cell defines the type order; the first group defines the feature order. Identifiers are literal, nonblank and unique on their axes. No axis is silently reordered, dropped, renamed or merged.

For every cell/type group, the method uses NaN-skipping raw fraction mean and population variance, then the source moment equations for fitted Beta alpha and beta. Unnormalized posterior fractions are `(mc + alpha) / (cov + alpha + beta)`. Optional normalization divides by `alpha / (alpha + beta)`. Clipping is active only when normalization is enabled, and follows the declared `numpy` or `xarray` predicate.

For M values, select `operation: "m-value"`, set `clip_norm_value` to `null`, and choose `m_value_alpha` explicitly. This regularization alpha is distinct from the fitted Beta alpha. The transform uses unnormalized, unclipped posterior fractions and `log2((fraction + m_value_alpha) / (1 - fraction + m_value_alpha))`. Optional per-cell normalization divides by the NaN-skipping mean of M values.

The declared reduction context is float64 contiguous feature axes on prepared two-dimensional per-type arrays. Arbitrary xarray tensor layouts, broadcasting, Dask reduction trees and lazy NetCDF writing are separate. StrictMath-based native log2 can differ from NumPy by rounding. For normalized M values with a finite divisor and at least one non-NaN value, admission requires `abs(mean_M) > 32 * ulp(max_abs_finite_M) / 1e-11`. This is an explicit conservative numerical guard, not a change to the source equations. Original reference values, including enormous, NaN and infinite values, remain retained. Capacity and cancellation limits are cooperative, not heap or immediate-interruption guarantees. A complete group must fit in memory.

## Saved outputs

| File | Contents |
| --- | --- |
| `inputs/counts.tsv` | Complete, unchanged original count bytes |
| `outputs/values.tsv` | Every input count, raw fraction, unnormalized posterior fraction and requested value |
| `outputs/priors.tsv` | Every group's mean, variance, fitted alpha/beta, prior mean, normalization divisor, coverage and clipping counts |
| `OUTPUT-AUDIT.json` | Complete population accounting, NaN/Inf counts, clipping replacements and output identities |
| `WARNINGS.md` | Scope, source behavior and required actions captured with the result |
| `request.json`, `source-inputs.json` | Explicit settings and original provenance |
| `manifest.json`, `COMPLETED.json` | Exact producer and complete saved population identities |

Failed attempts retain an incomplete marker and failure record once a capture has been created. Verification checks the complete file and directory population, exact producing JAR and all input bytes, then recomputes every output and prior. Checksums establish integrity, not authenticity, dataset completeness outside the supplied contract or biological validity.

## Relationship to the reviewed papers

The full-cohort source audit identified four ALLCools roles. A selected tag does not establish an unreported author commit or reproduce a full author workflow.

| Paper | Cataloged use and remaining work |
| --- | --- |
| [P047](https://doi.org/10.1038/s41586-023-06823-w) | Counts in 100 kb and gene-body features, posterior fractions, integration/transfer and pseudobulk DMRs. Edition unreported. Count preparation and exact integration/DMR recipes require separate work. |
| [P053](https://doi.org/10.1038/s41586-023-06819-6) | ALLCools 1.0.8 preprocessing, methylome tensors, posterior mCH/mCG, cluster-enriched features and ALLC merging. Its downstream DMR caller is methylpy; keep that provider and its recipe separate. |
| [P060](https://doi.org/10.1038/s41586-023-06808-9) | SeuratIntegration CCA/anchors, two-round RNA/MERFISH integration, transfer and imputation. Prepared posterior calculations do not implement this role. |
| [P061](https://doi.org/10.1038/s41586-023-06805-y) | Feature tensors, iterative clustering, pseudobulk merging, BaseDS, permutation DMS/DMR calls and integration. The selected tag exposes a `generate_base_ds` API but registers no `generate-base-ds` CLI; the updated author wrapper remains unresolved. Preserve the distinct coverage caps and 250 bp/PCC >0.3 DMR recipe. |

## Reproduce source comparisons

`tools/literature/reproduce_allcools_posterior.py` checks the exact comparison-source hashes, requires NumPy 1.23.5 and xarray 2023.1.0, and executes the original posterior function and `MCDS.add_m_value` on synthetic counts. Run it in a permitted, isolated environment with the required ALLCools dependencies:

```bash
python -B tools/literature/reproduce_allcools_posterior.py \
  --source-tree /absolute/unmodified/ALLCools-1.0.8 \
  --out /absolute/new-reference-directory
```

The runtime/source archive, installation logs, warnings and raw execution receipts stay private. The project packages the ALLCools MIT and NumPy BSD notices with its native implementation. No restricted raw paper text, original science data or runtime binaries are published with this guide.
