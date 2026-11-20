# Native edgeR count-matrix normalization

**Native Java normalization is implemented and qualified. At this checkpoint,
cumulative edgeR catalog admission and differential-model workflows remain open.** This adds
no admitted family or review credit yet. The catalog at this checkpoint stays at 138 integrations,
1,503 reviewed uses and 1,201 pending. An audited source census identifies
17 positive edgeR roles, including one proposed missed observation in P088.

Prepared feature-by-sample matrices support **TMM, TMMwsp, RLE, upper-quartile
and unity normalization; raw/log2 CPM; and raw/log2 RPKM**. Source profiles are
edgeR **3.32.1, 3.36.0, 3.40.2, 3.42.4 and 4.10.7**. Normal analysis and
saved-input verification run entirely in Java. There is no automatic upstream
fallback or package installation in this native command.

These operations do not implement filterByExpr, sumTechReps, dispersion
estimation, exact tests, GLM LRT/QLF tests, limma/voom, offset matrices,
annotation inference or S4 containers. A prepared normalization matrix does
not establish an author workflow or biological result. The older project
moderated/negative-binomial estimator remains a distinct related method.

## Run the file workflow

Build current source with the qualified JDK 21 build setup. The normal Gradle
build targets Java 17 bytecode and passes strict compiler warnings; execution
was tested on Windows Java 21/25 and Linux Java 21. Published release 0.4.0
predates this component. No new release is created here.

```powershell
.\gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --help
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --request examples/edger-native/request.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --verify build/NEW-edger-native-cpm
```

Use the [complete synthetic examples](../../examples/edger-native/README.md)
for TMM/CPM, TMMwsp/log-RPKM and REPLAY. This is a standalone class entry
point; global command discovery/admission is a separate unfinished step.
Use NEW outputs. Relative paths resolve from the request document, including
paths for the parent analysis and REPLAY destination.

Counts are a UTF-8 tab-separated matrix whose first header is `feature_id`,
followed by literal unique sample IDs; each remaining row starts with a
literal unique feature ID. Counts must be finite nonnegative decimal numbers;
fractional counts are accepted. IDs, row order, sample order and raw bytes are
retained. Missing values, duplicates, invalid UTF-8, whitespace in numeric
tokens and nonfinite input numbers are refused, rather than repaired.

Optional library sizes use `sample_id` and `library_size` headers, with exactly
one positive size in the original column order. RPKM additionally requires
`feature_id` and `length_bp`, with exactly one positive length in bases per
feature in the original row order. No labels are joined, sorted, made unique,
recycled or mapped automatically. There is no gene-length lookup.

**Preserve original library sizes when the supplied feature matrix was
filtered.** Without an explicit library table, sizes are column sums of the
supplied matrix. The command does not infer which earlier filtering, depth,
replicate or sample exclusions an author intended.

Required RUN fields are `schema_version: 1`, `mode: "RUN"`, exact `edition`,
`method`, `counts` and NEW `out`. Methods are case-sensitive `TMM`, `TMMwsp`,
`RLE`, `upperquartile` and `none`. Optional `transform` is `NONE`, `CPM`,
`LOG_CPM`, `RPKM` or `LOG_RPKM` (default CPM). Other scientific options:

| Field | Default | Meaning |
|---|---|---|
| `libraries` | Supplied matrix column sums | Exact original library sizes |
| `gene_lengths` | None | Required exactly for RPKM transforms |
| `reference_sample` | Original automatic rule | Literal count-header sample ID |
| `logratio_trim` | 0.3 | M-value trim, finite in [0,0.5) |
| `sum_trim` | 0.05 | A-value trim, finite in [0,0.5) |
| `weighting` | true | Original weighted mean rule |
| `a_cutoff` | -1e10 | Finite TMM A cutoff; original TMMwsp ignores it explicitly |
| `quantile` | 0.75 | Upper-quantile probability in [0,1] |
| `normalized_library_sizes` | true | Transforms use library size times normalization factor |
| `prior_count` | 2 | Original library-scaled prior for log transforms |

Resolved defaults are saved in `request.json`; original request bytes are
retained separately. Advanced capacity fields bound input/output bytes,
features, samples, entries and normalization work. Unknown/code fields and
duplicate JSON keys are refused. Raising an output or work limit requires an
explicit new request; the component does not reduce the dataset to fit.

`normalization.tsv` records each sample's original library size, factor and
effective size. `expression.tsv` retains the count matrix identities/order
for the requested transform. `analysis-status.json`, `WARNINGS.md`, captured
raw inputs, producer-class fingerprints and the complete inventory are saved.
VERIFY checks the inventory, exact qualified producer and closed scientific
request, recomputes from captured inputs, and compares every output and warning.
It also rejects unreferenced extra payloads. REPLAY uses NEW output outside the
parent; Windows junction and Linux symlink controls protect the parent too.

## Numerical behavior and failures

