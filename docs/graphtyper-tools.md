# GraphTyper joint calling

**Qualified scope: 14 complete original-calling captures and fresh saved-input replays, with independent original comparisons; 25 native/contract tests on each operating system. Catalog admission and publication are tracked separately.**

`graphtyper-tools` captures an actual aligned-read cohort and compatible reference, runs an original GraphTyper edition, audits complete final VCF records and literal genotypes, and reruns the analysis from the captured inputs for verification. Java provides native reference, alignment/index, VCF/reference and quality-arithmetic components. The original caller performs graph construction, discovery, read alignment within graphs and genotyping.

**Warnings and recovery actions**

| Exit | Meaning | Required action |
| --- | --- | --- |
| 0 | The named operation completed within its stated scope. A runtime binding alone admits no analysis. | For an analysis, retain `COMPLETED.json`, inspect every warning and original log, then run a fresh `--verify` with the exact producing JAR and unchanged runtime. |
| 3 / `SKIP3` | A required permitted program, runtime, input or platform is unavailable. Dependent analysis was skipped. | Supply or install the named prerequisite where permitted. If installation is forbidden, leave that analysis skipped. Retry using new binding and capture directories. |
| 2 / `FAIL2` | Input/reference/index/sample mismatch, changed source/runtime, failed original calling or QC, cancellation, numerical failure or exhausted capacity. No complete result is admitted. | Keep the failed attempt and all provisional files. Investigate the named cause in `FAILURE.json` and original logs. Correct it explicitly and retry using new paths. Never refresh historical seals or reuse a failed output directory. |

Inspect `WARNINGS.md`, `INPUT-AUDIT.json`, `OUTPUT-AUDIT.json`, `WORKER-FAILURE.json` when present, and **all** original stdout/stderr files. Some source warnings remain only in the original logs. Partial VCFs from a failed process are not completed results. These commands install nothing; download and installation remain separate permitted setup operations.

## Editions and paper roles

The [source audit](literature/graphtyper-source-audit/ROLES.json) preserves version evidence and unresolved author recipes separately.

