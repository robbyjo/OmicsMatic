# SpliceAI: original five-model inference and native VCF ledgers

`spliceai-tools` runs unchanged SpliceAI **1.3** with all five original neural models in the qualified private Linux CPU environment. Its portable Java backend also audits canonical SpliceAI VCF annotations. Both backends retain every input row, ALT, gene context, literal score/position token and duplicate annotation.

```text
java -jar omicsmatic-0.4.1.jar spliceai-tools --request request.json
java -jar omicsmatic-0.4.1.jar spliceai-tools --verify output-bundle
```

**Read `WARNINGS.md`, `analysis-status.json`, `result-summary.json`, raw VCFs and original worker stderr before using results.** Exit **3 / SKIP3** skips dependent analysis when an exact permitted prerequisite or requested edition is unavailable. Exit **2 / FAIL2** admits no complete result after invalid/changed inputs, hashes, runtime, numerical output, process failure, timeout or capacity failure. Preserve diagnostics, correct the named cause and retry into a **new output directory**. Existing output directories are preserved.

Missing or nonfinite scores remain **unassessable**. Unannotated alleles and the source's eight-dot MNV tuples are not converted to zero. Scores are source-formatted to two decimals; `0.00` does not establish biological absence. Negative finite values and signed zero are retained. Observed maxima/minima describe supplied scores; this command does not apply clinical or paper-specific classification rules.

## Portable annotated-VCF audit

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "backend": "NATIVE",
  "out": "spliceai-audit",
  "annotated_vcf": "complete-annotated.vcf.gz",
  "original_vcf": "complete-original.vcf"
}
```

`original_vcf` is optional. When supplied, every row and literal CHROM/POS/ID/REF/ALT and every sample/column label must survive in the same order. Original and source-reserialized QUAL, FILTER, non-SpliceAI INFO, FORMAT and sample tokens are both retained and differences reported. Without the original file, original-input preservation cannot be established. Plain or gzip UTF-8 input is accepted by content.

Require one canonical INFO header with these ten fields:

```text
ALLELE|SYMBOL|DS_AG|DS_AL|DS_DG|DS_DL|DP_AG|DP_AL|DP_DG|DP_DL
```

All four delta scores and positions remain separate. The command rejects ambiguous repeated ALT tokens, malformed schemas, repeated/flag/empty SpliceAI INFO, unmatched annotation alleles, invalid numbers and changed row populations. A `SpliceAI=.` value or absent INFO remains an explicit unannotated allele. Native import retains partial or nonfinite source tuples with diagnostic states and makes no claim that it executed a prediction model or established the original model edition/options.

## Complete original inference

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "backend": "ORIGINAL",
  "out": "spliceai-prediction",
  "profile": "original-SpliceAI1.3-CPU-linux64",
  "input_vcf": "complete-original.vcf",
  "runtime": "/opt/omicsmatic-spliceai13-20261128-1/runtime",
  "reference": {
    "fasta": "/references/complete-human/genome.fa",
    "fai": "/references/complete-human/genome.fa.fai",
    "fasta_sha256": "REPLACE_WITH_FULL_FASTA_SHA256",
    "fai_sha256": "REPLACE_WITH_FULL_INDEX_SHA256",
    "assembly": "GRCh37"
  },
  "annotation": {"kind": "bundled-GENCODE24lift37"},
  "analyses": [
    {"id": "unmasked", "distance": 50, "mask": 0},
    {"id": "masked", "distance": 50, "mask": 1}
  ],
  "threads": 4,
  "timeout_seconds": 3600
}
```

The placeholder hashes above are deliberately invalid: supply lowercase 64-character SHA256 values computed over the **entire** FASTA and index. Paths in a request resolve against its directory. The full uncompressed FASTA is read, hashed and independently reindexed in memory to validate its existing `.fai`; no external index is generated or overwritten. Its exact companion must be `FASTA.fai`. FASTA/index remain external and their identities are captured; saved verification requires neither resource. Inputs and gene annotations are captured completely. External reference bytes and original inputs are checked again after the complete workflow.

Every analysis requires explicit source `distance` (**0..4999 inclusive**) and `mask` (**0 or1**); there are no inferred analysis defaults. The source's 10,000-base model context and all five model assets are retained. Larger distance requests are SKIP3 and require separate edition qualification; they are not clamped or silently run with1.3.1. If original1.3 fails for a requested tuple, including deletion/window shape failures, preserve FAIL2 rather than shortening alleles, dropping rows or reducing the requested window/model set.

Annotation choices:

| `annotation.kind` | Required reference / meaning |
|---|---|
| `bundled-GENCODE24lift37` | GRCh37; unchanged complete upstream `grch37.txt` |
| `bundled-GENCODE24` | GRCh38; unchanged complete upstream `grch38.txt` |
| `provided` | Explicit `file` and nonblank provenance `id`; complete caller-supplied source table |