TMM uses average ranks for tied M/A values. TMMwsp uses singleton-positive
pairing, the original shrunk-M tie breaker, stable ordering and its distinct
weight formula. Its source accepts `Acutoff` without applying it; Java reports
that fact rather than adding a new filter. Empty shared TMM sets retain the
original unity-factor outcome and an explicit warning. Original undefined
RLE/quantile means are not replaced with unity. R NA/NaN undefined factor
outcomes are represented as Java NaN, with original R distinctions retained
in the reference records. No complete file result is admitted for nonfinite
normalization or expression values.

Log CPM scales the prior by library size and adds twice that scaled prior to
the denominator. A zero count with prior zero retains the original NaN; it
is not given a finite pseudocount or converted silently to another value.
Raw CPM preserves the historical versus 4.10.7 multiplication/division order.
The binary logarithm uses the attributed OpenLibm split-precision algorithm:
`log(x)/log(2)` changed genuine tied-rank decisions in the first candidate.
No epsilon tie-merging was added.

**SKIP3:** supply the named exact permitted source, runtime, edition or input,
or keep its dependent analysis skipped if it cannot be supplied. Unsupported
native editions such as 3.20.1, 3.22.3 and 3.38.4 are not replaced by nearby
profiles. **FAIL2 means NO COMPLETE RESULT.** Preserve original inputs, partials,
warnings and diagnostics, correct the cause and retry NEW output. Read status
JSON; a sealed checksum alone does not establish valid arithmetic or authenticity.

**Full original edgeR 3.32.1 and 3.36.0 packages failed installation against
R 4.6's LAPACK character-length interface. Their full-package/GLM analyses
remain SKIP3.** Supply and separately qualify a compatible original R runtime
to close those deployments. Do not strip ABI checks, patch the scientific
source or substitute another package edition. Their selected original CPM
kernels were compiled unchanged and tested separately, as described below;
that result does not close the failed full-package scope.

The retained full-package builds of 3.40.2/3.42.4 include an original compiler
format warning in unrelated hairpin-read reporting. The source was preserved;
hairpin analysis was not qualified. Initial R source references also retain
four inherited C.UTF-8 startup-locale warnings. Session/condition records are
saved; later explicit-C reference processes are separate named runs.

## Qualification and its limits

The normal JAR passes **5,165 checks on each of three JVMs**, including existing
generic differential-estimator regressions. All **23,460 previous decoded JAR
members** (including directory entries) remain byte-identical. New members are
the native component classes and the packaged attribution notice.

- 2,350 independent normalization returns use 47 datasets and five exact
  original R source editions under Windows R 4.6.1. Current 4.10.7 additionally
  uses the installed original namespace, with source bodies/formals checked.
- 552 CPM/RPKM returns use the full original 4.10.7 package.
- 2,208 additional historical returns use full original 3.40.2/3.42.4 packages
  and explicitly bounded original 3.32.1/3.36.0 C++ kernel providers. The latter
  compile unmodified original CPM/add-prior/object sources and use the original
  R matrix helpers; linking their exact native entry points is a declared
  reference adapter. It is not a stock package namespace or a GLM deployment.
- Two composed original-package examples (TMM/CPM and TMMwsp/log-RPKM) match
  Java exactly. Actual Java RUN/VERIFY/REPLAY succeeds with R absent from PATH.
  Twenty-five Windows21 captures recompute exactly on Windows25 and Linux21.
- Three actual parent/path-alias controls return FAIL2 before creating output,
  leaving each parent valid. File controls cover literal IDs, invalid data,
  capacity failures, missing-input skips, changed/resealed arithmetic, request
  provenance, producer changes and extra files.

Original model returns, conditions and RNG states are retained. The first
reference export rounded some supplied library-size inputs; the corrected
export uses 17-significant-digit strings. An independent R comparison confirms
that original inputs, all original returns/conditions and RNG states are
unchanged. Earlier failing candidates, the first fixture, build/control errors
and full-package installation skips remain labelled and preserved. Native
numerical differences are recorded per host; no tolerance was loosened to
accept a failing candidate. These synthetic/source checks establish neither
author inputs/host parity, all possible numerical cases nor whole-paper results.

See [saved proof and readback](edger-normalization-checks/README.md) and the
[17-paper source census](edger-normalization-checks/CENSUS.json). The full
250-paper semantic review and implementation goal remains unfinished. Next:
original dispersion/exact/GLM workflows, filtering/replicate functions, unresolved
historical profiles, source-role coverage/admission and remaining high-impact tools.

Original edgeR source, version and GPL metadata are linked from
[Bioconductor](https://bioconductor.org/packages/edgeR/). The Java derivative
is GPL-3.0-or-later. The OpenLibm source is pinned to
[v0.8.7 commit 9fbeafcd](https://github.com/JuliaMath/openlibm/tree/9fbeafcd4f1b6ef6aa3946c1c8faead50f38a94d);
its Sun permission notice is retained in source and the normal JAR.
