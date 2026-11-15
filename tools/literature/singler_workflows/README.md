# Original SingleR annotation workflows

This source adapter runs the complete unchanged original `SingleR` annotation
call. It supports five explicitly selected source releases, with separately
locked comparison environments. These environments are project choices, not
recovered author environments. Reference expression objects and their labels
must be supplied by the caller. Analysis downloads and installs nothing.

**Current scoped qualification and catalog availability are recorded in the
[integration guide](../../../docs/literature/SINGLER_CATALOG_INTEGRATION.md).
A successful installation alone adds no catalog credit.** Read the qualification
record for the exact controls that passed. Synthetic controls do not establish
biological accuracy, whole-package validity or reproduction of a paper.

## Select the intended release

| Original SingleR release | Bioconductor source release | Comparison R |
| --- | --- | --- |
| 1.2.4 | 3.11 | 4.0.5 |
| 1.8.1 | 3.14 | 4.1.3 |
| 2.2.0 | 3.17 | 4.3.3 |
| 2.6.0 | 3.19 | 4.3.3 |
| 2.8.0 | 3.20 | 4.4.3 |

Every selected SingleR package is built from its complete, unchanged original
Bioconductor archive. The 2.6.0 environment also builds original beachmat 2.20.0
and BiocNeighbors 1.22.0 into the private library. Each profile explicitly
installs the required GenomeInfoDbData archive; package-manager completion alone
does not establish that this R data package is available. The 1.2.4 profile also
builds unchanged DelayedMatrixStats 1.10.0 against the selected matrixStats
0.62.0. The original dependency binary captured an older native calling
interface and crashed even on finite row medians; that failed environment is
retained and is not qualified. Rebuilding the same dependency release restores
its original method delegation without changing source algorithms or versions.
The hashes, complete
source-file identities, original license declarations and exact archive names
are in [source-profiles.json](source-profiles.json).

P051 reports 2.8.0 and 2.6.0 in different analyses, with a third SingleR analysis
that does not state a release at its method anchor. P082 declares 2.2.0 in its
software inventory. P192 reports 1.2.4 for KOBS and 1.8.1 for RYSA. Do not borrow
one analysis's release, reference or arguments for another.

**P233 literally reports `2.41`. That spelling remains unresolved.** Neither
this adapter nor the installer interprets it as 2.4.1. Requesting that edition
produces SKIP3 and skips the dependent analysis. An explicitly selected other
release is a separate project analysis and supplies no parity claim for `2.41`.
The paper describes SingleR alongside a Seurat PBMC reference. The inspected
author notebook separately calls SingleR with HPCA and Blueprint/ENCODE main
and fine labels and performs a separate Seurat PBMC mapping. Its actual
executed commit, package release and reference snapshots remain unknown.

## Install where permitted

Supply a permitted Linux/WSL environment and an existing permitted Pixi
executable. The installer neither installs a package manager nor requests
registration details. Use a NEW private workspace:

```sh
python3 -B tools/literature/singler_workflows/install_runtime.py \
  --edition 2.8.0 --manager /permitted/pixi \
  --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-singler-install
```

For offline installation, omit `--allow-download` and supply both a complete
`--cache-dir` and `--source-cache`. The source cache must contain every exact
`archive_filename` from the selected profile in source-profiles.json, including
GenomeInfoDbData and any declared source-built dependencies. Offline installation
does not enable package-manager post-link downloads. Where installation is
forbidden, use an existing exact completed provider or skip the dependent
analysis. The installer records all commands and complete raw consoles and
retains failures and partial files. Only its final `COMPLETED.json` identifies
the resulting provider; the provider is a sibling ending in `-provider`.

`adopt_runtime.py` can audit an already source-built private workspace. Supply
`--edition`, `--workspace`, a NEW `--out` provider and one `--source
PACKAGE=/absolute/pristine/source` argument for every selected profile package.
The workspace requires `runtime/pixi.toml`, `runtime/pixi.lock`, the matching
prefix at `runtime/.pixi/envs/default` and a separate source-built `library`.
The audit checks complete source trees, installed namespace functions and S4
method signatures, dispatch wrappers and captured factory delegates. It records
complete prefix/library inventories and verifies active native-library paths.
It installs nothing and performs no annotation. Changed adapter code requires
a NEW audit/provider; never refresh an old provider or its evidence hashes.

## Create a trusted request

