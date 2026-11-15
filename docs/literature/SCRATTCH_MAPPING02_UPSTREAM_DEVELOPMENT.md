# scrattch.mapping 0.2 prepared-template workflow development

**Development interface. The mapping family remains missing in the catalog and
adds zero family credits. Catalog integration qualification remains pending.**
The prepared-template workflow has passed eleven fresh original-code controls
for strict returned values/RNG and complete saved conditions. A public installer
has completed both permitted-download
and cache-only setup; see the [installation guide](SCRATTCH_MAPPING02_RUNTIME_INSTALLATION.md).
The independent native Java normalization
component is documented in [its own guide](SCRATTCH_MAPPING02_NATIVE_COMPONENTS.md).

**The earlier live-reference identity gate remains failed.** Seven controls match complete returns and three match original
failures, including every analysis condition object and RNG state. The remaining
empty-query error contains an R error-handler closure whose environment holds a
process-specific `externalptr`. Strict identity fails between two independent
original-code calls as well as between the original and adapter. Both calls fail
without a result; their error class, message and handler function body match.
The complete objects and failed strict comparison are retained. No diagnostic
field is discarded or promoted into a passing identity comparison.
The separate saved-workflow comparison covers all eleven controls, including
the complete persisted empty-query error. Four original failures admit no
scientific return; all seven returned objects retain their source values and
warnings. This qualification concerns the prepared-template mapping path.

