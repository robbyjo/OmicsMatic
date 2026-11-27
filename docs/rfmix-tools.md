# RFMix local ancestry

`rfmix-tools` runs the original **RFMix v2.03-r0**, commit `09599c19c69f81571c7a7aab4cd2d847a9e0633a`, with explicit phased query/reference genotypes, a reference sample map, a genetic map and model settings. Java independently audits the complete captured inputs and all MSP, forward/backward posterior and global-ancestry output rows. Verification runs the original model again from saved inputs and compares all four original output files byte for byte.

**Warnings and required actions:** SIS-dependent analysis is **SKIPPED**. The inspected original SIS writer leaves iteration streams open; its header also includes samples absent from numeric rows. Every saved SIS row is checked, but its final-iteration probabilities are not certified. Keep the original bytes and warnings. MSP/FB/Q completion does not qualify SIS analysis. Neither this workflow nor a successful replay establishes an author's exact version, reference panel, biological accuracy or complete paper reproduction.

## Requirements

Inference and replay require Linux x86_64 Java 17+; execution validation uses Java 21. Windows users can run Linux Java inside WSL. Help, profiles and request examples are portable. Build the executable JAR from this source with `gradlew.bat executableJar` on Windows or `./gradlew executableJar` on Linux. Retain the resulting `build/cli/omicsmatic-0.4.1.jar`: a binding and capture require their exact producing JAR. A previously released JAR may not contain this command.

