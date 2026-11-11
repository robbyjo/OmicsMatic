# chromVAR matched background development workflow

`org.jlinalg.cli.ChromVarBackgroundRequestCli` prepares matched background peak indices through the unchanged original chromVAR sampler. It accepts an explicit bias table or computes native canonical-base GC from a supplied FASTA. The [complete checked example](../../examples/chromvar-background/README.md) then runs [native deviation scoring](CHROMVAR_DEVIATIONS_DEVELOPMENT.md) with the generated background matrix.

The standalone development JAR is `build/literature-review/signac-runtime/background-workflow-build-2/omicsmatic-0.3.7-chromvar-background-development.jar`:

```text
bytes:  164914725
SHA256: 9627cee2aea5a58248dfb5848800d644429de11dd952d4f9f30cc08f78409c34
```

It extends the validated native GC/deviation JAR without changing its existing entries. The shared canonical JAR is a separate build and is preserved. This development class has no root CLI route, catalog credit, release or deployment. The catalog remains 68 new scoped integrations, 71 including existing tools, and 259 missing tools.

Invoke `--request UTF8_JSON`, or `--help` for a short summary. Paths inside JSON resolve against the request directory. In this environment, keep the command-line request-file path ASCII for Windows Java 25; Unicode paths and peak/cell IDs inside UTF-8 requests are tested. The R worker exchanges audited numeric row indices because its C-locale text reader cannot represent arbitrary Unicode names. Captured inputs and published named outputs retain the exact original identities and order.

For BACKGROUND, required fields are `schema_version: 1`, `mode: "BACKGROUND"`, `profile`, `out`, `genome_build`, `bias_provenance`, `counts`, `peak_features`, `cells`, and the four runtime paths `rscript`, `r_motif_library`, `r_signac_library`, `r_base_library`. Use exactly one bias route:

- `bias`: UTF-8 TSV headed `peak_id` and `bias`, with every count peak exactly once in the same order. Finite signed values and exact `NA`, `NaN`, `Inf`, `-Inf` tokens pass unchanged to the original sampler. Undefined or degenerate covariance can fail; no repair occurs.
- `fasta` plus `peak_ranges`, optionally `fai`: an uncompressed FASTA and TSV headed `peak_id`, `chromosome`, `start`, `end`. Ranges are one-based closed, in the exact count-peak order. Coordinates must identify distinct ranges. [Native GC](CHROMVAR_GC_DEVELOPMENT.md) computes `(C+G)/(A+C+G+T)`, excluding ambiguity and preserving source `NaN` when the denominator is zero. It checks complete reference structure, contigs, bounds and any supplied byte-offset index. Reference and index inputs are left unwritten. ASCII contig IDs are required by this reference reader; no aliases or identifier repairs occur.

Counts are a plain UTF-8, uncompressed Matrix Market integer/real general coordinate matrix, peaks by cells. `peak_features` and `cells` are unique, nonempty IDs, one per line with no header, matching its dimensions. Counts must be finite nonnegative reals; duplicate coordinates require an explicitly recorded upstream aggregation rather than implicit coalescing. Numeric and pooled-count capacities from the native count reader apply. Zero pooled peak counts are retained until the original sampler rejects them; no filtering changes the population.

The two profiles are `1.16.0-source` and `1.34.1-package`. Their retained R background core functions and complete `utils.cpp` files are byte-identical. Both run on the recorded current installed dependencies. Each request guards the installed current R function's body and formals, runs the selected source function, resets the seed and repeats through the installed public `getBackgroundPeaks` Matrix interface. Background matrices and complete RNG states must agree exactly. This is source replay on shared dependencies; it does not authenticate an installed DLL against its source or execute a historical whole package. chromVAR 1.5.0 is outside this validation.

Effective settings:

| Field | Default | Admission |
| --- | ---: | --- |
| `seed` | 1 | integer 0 through 2147483647 |
| `niterations` | 50 | integer 1 through 10000 |
| `w` | 0.1 | finite, greater than zero, at most 1000000 |
| `bs` | 50 | integer 2 through 50 |
| `timeout` | 3600 | seconds, integer 1 through 86400 |
| `maximum_entries` | 2000000 | stored count coordinates, 0 through 2000000 |
| `maximum_input_bytes` | 2000000000 | per captured input, up to 20000000000 |
| `maximum_output_bytes` | 2000000000 | per normalized/published numeric table, up to 20000000000 |
| `maximum_background_values` | 20000000 | peak count times iterations, at most 20000000 |
| `maximum_work` | 1000000000 | admitted estimate, up to 1000000000000 |
| `maximum_queried_bases` | 100000000 | native GC reference queries, up to 10000000000 |

