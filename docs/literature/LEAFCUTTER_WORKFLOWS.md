# LeafCutter workflows: setup, inputs and result admission

This development adapter executes the unchanged original RegTools clustering script, phenotype/PCA script and differential-splicing R program at upstream commit `2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb`. Its R package reports **0.2.9**. The commit is the edition identifier; it is not an official 0.2.9 release tag. Java independently checks counts, phenotype preprocessing, PCA geometry and retained model calculations. Read the validation receipt in `leafcutter-workflow-development/VALIDATION.json` for the candidate JAR and admitted scope. The canonical release JAR is separate.

> **Required before using results:** require process exit **0** and `analysis-status.json` with **`result_admitted: true`**. Read `WARNINGS.md` and preserve the whole output directory, including the manifest, captured inputs, raw original files, RDS fits, lossless source reports, identifier/sample maps and process receipts.
>
> **SKIP3, exit 3:** a required runtime, edition, permission or installation is unavailable. Keep dependent analysis skipped. Supply the exact permitted runtime through separate preparation below; analysis installs nothing. If installation is forbidden, preserve the warning and skip the analysis.
>
> **FAIL2, exit 2:** no result from the whole requested batch is admitted, including earlier completed cases or stages. Keep all original and provisional files. Correct the named input, capacity, runtime, numerical, optimizer, timeout or process error and retry a **new** output path. Never omit a failing case, repair counts/IDs, replace missing/NaN states or switch versions to obtain a pass.

## Quick start

Use Java 21 inside Linux x86_64 or WSL for upstream execution. Windows Java 21/25 returns SKIP3 for this workflow; the native Java APIs linked below remain portable. Set `JAR` to the development artifact recorded in the validation receipt, not an older release JAR.

```sh
JAR=/absolute/path/to/omicsmatic-0.3.7-leafcutter-workflow-development.jar
java -cp "$JAR" org.jlinalg.cli.LeafCutterWorkflowCli --help
java -cp "$JAR" org.jlinalg.cli.LeafCutterWorkflowSetupCli \
  --export-runtime-setup /absolute/path/to/new-setup-kit
python3 -I -B /absolute/path/to/new-setup-kit/setup/prepare.py --help
```

Export only writes source, notices, dependency locks and the standalone setup helper. It downloads/installs nothing. Use a **new canonical absolute directory** under an existing writable parent. Do not use symlinked parents or an existing output.

## Prepare a private runtime

INSTALL requires an explicitly supplied, permitted micromamba executable and new private paths. The 239 exact dependency archives, Python/R versions, package sources and licenses are pinned in the exported kit. No shared package environment or original package requirements are modified. Compilation uses one job and `-O0 -g0`; allow time for the original Stan models to compile.

