# Original ChIP-seq 1.2.1 workflow adapter

This development executable runs the locked original nf-core/chipseq **1.2.1** workflow, captures its complete publication tree and requires five source-specific output audits. It uses exact Nextflow **21.04.1**, the admitted private Apptainer **1.5.5-1** runtime and the original pinned image. The released OmicsMatic executable is unchanged. The catalog remains at **99 new scoped integrations**; the broader nf-core family entry and the 250-paper implementation goal remain open.

**Read failures before using results.** Exit **3 / SKIP3** means the affected analysis was skipped because its exact prerequisite or requested capability is missing, forbidden or unsupported. Install/download the exact prerequisites separately only where permitted, or provide an admitted local runtime. Exit **2 / FAIL2** means no adapter result is admitted: inspect the named stage's `process.stderr`, `process.stdout`, `process.json`, `FAILURE.json` and `WARNINGS.md`. Preserve the failure and original data. Correct the cause and use new preparation/run paths for changed inputs, settings or capacities. Require a completed result and successful `--verify` before downstream use.

The executable takes its options directly; it has no central `omicsmatic` command route yet. Execution requires Linux x86_64 and Java 17 or later, including Linux Java inside WSL on Windows. Complete saved results can be verified on Windows and Linux without installing a scientific runtime.

Verify the repository checkpoint and view help from the repository root:

```text
python tools/literature/verify_nfcore_chipseq_checkpoint.py
java -jar docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac21.jar --help
```

Prepare the exact prerequisites first. Export the Nextflow setup kit with `--export-runtime-setup NEW_DIRECTORY`, read its `README.txt`, and select exact engine `linux-x86_64-nextflow-21.04.1`. Keep its completed current-host report; repeat the separate health check after a host/kernel change. The [foundation guide](NFCORE_FOUNDATION.md) describes this setup. The [postfit guide](NFCORE_DESEQ126.md) identifies the exact separately staged container runtime and image. The adapter checks their complete admitted inventory and image hash. Arbitrary runtime rebuilds or differently tagged images are not interchangeable. Analysis does not download, install or change namespace/security settings. If the permitted exact setup is unavailable, skip this analysis.

Obtain the exact original workflow source separately and keep its original executable permissions. Every one of its 85 files must match `src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json`. The selected tag resolves to commit `0f487ed76dc947793ab48527d8d3025f5f3060a5`; this is the tested upstream source selection, not an inferred author commit. The original workflow uses a six-column samplesheet:

```csv
group,replicate,fastq_1,fastq_2,antibody,control
T0,1,reads/T0_R1.fastq.gz,reads/T0_R2.fastq.gz,MARK,INPUT
INPUT,1,reads/INPUT_R1.fastq.gz,reads/INPUT_R2.fastq.gz,,
```

Supply the complete experiment and appropriate original control mapping; the two-row example illustrates the schema only. Relative FASTQ paths resolve against the explicitly selected `--data-root`. All design, FASTQ, FASTA, GTF and additional resource paths must lie under that root. The preparer copies only selected files. It retains the original design and input byte hashes, changes only the two filename columns in a separate derived design, and preserves original non-path cells. It does not rename scientific identifiers, infer mates, repair coordinates, reduce input rows or select reads.

On Linux, replace these absolute example paths with your actual paths. The preparation and run directories must be new, disjoint, and have existing canonical parents. Use ASCII run paths without whitespace or commas. Values for genome size and resource limits below are examples, not scientific recommendations:

```bash
python3 tools/literature/prepare_nfcore_chipseq121.py \
  --workflow /absolute/source/chipseq-1.2.1 \
  --data-root /absolute/experiment \
  --design /absolute/experiment/design.csv \
  --fasta /absolute/experiment/reference/genome.fa \
  --gtf /absolute/experiment/reference/genes.gtf \
  --macs-genome-size 1.2e7 \
  --container-home /absolute/admitted-apptainer-home \
  --image /absolute/admitted-chipseq-1.2.1.sif \
  --out /absolute/new-preparation \
  --run-root /absolute/new-run \
  --max-cpus 4 --executor-memory-bytes 25769803776 \
  --max-memory 8.GB --max-time 2.h

java -Xmx2g -jar docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac21.jar \
  --request /absolute/new-preparation/request.json \
  --home /absolute/exact-nextflow21-home \
  --setup-report /absolute/current-completed-host-report \
  --out /absolute/new-run

java -Xmx2g -jar docs/literature/nfcore-chipseq121-development/nfcore-chipseq-javac21.jar \
  --verify /absolute/new-run
```

Preparation alone admits no analysis. A runtime-only overlay selects the local executor, its aggregate CPU/memory capacity, the exact image, private cache and permitted unprivileged namespace. Original workflow code, scientific defaults and original task requirements remain intact. The original configs remain primary Nextflow configs in their declared order; no institutional overrides are inferred. Source task maxima remain explicit and separate from aggregate executor limits.

