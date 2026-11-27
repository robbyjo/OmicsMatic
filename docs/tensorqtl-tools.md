# Original tensorQTL workflows

`tensorqtl-tools` runs original tensorQTL 1.0.5 or 1.0.9 on prepared dosage,
phenotype and covariate matrices. Java validates the declared model, captures
literal inputs, supervises the owned process and verifies its saved results.
The original Python/PyTorch/SciPy/R functions perform the statistical analysis.
Original code, runtime binaries and raw model objects are private dependencies.

**Development scope:** these are selected software profiles and synthetic
validation cases. They do not establish an executed author recipe or reproduce
a whole paper. Read [the four paper roles](literature/tensorqtl-source-audit/ROLES.json)
and [source provenance](literature/tensorqtl-source-audit/SOURCE-PROVENANCE.json).
P157's likelihood-ratio description remains unresolved: original interaction
coefficient t-tests are reported as such. P183's named custom programs are
absent from the inspected author tree. Current linked revisions and unpinned
environments do not recover historical executed commands. Standalone susieR
validation does not establish tensorQTL's own SuSiE hosting.

```text
java -jar omicsmatic-0.4.1.jar tensorqtl-tools --request request.json
java -jar omicsmatic-0.4.1.jar tensorqtl-tools --verify completed-analysis
```

Use schema_version 1, mode RUN, NEW out, profile, operation, python,
source_directory, device CPU/CUDA, explicit project seed, and
acknowledge_original_processing=true. A project seed is never an inferred
author seed. Relative paths resolve against the request. A REPLAY request
contains mode REPLAY, analysis and NEW out, with optional explicit replacement
python/r_home/r_libraries bindings; it first verifies the parent capture.

| Operation | Original calls and additional inputs |
| --- | --- |
| CIS_NOMINAL | cis.map_nominal; variants and phenotype_positions; optional groups, interaction, or modern paired_covariates |
| CIS_PERMUTE | cis.map_cis; variants, phenotype_positions, explicit qvalues boolean; optional groups or modern paired_covariates |
| CIS_DISCOVERY | cis.map_cis, original R qvalues, cis.map_independent; variants, phenotype_positions and covariates; optional groups |
| CIS_INDEPENDENT | cis.map_independent; supplied original cis_results plus variants, phenotype_positions and covariates; optional groups |
| TRANS_NOMINAL | trans.map_trans; optional interaction and explicit original cis-distance exclusion |
| QVALUES | original calculate_qvalues on cis_results; CPU only |
| CIS_SUSIE | original 1.0.9 susie.map/susie; variants, phenotype_positions and covariates; optional paired_covariates |

Mapping requires genotypes and phenotypes. Their TSV first columns are
variant_id and phenotype_id, followed by at least five exact ordered sample
IDs. Dosages are finite [0,2] values or the original explicit -9 missing
sentinel. Fractional dosages remain fractional. Phenotypes are finite values;
the adapter does not normalize them. Covariate/interaction TSV files start
with sample_id and contain numeric columns in the exact matrix sample order.
No intercept is added; centered covariates must have full column rank.
Paired covariates start with phenotype_id and have exact sample columns,
covering a distinct subset of the supplied phenotypes. Groups contain exactly
phenotype_id,group_id in phenotype order. No ID trimming, sorting, intersection,
allele harmonization, strand conversion or missing-value repair occurs.

Variants contain variant_id,chr,pos in dosage order, with contigs contiguous
and positions sorted within each contig. Phenotype positions contain
phenotype_id,chr,pos; 1.0.9 also accepts chr,start,end with position_kind=interval.
Coordinates are explicit one-based positive integers and intervals are closed.
For sparse trans exclusion, supply phenotype_positions and trans_filter_positions
(variant_id,chr,pos), plus trans_filter_position_kind=variant or
declared-region-center. The latter alone permits fractional region centers.
For example, P157's MHC center 29563353.5 must not be rounded or replaced with
each HLA allele's position. cis_exclusion_window defaults to 5000000.

Original defaults are retained where exposed: cis window 1000000; nperm 10000;
cis MAF 0, trans MAF .05; FDR .05; qvalue_lambda unspecified; modern
random_tiebreak=false. Original interaction eigenMT is opt-in, with
maf_threshold_interaction=.05. Trans defaults are sparse output,
pval_threshold=1e-5, batch_size=20000, alleles=2 and return_r2=false. These
retained-output thresholds are separate from a paper's significance rule.
SuSiE defaults are L=10, scaled_prior_variance=.2, estimated prior/residual
variances, tolerance=.001, coverage=.95, min_abs_corr=.5 and
maximum_iterations=200. Unsupported or ignored options are refused.

