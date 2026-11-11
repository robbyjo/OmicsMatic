# ChIP-seq 1.2.1 original DESeq2 postfit audit

**Development component only. This adds no tool to the integration count. The full nf-core adapter and the 250-paper implementation goal remain unfinished.**

This separate executable inspects a complete captured original nf-core/chipseq 1.2.1 result. It runs the original R 3.6.2 / DESeq2 1.26.0 / genefilter 1.68.0 postfit calculation in the exact private upstream image. Java checks the complete count, model and published-output populations. It does not fit a replacement negative-binomial model, independently recompute rlog/vst, or admit biological results. The released OmicsMatic JAR is unchanged.

Run the repository integrity check first:

```powershell
python tools/literature/verify_nfcore_deseq126_checkpoint.py
```

The native reference replays work on Windows and Linux without R or a container runtime. The executable's original postfit inspection requires Linux Java 17 or later, x86_64, an allowed unprivileged container namespace, the exact private Apptainer 1.5.5-1 home, and the saved original ChIP-seq 1.2.1 SIF. These large runtime files are separate prerequisites; the JAR does not contain or install them. The resource inventory and image hash identify the already admitted development runtime; an arbitrary rebuild or another DESeq2 version is not interchangeable. The literal provider suffix `-1` is retained.

On the development Linux environment, the exact staged paths are `/var/tmp/omicsmatic-nfcore-apptainer-1` and `/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif`. Other users need a permitted installation and this exact admitted image before running the executable. If installation is forbidden or a prerequisite is unavailable, the dependent analysis is skipped. Do not weaken the lock or relabel another image to bypass that outcome.

Run on Linux, replacing the captured result and output paths with your own. The output's parent directory must already exist. The output directory must be new and disjoint from the original result, producer workspace, image and runtime.

```bash
java -Xmx2g -jar docs/literature/nfcore-deseq126-development/nfcore-deseq126-javac21.jar \
  --engine-result /absolute/path/to/complete-captured-attempt \
  --peak-type broadPeak --min-replicates 1 --read-mode paired \
  --container-home /var/tmp/omicsmatic-nfcore-apptainer-1 \
  --image /var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif \
  --out /absolute/path/to/new-audit
```

Declare the actual source peak kind, replicate threshold and read mode; the example uses the captured development control's values. Typed saved parameters must agree. The single-read profile uses `.mLb.clN.sorted.bam`; paired reads use `.mLb.clN.bam`. No sample aliases, suffix repairs, alternate versions or smaller populations are inferred.

The audit verifies the complete captured bundle and all 85 locked pipeline source files, the full checked sample/control graph, complete peak populations and native consensus outputs. It selects the original differential branch only when both `replicatesExist` and `multipleGroups` are true. Each saved model must match the complete featureCounts count matrix, `~condition` design, actual saved treatment-coded model matrix, original coefficient names, default Wald/no-beta-prior/no-t-test settings and sample order.

Java verifies full-precision default ratio size factors, every normalized count, effective fitting-count base means, defined two-sided normal Wald probabilities, all 50 Type7/BH/LOWESS filtering families and selection at source alpha **0.1**, every adjusted value, strict FDR `<0.01` and `<0.05` row membership, BED projections and the combined master table. Written filtered files have no extra fold-change filter. Rounded seven-significant-digit size-factor files are checked at their original display precision; full-precision calculations use the fitted object.

**Warnings and required actions**

Read `WARNINGS.md` before using an audit. It names relevant source behavior and retains original warnings; `audit.json`, each `process/process.stderr`, `worker/ORIGINAL_WARNINGS.txt` and `worker/sessionInfo.txt` provide the details.

- **Source coordinates:** the original pipeline copies BED's zero-based Start unchanged into SAF. The audit preserves that Start and featureCounts' inclusive `Length=End-Start+1`. A coordinate conversion needs a separate declared analysis with originals retained.
- **Actual dispersion fit:** the captured 1,472-gene control records `local`, following the original fit fallback. The audit reports the saved fit; it does not assume `parametric` or refit it.
- **Replacement and missing values:** original raw counts remain intact. Original effective replacement counts and replacement flags are exported separately. DESeq2 can retain an `NA` replacement flag for an all-zero raw gene; that flag remains `NA`. Missing statistics, `NA` versus `NaN`, and source scientific notation are retained. Exact integer values such as `1e+08` are accepted without rounding; fractional or out-of-range counts are refused.
- **Convergence:** original nonconverged genes are named and warned about. Review their diagnostics before scientific use. A component consistency check provides no fitted-model admission.
- **Original package warning:** the image reports that matrixStats was built under R 3.6.3. The complete warning remains visible; packages are not upgraded or replaced during analysis.
- **SKIP3 / exit 3 — analysis skipped:** a required platform, runtime, image or allowed namespace is unavailable. Install/download the exact prerequisites only where permitted, or use an allowed Linux environment. Retry a new output directory after resolving the named cause.
- **FAIL2 / exit 2 — no component result:** an input, integrity, process, cleanup, capacity or numeric contract failed. Preserve the failed directory and original data. Inspect the named cause, correct it, and retry a new directory. Do not hide a failure by editing saved hashes, dropping genes or comparisons, changing coordinates, filling missing values, or changing source defaults.

Limits apply to the owned process tree and complete audit workspace. Defaults are 1,800 seconds wall time and CPU per process, 8 GiB address space per process, 2 GiB sampled aggregate RSS, 16 GiB workspace, 1,000,000,000 bytes per output file, 100,000 filesystem entries, 128 processes, 256 threads, 1,024 open files and 25 ms monitoring. Sampled aggregate RSS is an observation bound, not a hard kernel aggregate memory limit. `--help` lists explicit capacity overrides. Exceeding a limit fails the audit; it does not reduce scientific analysis parameters. The native complete-matrix limit is 20 million entries, at most one million genes and 10,000 samples; actual heap availability also matters.

The complete original reference populations contain 1,472 genes/four samples, 300 genes/six samples/three contrasts (including all-zero genes and a contrast with two zero groups), and 300 genes/16 samples with one automatically replaced outlier. Both compiler builds are replayed under Windows Java 21, Windows Java 25 and Linux Java 21. Deliberate late-row, sample, design, contrast, alpha, adjusted-value, filter-family, coordinate, replacement, integer, FDR and BED inconsistencies must fail. Runtime controls distinguish unavailable prerequisites from integrity/capacity failures and verify owned-process cleanup. The host allows namespaces, so its prohibition branch is not exercised by changing host security settings.

Source, license notices, complete reference exports, passing controls and earlier failed attempts are retained in the development checkpoint. See [native consensus](NFCORE_CONSENSUS.md) and [source numerical methods](SOURCE_NUMERICS.md) for the byte-identical inherited components. All evidence is a scoped software control; it does not reproduce a cohort paper or certify scientific validity.