Additional original parameters use `--parameters /absolute/parameters.json`. Unknown keys, duplicate JSON keys and wrong scalar types fail. Original numeric tokens retain their spelling. Additional file inputs must be explicitly captured, for example `--resource blacklist=/absolute/experiment/blacklist.bed` with `{"blacklist":{"$input":"blacklist"}}` in the parameter file. Bind an optional directory member with `{"$input":"resource_name","path":"relative/member"}`. Literal external file paths are refused. The explicit local-reference profile does not admit iGenomes resolution, institutional config changes, notifications or alternate publication modes. Required branches cannot be skipped to conceal failures. Typed peak/read selections and consensus minimum must agree with the original parameters. The complete execution control covers paired-end broad peaks with the original default minimum; it does not establish every combination of optional settings.

Optional `--limits /absolute/limits.json` overrides the recorded capacities. Defaults include two-hour wall/per-process CPU limits, 64 GiB per-process address space, 256 GiB owned workspace, 512 owned processes, 16,384 threads, sampled aggregate RSS of 32 GiB, 2 GiB child Java heap and 64 MiB engine metadata. The process monitor's sampled RSS check is not a hard aggregate memory bound. Fixed scientific auditors also have explicit complete-table/record capacities described in the linked component guides. Insufficient capacity fails; no smaller dataset or partial curve is substituted. A fresh parent JVM is required after an in-place host Java update; do not change launch mechanisms or rewrite scientific settings to disguise a spawn-helper failure.

`--resume /absolute/original-run --home ... --setup-report ...` resumes only a known terminal attempt using its immutable captured request, inputs and engine cache. It does not recapture changed producer files. Indeterminate `RUNNING` attempts require inspection of their owned-process evidence; no automatic takeover is admitted. Changed inputs/settings/capacities require new preparation/run paths.

The local run retains the mutable engine work/cache and every attempt. Each completed `results/NNNNNN` is a separate portable bundle containing the complete original engine attempt and immutable objects, all component reports, runtime/owned-process ledgers, original request/recipe, exact adapter JAR, warnings and complete manifests. Copy that entire result directory; a subset of published tables is insufficient. Verify the copied directory using `--verify /absolute/copied-result`. Verification rechecks all file/directory members, original source/engine contracts, component/engine bindings and clean required process receipts. It does not rerun science or readmit the original host. Verifying a run root requires its latest attempt to be completed; a failed latest attempt never falls back to an earlier success. An explicitly selected retained completed result remains independently verifiable. Hashes establish integrity, not execution authenticity.

The five required checks cover complete original FASTQ/GTF/reference and sample mappings, complete filtered BAM flags and FRiP, original consensus/SAF outputs, original marked-BAM duplication metrics and full library-complexity curves, complete HOMER annotation/QC tables, and conditionally requested original DESeq2 postfit results. The original workflow's own task/output declarations and complete trace remain mandatory for every stage, including QC programs outside those native checks. No failed, ignored, aborted or unfinished task is admitted. Source-ineligible differential analysis receives an explicit `NOT_REQUESTED` record; this is not a negative biological finding.

Native HOMER checks retain a subtle source rule: `parseGTF.pl -gid` emits transcript rows sorted by transcript ID; the original relative-position reader keeps the last row for each exact contig/gene ID. Raw transcript rows remain retained, and no gene-span merge is inferred. Unicode scalar ordering, gene metadata version trimming, tied nearest assignments, BED-to-HOMER coordinate conversion and dot-strand handling follow the tested source behavior. Raw missing TSS annotations stay `NA`; only the source plotting copy becomes `Unassigned`. Absent sample/feature categories stay `NA`, not zero. Consensus BED score is literal zero, distinct from the Boolean table's peak count. All peak-QC measures and six statistics are checked over complete source populations, including Type-7 quartiles and original `end-start` widths.

Original feature classifications and PDF rendering remain upstream methods. Optical clustering, Preseq extrapolation, negative-binomial fitting and stored rlog/vst values are not independently reimplemented by this adapter. Original dispersion-fit fallback, missing/nonfinite estimates, package-version warnings, permission-mode departures and optional Graphviz warnings remain visible. Read all component warnings after success. Software controls do not certify biological accuracy or reproduce a paper. P169 reports pipeline 1.2.1 and leaves its Nextflow edition unreported; this adapter's explicit engine selection must not be attributed to the authors. ChIP-seq 2.0.0, eager, rnasplice, Hi-C and unversioned paper roles remain separate required work. Cell Ranger remains excluded.

See the [consensus](NFCORE_CONSENSUS.md), [Picard complexity](NFCORE_COMPLEXITY.md) and [DESeq2 postfit](NFCORE_DESEQ126.md) guides for their full source contracts and limitations. The checkpoint retains complete original annotation/QC references, sparse and Unicode/version controls, cross-compiler/platform replays, deliberate discrepancies and earlier failures. Large original FASTQs/BAMs and private scientific runtimes remain local.

The execution evidence retains the first run's 97 completed original tasks and both compiler builds' resumed runs, each with 95 cached tasks and two newly completed tasks. These runs reuse the same complete six-library input population. Annotation controls replay 4,829 original rows plus two separate nine-row source controls on Windows Java 21/25 and Linux Java 21 with both compiler builds; all 17 deliberate annotation/QC discrepancies are refused. The typed request checks cover 34 cases across both builds. Portable controls cover eight successful selections/replays and 15 deliberate failures, including rehashed semantic changes and a failed latest pointer. Two actual runtime fault controls produce the expected skip and no-result outcomes. These are software checks, not independent biological datasets.
