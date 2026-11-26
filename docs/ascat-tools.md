# ASCAT workflows

`ascat-tools` runs original ASCAT through explicit, private Linux runtimes. Java captures every input, checks complete output populations and independently evaluates the original segment/objective calculations. Original R code performs correction, segmentation and model fitting. `ascat-objective` remains a separate native numerical command.

**Warnings and required actions:** A high ASCAT goodness-of-fit score does not establish correct segmentation or biological recovery. A retained synthetic experiment produced scores above 99.99% while missing its intended copy-number states and purity. Inspect raw and segmented plots, source metrics, model alternatives and experiment-specific QC; do not use the score as the sole validation criterion. This workflow does not reproduce an entire paper or establish that its reference data and parameters match yours.

**Require both a completed capture and a successful fresh replay.** Read `WARNINGS.md`, `OUTPUT-AUDIT.json`, `engine/SOURCE-WARNINGS.json`, every `sample-summary.tsv` and original process logs. SKIP3 means a permitted prerequisite is unavailable and the dependent analysis was skipped. Supply the named software, input, index or reference where installation/use is permitted, or retain the skip. FAIL2 means there is no complete result: preserve the attempt, correct the named data/runtime/model/QC/resource/cancellation problem and retry new output paths. Never edit a seal or reuse a failed output directory. An externally killed JVM may return a signal status and leave an incomplete capture.

The qualified targeted experiment emits **50,136 original min/max warnings**. All25,068 retained matched-normal loci are heterozygous; the original homozygous-stretch helper calculates a zero threshold and tries empty stretches. A separate direct-source diagnostic confirms that their bounds are NA and the original segmentation code skips them. Final finite copy states match independent truth at every retained locus. Full warning arrays and logs remain intact, and the CLI prints original messages. This diagnosis applies to that retained experiment: investigate warnings in your own run rather than assuming the same cause or harmlessness. Do not add artificial loci or change source thresholds to suppress them.

## Start with a source-built JAR

Earlier 0.4.1 release downloads predate this command. Build the current source with Java 21:

```powershell
.\gradlew.bat executableJar
```

The resulting JAR is under `build/cli/`. Metadata and example generation work on Windows. Execution and replay require Linux x86_64 Java, including Java running inside WSL. Calling the execution commands from Windows returns SKIP3 before parsing Linux paths.

```bash
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --help
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --profiles
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --example binding > /absolute/binding-request.json
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --example matrices > /absolute/analysis-request.json
```

Replace every placeholder with the actual experiment and permitted runtime. Keep all generated fields: omitted fields and unknown parameters are refused instead of selecting hidden defaults. Example QC thresholds and penalties are examples, not author recipes or universal cutoffs.

## Prepare and bind an original runtime

The binder checks installed prerequisites; it does not install software or fit samples. Install only where permitted, in a separate private prefix/library. Preserve the exact producing JAR and completed binding for replay.