The work estimate includes both actual sampler calls: `2 * (bs^4 + peaks*bs^2 + peaks*niterations)`. It is a capacity guard, not a measured instruction count or a complete R memory bound. The grid requires `bs^4` pairwise distances. Output-byte limits are per numeric table, not a total-capture quota. RNG is fixed to Mersenne-Twister, Inversion and Rejection under R 4.6.1; full 626-integer before/after states are retained.

**WARNINGS / REQUIRED ACTIONS**

- **SKIP3 — dependent analysis skipped:** BACKGROUND needs R 4.6.1, chromVAR 1.34.1, Matrix 1.7.5, nabor 0.5.0, Rcpp 1.1.2, RcppArmadillo 15.6.2-1 and jsonlite 2.0.0. R normalizes the Armadillo version to `15.6.2.1` in recorded checks. Supply the four runtime paths to permitted private installations with these exact dependencies. This command does not download or install software. If installation is forbidden or dependencies are unavailable, preserve the skip and do not run downstream scoring without a valid separately prepared background.
- **FAIL2 — no result admitted:** inspect `WARNINGS.md`, status, manifest and `worker` logs. Source covariance failures can arise from constant/undefined bias, constant intensity, too few independent peaks or zero pooled counts. Invalid references/indexes, unsupported inputs, exceeded capacities and timeouts also fail. Preserve the attempt, correct the named cause and retry into new paths. The command does not impute, filter or rename data. A timeout terminates the owned worker process tree; the real cleanup path is tested.
- **VERIFY audits consistency:** Java-only verification recomputes native count/reference/GC preparation and checks the exact source bytes, requests, input hashes, runtime/path declarations, settings, shapes, index bounds, published/worker agreement, recorded RNG-state agreement, warnings and complete file inventory. It does not rerun R sampling or independently generate RNG states. A retained synthetic adversarial test demonstrates that coordinated fabricated worker and published indices followed by rehashing pass these structural checks. Hashes are integrity evidence, not authenticity or proof of execution. A successful VERIFY must not be described as independent statistical reproduction.
- **Scientific scope:** review the actual peak population, assembly, bias formula, motif collection, source edition and settings. Synthetic examples and numerical agreement do not establish biological accuracy, regulatory causality, whole-paper reproduction or complete Signac support. Source missing values and failures remain explicit.

Successful outputs include `background.tsv` (named peaks by one-based iteration indices), `bias.tsv`, `diagnostics.json`, `manifest.json`, `analysis-status.json`, `WARNINGS.md`, `verify-request.json` and `prepared-inputs.json`. Original input snapshots, worker code/source notices, effective settings, RNG states and logs are retained. The checksum inventory binds all captured files, including nested files. An existing output directory is refused. Failed/skipped runs publish their diagnostics with no admitted root result tables; partial files, if any, remain explicitly retained.

`prepared-inputs.json` is a fragment for the native deviation request, not a complete executable request. Add a new `out`, `motif_collection`, `motif_labels` and either `annotation_peaks` or `motif_matrix`. Its count/peak/cell/background paths resolve against the background analysis directory; rebase them if writing the completed request elsewhere. Keep follow-up requests outside a sealed capture so its inventory stays valid. The checked example provides complete current and legacy requests without placeholder inputs.

Verification evidence is saved in [VALIDATION.json](chromvar-background-development/VALIDATION.json). The actual request matrix covers 15 source populations and both profiles: 18 successful original background/RNG comparisons, 12 retained original failures, 54 portable successful capture verifications and 36 failed-capture rejections. Separate reference-chain controls compare native GC, sampled backgrounds, RNG, expectations, deviations, z scores and fraction metadata against original indexed-reference and public R calls. Recovery controls include actual dependency skips, owned timeout cleanup, reference/capacity failures, rehashed corruption checks and the explicit authentication limitation. Native computations and captured verification are tested on Windows Java 21/25 and Linux Java 21; actual upstream R fitting is validated on Windows.
