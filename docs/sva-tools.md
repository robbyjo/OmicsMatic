# Original SVA workflows

`sva-tools` calls the unchanged original `sva`, `svaseq` and `num.sv` functions
from sva 3.46.0. It adds surrogate-variable estimation to prepared omics
matrices. Native continuous `combat-native` and the older approximate Java
SVA method remain separate commands.

**Development scope:** this integration validates a chosen original software
profile. It does not reconstruct an unreported paper recipe or qualify a
whole paper. P176 identifies `svaseq` on DESeq2-normalized counts, but its
author package version and complete design remain unresolved. P185 reports
zero significant methylation factors without specifying the function,
estimator, designs, seed or permutations. Do not infer those choices or force
zero. P108's ComBat operation is already covered separately; P086 count
ComBat and P128 longitudinal ComBat remain separate pending operations.

```text
java -jar omicsmatic-0.4.1.jar sva-tools --request request.json
java -jar omicsmatic-0.4.1.jar sva-tools --verify completed-analysis
```

Requests use schema_version 1, mode RUN, profile
`sva-3.46.0-R4.2.3-linux64`, operation SVA/SVASEQ/NUM_SV, NEW out, matrix,
design, sva_source, units, seed, rscript and r_libraries. Paths are relative
to the request. No R formulas or expressions are accepted. See the synthetic
prepared example in `examples/sva-tools`; its choices are demonstration
choices, not author settings.

Matrix TSV: first column `feature_id`, then exact sample IDs, one feature per
row; at least ten features and three samples. Design TSV: `sample_id`, then
explicit numeric model columns, with the same sample order. Designs must be
full rank; an optional null_design must be nested in the full design. No
sorting, ID trimming, intersection, count rounding or missing-value repair
occurs. All supplied numeric values must be finite decimal float64 values.
Original input bytes are copied and pinned before deterministic transport.

SVA and NUM_SV accept units continuous-values/log2-expression/methylation-M.
SVASEQ accepts raw-counts/normalized-counts/estimated-counts, including
nonnegative fractional normalized counts. Original SVASEQ applies natural
log(dat + constant), default constant 1. Input preparation and biological
design choices remain the caller's responsibility.

For SVA/SVASEQ, declare method irw/two-step/supervised and n_sv as an integer
(including zero), or `"auto"` with explicit num_sv_method be/leek. Optional
iterations sets the original IRW B (default 5): these are fixed iterations,
not a convergence criterion. controls TSV has feature_id/probability in exact
feature order. The original function overrides another method to supervised
when controls are supplied; its console warning is retained. Explicit
iterations/null_design are refused when that override would ignore them.
controls with vfilter invokes the original error, with no result admitted.

Optional vfilter retains original rank/tie rules: SVA uses <=; SVASEQ and
NUM_SV use <. Automatic wrappers pass vfilter to num.sv again, including the
original resulting errors. No repair is applied. NUM_SV requires
num_sv_method be/leek; only standalone be accepts permutations (default 20).
Automatic wrapper BE retains the original num.sv B=20. A declared project
seed initializes R Mersenne-Twister/Inversion/Rejection. Both RNG states are
saved; this is not an inferred author seed.

The qualified private runtime uses Linux x86_64, R 4.2.3, sva 3.46.0,
genefilter 1.80.0, limma 3.54.0, edgeR 3.40.0, matrixStats 1.3.0,
BiocParallel 1.32.5 and jsonlite 1.8.8. See the packaged
`sva-workflow/SOURCE-PROFILE.json`. Supply the exact official
[sva 3.46.0 source archive](https://bioconductor.org/packages/3.16/bioc/src/contrib/sva_3.46.0.tar.gz)
as sva_source; its SHA256 and size are checked before extraction. Source
body/formals are compared with the loaded namespace before and after calls.
Exact qualified core package bytes (including sva's compiled library and
R base/stats files) are checked before execution. All loaded package file
fingerprints are recorded and checked for changes.
Original compiled DLL provenance is recorded; these guards do not establish
a complete native-library build or host attestation. The runtime/source
archive and raw traces stay private. Downloads are separate from analysis;
the Java command never installs software or accepts additional terms.

Outputs preserve component types, lengths and dimensions, including original
SVA empty matrices and SVASEQ scalar-zero shapes. Components are exported in
R column-major order in results/components.tsv; shapes.json and selection.tsv
describe them. worker/objects.rds retains original input/model/result objects
and RNG states. Original nonfinite outputs are retained but refused. VERIFY
requires no R: it checks all saved hashes, literal intake/transport, packaged
source, result shape/probability bounds and factor norms (plus orthogonality for
IRW/supervised; original two-step factors can be correlated). It does
not refit the model, parse binary RDS, independently recompute selection
statistics, or authenticate coordinated file/seal replacements. REPLAY
requires mode REPLAY, analysis and NEW out, optionally rscript/r_libraries,
and reruns the same declaration after verifying the parent capture.

**Warnings and required actions:** read WARNINGS.md, analysis-status.json,
worker/report.json, worker/warnings.txt and stdout/stderr logs. Admit only a
completed capture that passes VERIFY. SKIP3 (exit 3) skips the dependent
analysis when a permitted prerequisite is missing or the exact profile is
unavailable. Restore the named permitted file/runtime/package or retain the
skip. FAIL2 (exit 2) admits no complete result: preserve partials, correct the
named data/design/integrity/numerical/process/capacity cause, and retry NEW
output. Never substitute a newer edition, repair IDs, round counts or force
a reported factor count. Timeout/output limits are monitored; work/input
limits are estimates, not operating-system resource quotas.