| Profile | Source | Role in the audited papers |
| --- | --- | --- |
| `graphtyper-v2.7.5-ddc58a70` | [v2.7.5](https://github.com/DecodeGenetics/graphtyper/tree/ddc58a70349ecf303845c5995d1e9c964cf99ed7), MIT | P116: SNP/indel joint calling after the stated upstream coverage and duplicate processing. The reported variant information criterion is not automatically an `AAScore` cutoff. |
| `graphtyper-v2.7.1-ffb1ab2b` | [v2.7.1](https://github.com/DecodeGenetics/graphtyper/tree/ffb1ab2bea804973f197784783707dd46d7d5087), MIT | P152: WGS calling alongside GATK HaplotypeCaller. The division between callers and imported HQ recipe remain unresolved. |
| `graphtyper-v2.0-beta-b9c603e8` | [v2.0-beta](https://github.com/DecodeGenetics/graphtyper/tree/b9c603e827a4f7da65cde142dc9c8205b7d0546e), GPLv3 | P149: cohort SNP/indel calling before separate phasing and imputation. Methods did not specify the version locally; code availability names this legacy edition. |
| `graphtyper-v1.4-ebd4855d` | [v1.4](https://github.com/DecodeGenetics/graphtyper/tree/ebd4855d08fbe1ddaa36d4bc0cebe5ba4bb27f73), GPLv3 | P132: germline joint calls for association analyses before phasing and imputation. This is not the somatic clonal-haematopoiesis driver-calling workflow. |

The v1.4 tag's own CMake version is **1.3**. Its public profile retains both identities. It has no official release binary in the audited release. The private source build uses its exact nine original dependency commits, C++11, and explicit standard-header compatibility flags `-include cstdint` and `-include array` for the newer compiler. Scientific source files remain unchanged. Do not replace it with a newer GraphTyper binary merely to obtain matching version text.

Legacy editions use different unchanged [upstream pipeline](https://github.com/DecodeGenetics/graphtyper-pipelines) commits: `e7ec5ab56a1086ca00cbc88a0e75cf060f960c8e` for v1.4 and `00b4a0ae52cc56f50825b8a138e654a4dbfea427` for v2.0-beta. The former has four calling/cleaning iterations and only final small-variant results; the latter uses five and also retains haplotype and haplotype-call outputs. The older pipeline has no genotype-only branch.

**v1.4 naming limitation:** its discovery VCF defaults to canonical `chr`-prefixed human coordinates. Consistent unprefixed inputs can therefore produce discovery sites that subsequent indexed graph initialization misses. This integration rejects unprefixed v1.4 target names. Prepare an actually compatible reference, BAMs, indexes, sites and targets explicitly, with provenance; it performs no automatic name conversion. The original fixed human contig lengths in its VCF headers are retained and do not describe a caller-supplied partial reference. Native reference audits use the actual FASTA, not those source header lengths.

## Set up and run

Upstream execution and replay require Linux x86_64 Java. On Windows, run Linux Java inside WSL. Native Java methods and their portable tests also run on Windows. Keep original articles, downloaded source, binaries, raw runtime traces and research inputs private. Public resources contain file identities, own code and synthetic numerical oracles, not those private materials.

1. Obtain the selected original source view and original executable from the [official releases](https://github.com/DecodeGenetics/graphtyper/releases), or the declared exact v1.4 source build. Keep dependency sources and build provenance for a source build. The packaged `--profiles` output lists exact admitted source-file and binary SHA256 identities. A different build needs separate review and qualification; do not alter an old binding's identities.
2. Supply existing Python 3.9 or newer. The two newer editions use their original integrated binary and need no external Samtools, Tabix or BCFtools in this workflow. Legacy scripts require the exact tested Samtools, Tabix, BCFtools and GNU parallel binaries listed by the profiles, plus Bash, Perl and the ordinary named shell utilities. Declare complete private runtime roots, actual library search paths and unchanged original pipeline files. Binding records health probes, executable/library closure, Python standard library, and the legacy Perl search population. It changes no system packages.
3. Create and edit the appropriate binding example. Choose the version explicitly. Legacy scripts require program, pipeline and output paths without whitespace or shell metacharacters.

```text
java -jar omicsmatic-0.4.1.jar graphtyper-tools --profiles
java -jar omicsmatic-0.4.1.jar graphtyper-tools --example binding
java -jar omicsmatic-0.4.1.jar graphtyper-tools --example legacy-binding
java -jar omicsmatic-0.4.1.jar graphtyper-tools --example v1.4-binding
java -jar omicsmatic-0.4.1.jar graphtyper-tools --bind binding-request.json --out /new/binding
```

4. Create an analysis example (`analysis`, `legacy-analysis` or `v1.4-analysis`) and replace **all** illustrative paths, sample IDs and preparation text. Declare the complete cohort and actual one-based inclusive, ordered, nonoverlapping targets. Inputs are copied under owned names; their bytes, indexes and file population are captured completely. The workflow starts with an actual uncompressed indexed FASTA and coordinate-sorted indexed BAMs, or modern-edition CRAMs. Actual RG/SM header metadata and matching mapped-read RG tags are required; this interface does not infer original fallback sample IDs from filenames. Any read-group preparation must be explicit and describe the actual samples. Legacy profiles require prepared BAMs. A sample must not span multiple supplied alignment files; explicitly merge and document its actual alignment before capture. Literal sample IDs must be safe for original per-sample filenames. No sample identifiers are repaired.
5. Set capacities deliberately. Example byte capacities permit large WGS inputs but do not allocate storage or reserve memory. Record, line, read-length, genotype-cell, wall, workspace and process limits are enforced; exhaustion fails the whole attempt. Legacy original read length is bounded at 151 bases. No reads are trimmed or dropped to meet a limit.

```text
java -jar omicsmatic-0.4.1.jar graphtyper-tools --request analysis-request.json --out /new/capture
java -jar omicsmatic-0.4.1.jar graphtyper-tools --verify /new/capture --scratch /new/replay
```

Preserve the exact producing JAR and runtime. An updated JAR does not replace a historical producer. Verification checks the complete saved file population and runtime, reruns original calling from saved inputs, and compares every canonical final VCF field, genotype allele/counter, native input audit and native record table. Raw files and clocks remain sealed. Only original `##fileDate=` clock headers are excluded from scientific replay comparison. This does not authenticate external evidence or reproduce the papers' undisclosed recipes.

## Calling and merge choices

Modern `mode` is one of `discovery`, `discovery-with-prior`, and `genotype-only`. Discovery uses the original integrated caller's defaults. Prior discovery passes the actual indexed VCF through `--prior_vcf`; genotype-only passes `--vcf`. These are different models and are not promises that the final variant population equals the supplied site's representation. The VCF and its actual index must be supplied together for either initialization mode. Prior sites must contain literal SNP/indel alleles in this profile; symbolic/SV and breakend preparation is outside its scope. Their full raw record and indexed-overlap populations are audited before calling.

Legacy support settings are explicit. The original scripts still keep any hardcoded discovery-stage settings; caller support parameters apply only where those scripts pass them. The v1.4 pipeline selects its original small/cohort option branch, with the declared explicit options supplied to both branches.

Legacy targets must consist of complete declared slices, so the last slice cannot silently extend the target. `legacy_merge_policy` is explicit:

- `original-node-no-sort` runs the unchanged original node orchestration. Its no-sort merge can fail Tabix indexing when overlapping haplotype-call outputs are out of order. Such a run fails admission, with no successful-output salvage.
- `original-slices-sorted` runs unchanged per-slice scripts and the **upstream default sorted `vcf_concatenate`** on each complete final-role population. It applies the original sorting, INFO regeneration, variant-ID suffix generation and zero-QUAL eligibility. It is a declared different merge policy, with no claim that it reproduces the failing no-sort node operation or an author's exact policy.

Original scripts delete temporary graph files between iterations as part of normal upstream operation, even when final cleanup is disabled. Surviving intermediate files, every command/log and all final output roles remain retained. No source patch or silent scientific correction is applied.

## Native Java components

- `GraphtyperAlignmentAudit`: actual FASTA index/sequence checks; every alignment and literal RG/SM identity; coordinate order; full SAM-field hashes; complete sequential versus indexed overlap populations. Reference-name/length agreement does not establish a genome build, and undeclared BAM sequence checksums cannot establish one either.
- `GraphtyperVcfAudit`: every original final data field, full header population and literal GT allele; complete, missing, partial and phased genotype counts; separate GT-derived counts and source INFO `AC`/`AN`. It does not impute missing alleles or equate those two count definitions.
- `GraphtyperVcfReferenceAudit`: actual REF sequence matching and supplied SNP/indel index-query agreement.
- `GraphtyperQuality`: original v2.7.1/v2.7.5 raw allele-score and logistic-filter arithmetic on supplied **unrounded internal features**. Its 712 independent unmodified-C++-header oracles include boundary neighbours, original MQ capping and logistic saturation. It requires finite features/predictors and preserves source saturation. It does not derive all features or implement complete eligibility gates, penalties or a paper's HQ recipe. In particular, printed missing `ABHom=-1` can correspond to internal `ABHom=0.985`; rounded VCF fields cannot reconstruct the original classifier inputs.

None of these components performs population phasing, imputation, somatic driver identification, GATK joint calling, the complete GraphTyper SV pipeline or whole-paper reproduction. Those scopes must remain separate.

The independent PySAM controls retain missing-index notices for modern original `input_sites` VCFs. Those source intermediates have no TBI and are audited sequentially in full; no missing records or index repair is inferred. The original files and warnings remain retained.
