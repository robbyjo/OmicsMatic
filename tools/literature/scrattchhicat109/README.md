# Original HICAT 1.0.9 modified-DoubletFinder workflow

This adapter runs the unchanged original `doubletFinder` function from
`scrattch.hicat` 1.0.9 and keeps its complete return, conditions and RNG state.
The separate [Java selector](../doublet_score_threshold/README.md) applies the
paper's strict `score > 0.3` removal rule to supplied scores. It computes no
HICAT score. This is a source adapter and Java API; neither adds a root CLI
subcommand.

**Read the warnings before using a result.** Only a final `COMPLETED.json`
admits a public-runner result. An RDS file, an R process with exit zero or a
successful installation alone does not establish a complete analysis.

## Paper and selected original source

P059, [PMC10719114](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719114/),
DOI 10.1038/s41586-023-06812-z, reports modified DoubletFinder in HICAT 1.0.9
and removal of scores strictly greater than 0.3. Its 62 housekeeping genes
describe a **separate QC score**. They are not an established doublet-scoring
gene panel. The other HICAT mentions describe predecessor clustering; this
workflow does not reproduce that clustering.

The selected original commit is
[`24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4`](https://github.com/AllenInstitute/scrattch.hicat/tree/24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4),
dated 2022-05-23, on the historical `dev_zy` branch and labelled 1.0.9.
The paper's actual commit, exact function, gene panel, arguments, RNG state
and environment remain unknown. Selecting this function and environment does
not recover the authors' analysis. Standard DoubletFinder pANN and BIGCAT
marker-doublet detection are separate methods.

**Keep the upstream source and runtime private.** The pinned original
[DESCRIPTION](https://github.com/AllenInstitute/scrattch.hicat/blob/24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4/DESCRIPTION)
labels GPL-3; its
[LICENSE](https://github.com/AllenInstitute/scrattch.hicat/blob/24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4/LICENSE)
contains different Allen Institute terms, including a noncommercial
redistribution restriction. Both are preserved without resolving the
discrepancy. This project distributes its own adapter and source identities;
it does not bundle the upstream source/runtime or a native port of its
scoring algorithm. The current intended use is educational, research or
nonprofit. Retain and follow the original terms for any other use or transfer.

## Install where permitted

Use Linux or a permitted Linux/WSL environment. Supply a permitted Pixi
executable; the installer does not install a package manager. The exact
dependency lock selects R 4.2.3 and 333 package artifacts as a project comparison
environment. HICAT 1.0.9 and RANN 2.6.1 are built privately from unchanged
original source. This is not an inferred author environment.

With installation and downloads permitted:

```sh
python3 -B tools/literature/scrattchhicat109/install_runtime.py \
  --manager /permitted/pixi --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-hicat-install
```

Use the exact provider path recorded in the installer's `COMPLETED.json`.
By default it is a sibling directory ending in `-provider`.

Where downloads are forbidden, omit `--allow-download` and provide both
`--source-cache /private/exact-archives` and a complete permitted package cache.
The archive cache must contain these exact filenames and identities from
[source-profile.json](source-profile.json):

| Package | Exact archive filename |
| --- | --- |
| HICAT 1.0.9 | `scrattch.hicat-24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4.tar.gz` |
| RANN 2.6.1 | `RANN_2.6.1.tar.gz` |

If installation is forbidden, use an already completed exact permitted
provider or skip the dependent analysis. `adopt_runtime.py` audits an existing
private runtime; it neither installs nor runs scoring. It requires the exact
dependency manifest/lock, both complete original source trees and unchanged
installed functions and native libraries. Reuse no output or provider path.
An adapter change requires a NEW audited provider; never refresh old evidence.

## Create a trusted request and run

RDS requests are trusted executable R inputs, not a sandbox. Use the selected
runtime to serialize the original count matrix, explicit gene panel and whole
caller RNG state. Counts are genes by cells, before normalization; supply
the real original row and column identifiers. The following is a constructor
recipe; its files, gene panel and seed are caller choices:

```r
counts <- readRDS("/trusted/raw-counts.rds")
genes <- readLines("/trusted/doublet-scoring-genes.txt", warn = FALSE)
set.seed(715L)
request <- list(
  schema = 1L,
  operation = "doubletFinder",
  arguments = list(data = counts, select.genes = genes),
  rng = list(kind = RNGkind(), seed = .Random.seed),
  caller_packages = character(),
  options = list()
)
saveRDS(request, "/trusted/NEW-request.rds", version = 3)
```

Optional original arguments include `proportion.artificial`, `k` and `plot`.
Omitting them preserves source defaults, including `plot = FALSE`. The only
optional caller attachments are `Matrix` and `ggplot2`; the only supported
caller options are `warn` and `warnPartialMatchDollar`. The adapter does not
invent missing scoring genes, fix labels, coerce sparse counts to dense, select
PCs differently or change unsupported source arguments.

```sh
python3 -B tools/literature/scrattchhicat109/run.py \
  --runtime /private/completed-provider --request /trusted/NEW-request.rds \
  --out /results/NEW-hicat-analysis
```

The original complete return is `original/complete-original-return.rds`: a
list of real-cell and artificial-cell named score vectors for the successful
non-plotting calls. The runner retains the captured request, complete original
conditions, full RNG before/after, source audits, caller context and raw
consoles. Literal identifiers, duplicate names and NA/NaN/Inf remain intact.
The Java selector returns original row positions; the caller keeps identifiers
and distinguishes unknown scores from retained cells.

## Warnings, errors and recovery

**SKIP3: dependent analysis was skipped.** Read the named missing or forbidden
requirement in the console, `WARNINGS.md`, `FAILURE.json` and raw logs. Provide
the exact permitted Linux execution, source, dependency, cache or runtime
requirement and retry NEW paths. Do not treat a skip as zero doublets or a
complete result. Analysis itself never downloads or installs software.

**FAIL2: no complete result is admitted.** This includes original source
errors, altered code/runtime/input, invalid output paths and recording failures
after scoring returned. Retain every original/partial file, resolve the named
cause and retry NEW paths. A leftover return object does not override FAIL2.

The original help-file parsing warning is retained. Actual original-call
controls also retain these failures:

| Trigger | Observed source behavior |
| --- | --- |
| `plot=TRUE`, no caller attachment of ggplot2 | Missing unqualified `aes` function |
| `plot=TRUE`, ggplot2 attached | Missing misspelled `artifical.doublet.score` return variable |
| `k=1` or `k=2` | Matrix dimension dropped before `rowMeans` |
| One artificial cell | Dimension dropped before setting column names |
| No artificial cells | Column-name/array extent mismatch |
| Fewer than ten real cells | Fixed ten-neighbour original query fails |
| Missing gene or NA/Inf counts | Original indexing/PCA error |
| `warnPartialMatchDollar=TRUE, warn=2` | Original `rotatio` partial-match warning becomes an error |
| One dense zero-count column | PCA rejects nonfinite values; the sparse control returns |

An expected-error comparison passing does not admit a result for that input.
Default plotting is FALSE. Changing plotting or sparse representation to evade
an error is not an implicit fallback. Warnings that do not prevent a complete
return remain recorded. R handler totals do not count every native diagnostic.

## Validation scope

Fresh-process direct calls and Worker.R agree on all 26 controls: 14 complete
returns and 12 retained original failures. Comparisons include complete score
objects, names, original conditions/errors and full RNG states. A 10,001-real-
cell control crosses the original sampled-PCA branch; dense/dgC/dgT inputs,
odd neighbourhoods, literal/duplicate/colliding IDs, gene panels and source
failure boundaries are included. The rebuilt original RANN native code also
matches an independent Euclidean-neighbour probe.

Both public installer branches completed, and actual public-runner controls
exercise success, source errors, forbidden prerequisites, existing-output
preservation, stale-provider refusal and a retained post-return recording
fault. The Java selector separately matches literal R comparisons on Windows
and Linux. These are scoped synthetic controls. Whole-package behavior,
predecessor clustering, manual biological decisions and paper reproduction
remain unqualified. Catalog admission and its family accounting are a separate
step; this work adds no credit until that admission is verified.