Prepare an unmodified private original RFMix runtime and retain its complete original 38-file source tree. The [original source notice](https://github.com/slowkoni/rfmix/blob/09599c19c69f81571c7a7aab4cd2d847a9e0633a/README.md) permits noncommercial academic research; other uses require separate Stanford permission. Preserve original notices. The binding command installs nothing and does not redistribute original source or binaries. Runtime preparation with GNU autotools must use the declared source and retain build logs and compiler warnings.

Provide original `gzip`. BCF input additionally requires a permitted BCFtools executable, its dependencies, and an existing CSI index for each BCF. BCFtools and gzip run through immutable, quoted launchers in the binding. The BCFtools library environment is separate from RFMix's environment. The tested helper is Ubuntu-packaged BCFtools/HTSlib 1.19; a version banner alone is not evidence of a qualified scientific workflow.

## Run and verify

Use absolute Linux paths. These examples write new JSON requests; edit every example path and review every setting before execution.

```console
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --profiles
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --example binding > binding-request.json
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --bind binding-request.json --out /absolute/new-binding
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --example analysis > analysis-request.json
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --request analysis-request.json --out /absolute/new-capture
java -jar /absolute/source-built/omicsmatic-0.4.1.jar rfmix-tools --verify /absolute/new-capture --scratch /absolute/new-replay
```

In `binding-request.json`, `source` is the exact unmodified source tree, `prefix` the complete private installation, `executable` its original RFMix executable, and `python` an existing Python 3 interpreter used only for binding. `helpers.gzip` is required; `helpers.bcftools` can be null for VCF-only bindings. List complete private dependency roots in `roots`. `environment` contains explicit `PATH` and `LD_LIBRARY_PATH`; `bcftools_library_path` contains only the BCF helper's isolated library search path. Binding checks ELF architecture, the original version, actual inference on its own synthetic phased cohort, native output checks, complete declared-root populations, modes, symlinks and selected dynamic dependencies. It records the host kernel and producing JAR. Binding alone performs no user analysis.

In `analysis-request.json`, set `runtime` to the completed binding and describe the real build, phasing and cohort preparation in `preparation`. Declare each query/reference `format` as `vcf`, `vcf-gzip`, or `bcf`; set `index` to its existing absolute CSI path for BCF and null otherwise. The sample map declares `text` or `gzip`. The genetic map is plain text. All original files, including unused columns and excluded rows, are copied unchanged to stable names and checked against pre-copy identities. gzip streams undergo the original helper's complete integrity check. BCF is fully decoded with explicit `bcftools view --no-version` for the independent input audit: existing input headers remain, and no new path/timestamp header is added by this derived decode. Its complete command and stdout/stderr are retained. The original RFMix execution still reads the indexed BCF through its original BCFtools invocation. No automatic index generation, phasing, allele swap, identifier repair, missing-value repair or fallback occurs.

Use a **new directory for every attempt and replay**. Preserve producing JARs, original runtimes, successful and failed captures, and all diagnostics. A changed runtime, source, capture, kernel or producer requires investigation and a new binding/capture; do not refresh historical seals. Replay uses captured input bytes, so it does not require the original external input paths to remain available.

## Input and model rules

| Item | Required interpretation |
| --- | --- |
| Query samples | Every query VCF sample, in original header order; IDs distinct from every reference VCF ID. |
| Reference samples | Selected in **sample-map row order**. The map is TAB-delimited; source-ignored entries remain counted. Population labels coalesce by original C-locale ASCII case rules, retaining the first label, then sort by original byte order. |
| Missingness | The original filter counts **all reference VCF columns**, including samples absent from the sample map. Threshold equality is retained. This source applies no MAF/MAC filter. |
| Retained loci | One ordered chromosome block per input; unique increasing physical positions; position intersection and explicit range/missingness exclusions. Literal matching biallelic REF/ALT labels are required because the original source joins by position without checking alleles. |
| Calls | GT first, diploid 0/1/missing. Unresolved slash phase is refused. Fully observed homozygous slash calls are kept unchanged with warnings; fully missing calls remain subject to the declared threshold. |
| Genetic map | Literal chromosome or source-supported `chr` prefix; single TAB/space separators; at least two unique physical anchors; finite nondecreasing cM. No whitespace or coordinate repair. Source sorting, interpolation/extrapolation and binary32 SNP-map storage are explicit. A finite negative absolute cM offset is supported. |
| RF window | Explicit `snps` integer >=2, or `cM` value >0 and <2. The source interprets values >=2 as SNP counts. |
| CRF spacing | Explicit `snps` integer >=1, or `cM` value >0 and <1. The source uses its original ceiling/end-collapse grid, including float storage. |
| Model | Explicit CRF fixed weight or original internal calibration; generations, maximum EM iterations, reanalysis of references, trees, node size, missingness, bootstrap mode, minimum SNPs, threads and signed 32-bit seed. All valued long options use `--name=value`, as required by this source parser. |
| Range | Null for full chromosome or explicit inclusive start/end Mbp. Record the original floating multiplication and signed-int truncation to bp; do not infer endpoint conventions. |

`capacity` bounds complete input/output bytes, decoded bytes, file population, total wall time, raw variant records, samples, populations, line length and native numeric/genotype cells. `process` adds explicit original-process wall, address-space, CPU, file, workspace, process/thread and sampled aggregate RSS limits. Sampled RSS is a monitor, not a hard aggregate memory bound. Capacity exhaustion fails without truncating the population. The process guard stops only its owned process session and records whether any owned children remain.

## Results, warnings and recovery

The capture contains original inputs, their source-path/byte identities, effective parameters, source profile, runtime binding seal, `INPUT-AUDIT.json`, `OUTPUT-AUDIT.json`, original `engine/ancestry.*` outputs, original process stdout/stderr and owned-process ledgers. The input audit records every selected-chromosome locus disposition, reference selection and ignored sample-map rows. The output audit checks complete MSP state vectors, posterior grids/probabilities, Q sample/population order and Q fractions reconstructed from equal CRF-marker weights. Q is not automatically a SNP, physical-length or genetic-length weighted average. MSP coordinates are source conventions and must not be silently converted into BED intervals.

EM may stop before its requested maximum under the original likelihood-gain rule, including a negative gain. The complete finite printed initial objective and iteration trace are retained; a printed convergence message does not independently certify statistical convergence. Reanalyzed-reference Q rows receive finite/population/printing checks; query-only MSP/FB cannot reconstruct reference paths. All original warnings remain in raw logs, with identified warning lines also surfaced through the CLI and output audit.

| Exit/status | Meaning and required action |
| --- | --- |
| 0, `COMPLETE_RFMIX_RUNTIME_BINDING_ONLY` | Binding and synthetic health checks passed; no user analysis performed. |
| 0, `COMPLETE_RFMIX_MSP_FB_Q_WORKFLOW_SIS_UNQUALIFIED` | Captured original inference and complete core audits passed. Run a fresh `--verify` before using the result. SIS-dependent analysis remains skipped. |
| 0, `PASS_COMPLETE_RFMIX_BINARY_AND_NUMERICAL_REPLAY` | Fresh original inference and recomputed native audits match the complete saved outputs. All scope limits remain. |
| 3, `SKIP3 DEPENDENT RFMIX ANALYSIS SKIPPED` | A permitted input, runtime, helper, index or supported platform is missing/forbidden. Install only where allowed or supply the exact prerequisite and use NEW paths. If that is unavailable, retain the skip. |
| 2, `FAIL2 NO COMPLETE RFMIX ANALYSIS` | Invalid/changed input, source, runtime, producer, model result, QC, cancellation or resource exhaustion. Inspect the named error, `FAILURE.json` when created, original logs and process ledgers. Correct the cause explicitly and retry NEW paths. No failed attempt is a complete result. |

## Paper scope

The frozen 250-paper source census found 13 RFMix contexts across P041, P142, P184 and P229. The [source audit](literature/rfmix-source-audit/README.md) retains original versions and source roles. P041 explicitly reports **v1.5.4**: its legacy runtime remains unavailable/skipped, and this v2 adapter is not a substitute. P142's version is unreported and its per-locus ancestry averaging differs from assuming Q weights. P184 used RFMix for cohort curation and excluded it from the Diet Network benchmark. P229 reports v2, 10 EM iterations, 10 generations and 0.2 cM windows; the inspected current source instead defaults to 50/5 SNP RF/CRF settings. Require actual explicit settings and do not infer the authors' revision. Supplementary reference panels, exact masking, dating and downstream biological analysis remain separate.