**The setup/cache directory and runtime prefix parent must be case-sensitive.** Linux dependencies contain filenames that differ only in letter case. On an unsuitable filesystem preparation returns SKIP3 **before invoking the installer or creating a software prefix**. Prefer new directories on a native Linux/WSL filesystem. For permitted Windows storage, prepare a new empty case-sensitive folder following [Microsoft's WSL instructions](https://learn.microsoft.com/en-us/windows/wsl/case-sensitivity), then create setup and prefix children from WSL. The helper probes the actual directories; it does not change filesystem settings. Preserve a failed partial prefix and use a fresh location.

```sh
python3 -I -B /absolute/path/to/new-setup-kit/setup/prepare.py \
  --mode INSTALL \
  --out /absolute/case-sensitive/path/new-preparation \
  --prefix /absolute/case-sensitive/path/new-runtime \
  --micromamba /absolute/path/to/permitted/micromamba \
  --allow-install --allow-download
```

If downloads are forbidden, omit `--allow-download` and supply `--archive-cache /absolute/path/to/exact-archive-cache`. The flat cache must contain all required archive filenames with exact bytes and hashes. A missing archive keeps preparation and dependent analysis skipped. `--allow-install` remains required even with a complete cache. The included R source archives need no extra download.

CAPTURE is available for the existing exact dependency and private-library builds made by this project's original LeafCutter preparation tools:

```sh
python3 -I -B /absolute/path/to/new-setup-kit/setup/prepare.py \
  --mode CAPTURE --out /absolute/path/to/new-binding \
  --dependency-receipt /absolute/path/to/dependency-build/COMPLETED.json \
  --library-receipt /absolute/path/to/private-R-library-build/COMPLETED.json
```

CAPTURE requires those complete matching build receipts, original source stage and unchanged inventories. It does not accept an arbitrary installed package based on its version string. Both modes must finish with `PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY` in `COMPLETED.json`. Read preparation `WARNINGS.md`, compiler diagnostics and namespace health. A successful binding checks 24 original namespace functions, five Stan models, lossless numeric export and complete runtime/library identities; it does not itself admit an analysis.

## A differential-splicing request

Save the following JSON as a file. Replace all `/absolute/...` paths. The output must be new. All scientific parameters are explicit; these example values are **not inferred paper settings**.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "edition": "source-2c9907ef66ad",
  "runtime": "/absolute/path/to/preparation/COMPLETED.json",
  "out": "/absolute/path/to/new-analysis",
  "maximum_input_bytes": 67108864,
  "maximum_output_bytes": 536870912,
  "maximum_files": 100000,
  "timeout_seconds": 1200,
  "maximum_audit_operations": 100000000,
  "analyses": [{
    "id": "case-control",
    "kind": "differential",
    "counts": "/absolute/path/to/counts.txt.gz",
    "differential": {
      "groups": "/absolute/path/to/groups.txt",
      "maximum_cluster_size": 10,
      "minimum_samples_per_intron": 5,
      "minimum_samples_per_group": 4,
      "minimum_coverage": 20,
      "cluster_timeout_seconds": 60,
      "threads": 1,
      "initialization": "smart",
      "seed": 4711,
      "sample_selection_policy": "groups-file-order"
    }
  }]
}
```

```sh
java -Xmx3g -cp "$JAR" org.jlinalg.cli.LeafCutterWorkflowCli \
  --request /absolute/path/to/request.json