**Runtime setup and skips.** Select exactly
`tensorqtl-1.0.5-py3.8.10-torch1.8.1+cu111` or
`tensorqtl-1.0.9-py3.11.17-torch2.3.1+cu121` on Linux x86_64. Exact original
file URLs/hashes, package versions and critical runtime-file hashes are in
[SOURCE-PROFILES.json](../src/main/resources/org/jlinalg/omics/tensorqtl-workflow/SOURCE-PROFILES.json)
and its two compressed inventories. Obtain original source from the listed
immutable URLs under its BSD 3-Clause terms. Set python to the separately
installed executable and source_directory to the directory containing the
listed original package files. R-dependent operations also require explicit
r_home with R 4.2.3, qvalue 2.30.0 and rpy2 3.5.11. Optional r_libraries must
resolve that exact qvalue namespace. The legacy bridge uses ABI mode; modern
uses API mode. Private validated Linux profiles are retained on this project
workstation; changing their bytes requires separate qualification. Windows
supports saved-bundle verification, not these upstream runtime executions.

**SKIP3 / exit 3:** a named required file, package, original edition, executable,
R library or requested CUDA device is unavailable. The dependent analysis is
skipped. Restore the named permitted exact dependency or retain the skip.
There is no adjacent-version substitution or CPU fallback for a CUDA request.

**FAIL2 / exit 2:** invalid data/design, changed source/runtime, original error,
failed optimizer, nonconverged SuSiE model, unexpected nonfinite result,
interruption, timeout, incomplete output or capacity failure. No complete
result is admitted. Preserve the capture and diagnostics, correct the named
cause and retry into NEW output. Do not edit source or silently repair values
to obtain a passing result. Explicit CIS_PERMUTE beta_approx=false preserves
the original NaNs in beta_shape1, beta_shape2, true_df, pval_true_df and pval_beta;
these fields are undefined in that requested mode, not imputed estimates.

**Read WARNINGS.md and analysis-status.json first**, then worker/report.json,
worker/warnings.txt, stdout.log, stderr.log and any R-warnings.txt/traceback.txt.
Only completed-with-warnings with results_admitted=true admits a complete
workflow. A no-discovery qvalue table may be valid, while original conditional
mapping may reject that table because it has no significant phenotypes.
Such an original error is retained; the adapter does not invent discoveries.

**Known refused source branch:** the qualified 1.0.9 dense trans-interaction
fixture fails in the original code with a matrix-shape error. The same error
occurs in an independent direct-original call. The failed capture and original
trace are retained; no complete dense-interaction result is admitted for that
case. Explicit sparse interaction is a separate branch with separate evidence.
No source patch or automatic conversion to sparse output occurs.

The original 1.0.5 dense-interaction return tuple names its minor-allele sample
and allele-count series `maf` too. Their original names/integers are preserved;
the verifier applies count bounds to tuple slots 4 and 5, and frequency bounds
to slot 3. The first validator's rejected attempt remains retained. Original
interaction branches do not add r2 output when return_r2 is set; their allele
statistics retain the source's diploid calculation.

Mapping keeps original float32 arithmetic, original imputation/filtering and
version-specific branches. It is not FP64 mapping. CPU and actual CUDA
execution have separate evidence; cross-device bitwise identity is not assumed.
Read-only observers retain original SciPy optimizer returns and every original
SuSiE return before its host discards no-credible-set models. The source's
signed ELBO stopping rule is preserved; source convergence is not an
independent numerical proof. Failed convergence is never relabeled success.

The capture retains original returned objects, RNG states, R qvalue objects,
original nominal parquet/gzip files, a typed numeric export and source/runtime
guards. The object journal contains original torch.save payloads and must not
be unpickled from an untrusted source. VERIFY never unpickles it or runs R/Python;
it checks hashes, literal intake, journal boundaries, result structure and
numeric bounds. It neither refits the model nor authenticates a coordinated
replacement of all hashes and data. Runtime guards cover selected critical
Python/native/R files and original loaded function code; host libraries,
drivers and the whole operating system are outside that claim.

Logical/monitored limits default to 512 MB input, 2 GB new output, 100000
variants, 10000 phenotypes/samples, 10 million dense entries and a conservative
8e12 mapping-work estimate. They are configurable within closed bounds, not
operating-system CPU/RAM/disk quotas. Preserve raw scientific inputs, runtimes,
failed attempts and evidence during cleanup.