| Profile | Original source | Required R | Preparation |
| --- | --- | --- | --- |
| `ASCAT-3.2.0-R4.6.2` | [3.2.0 commit 4445969](https://github.com/VanLoo-lab/ascat/tree/4445969adf5956d0aa1f4b6e393cf31d8a34fb95) | 4.6.2 | Matrices; original HTS and targeted preparation |
| `ASCAT-2.5.2-R4.0.2` | [2.5.2 commit fdf9325](https://github.com/VanLoo-lab/ascat/tree/fdf9325ea39402f30046f8b9e1ac254ffb4fd67d) | 4.0.2 | Prepared matrices |

The selected source path is the package directory containing `DESCRIPTION`, `NAMESPACE` and `R/`, usually the archive's `ASCAT/` subdirectory. Use its complete unmodified population. `--profiles` lists every required package version, namespace-function hash and source-file identity. A matching version label alone is insufficient. A failed legacy run under R 4.6.2 is retained separately; no upstream source was patched to make it run.

The qualified legacy baseline is the complete matched100-sample example without correction. A separate single-sample legacy GC experiment failed in the original `ascat.aspcf` code (`ghs[[sample]]: subscript out of bounds`); direct unmodified-source execution reproduced it. That recipe remains unqualified. Do not add artificial samples, repair the source or silently switch versions to force success. Use a separately justified compatible workflow/profile or skip the dependent analysis. A current tumor-only experiment likewise had no optimum and was refused; a separate experiment with explicitly declared genotype composition passed. Platform choice must follow the actual assay and inputs.

Declare `PATH`, `LD_LIBRARY_PATH`, `R_LIBS_USER`, empty `R_LIBS_SITE`, `JAVA_HOME` and three explicit empty startup files in the binding request. Include complete prefix/library/config roots and the fonts used for plotting. The `python` interpreter must be the interpreter selected by that `PATH`; private R/Conda prefixes can also contain Python. Empty startup files prevent hidden user R configuration from changing the run. Never point roots at a broad changing workspace.

For matrix-only analysis, `counter` can be JSON `null`. HTS/targeted preparation needs an unmodified [alleleCounter 4.3.0](https://github.com/cancerit/alleleCount/tree/64c5f3f5b4d14a5c87bbeea860bf8bc6c5930c79) C runtime. Add this object to a current-profile binding request:

```json
{
  "source": "/absolute/unmodified/alleleCount-source",
  "executable": "/absolute/counter-prefix/bin/alleleCounter",
  "environment": {
    "LD_LIBRARY_PATH": "/absolute/counter-prefix/lib:/absolute/required-sdk/lib",
    "HTS_PATH": "/absolute/counter-prefix/libexec/htslib"
  }
}
```

This counter environment is isolated in a bound launcher; it is not injected into R. The private qualification build uses original HTSlib 1.11 and libdeflate 1.6. Their libraries must not replace newer libraries required by the R plotting runtime. The Perl wrapper is not required or qualified. ASCAT's shell-based counting APIs require binding, capture and replay paths without spaces or shell metacharacters; original input paths are copied to safe captured names.

```bash
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --bind /absolute/binding-request.json --out /absolute/NEW-binding
```

The full directory/link/native-library census, exact program selections, original namespace and plotting health must pass. Binding completion means only that the runtime is bound. After a host/kernel, source, program or dependency change, prepare a new binding; preserve earlier bindings and their producing JARs.

## Declare the complete experiment

Choose a generated template with `--example matrices`, `--example hts` or `--example targeted`.

For matrices, provide complete tab-delimited tumor logR and BAF, plus both normal matrices or neither. The first three columns are source locus ID, chromosome and positive position; every subsequent column must match the declared samples in order. Source locus IDs/coordinates must agree across matrices. `NA`, `NaN`, `Inf` and `-Inf` remain literal source input tokens. Original ASCAT performs its own documented transformations and explicit chromosome selection; all input file bytes remain captured. No sample is dropped to obtain successful admission.

For BAM/CRAM preparation, provide each tumor and optional matched normal, an allele-information file for every declared chromosome, and a loci file per chromosome in `hts` mode. Existing indexes must be named `ALIGNMENT.bam.bai` or `ALIGNMENT.cram.crai`. BAM auditing checks every BGZF block and BAM record; CRAM has complete byte capture and magic validation, with upstream indexed decoding of requested loci, not an independent every-record CRAM audit. CRAM requires an explicit complete FASTA and existing `FASTA.fai`. Actual counter qualification checks the FASTA argument by execution.

Allele files have a header and columns `position`, `a0`, `a1`; original ASCAT uses numeric A/C/G/T indices 1/2/3/4. Loci files have no header and contain chromosome and position. Targeted mode generates its loci from the exact zero-based BED capture design and a complete panel containing one distinct normal per patient. Each panel member needs its ID, patient ID, XX/XY declaration and indexed alignment. The source performs all panel filtering and exports its QC/cleaned references; it can remove samples or loci under its original rules. Inspect these outputs. The original problem-loci reader uses a header despite its no-header parameter description; supply and inspect the intended source input explicitly rather than silently repairing it.

Declare sex handling and reference build, chromosomes, gamma, penalty, seed, fitting bounds, correction files and a minimum goodness-of-fit threshold. The admission comparison is explicitly `>=` the supplied threshold; this does not resolve a paper that reports inconsistent `>`/`>=` boundaries. `none`, GC and current GC+replication-timing correction call the corresponding original functions. Tumor-only fitting requires an explicit genotype platform from the selected original namespace's live branches. No platform is inferred.

Original correction can remove probes absent from its reference file. Compare the complete input counts in `INPUT-AUDIT.json` with corrected/segmented row counts and locus IDs in `OUTPUT-AUDIT.json` and the exported tables. Supply complete intended correction references, and investigate every unintended loss. Source filtering is preserved; a completed process does not establish that your reference coverage is adequate.

`aspcf` fits samples separately. Joint `asmultipcf` requires prepared cohort matrices, an explicit source algorithm/refinement state, and acknowledgment of its first-normal germline policy via `source-first-normal`. Optional weights cover every sample. Manual rho/psi states must cover every matrix sample; current source permits mixed missing/manual states, whereas legacy mixed states are refused. Legacy source retains its fixed fitting bounds. Runtime failures and unreportable samples remain failures, including cases whose output files were partly written.

Capacity settings bound input/output bytes, files, total time, complete native objective cells, BAM decoding and child execution. Hard per-process address-space/CPU/file/open-file limits accompany owned-process wall, workspace, process/thread and sampled aggregate RSS monitoring. Sampled RSS is not a hard aggregate memory limit. Increase only appropriate declared capacities before retrying a new path; do not remove samples or loci to force a pass.

## Run, inspect and replay

```bash
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --request /absolute/analysis-request.json --out /absolute/NEW-capture
java -jar /absolute/omicsmatic-0.4.1.jar ascat-tools --verify /absolute/NEW-capture --scratch /absolute/NEW-replay
```

The capture retains request/provenance, every input and index, effective source parameters, original loaded/corrected/segmented/fitted objects, complete per-locus and segment tables, sample admission states, current-edition metrics, plots, warnings and process records. Original `NULL`, `NA`, `NaN`, `Inf`, duplicate ordinal headers and failed states are not silently repaired. Legacy metrics are labeled as legacy source outputs; current-only metrics are not fabricated.

Per-SNP `nA`/`nB` describe nucleotide-allele copy counts. Segment `nMajor`/`nMinor` describe major/minor copy states. At a germline-homozygous SNP, the former can be `(totalCN, 0)` inside a balanced segment. Choose the table appropriate to the question; these encodings are not interchangeable.

Java checks every declared table row and sample, all original model PNG chunk CRCs, complete autosomal segment runs and all explicit purity/ploidy objective cells. PNG CRC validation is not full pixel decoding or a nonblank-plot test. Original fitting can cap rho-above-one fallback objective cells at `1e20`; the raw native objective is preserved and the source fitting changes are counted separately.

Fresh verification uses only captured inputs and an unchanged bound runtime, reruns the complete upstream workflow and compares canonical scientific output bytes, including original model objects, complete tables, QC and PNGs. It never evaluates arbitrary user R code or deserializes user-provided RDS. Keep the original source-built JAR: changing it invalidates the previous producing context.

## Paper-specific limits and additional tools

ASCAT R fitting, `ascatNgs`, benchmark comparisons and imported TCGA purity/ploidy estimates are different roles. Core ASCAT does not supply a complete WGS calling pipeline. An author's unreported R version, internal ascatNgs release, preprocessing, event filters or model parameters are not inferred from a public comparison tag.

P146's supplement explicitly names ConstructASCATFiles and PREASCAT before ASCAT fitting. Their own original analysis code has no source-wide license identified in the inspected snapshots. **SKIP3: their runtime analyses remain skipped pending applicable permission or licensing from the rights holders.** The bundled SAMtools license and generated IDE-file notices do not establish terms for the repositories' own analysis code. Neither was installed, executed or redistributed. Prepared matrices obtained through an independently permitted process can still be analyzed by ASCAT; this does not qualify those preprocessing tools or reproduce P146.

The full 250-paper implementation and semantic-completeness audit remain open. Cell Ranger remains excluded by the user. MaxQuant runtime analysis remains skipped, with MaxQuant, Andromeda and MaxDIA pending.