For a provided table, use `{"kind":"provided","file":"complete-gene-table.tsv","id":"literal_annotation_provenance"}`. Its exact seven columns are `#NAME`, `CHROM`, `STRAND`, `TX_START`, `TX_END`, `EXON_START`, `EXON_END`. Start coordinates are zero-based and ends are one-based inclusive; exon lists require trailing commas. Gene versions and duplicate contexts are preserved. Invalid intervals, missing identifiers and representations that pandas would silently turn into missing identifiers are rejected. No transcript edition, MANE/canonical fallback or custom GENCODE39 definition is inferred.

The source normalizes contig prefixes separately against its first annotation contig and first FASTA contig. In1.3, removing a prefix uses Python `strip('chr')`, including its character-strip semantics at both ends. Java preflight records this exact FASTA lookup in `reference-checks.tsv`; it does not change original VCF labels, invent other chromosome aliases or perform lift-over. Literal uppercase REF must match the reference. Ambiguous N bases remain explicit. Fresh input must have no existing SpliceAI header/INFO. Unsupported symbolic/breakend/star alleles produce SKIP3 without recoding them as DNA; the source's `<NON_REF>` and dot-ALT omissions remain explicit unannotated alleles.

## Runtime and permissions

The original profile pins Git tag **v1.3**, commit **409d0683bfe48bebe6dbfc96abf84629011100e8**, and PyPI1.3 prediction files, both complete gene tables and all five model files. These prediction assets were byte-identical across those two original distributions; their full distributions are not claimed identical. The fixed driver calls unchanged `spliceai.__main__.main`, rather than the wheel's extra console interruption helper.

The qualified private environment uses Python3.7.6, CPU TensorFlow1.15.5, Keras2.2.4, numpy1.18.5, pandas0.25.3, h5py2.10.0, pysam0.16.0.1, pyfaidx0.5.9.5, protobuf3.20.3 and scipy1.4.1. The packaged `org/jlinalg/omics/spliceai13/RUNTIME.json` records the entire installed environment and current host executable-library hashes, including all original model assets. Source/models/binaries are **not distributed in this repository**. Historical source and current upstream licensing may differ; obtain the intended original artifacts under applicable permitted terms.

Original execution is qualified for the recorded Linux x86_64 CPU environment. Windows supports native audit and portable saved verification. The adapter does not install software, relax host hashes, automatically refresh the profile, load user Python code or substitute another TensorFlow/model edition. Preserve or restore the exact qualified private runtime at its recorded prefix; relocation is not qualified. A new installation/host requires separate complete runtime/source/model qualification against the recorded manifest and original controls. Do not accept a changed runtime merely because its package versions look similar. If you cannot obtain or install the exact permitted prerequisites, **leave the dependent analysis skipped** and use supplied annotations only when their provenance is suitable.

Worker stdout/stderr, request, runtime report and owned-process receipt are retained. Normal process exit alone is insufficient: original `main` must return normally, all requested input rows must survive, all five models and pinned dependencies must be available, and fresh inference must contain no partial/nonfinite/gene-unassigned prediction tuples. CPU-only affinity and requested thread settings are recorded. Source deprecation, CPU-instruction and absent-training-configuration warnings remain visible; inference does not need to compile the original models. Only owned workers/descendants are cleaned up after an actual failure, timeout or cancellation.

## Outputs, limits and saved verification

Each analysis contains `variants.tsv`, `annotations.tsv`, `alleles.tsv`, `genes.tsv`, `source-reserialization.tsv` and `schema.json`. Annotation indexes preserve duplicate gene contexts. Raw original/source fields remain in JSON columns and original VCF captures. Results report unannotated alleles, missing/nonfinite tuples, partial tuples and changed source serialization explicitly.

Defaults:8GiB captured input,32GB full external/captured resources,16GiB worker/results output,100,000 output members,32GiB decoded characters,8MiB per line,10million variant rows and100million annotation/allele/exon entries. Override the corresponding closed `maximum_*` fields explicitly where needed; exceeding a complete-input bound is FAIL2, without sampling/truncation. There are1..16 uniquely named requested analyses. Time/output monitoring is not an OS memory/disk quota.

`--verify` checks the complete integrity seal, packaged producer/profile/worker bindings, original-versus-captured scientific options and exact analysis population, then recomputes **every native result ledger** from retained complete VCFs. It works without the original runtime, model files, FASTA or index. It does not repeat neural inference, independently authenticate upstream execution or certify an assembly label. SHA256 seals detect integrity changes; they are not signatures. Use the retained producing JAR for its saved captures.

The paper-role audit is [here](literature/spliceai-source-audit/ROLES.json). Full collection scope remains250 papers; this workflow does not establish complete BRaVa/VEP plugins, author-defined GENCODE39 inputs, junction-specific masks, experimental validation, clinical interpretation or whole-paper reproduction.

Primary sources: [original v1.3](https://github.com/Illumina/SpliceAI/tree/v1.3), [original utilities](https://github.com/Illumina/SpliceAI/blob/v1.3/spliceai/utils.py), [original tests](https://github.com/Illumina/SpliceAI/blob/v1.3/tests/test_delta_score.py), [PyPI1.3](https://pypi.org/project/spliceai/1.3/).
