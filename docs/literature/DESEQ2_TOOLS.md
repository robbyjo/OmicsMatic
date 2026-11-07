# DESeq2: native normalization and original-runtime analysis

Current workspace source provides two main commands: `deseq2-normalize` for native Java size factors/count normalization, and `deseq2-tools` for original DESeq2 inference, shrinkage, transformations and portable verification. The tested development executable has both commands. Older canonical/released JARs lack these routes; this work has not been released, pushed or deployed.

## Quick start

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/deseq2-tools-development.init.gradle executableJar
$deseqJar = 'build/literature-review/deseq2-runtime/public-tools-development/omicsmatic-0.3.7-deseq2-tools-development.jar'
java -jar $deseqJar deseq2-normalize --request examples/deseq2-tools/normalize.json
```

Set `JAVA_HOME` to your actual JDK. If dependencies are not cached, remove `--offline` only where downloads are permitted. The normalization example needs Java only. It writes a **new** `build/deseq2-tools-example-normalize` folder. Read its `WARNINGS.md`, `analysis-status.json`, `size-factors.tsv` and `normalized-counts.tsv`. Native normalization does not fit differential expression.

For original-runtime analysis, first install a new private runtime where installation is allowed:

```powershell
New-Item -ItemType Directory -Force build/deseq2-user-runtime | Out-Null
& 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --vanilla tools/literature/install_deseq2_runtime.R build/deseq2-user-runtime/R-lib build/deseq2-user-runtime/report allow
```

Use your actual Rscript path. This tested profile requires **R 4.6.1 and DESeq2 1.52.0** with the declared dependencies. The installer uses Bioconductor 3.23 plus CRAN. Read setup `WARNINGS.md` and `STATUS.tsv`; a successful install is not a completed analysis. Reuse a successful runtime instead of reinstalling into existing paths. Requests declare `rscript` and `r_libraries`; update them to your permitted installations.

```powershell
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/paired.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/shrinkage.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/lrt.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/verify-paired.json
```

The paired example uses six synthetic paired donors, explicit mean dispersion fitting, no automatic outlier replacement, Wald and threshold tests, VST/rlog, and selection of 200 genes **after normalization across all 240 supplied genes**. The other examples demonstrate normal/apeglm/ashr shrinkage and an overall three-condition LRT. These are explicit synthetic settings, not inferred paper settings. Examples share immutable input tables with the earlier standalone examples. Use fresh output paths after any attempt; existing folders are refused.

## Required recovery

**SKIP3 means skip the dependent analysis.** Supply the named exact runtime/package only where installation and execution are permitted. If installation is forbidden, invoke the installer with `forbid` instead of `allow`; it reports a skip without creating a library. If requirements remain unavailable or forbidden, do not run the dependent request. Analysis never installs packages automatically or silently substitutes another edition.

**FAIL2 means no result is admitted, including partial tables.** Preserve the attempt, read `WARNINGS.md`, `analysis-status.json`, `worker/worker-report.json`, worker stdout/stderr and the manifest. Correct the named count/sample/design/contrast/source/process/capacity problem, then choose NEW output paths. A successful setup, partial TSV or version probe is insufficient. For deseq2-tools ANALYZE, require its completion marker, matching hashes and a successful VERIFY; inspect retained source warnings and nonfinite values. Native deseq2-normalize NORMALIZE has no VERIFY operation: inspect its completion/status, manifest, size factors and normalized-count warnings directly; it does not perform a differential fit.

Inputs must be integer raw counts, or estimated counts with explicitly requested `R_round`. TPM, FPKM, RPKM, CPM, log values, residuals and VST output are not raw counts. There is no automatic rounding, count reconstruction, identifier repair, imputation or biological-replicate inference. Explicit technical collapsing retains biological replicate identity. Quantifier import/tximport length offsets, SoupX correction, pseudobulking and upstream peak counting remain separate.

**VERIFY checks capture consistency, not authenticity or independent statistical inference.** It checks sources, input identities, effective parameters, native normalization and published/worker table agreement. It does not refit negative-binomial models, iterative factors, shrinkage or transformations. Coordinated fabricated worker/published tables with recomputed hashes can pass structural checks. Source guards check selected R functions/formals, not installed DLL authenticity; hashes do not prove execution or biology. The logical output cap is not an R memory/RSS cap.

## Paper review and limits

The fixed 250-paper cohort contains 49 checked DESeq2 records: 47 previously extracted uses plus two recovered Code availability declarations. P088's publisher supplement establishes **normalization before limma-voom**, not DESeq2 differential testing or LDSC heritability. P082 explicitly lists DESeq2 but its exact role remains unresolved after checking the main text and eight author notebooks; do not assign scran/scNOVA tests to it.

The reviewed uses retain historical editions and unknown settings. P098 explicitly reports 1.40.2 and P248 1.30.1; P049 reports only 1.44. P124's 1.36 applies to ATAC-seq only; P155's version attribution is ambiguous. Reported TPM/transformed-input discrepancies in P086, P108, P226 and P249 remain unresolved. P086's attribution of `ComBat_seq` to DESeq2 is retained as a discrepancy: the function belongs to **sva**. Do not silently replace it with ComBat or alter count units.

Historical packages, HOMER/iDEP/DiffBind/Galaxy/Signac wrappers, limma-voom, SVA, GSVA/GSEA, FastQTL/QTLtools, bootstrap/mRRA/MAGeCK procedures and whole-paper pipelines are separate requirements. The current runtime does not certify historical 1.14–1.48 behavior; older threshold-test semantics may differ. Cell Ranger remains excluded by user instruction.

## Validation and references

The normal Gradle executable changes only the main dispatcher class relative to the frozen inference JAR. All scientific classes/resources and the canonical shared JAR remain unchanged. Four validated main-route R fits on Windows Java 21/25 match independent original expectations across 35,457 finite values and 665 nonfinite kinds, with maximum relative error zero. Six native analyses and 12 Java-only capture verifications also cover Linux Java 21. Actual Linux R fitting was not executed. Existing-output refusals, a real missing-runtime skip and rejection of its capture pass. A first harness attempt with a Windows-only absolute VERIFY path on Linux is retained; the corrected request uses a portable relative path.

The parent inference checkpoint retains 26 original fits, 264,436 finite/2,972 nonfinite comparisons, typed contract tests, failures, skips and timeout cleanup. The native checkpoint retains original size-factor comparisons and source edge cases. These are scoped synthetic/source controls, not biological accuracy, historical whole-package or whole-paper reproduction.

- [Current main workflow evidence](deseq2-tools-development/VALIDATION.json)
- [Original-runtime contract/reference](DESEQ2_INFERENCE_DEVELOPMENT.md)
- [Native normalization contract/reference](DESEQ2_NORMALIZATION_DEVELOPMENT.md)
- [Main-command examples](../../examples/deseq2-tools/README.md)
- [49 reviewed paper roles](omics-2023-2026/deseq2-role-coverage.json)
- [Exact reviewed declaration extension](../../tools/literature/collect_reviewed_omics.py) and [additional primary-source locators](../../tools/literature/deseq2-additional-source-evidence.json)

Earlier standalone guides/evidence describe their frozen, pre-dispatch checkpoints. Use this guide for the current main routes. The full toolbox implementation and paper-wide semantic review remain incomplete; literature pages remain withheld from deployment.