Use the selected runtime to read the exact original inputs and serialize the
request. RDS is a trusted executable input format; this adapter is not a code
sandbox. Matrices use genes in rows and cells or reference samples in columns.
Use original identifiers and reference labels. For SummarizedExperiment or
SingleCellExperiment inputs, supply any intended original assay arguments.
The example seed is a caller choice:

```r
test <- readRDS("/trusted/query-expression.rds")
ref <- readRDS("/trusted/reference-expression.rds")
labels <- readRDS("/trusted/reference-labels.rds")
set.seed(917L)
request <- list(
  schema = 1L,
  edition = "2.8.0",
  operation = "SingleR",
  arguments = list(test = test, ref = ref, labels = labels),
  rng = list(kind = RNGkind(), seed = .Random.seed),
  caller_packages = character(),
  options = list()
)
saveRDS(request, "/trusted/NEW-request.rds", version = 3)
```

The original `SingleR` call performs its marker selection, reference training,
quantile correlation scoring, optional fine tuning and optional pruning. Supply
intended original arguments in `arguments`; omitted arguments retain the chosen
release's own defaults. Multiple references, reference aggregation and query
clusters use the original functions' semantics. No identifier repair, reference
substitution, implicit log normalization or argument fallback is added. If the
original source removes missing genes under `check.missing`, that original
behavior is retained together with the complete original request.

```sh
python3 -B tools/literature/singler_workflows/run.py \
  --edition 2.8.0 --runtime /private/completed-provider \
  --request /trusted/NEW-request.rds --out /results/NEW-annotation
```

The complete return is `original/complete-original-return.rds`. All fields,
scores, labels, pruned labels, metadata and attributes are retained. The request,
complete original conditions, RNG before/after, source audits, caller context
and raw consoles are also retained. NA/NaN/Inf and duplicate/literal identifiers
remain intact. Inspect the original return rather than assuming a common output
schema across releases and options.

The pre-call RNG state is saved separately before invoking the original
analysis. A native crash may prevent recording the later RNG state and R
condition/status files. Such an absence is not a zero count or an empty result;
the public runner reports the actual original process exit and admits no result.

Complete captured objects are compared through their version-3 serialized
representations, including environment contents and reference topology. Fresh
SerialParam environments have distinct memory identities. Neither their fields
nor captured condition calls or function bodies are removed for comparison.

The adapter also exposes original `aggregateReference`, `getDeltaFromMedian`
and `pruneScores` calls with `operation` set to that original function name.
These operations do not substitute for annotation.

## Train and classify in the same process

For explicit separate training and classification arguments, choose
`operation = "train-classify"` and set:

```r
request$arguments <- list(
  training = list(ref = ref, labels = labels),
  classification = list(test = test)
)
```

The adapter calls unchanged `trainSingleR`, records its complete original
return, and passes that live model to unchanged `classifySingleR` in the same
process. It retains every field, including any native external pointers.
**A serialized external pointer is not a reusable live model.** Reading the
training RDS in another process does not restore its native index. Use the
original training inputs to train again in the new process; do not claim that
equal serialized pointer placeholders establish equal live native indexes.

## Warnings, errors and required action

**SKIP3: dependent analysis was skipped.** Read the named unavailable or
forbidden exact source, dependency, runtime, Linux, download or installation
requirement. Supply that permitted requirement and retry NEW writable paths.
An unknown release such as literal `2.41` requires source/version clarification;
choosing another release does not resolve the paper's declaration. Missing
references must be supplied explicitly; a current online reference is not an
automatic replacement. A skip is not an annotation or an empty biological result.

**FAIL2: no complete result is admitted.** This includes original analysis
errors, altered source/runtime/input, invalid output paths and recording failures
after an original return. Preserve all original and partial files. Read
`FAILURE.json`, `WARNINGS.md`, `original/R-status.json` (`error.message`), the
complete original conditions and both raw consoles. Resolve the named cause
and retry NEW paths. An RDS file or zero R-process exit is insufficient.
Only final public-runner `COMPLETED.json` admits that recorded operation.

Original errors are retained without source repair or a version fallback.
Warnings that allow a complete original return remain recorded. R-handler
counts do not count every native diagnostic. This adapter supplies no root Java
CLI subcommand, current celldex reference download, author reference snapshot,
manual cell removal decision, full package qualification or paper reproduction.
