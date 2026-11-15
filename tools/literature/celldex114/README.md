# Original celldex 1.14.0 reference retrieval

This adapter runs unchanged original celldex functions using a source-built
**1.14.0** package. It preserves whole R returns, labels, gene/sample identifiers,
conditions/errors, RNG, raw consoles and the complete owned reference cache.
Runtime installation and reference retrieval have separate permission flags.
Only a final public-runner `COMPLETED.json` admits its recorded operation.

The [integration guide](../../../docs/literature/CELLDEX114_REFERENCE_WORKFLOWS.md)
describes source identity, comparison environments, warnings and qualification.
Installation or a source audit alone grants no catalog implementation credit.
The catalog's explicit admission record controls that credit.

**P051 reports celldex 1.14.0 and literally describes MouseRNAseq ImmGen
references. It does not establish the exact functions, reference snapshots,
gene-ID options or complete author environment.** The human-neutrophil heading
and that reference wording remain unchanged. Selecting `ImmGenData()`,
`MouseRNAseqData()` or a dated `fetchReference()` is an explicit project choice.
Do not assert that a chosen reference is the paper's exact input. SingleR
annotation and SPOTlight deconvolution remain separate operations.

## Install a permitted runtime

Use a permitted Linux/WSL environment, an existing permitted Pixi executable
and NEW private paths. This installs original celldex 1.14.0 and its declared
locked comparison dependencies; it retrieves no reference dataset:

```sh
python3 -B tools/literature/celldex114/install_runtime.py \
  --manager /permitted/pixi --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-celldex-install
```

For offline installation, omit `--allow-download` and supply a complete exact
`--cache-dir` and `--source-cache`. The source cache contains the two exact
archive filenames in `source-profile.json`, including GenomeInfoDbData. If
installation is forbidden, use an existing completed exact provider or skip
dependent analysis. Installation errors retain all logs and partial files.
The installer completion record names the provider, a sibling ending in
`-provider`. Do not refresh old provider hashes after code/runtime changes;
preserve the old provider and audit a NEW one.

## Create a trusted original request

Use the selected R runtime. RDS requests are trusted executable R inputs, not
an untrusted-code sandbox. This is an explicitly chosen dated reference, not
an inferred author setting:

```r
set.seed(917L)
request <- list(
  schema = 1L,
  edition = "1.14.0",
  operation = "fetchReference",
  arguments = list(
    name = "immgen", version = "2024-02-26", realize.assays = TRUE
  ),
  rng = list(kind = RNGkind(), seed = .Random.seed),
  options = list(), caller_packages = character(), hub_options = list()
)
saveRDS(request, "/trusted/NEW-request.rds", version = 3)
```

Other original operations include `ImmGenData`, `MouseRNAseqData`, the five
other named celldex reference getters, `fetchMetadata`, `listReferences`,
`listVersions`, `fetchLatestVersion`, `surveyReferences`, `searchReferences`
and the re-exported `defineTextQuery`. Arguments retain the selected original
function's semantics. Only the explicitly documented qualification controls
establish tested coverage; availability of an entry point is not whole-package
validation. Publication/upload and `saveReference` are not exposed by this
retrieval adapter.

Use `arguments = list()` with `operation = "ImmGenData"` or
`"MouseRNAseqData"` for those original defaults. In this source release,
default nonlegacy calls with all ontology labels use the dated gypsum backend;
`legacy=TRUE` or another `cell.ont` mode uses the original ExperimentHub path.
`ensembl=TRUE` uses original AnnotationHub/ensembldb mapping and may warn,
drop unmapped genes or remove duplicate mapped IDs under the original code.
This is original selected behavior, not an adapter identifier repair. No caller
argument is silently substituted or ignored.

## Retrieve and retain a reference

Reference retrieval intentionally downloads data only with explicit permission:

```sh
python3 -B tools/literature/celldex114/run.py \
  --runtime /private/NEW-celldex-install-provider \
  --request /trusted/NEW-request.rds --allow-download \
  --out /results/NEW-reference
```

For offline retrieval, omit `--allow-download` and supply a complete seed cache:

```sh
python3 -B tools/literature/celldex114/run.py \
  --runtime /private/completed-celldex-provider \
  --request /trusted/NEW-request.rds \
  --reference-cache /preserved/reference-cache \
  --out /results/NEW-offline-reference
```

For cached legacy or Ensembl operations, explicitly select the original Hubs'
local mode in the request before saving it:

```r
request$hub_options <- list(
  ExperimentHub = list(LOCAL = TRUE),
  AnnotationHub = list(LOCAL = TRUE)
)
saveRDS(request, "/trusted/NEW-local-request.rds", version = 3)
```

Use that NEW request in the offline command. Without these caller options,
the original Hub defaults still check remote services even with cached data;
in a network-isolated process they can warn and error. The adapter does not
silently change those defaults or admit a result for that error. `LOCAL=TRUE`
requires the complete intended Hub cache, including its database/index and
resource files. It is a project caller choice, not a recovered paper option.

The seed's top level contains `gypsum/`, `ExperimentHub/` and/or
`AnnotationHub/`, as needed. It is copied into the NEW output's
`reference-cache/`; the original seed must remain byte unchanged. Absolute or
external cache links require an explicit self-contained input copy. The cache
is managed through original gypsum/Hub configuration APIs, so omit literal
`cache` arguments from the RDS request. Optional `hub_options` supplies named
original ExperimentHub/AnnotationHub options other than `CACHE`; record the
intended snapshot date explicitly if it is known.

Offline R children run under a network namespace. If the environment cannot
provide that isolation, dependent retrieval is skipped with SKIP3; no silent
network fallback occurs. Original remote listing/latest operations require
network permission. Analysis never installs runtime software. A reference
cache is an input with its own provenance; a local COMPLETE marker is not
cryptographic authentication of an upstream source.

The full original result is `original/complete-original-return.rds`. Read it
with the selected permitted R environment. The complete conditions, RNG before
and after, separate pre-call RNG, caller context, namespace audits and raw
consoles are retained. `complete-cache-before.json` and
`complete-cache-after.json` identify all retained cache files.

**Lazy references require their backing files.** A default `fetchReference`
return may contain ReloadedArray objects with file paths. Keep the owned
`reference-cache/` intact at the recorded paths. Serializing an R object does
not embed every lazy file or restore a live external pointer. If portability is
needed, explicitly request the original `realize.assays=TRUE` behavior before
the call; the adapter never silently realizes or rewrites an original return.

## Warnings, errors and recovery

**SKIP3: dependent retrieval was skipped.** Read the named missing or forbidden
source, dependency, installation, reference, network-isolation or download
requirement. Supply that exact permitted requirement and retry NEW paths.
Cached retrieval is separate from installing software. A skip is not an empty
reference or biological result.

**FAIL2: no complete result is admitted.** This includes original errors,
invalid arguments, malformed caches, source/runtime/input changes, native
crashes and recording failures after an original return. Preserve all original
and partial files. Read `WARNINGS.md`, `FAILURE.json`, `original/R-status.json`,
complete original conditions and both raw consoles. Resolve the stated original
input or filesystem cause and retry NEW paths. A leftover RDS or successful R
process is insufficient. Missing records after a crash are unknown, never zero.

Read every original warning, especially Ensembl mapping warnings. The adapter
retains original warning policy; `options = list(warn=2L)` may turn warnings into
original errors. It does not change warning policy, repair source mistakes,
impute missing values, trim IDs or select substitute references. Comparisons
must distinguish whole object equality, lazy payload equality and any retained
caller-context differences. Synthetic or public-reference controls establish
no whole-tool validity, author input identity or paper reproduction.
