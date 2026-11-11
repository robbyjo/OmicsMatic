# Native DESeq2 size-factor normalization: development scope

The project now provides Java size-factor estimation corresponding to the median-location subset of `DESeq2::estimateSizeFactorsForMatrix` in DESeq2 1.52.0. It accepts raw integer counts, estimates `ratio` or `poscounts` factors and produces normalized counts. It also accepts explicit geometric means and an ordered control-gene selection.

**This is a normalization milestone.** It does not provide DESeq2 dispersion fitting, Wald/LRT inference, VST, rlog, log-fold-change shrinkage, tximport offsets, pseudobulk assembly or a whole-paper workflow. The catalog still classifies DESeq2 as method overlap. This work adds no new catalog implementation credit. There is no new release, deployment or main-dispatcher route.

The fixed 250-paper catalog names DESeq2 in 47 papers. A private primary-XML role audit identified normalization, Wald/LRT testing, transformed counts, shrinkage and quantifier-import contexts. These roles require further semantic review and broader integration; this small Java API does not cover them all. Reported paper editions are not replaced by an assumption that every paper used the reference edition.

## Run the example

The runnable requests and exact commands are in [examples/deseq2-normalization](../../examples/deseq2-normalization/README.md). The development artifact is built through the normal `executableJar` task with an isolated destination. The shared canonical executable is preserved.

To build an equivalent local executable from current source without changing that canonical file:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/deseq2-normalization-development.init.gradle executableJar
```

Set `JAVA_HOME` to your actual Java installation. The checked-in init script changes only the artifact destination and name, producing `build/literature-review/deseq2-runtime/normalization-development/omicsmatic-0.3.7-deseq2-normalization-development.jar`. Offline builds require cached dependencies; if the cache is incomplete, permit downloads or report the missing dependency and skip execution. Do not assume a development artifact is included in a public release.

## Input contract and source behavior

Counts are a UTF-8 TSV with header `gene` followed by unique sample IDs. Each remaining row contains a unique exact gene ID and decimal integers between zero and 2,147,483,647. Fractional quantifier estimates, negative values, missing counts, duplicate IDs and malformed rows are refused. The command never rounds counts or repairs identifiers. Count-table filtering and sample ordering are explicit caller choices.

Optional `geometric_means` is a TSV headed `gene` and `geometric_mean`, with exactly the count-table gene identities in the same order. Values may include zero, `Inf`, `NaN` or `NA`; nonfinite means exclude rows from estimation. Negative means are refused. Optional `controls` is a one-column TSV headed `gene`. Its exact entries may repeat. A header-only control file is valid and produces missing factors; an unknown gene is an error.

The median is computed on log ratios and then exponentiated. With an even number of eligible genes, this differs from taking the ordinary median of raw ratios. For computed `poscounts` means, positive log counts are summed and divided by the total number of samples, including samples with zeros. All-zero genes are excluded. Factors estimated without supplied means are not additionally centered. Supplying means triggers geometric centering of factors after estimation.

The original global error when every log geometric mean is infinite is retained. An all-NaN supplied vector instead yields source `NA` factors. A sample with no eligible positive count also gets `NA`; supplying means then propagates missingness across the centered factor vector. Overflow and indeterminate centering retain `Inf`/`NaN`. These are prominently flagged, not silently replaced. Normalized counts divide by the resulting factors with the same undefined-value behavior.

Custom location functions, R's negative/logical/recycled control-index conventions, fractional low-level matrices and iterative size-factor estimation are outside this typed raw-count subset. Java control indices are zero-based; the TSV interface selects by exact gene ID.

## Warnings and recovery

Every published attempt contains `WARNINGS.md` and `analysis-status.json`. A completed normalization with undefined or nonpositive factors has status `completed-with-unusable-factors` and `usable_for_differential_fit: false`. This status preserves the source calculation while preventing a claim that the factors satisfy downstream fit prerequisites.

**`FAIL2` means no normalization result is admitted.** Any partial tables in that retained attempt must not be used. Read the named error, correct the count/identity/selection/capacity issue, preserve the attempt and choose a new output directory. Existing directories are refused without overwriting their files. Admission errors detected before capture are printed prominently on stderr.

The request can declare `maximum_entries`, `maximum_work`, `scratch_entries`, `maximum_input_bytes` and `maximum_output_bytes`. Defaults are 20 million count entries, 100 million calculation visits, 2 million scratch-vector entries, 256 million captured-input bytes and 512 million output-table bytes. These are explicit resource limits, not a promise that every permitted matrix will fit every JVM heap. Increase the heap or reduce the explicit problem population when needed; do not silently drop rows or samples.

Java normalization requires no external runtime. A separate validated upstream workflow is still needed for DESeq2 inference. If its required runtime is unavailable or installation is forbidden, skip that dependent analysis and clearly name what the user must install or provide.

## Reference setup and validation

The original DESeq2 1.52.0 Windows package and its missing `locfit` dependency were installed into a new private reference library under `build/literature-review/deseq2-runtime/reference-setup-1/R-lib`. R 4.6.1 loaded the package and completed a small negative-binomial smoke fit. SHA-256 inventories showed all 27,958 files in the four pre-existing R libraries unchanged. This setup is a reference environment, not admission of a user inference workflow.

The first canonical-host download returned HTTP 403. Its failed attempt is retained. The successful source and binary came from Posit's mirror, which is listed by [Bioconductor](https://bioconductor.org/about/mirrors/). Version and R compatibility were checked against the [official DESeq2 package page](https://bioconductor.org/packages/3.23/bioc/html/DESeq2.html).

The loaded function's body and formals matched the official source archive. Original-package numerical controls cover 76 cases: log medians, zeros, both estimators, supplied means, repeated/empty controls, missing values, extreme counts and 48 seeded random matrices. The first Java comparison failed two all-NaN-mean cases; that failed run is retained and the guard was corrected to match the original function.

The corrected implementation passed 119 focused tests through normal Gradle. The packaged development artifact passed the same 119 checks separately on Windows Java 21, Windows Java 25 and Linux Java 21. Fifteen additional actual-main protocols checked successful execution, undefined-factor reporting, fractional-count rejection, existing-output preservation and help. Every pre-existing file entry in the preceding chromVAR development executable remained byte-identical; only seven new normalization class entries were added.

The numerical controls compare finite values with relative tolerance 2e-12 and check `NA`, `NaN` and signed infinities by kind. They do not establish historical package compatibility, biological correctness or source authentication. The full project test suite was not rerun for this isolated development milestone. The retained [validation record](deseq2-normalization-development/VALIDATION.json) and [warnings](deseq2-normalization-development/WARNINGS.md) describe the evidence and limits.

## Java API

`org.jlinalg.sequence.Deseq2SizeFactors` accepts a gene-by-sample `int[][]`. `estimate(counts, Type.RATIO)` is the simplest call. The full overload accepts geometric means, ordered zero-based controls, capacities and a cancellation supplier. Results defensively copy their arrays and retain source missing values. `normalizedCounts` offers bounded matrix output; the command streams its TSV output. Callers must keep input arrays unchanged during a call.

Source behavior is referenced to `estimateSizeFactorsForMatrix` in [DESeq2's official release source](https://github.com/bioc/DESeq2/blob/RELEASE_3_23/R/core.R). The retained original function is attributed and distributed with its LGPL notice in the test-resource directory.