`tools/literature/scrattchmapping02/run.py` calls the unchanged original
`run_mapping_on_taxonomy` function using a caller's complete prepared taxonomy,
marker indexes and query matrix. It preserves the complete R return, RNG state,
original condition objects and raw consoles. The selected edition is the
[original v0.2 commit](https://github.com/AllenInstitute/scrattch.mapping/tree/df8751129e47a140e2d3ceecfd71438fca1149ef).
This is a selected comparison environment, not a recovered paper environment.
P059 reports v0.2; P058 does not report an edition. P058 must retain that unknown.

The current interface covers correlation distance, serial query blocks and
prepared Annoy indexes. It accepts dense R matrices or `dgCMatrix` queries.
It does not discover markers, build scientific reference taxonomies, retrieve
private Allen files or choose paper parameters. Synthetic controls use the
original index-building and hierarchy functions; they are software controls.

## Prerequisites and skipped analyses

**If any prerequisite or its installation is unavailable or forbidden, skip the
dependent analysis.** `SKIP3` means the required original runtime was unavailable.
`FAIL2` means no complete result is admitted, including when recording fails
after R returns. A successful child process alone is insufficient. Inspect the
terminal process exit, `analysis-status.json`, `FINALIZATION_ERROR.json` if
present, and `WARNINGS.md`. Preserve every failed attempt and retry new paths.

The full profile requires Linux/WSL, the exact committed R 4.2.3 lock, original
scrattch.mapping 0.2 and its dependencies, Python 3.10.22, NumPy 1.24.4, Python
anndata 0.9.2 and a separate real Conda 24.3.0 manager. In particular, the selected
BiocNeighbors 1.16.0 retains `buildAnnoy`; a tested newer dependency removed it.
The original hierarchy uses vector logical conditions that warn in R 4.2 and
can fail in newer R. The adapter does not patch those conditions.

The committed `reference-pixi.*` and `conda-manager-pixi.*` files describe these
two environments. They differ from `native-reference-pixi.*`, which belongs
only to the independent native normalization controls. Do not exchange them.
The full source dependencies include an archived R anndata 0.7.5.6 wrapper and
original annotation data packages; merely installing the Pixi lock is
insufficient. Use the public installer or supply an already installed complete
profile to the auditor below. Analysis installs and downloads nothing.

The original scrattch.io and scrattch.vis dependency licenses restrict commercial
redistribution. Keep their installed runtime private and separate from project
distributions. The unchanged mapping source is GPL-3. The source profile lists
all 134 original mapping files; the runtime auditor also checks all 102
source-defined function bodies and formals, dependency versions and the actual
Python interpreter. It records private runtime/library/Conda-manager file
inventories. It does not establish identity of the operating system.

Old reticulate also needs a compatible Conda layout when using a Pixi prefix:
the selected R/Python prefix has a `condabin` directory and `bin/conda` points to
the separately installed real Conda manager. Setting `RETICULATE_CONDA` alone
was insufficient in the tested old reticulate profile. Do not use a fake Conda
executable, change `.onLoad`, or accept its fallback `/usr/bin/python3`. The
auditor rejects a different actual interpreter.

## Auditing an already installed private runtime

Run these commands inside Linux/WSL. Replace the absolute paths with permitted
local directories. Every `--out` must be new. No command below installs software.

```sh
python3 -B /path/to/OmicsMatic/tools/literature/scrattchmapping02/adopt_runtime.py \
  --prefix /work/private-mapping-runtime \
  --manager-workspace /work/private-conda-manager \
  --source /work/pristine-original-mapping-source \
  --out /work/mapping-provider-001
```

`--prefix` holds the matching `pixi.toml`, `pixi.lock`, `.pixi/envs/default` and
the private original R `library` directory. The manager workspace holds its own
matching manifest, lock and environment. The source directory contains exactly
the original 134 regular source files. The successful provider receipt admits
the runtime only; it is not an analysis or a catalog integration credit.

## Preparing and running a request

All taxonomy, marker-index and external Annoy files must reside in one complete
regular asset directory, with no symlinks. Serialized Annoy indexes contain
absolute Linux paths. Keep those paths intact. To relocate indexes, rebuild them
with the original preparation workflow at new paths and requalify; retain the
old inputs. The adapter inventories the entire asset directory before and after
the analysis and rejects drift. Outputs must be separate from assets, requests,
source and runtime directories.

This R example illustrates the request for a prepared four-level
root/class/subclass/cluster taxonomy. Its seed and numeric parameters are example
choices, not recovered paper arguments. The template's own hierarchy determines
mapping when `Taxonomy=NA`; the wrapper does not rebuild it using `nlevel`.

```r
query <- readRDS("/data/query.rds")
set.seed(42)
request <- list(
  operation = "run_mapping_on_taxonomy",
  arguments = list(
    query.dat = query,
    Taxonomy = NA_character_,
    TaxFN = normalizePath("/data/reference-assets/train.list.rda", mustWork=TRUE),
    mapping.method = "hierarchy", nlevel = 4L,
    iter = 100L, mc.cores = 1L, blocksize = 5000L,
    dist.method = "cor", topk = 1L,
    flag.serial = TRUE, flag.parallel.tmp = FALSE, flag.fuzzy = FALSE
  ),
  rng = list(kind=RNGkind(), seed=.Random.seed)
)
saveRDS(request, "/data/request.rds", version=3)
```

Use only trusted RDS inputs. The `.rda` taxonomy file must contain `train.list`;
its `MI_FN` must point to a file containing the complete `marker_index`, with
every referenced Annoy file present. No identifiers, matrix values or indexes
are repaired or rewritten.

```sh
python3 -B /path/to/OmicsMatic/tools/literature/scrattchmapping02/run.py \
  --runtime /work/mapping-provider-001 \
  --request /data/request.rds \
  --assets /data/reference-assets \
  --out /work/mapping-analysis-001
```

The original R child has a default wall limit of 1,800 seconds, with at most
50,000 workspace files and 2 GiB of workspace file contents. The complete runtime
checks before and after that child are outside its wall timer. Use
`--wall-seconds`, `--maximum-files` and
`--maximum-workspace-bytes` to declare adequate limits. Exceeding a limit fails
the attempt; it does not truncate scientific outputs into an admitted result.
The runner stops its own child process group and retains partial files.

On a successful run, the original `original/complete-result.rds` contains the
public wrapper's whole return, including `map.freq`, `best.map.df`, `cl.df` and
`all.markers`. The separate RNG and condition RDS files retain original types,
names, attributes, warnings and errors. These are R objects, not a portable
numeric JSON reconstruction. Raw console diagnostic totals are unknown; R
handler totals do not enumerate every native diagnostic.

## Original behaviors that require review

The internal recursive predictor can put character sample IDs into `path.cor`.
The public bootstrap wrapper then performs its own overwrite: it returns missing
path correlations for a root/cluster template and calculates correlations when
the deeper template supplies ancestor markers. The adapter preserves both
original paths. It does not coerce the internal field or waive final differences.

Original duplicate cell IDs, single-cell inputs and missing markers have produced
errors in controls. Those errors remain failures. Zero expression has produced a
return together with warnings and nonfinite values. A return does not establish
biological validity; review the complete conditions and data before using it.
Do not rename cells, impute markers, suppress warnings or replace nonfinite
values to obtain a passing adapter run.

The original live-reference comparison uses strict R `identical`: numeric bits,
NA/NaN representations, field and attribute order, the entire return, RNG state,
and every analysis condition object. Its failed empty-query comparison remains
retained. The saved-workflow comparator preserves strict identity for all returned
values and RNG states. Complete conditions first use strict R identity; if that
differs, their complete native-R version-3 serialized representations must be
byte-identical. No original condition field is selected out, converted or deleted.
This verifies the information retained in RDS, including complete calls, classes,
attributes and serialized closure environments. RDS does not retain a live C
error-handler pointer address or recreate its execution context. That identity
is not claimed. Raw compressed RDS file identity is also not claimed.

The original-repeat control demonstrates the same empty-query live-reference
difference and identical complete saved errors. The actual-comparator rejection
script exercises altered numeric values, field order, RNG, NA versus NaN and error
fields. Exact serialization alone can also distinguish storage-sharing details
of R objects that strict R `identical` regards as equal; a strict equal condition
already passes the complete-object comparison.

Reference calls run in fresh R processes;
preparing indexes in the same session changes package-startup diagnostics.
Earlier failed reference comparisons and original runtime failures remain in
the retained development history. The independent native component's historical
case-insensitive path collision and unavailable overwritten RDS remain disclosed
in its own retained-history warning; this workflow does not replace that record.
