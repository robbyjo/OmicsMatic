# Original BIGCAT 0.0.5 workflows

**Read [current qualification and catalog status](CATALOG_STATUS.md) before use.**
This is scoped operation coverage, with original failures and source-profile
limits retained. Whole-tool or whole-paper reproduction is not claimed.
The separate native Java marker-doublet component is
documented in [../scrattchbigcat005/README.md](../scrattchbigcat005/README.md).

The adapter runs unchanged `scrattch.bigcat` functions in a fresh Linux R process,
with explicit original source and dependency profiles. It retains the complete
R return, conditions, RNG states, source audits, console output and workspace
files. Inputs are trusted RDS objects; this is not a sandbox for untrusted R
serialization or arbitrary user code.

The reported uses come from [P058](https://doi.org/10.1038/s41586-023-06817-8) and
[P059](https://doi.org/10.1038/s41586-023-06812-z). P059 reports version 0.0.5.
P058's edition and both papers' actual Git commits and caller environments are
unknown. The following public snapshots are project choices.

| Explicit profile | Original source | Selected R | Material limits |
| --- | --- | --- | --- |
| `prepublication-2023` | `bbfe5277de63c65a5342e43f3e1eeb1015755fc7`, 3 October 2023 | 4.2.3 | The original `compute_knn` indexing error prevents ordinary two-dimensional harmonization. Default-vector mapping and the free-variable imputation defaults can also fail. |
| `pre-regression-2022` | `5134c0b0a107a724f7d1f091c23a73ab691b4a66`, 8 December 2022 | 4.1.3 | Earlier original harmonization needs explicit caller `sets`; R retains its vector-condition warnings. This snapshot does not contain `impute_cross_big`. |

Both profiles use Arrow 12.0.1 with the specifically declared C++ artifact.
The R 4.1 profile builds the unchanged CRAN R source. A same-version replacement
with different recipe patches is not accepted. Original source members, complete
runtime inventories and same-name namespace function bodies are checked; failed
checks must not be bypassed by refreshing saved hashes.

## Installation

Use permitted Linux x86-64 or WSL, Python 3.9 or newer, and a permitted Pixi manager.
Use a new private directory. The install command executes no paper analysis.

```bash
python3 -B tools/literature/scrattchbigcat005_workflows/install_runtime.py \
  --profile prepublication-2023 --manager /absolute/path/to/pixi \
  --out /absolute/new/bigcat-runtime --allow-install --allow-download
```

To install without downloading, omit `--allow-download` and supply
`--source-cache /absolute/archive-cache --cache-dir /absolute/pixi-cache`.
The source cache must contain every exact archive named in `profiles.json`,
including Arrow. The package cache must contain all locked dependencies.
Installation preserves publisher executable modes, including Arrow's configure
scripts. Source copies used for compilation are separate from original sources.

**If installation or downloading is forbidden, do not run the dependent
analysis.** Missing or forbidden prerequisites produce exit 3 (`SKIP3`) and
`WARNINGS.md`, with the cause and recovery action. Supply an allowed complete
runtime/cache or change the execution environment, then retry in new directories.
Neither analysis nor adoption silently installs software.

An existing installation can be audited with `adopt_runtime.py`. Supply its
private runtime workspace, explicit profile, every declared package's unchanged
source through repeated `--source PACKAGE=/absolute/source` arguments, and a new
`--out` provider directory. A completed installer records the provider path in
its `COMPLETED.json`. An audit is readiness evidence, not an analysis result.

## A small request

This R code creates a dense marker-doublet request. Keep exact biological IDs in
real inputs; this example uses synthetic names and values.

```r
set.seed(1729L)
request <- list(
  schema_version=1L, profile="prepublication-2023",
  operation="find_doublet_by_marker",
  arguments=list(
    cl.means=matrix(c(5,0,0,5,5,5),nrow=2L,
      dimnames=list(c("g1","g2"),c("A","B","mixed"))),
    markers=list(A="g1",B="g2")),
  rng=list(kind=RNGkind(),seed=.Random.seed), globals=list(),
  preparation=list(),post_selection=NULL,arrow_cpu_count=1L)
saveRDS(request,"marker-request.rds",version=3)
```

Create an empty assets directory for this example, then run:

```bash
python3 -B tools/literature/scrattchbigcat005_workflows/run.py \
  --runtime /absolute/path/to/completed-provider \
  --request /absolute/path/to/marker-request.rds \
  --assets /absolute/path/to/empty-assets --out /absolute/new/marker-result
```

Read `WARNINGS.md` first. Exit 2 (`FAIL2`) admits no complete result, including
when the original operation returned but recording failed. Preserve all partial
files and logs, fix the named input/environment/output problem, and retry with
new writable paths. Only a final `COMPLETED.json` admits the recorded operation.
R-level success alone is insufficient. An original NULL return is disclosed and
does not establish a clustering result. Complete diagnostic counts from native
libraries and persistence of live R pointers are not qualified.

Independent processes can produce nonidentical FBM class-definition identities
or cyclic plot environments. Preserve and inspect those complete objects and
comparison differences; equality of numeric backing files does not establish
live-pointer persistence or whole R-object identity.

## File-backed data and compound requests

`Request.R` supports explicit original Parquet, FBM and Annoy-index constructor
recipes. Path placeholders resolve inside a new mutable workspace; references
select an earlier recipe's complete original object. Mutable imputation must
use an FBM created in that workspace. Protected assets remain read-only.

`centroid_cross_validation` takes explicit fold assignments and marker panels.
It calls original Parquet summaries over every training cell, then original
index building and nearest-centroid mapping for each fold/panel. It does not use
the separate summary helper's 200-cell sampling default. Incomplete query
coverage produces an unknown accuracy; missing IDs are not silently dropped.
Source distances retain their original meaning and values.

`constellation_from_embedding` calls the original graph and plotting functions
with an explicit outside-neighbour fraction threshold. The original graph drops
rank one. Requesting `k=16` therefore yields 15 retained ranks; this is an explicit
project choice, not a recovered author argument. All within-type rows remain
available to the plot for node metadata.

Returned Annoy files in the owned temporary directory are copied and checksummed
before R cleanup. `complete-original-asset-links.rds` records original paths and
retained copies. Original object fields are not rewritten; a retained index file
does not establish that its serialized R object can be reused in another process.

Optional SPN selection runs **after** original integration, at an explicitly
chosen hierarchy level, and retains clusters with at least the requested number
of SPN nuclei. The five-nucleus rule is not a clustering parameter. This selection
does not perform or establish the papers' subsequent reciprocal correspondence
mapping or the separate fewer-than-three-mapped-cells exclusion.

`GenerateWorkflowControls.R` creates synthetic requests for all eight named
operations and the two compound workflows. `GenerateDirectReference.R` records
independent original calls; `CompareWorkflowReferences.R` retains full comparison
differences and checks training means independently. These are development
controls, not author datasets or biological validation. The qualification record
and saved-result replay command are linked from `CATALOG_STATUS.md`.
