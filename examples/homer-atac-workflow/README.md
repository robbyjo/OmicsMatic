# Original ATAC tags, regions and signal tracks

This development example uses original HOMER 5.1 and the pinned original UCSC
`bedGraphToBigWig` 2.10. It starts from **plain UTF8 SAM**, uses explicit MAPQ30
filtering, builds each replicate and the merge, calls regions with 150 bp peak
fragments and 300 bp initial peaks, and creates one-base tracks normalized to
10 million weighted tags. Java independently checks the complete populations,
final region counts, printed coverage and raw BigWig intervals.

The example is synthetic. Its filter-off choices, fragment collisions and
software versions do not establish the settings or conclusions of P016.
Read [the workflow guide](../../docs/literature/omics-2023-2026/HOMER_ATAC_WORKFLOWS.md)
before using study data.

## Setup where permitted

Use Linux x86_64 Python and Java, inside WSL on Windows. Choose private Linux
paths containing letters, digits, underscore, dot, slash or hyphen. Original
C++ utilities use shell commands and comma-sensitive filename parsing. Unicode
source paths and IDs are preserved and copied to safe execution aliases.

From the repository root:

```sh
python3 tools/literature/install_homer_atac.py \
  --home /PRIVATE/homer-atac-5.1 \
  --cache /PRIVATE/homer-cache \
  --report /PRIVATE/new-atac-setup \
  --allow-install \
  --archive-dir /PRIVATE/offline-archives \
  --converter-path /PRIVATE/bedGraphToBigWig \
  --compiler /PATH/TO/g++ --make /PATH/TO/make
```

The lock is `tools/literature/homer-atac-lock.json`. Supply its exact HOMER5.1
archive and converter content pins. Alternatively add `--allow-download` where
downloads are allowed. A changed upstream download is retained and rejected;
versions are never substituted. Setup needs C++11 headers, pthread, math and
static C++/GCC runtime libraries. It configures only the compiled installation
prefix and preserves original notices; it installs no OS package or genome.

An existing home is checked without install/download authorization:

```sh
python3 tools/literature/install_homer_atac.py \
  --home /PRIVATE/homer-atac-5.1 --cache /PRIVATE/homer-cache \
  --report /PRIVATE/new-readonly-check
```

If installation, execution or a required dependency is unavailable, setup or
analysis reports **SKIPPED (exit3)** with its cause and **Required action**.
Other input, process, numerical and capacity failures use **exit2**. Preserve
failed homes, reports, caches and `partial/`; correct the cause and use new paths.
Analysis never installs, downloads, registers or silently switches versions.

## Prepare inputs

```sh
python3 examples/homer-atac-workflow/prepare_inputs.py --out /PRIVATE/new-atac-inputs
```

`replicates.tsv` has the exact header `replicate_id<TAB>sam_path`; paths are
relative to that list. The single-replicate list is `single.tsv`. Reference
lengths have no header: `contig<TAB>length`. The source SAM `@SQ` dictionaries
must agree with the supplied lengths and cover all eligible contigs.

For study data, perform intended adapter removal, alignment and duplicate
processing upstream. This entry performs no alignment or deduplication. BAM,
CRAM and compressed SAM require an explicit prior export/decompression step.
Do not label a synthetic dictionary as mm10 or infer aliases between assemblies.

## Run one explicit technical recipe

```sh
java -Xmx2g -jar /PATH/TO/omicsmatic.jar homer-tools atac \
  --home /PRIVATE/homer-atac-5.1 --profile linux-x86_64-atac-5.1 \
  --out /PRIVATE/new-atac-result \
  --sam-list /PRIVATE/new-atac-inputs/replicates.tsv \
  --reference-lengths /PRIVATE/new-atac-inputs/reference.tsv \
  --reference-build synthetic-ATAC-example-v1 \
  --reference-provenance "Independent synthetic fixture; no paper data" \
  --duplicate-alignments error --supplementary-alignments error \
  --unpaired-alignments error --improper-pairs error \
  --zero-template-length error --identical-replicates error \
  --effective-genome-size 3000000 --genome-size-policy error \
  --peak-style original-default --peak-local-fold 0 \
  --peak-clonal-fold 0 --peak-tag-cap 0 \
  --coverage-length-normalization 100 --coverage-endpoints extend \
  --maximum-records 10000 --maximum-ledger-keys 100 \
  --maximum-chromosome-tags 10000 --maximum-regions 1000 \
  --maximum-coverage-rows 100000 --workspace-bytes 268435456
```

Here local/clonal filters and the discovery tag cap are disabled to exercise a
known synthetic positive population. Choose scientifically appropriate values
for real data. `peak-style`, local/clonal folds, tag cap, genome denominator,
length normalization and endpoint handling are explicit because P016 does not
state complete HOMER commands or its edition. The upstream FDR default is0.001
and minimum region distance is600 in this entry; both can be set explicitly.

## Verify all three recipes against fresh originals

```sh
python3 tools/literature/verify_homer_atac_workflow.py \
  --home /PRIVATE/homer-atac-5.1 --jar /PATH/TO/omicsmatic.jar \
  --out /PRIVATE/new-atac-verification
```

The verifier runs default-region, ATAC and single-replicate histone recipes.
Each packaged run must complete every native audit and retain clear warnings.
It reruns the original programs from the immutable SAM population in separate
new directories and compares every tag/QC population, region row, coverage bin
and converter artifact. It is a technical check, without a biological or
statistical calibration certificate.

Review `WARNINGS.md`, `session.json`, `runtime-admission.json`, `replicates.tsv`,
`alignments.tsv`, `native-expected-tags.tsv`, `native-actual-tags.tsv`,
`regions.tsv`, `native-coverage.bedgraph`, `coverage.stdout`, `coverage.bw` and
all original stderr files. The manifest hashes every retained file. Missing
source coverage bins are not automatically evidence of biological zeros.
Broader HOMER integration remains unfinished, including the reviewed RNA role.
Cell Ranger remains excluded.