```

Each batch has 1–32 uniquely named cases. Unknown or unused fields are rejected. Case and junction sample IDs use `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`, are unique under case folding, and junction sample IDs cannot contain `.junc`. Paths resolve relative to the request file, but canonical absolute paths make requests easiest to inspect. Input and output byte/file limits and native operation limits are explicit. Process-group timeouts and sampled capacity checks are enforced; these are not operating-system CPU/memory/disk quotas.

## Input contracts and other stages

**Differential counts** have a header containing **sample IDs only**, one fewer field than each data row:

```text
S1 S2 S3 S4
chr1:100:200:clu_1_+ 12 10 20 19
chr1:120:200:clu_1_+ 18 20 10 11
```

**Ratio counts for phenotype preparation** instead start their header with **`chrom`**:

```text
chrom S1 S2 S3 S4
chr1:100:200:clu_1_+ 12/30 10/30 20/30 19/30
chr1:120:200:clu_1_+ 18/30 20/30 10/30 11/30
```

Do not interchange these formats. Plain or gzip files are supported. Counts are nonnegative decimal signed-64-bit integers. For R model evaluation, selected counts and per-sample cluster sums must also be at most 2^53; clustering retains larger valid int64 counts exactly. Duplicate/ragged rows or ambiguous identifiers fail instead of being repaired.

**Groups** has no header: sample ID, group, then optional covariate columns, separated by whitespace. Original R selects/reorders count columns in groups-file order; count-only donors are explicitly recorded as omitted in `sample-selection.json`, with their original calls retained. Exactly two groups are required. All-numeric/all-logical sample-ID columns are rejected because original R can coerce them into positional indexing; supply a separate, explicit string-ID mapping outside this workflow. Numeric covariates use original sample-SD scaling; factor/logical contrasts retain original level ordering. Group labels must also yield unambiguous output headers. Do not silently relabel them.

The `analyses` item can instead use one of these shapes, retaining the outer request above:

```json
{
  "id": "phenotypes",
  "kind": "phenotype",
  "counts_ratio": "/absolute/path/to/ratios.counts.gz",
  "phenotype": {"pcs": 2, "seed": 4711, "blacklist": ["X", "Y"]}
}
```

```json
{
  "id": "junction-clusters",
  "kind": "cluster",
  "samples": [
    {"sample_id": "S1", "junctions": "/absolute/path/to/S1.junc"},
    {"sample_id": "S2", "junctions": "/absolute/path/to/S2.junc"}
  ],
  "cluster": {
    "maximum_intron_length": 100000,
    "minimum_reads": 30,
    "minimum_ratio": 0.001,
    "check_chromosomes": true
  }
}
```

Junction input is the original RegTools BED12 profile. For example, this is one tab-separated row:

```text
chr1	90	209	read-0	10	+	90	209	0	2	10,10	0,109
```

The block-size field has exactly two integers (`10,10`), with **no trailing comma**, because the original parser unpacks exactly two values. Rows with more than two blocks receive the original exclusion disposition. The workflow does not extract junctions from BAM. For a **pipeline**, set `kind` to `pipeline`, include `samples` and `cluster`, then add `phenotype`, `differential`, or both. Its later stages consume its own original clustering outputs; do not also supply `counts`/`counts_ratio`.

`minimum_reads` gates individual introns as well as clusters in this original script. Current source defaults are 30 reads and 100 kb; paper P201 reports 50 reads and 500 kb while calling them defaults, with the historical release unspecified. Do not infer equivalence. The chromosome blacklist is literal: `X` is distinct from `chrX`. Source phenotype processing adds its pseudocount, marks denominator<1 as missing, filters and mean-imputes only derived outputs, standardizes, and transforms ranks across introns within each sample. Original `.phen` and QQ output order differ and are both preserved. PCA uses **introns as observations and samples as features**, including its original full/randomized solver selection. Undefined explained-variance ratios remain explicit NaN states; do not replace them with zero or interpret zero-rank components as biological covariates.

## Outputs, source behavior and limits

The original files remain under `worker/<case>/`. Every complete output family is listed and hashed in `manifest.json`. `native-audit.json` summarizes independent Java checks; detailed clustering, phenotype and model audit files remain beside original outputs. Differential reports retain every source filter reason, NA table cell, retained optimizer estimate, model objective, effect, LRT/BH value and sample/identifier mapping. No timeout, returned error or nonzero retained optimizer code is admitted as an ordinary filtered cluster.

Optional `differential.exons` supplies the original exon table (`chr start end strand gene_name`). The original endpoint matching ignores strand. This adapter retains that behavior and the supplied annotation; it does not certify assembly correctness or independently reannotate genes. The original `add_chr` decision uses the first ID to decide whether to prefix the whole vector. Raw/reported identities remain distinct in `source-identifier-mapping.json`.

Signaled R warnings/messages are retained prominently without replacing or muffling original handlers. Original `debug=FALSE` suppresses some other optimizer console output, and discarded null-refit return codes are not exposed by the original API. Full RDS files and 17-significant-digit snapshots preserve retained estimates; native calculations do **not** independently optimize/refit models or certify global optima, authentic provenance or biological calibration.

The generated bgzip/tabix preparation script is retained but not executed. QTL engines, LeafViz, BAM extraction, paper-specific masks, historical 0.2.6/0.2.7 binary parity and whole-paper reproduction are outside this adapter. Original repository files carry Apache-2.0 terms; the R package uses GPL>=3, and addon licenses are retained in the setup kit.

Native component references: [clustering](LEAFCUTTER_DEVELOPMENT.md), [phenotype preprocessing](LEAFCUTTER_PHENOTYPES_DEVELOPMENT.md), [model evaluation](LEAFCUTTER_MODELS_DEVELOPMENT.md). Their separate evidence and scope remain unchanged. The project-wide 250-paper implementation goal remains incomplete.
