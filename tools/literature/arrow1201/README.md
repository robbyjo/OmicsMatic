# Original R Arrow 12.0.1 scoped Parquet workflow

**The source-bound workflow controls pass; catalog admission is pending.**
The selected C++ artifact is the recipe-unpatched `h32ed672_2_cpu` build.
An earlier same-version `h0de2578_50_cpu` candidate declares four backports;
its provisional proof is invalidated and retained unchanged under
`docs/literature/arrow1201-workflow-validation`. The current runner rejects that
patched build with SKIP3 before executing R. The NEW selected qualification is
under `docs/literature/arrow1201-pristine-workflow-validation`.
R Arrow remains a missing family in the current 250-paper catalog. Installing
the dependency or passing a component control adds no integration credit.

P059 ([Nature, DOI 10.1038/s41586-023-06812-z](https://doi.org/10.1038/s41586-023-06812-z))
reports R Arrow 12.0.1 for loading sparse expression data in Parquet chunks of
50,000 cells by 500 genes and querying chunked pairwise cluster DEG results.
Its exact Arrow calls, Parquet schema, memory-map settings and query options are
unreported. The fixed primary XML and reviewed-use record remain unchanged.

This development adapter calls unchanged R Arrow 12.0.1 public functions:

* `read-chunks`: read an ordered caller list of local Parquet files using
  `arrow::read_parquet`, retaining each complete collected R data frame.
* `dataset-query`: use `arrow::open_dataset`, apply caller-declared `dplyr`
  `filter`, `select` and `arrange` steps lazily, and collect the resulting
  complete R data frame. Unsupported Dataset expressions retain the original
  upstream error. No automatic fallback collects an entire Dataset.

RDS preserves returned classes, attributes, column order and integer64 values.
All original condition objects, stack captures, raw console logs and RNG
snapshots are retained. Live Dataset/Table C++ pointers are not persisted.
Only trusted RDS requests should be used: R expressions are executable code.
PyArrow, PacBio Arrow and scrattch.bigcat are separate interfaces. Sparse-matrix
encoding/conversion, author-schema reconstruction and whole-paper reproduction
remain outside the implemented tabular workflow.

## Install in a permitted Linux or WSL environment

The selected locked profile pins R Arrow `r42h59595ed_0` and the recipe-unpatched
Arrow C++ `h32ed672_2_cpu` artifact, both version 12.0.1. It uses
R 4.2.3, dplyr 1.1.4,
jsonlite 1.8.8 and bit64 4.0.5. These dependency choices establish a tested
project environment; they do not establish the authors' environment. The CRAN
source archive is Apache-licensed and all 1,619 original regular source files
are checked. The runtime audit compares 13 loaded I/O/query functions with
their original source AST, and checks package versions and capabilities.

Commands below run **inside Linux/WSL**, from the project root. With the Windows
checkout shown in this project, enter WSL and use `cd /mnt/d/projects/JLinAlg`.
Replace the Pixi path with a permitted executable; the tested manager is 0.83.0.
Every installation and analysis output must be a **new** path.

```sh
python3 -B tools/literature/arrow1201/install_runtime.py \
  --manager /path/to/permitted/pixi \
  --allow-install --allow-download \
  --cache-dir /path/to/permitted/arrow1201-cache \
  --out /path/to/new/arrow1201-install
```

If downloads are forbidden, obtain the exact CRAN
`arrow_12.0.1.tar.gz` in an approved environment, retain its original name and
transfer it with a complete populated Pixi cache. Then install offline:

```sh
python3 -B tools/literature/arrow1201/install_runtime.py \
  --manager /path/to/permitted/pixi --allow-install \
  --source-cache /path/to/original-source-archives \
  --cache-dir /path/to/complete-permitted-pixi-cache \
  --out /path/to/new/offline-arrow1201-install
```

The source archive must be 4,095,630 bytes with SHA-256
`145b78648bd0599f33f7854a3897a58f4bda4c22d329031bf781efb3b74d3d73`.
An incorrect archive fails; the installer does not substitute another edition.
Without `--allow-install`, installation exits **3** and dependent analysis is
skipped. A forbidden or unavailable download also exits **3**. No analysis is
performed by the installer.

**Review installation warnings.** The superseded candidate reported a skipped
UCX post-link script. Every installation retains `locked-dependencies.stderr`;
review its actual messages. Distributed transports are unqualified.
The selected Arrow build reports LZO
unavailable. LZO-dependent input requires a separately permitted and qualified
runtime. Original read failures remain failures; no conversion is performed.

## Prepare and run a request

Construct requests with the installed Rscript. Save the following as an R file,
replace the absolute local paths and column names with your declared schema,
and run it with the installed Rscript. An output request must not overwrite an
older request. These example column names and values are project choices.

```r
stopifnot(!file.exists("/absolute/path/to/new-request.rds"))
request <- list(
  schema_version = 1L,
  operation = "dataset-query",
  cpu_count = 1L,
  arguments = list(
    sources = "/absolute/path/to/assets/deg",
    format = "parquet",
    unify_schemas = TRUE
  ),
  steps = list(
    list(verb = "filter", arguments = list(
      quote(fg_cluster == "C1"), quote(bg_cluster == "C2"),
      quote(adj_p < 0.05)
    )),
    list(verb = "arrange", arguments = list(quote(row_id))),
    list(verb = "select", arguments = list(
      quote(gene_id), quote(log2FC), quote(adj_p), quote(row_id)
    ))
  ),
  collect_arguments = list(as_data_frame = TRUE)
)
saveRDS(request, "/absolute/path/to/new-request.rds", version = 3)
```

For chunk reads, use this request shape instead:

```r
request <- list(
  schema_version = 1L, operation = "read-chunks", cpu_count = 1L,
  chunks = list(
    first = list(file = "/absolute/path/to/assets/chunk-0.parquet",
                 as_data_frame = TRUE),
    second = list(file = "/absolute/path/to/assets/chunk-1.parquet",
                  as_data_frame = TRUE)
  )
)
```

Optional read arguments such as `col_select` are passed directly to the original
function. The supplied order and labels of `chunks` are retained. Reading these
tables does not infer or reconstruct the authors' sparse-matrix encoding.
Dataset source files and directories must be inside `--assets`. Assets are
regular local files; symbolic links and remote/cloud sources are outside this
declared workflow. Requests use absolute Linux paths. `--assets` is completely
inventoried before and after the run, so use the smallest complete input tree.
The request file should be outside the assets directory.

```sh
python3 -B tools/literature/arrow1201/run.py \
  --runtime /path/to/arrow1201-install/provider \
  --request /absolute/path/to/new-request.rds \
  --assets /absolute/path/to/assets \
  --out /path/to/new/arrow1201-analysis
```

Analysis never installs or downloads software. Success requires exit **0**,
`COMPLETED.json`, a matching original-file manifest and no failure/finalization
marker. The original result is `original/complete-result.rds`; use `readRDS()`
with the declared compatible R environment. Read `WARNINGS.md`, the raw console
and `original/complete-conditions.rds` even on success.

## When a run is skipped or fails

| Status | Meaning | Required action |
| --- | --- | --- |
| **SKIP3** | A prerequisite is missing or installation/download is forbidden. Dependent analysis was skipped. | Read the named cause. If permitted, prepare the exact runtime using the installation command; otherwise retain the warning and skip this analysis. Retry with a new output. |
| **FAIL2** | Invalid declaration, original analysis error, changed inputs/runtime or incomplete recording. No complete result is admitted. | Read `FAILURE.json`, `FAILED.json` or `FINALIZATION_ERROR.json` and raw console. Correct the stated cause in a new request/environment, retain the old inputs and partials, then use a new output. |

An unsupported Dataset filter can often be expressed using supported Arrow
functions. Choose that change explicitly in a new request; the adapter never
rewrites an expression or silently collects the full Dataset. Missing columns
require a corrected declared schema. Corrupt Parquet requires a verified
original input. Recording failures require a writable destination with space.
Preserve every partial RDS and historical warning/error.

## Source and interpretation

* [Original CRAN 12.0.1 archive](https://cran.r-project.org/src/contrib/Archive/arrow/arrow_12.0.1.tar.gz)
* [12.0.1 read_parquet](https://arrow.apache.org/docs/12.0/r/reference/read_parquet.html)
* [12.0.1 open_dataset](https://arrow.apache.org/docs/12.0/r/reference/open_dataset.html)
* [12.0.1 multi-file data and lazy queries](https://arrow.apache.org/docs/12.0/r/articles/dataset.html)

On the selected recipe-unpatched build, thirteen original controls produced ten complete returns and three original
failures. Two unchanged comparator controls were accepted and twelve deliberately
altered objects were rejected. Download-enabled and cache-only installations
passed; five real run/recording policy tests and a real UID-65534 denied-subtree
test passed. The policy tests include actual same-version patched-build rejection,
missing/forbidden prerequisites and a receipt failure after a real original
return. Evidence is in
`docs/literature/arrow1201-pristine-workflow-validation/QUALIFICATION.json` and its frozen
bundle. The checkpoint verifier checks every frozen member and replays the saved
comparisons without fitting models or executing new I/O analysis:

```sh
python3 -B tools/literature/arrow1201/verify_checkpoint.py \
  --runtime /path/to/arrow1201-install/provider \
  --out build/literature-review/monocle-runtime/new-arrow1201-check
```

Controls compare complete returns and double payloads, including
signed zero and NA/NaN distinctions. They retain complete original errors.
The missing-column control's rlang stack records its actual caller context;
whole-condition identity with a bare upstream call is false even
though error class, message and call agree. No condition field is removed or
coerced to manufacture equality. Its only differing complete root field is
`trace`; all other fields, attributes and field order agree. This difference is
explicit in qualification. The synthetic 50,000-by-500 wide table tests a file dimension;
it is not a recovered author storage schema or a paper reproduction.

The selected C++ recipe declares no source patches and identifies the official
Apache 12.0.1 source archive. An independent official download matched SHA-256
`3481c411393aa15c75e88d93cf8315faf7f43e180fe0790128d3840d417de858`
(20,172,604 bytes; 6,889 regular files). This is a source-origin and packaged
artifact audit; the C++ binary was not independently rebuilt. Its reported
Git ID `752163190f2c02791addd4cc8c9f95790fab4b13` identifies the conda-forge
feedstock packaging commit, not the upstream Arrow tag commit
`6af660f48472b8b45a5e01b7136b9b040b185eb1`. Those identities are kept separate.
